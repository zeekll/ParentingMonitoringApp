package com.example.parentingmonitoringapp

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Admin > Manage Users: View / Add / Edit / Delete user accounts.
 *
 * NOTE on Delete: the client SDK cannot delete another user's Firebase Auth
 * credential (that requires the Admin SDK / a Cloud Function). "Delete" here
 * removes the Firestore users/{uid} record (and, for students, offers to also
 * remove the students/{studentId} record) so the account loses all app access
 * and data, but the login credential itself will need a server-side cleanup
 * job to fully remove.
 */
class ManageUsersActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var tvStatus: TextView
    private lateinit var userListContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_users)

        db = FirebaseFirestore.getInstance()
        tvStatus = findViewById(R.id.tvStatus)
        userListContainer = findViewById(R.id.userListContainer)

        findViewById<MaterialButton>(R.id.btnAddUser).setOnClickListener {
            startActivity(Intent(this, AddUserActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadUsers() // refresh after returning from Add/Edit
    }

    private fun loadUsers() {
        tvStatus.text = "Loading users…"
        userListContainer.removeAllViews()

        db.collection("users").get()
            .addOnSuccessListener { docs ->
                if (docs.isEmpty) {
                    tvStatus.text = "No users found."
                    return@addOnSuccessListener
                }
                tvStatus.text = "${docs.size()} user(s)"
                for (doc in docs.documents) {
                    userListContainer.addView(buildUserRow(doc))
                }
            }
            .addOnFailureListener {
                tvStatus.text = "Failed to load users: ${it.localizedMessage}"
            }
    }

    private fun buildUserRow(doc: DocumentSnapshot): LinearLayout {
        val uid = doc.id
        val name = doc.getString("name") ?: "(no name)"
        val email = doc.getString("email") ?: "-"
        val role = doc.getString("role") ?: "parent"

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(28, 24, 20, 24)
            setBackgroundColor(android.graphics.Color.parseColor("#F6F4FE"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 10 }
        }

        val infoCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val nameRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        nameRow.addView(TextView(this).apply {
            text = name
            textSize = 15f
            setTextColor(ContextCompat.getColor(this@ManageUsersActivity, R.color.ink))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        nameRow.addView(TextView(this).apply {
            text = "  •  ${role.replaceFirstChar { it.uppercase() }}"
            textSize = 12f
            setTextColor(roleColor(role))
        })

        infoCol.addView(nameRow)
        infoCol.addView(TextView(this).apply {
            text = email
            textSize = 12.5f
            setTextColor(ContextCompat.getColor(this@ManageUsersActivity, R.color.ink_soft))
        })
        if (role == "student") {
            val studentId = doc.getString("studentId")
            if (!studentId.isNullOrEmpty()) {
                infoCol.addView(TextView(this).apply {
                    text = "ID: $studentId"
                    textSize = 11.5f
                    setTextColor(ContextCompat.getColor(this@ManageUsersActivity, R.color.ink_soft))
                })
            }
        }

        val actionsCol = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        actionsCol.addView(smallTextButton("Edit", R.color.indigo_500) {
            val intent = Intent(this@ManageUsersActivity, EditUserActivity::class.java)
            intent.putExtra("uid", uid)
            startActivity(intent)
        })
        actionsCol.addView(smallTextButton("Delete", R.color.danger) {
            confirmDelete(doc)
        })

        row.addView(infoCol)
        row.addView(actionsCol)
        return row
    }

    private fun smallTextButton(label: String, colorRes: Int, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@ManageUsersActivity, colorRes))
            setPadding(20, 10, 20, 10)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    private fun roleColor(role: String): Int {
        val res = when (role) {
            "admin" -> R.color.indigo_500
            "student" -> R.color.success
            else -> R.color.danger
        }
        return ContextCompat.getColor(this, res)
    }

    private fun confirmDelete(doc: DocumentSnapshot) {
        val uid = doc.id
        val name = doc.getString("name") ?: uid
        val role = doc.getString("role") ?: "parent"

        val linkedStudentIds: List<String> = when (role) {
            "parent" -> ChildLinkStore.getLinkedStudentIds(doc)
            else -> emptyList()
        }

        AlertDialog.Builder(this)
            .setTitle("Delete $name?")
            .setMessage(
                "This removes their app data and access immediately. " +
                        "Note: their login credential itself can only be fully deleted " +
                        "by the school's Firebase administrator (client apps can't delete " +
                        "another user's login from here)."
            )
            .setPositiveButton("Delete") { _, _ -> performDelete(uid, linkedStudentIds) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Deletes the users/{uid} profile. If this was a parent account,
     * also un-links them from any student master records so those students
     * can be relinked to a new parent later. (For a deleted student account,
     * the students/{studentId} academic record is left intact on purpose —
     * attendance/grades reference it by studentId, not by uid.)
     */
    private fun performDelete(uid: String, parentLinkedStudentIds: List<String>) {
        val batch = db.batch()
        batch.delete(db.collection("users").document(uid))

        for (studentId in parentLinkedStudentIds) {
            batch.update(
                db.collection("students").document(studentId),
                mapOf("parentUid" to com.google.firebase.firestore.FieldValue.delete())
            )
        }

        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(this, "User removed.", Toast.LENGTH_SHORT).show()
                loadUsers()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to delete: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
            }
    }
}