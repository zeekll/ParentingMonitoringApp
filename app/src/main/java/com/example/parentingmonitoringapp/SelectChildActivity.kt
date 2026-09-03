package com.example.parentingmonitoringapp

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/**
 * "Select Child / Main View" step of the parent flow: Parent Login -> Select
 * Child -> Dashboard. Shown when a parent has more than one linked child, so
 * they can pick which child's records to view, and lets them link an
 * additional child from here as well.
 *
 * Launch with EXTRA_FROM_LOGIN = true when arriving straight after sign-in
 * (so picking a child clears the back stack into the dashboard); otherwise
 * it's treated as a "switch child" screen and simply finishes after picking.
 */
class SelectChildActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FROM_LOGIN = "fromLogin"
    }

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var tvStatus: TextView
    private lateinit var container: LinearLayout

    private var fromLogin = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_child)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        tvStatus = findViewById(R.id.tvStatus)
        container = findViewById(R.id.childrenContainer)
        fromLogin = intent.getBooleanExtra(EXTRA_FROM_LOGIN, false)

        findViewById<TextView>(R.id.btnAddChild).setOnClickListener { showLinkChildDialog() }

        loadChildren()
    }

    private fun loadChildren() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            tvStatus.text = "Not logged in."
            return
        }

        tvStatus.text = "Loading your children..."
        db.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                val linkedIds = ChildLinkStore.getLinkedStudentIds(userDoc)
                if (linkedIds.isEmpty()) {
                    tvStatus.text = "No linked children yet. Use \"Link another child\" below to add one."
                    return@addOnSuccessListener
                }

                val selected = ChildLinkStore.getSelectedStudentId(this, uid)
                tvStatus.text = "${linkedIds.size} linked ${if (linkedIds.size == 1) "child" else "children"}:"
                container.removeAllViews()

                for (studentId in linkedIds) {
                    db.collection("students").document(studentId).get()
                        .addOnSuccessListener { studentDoc ->
                            val name = studentDoc.getString("studentName") ?: studentId
                            val course = studentDoc.getString("course") ?: "-"
                            val section = studentDoc.getString("section") ?: "-"
                            addChildRow(uid, studentId, name, "$course - $section", studentId == selected)
                        }
                        .addOnFailureListener {
                            addChildRow(uid, studentId, studentId, "Details unavailable", studentId == selected)
                        }
                }
            }
            .addOnFailureListener {
                tvStatus.text = "Failed to load your children: ${it.localizedMessage}"
            }
    }

    private fun addChildRow(
        parentUid: String, studentId: String, name: String, subtitle: String, isSelected: Boolean
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(if (isSelected) Color.parseColor("#E4E0FB") else Color.parseColor("#F6F4FE"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8 }
            isClickable = true
            isFocusable = true
        }

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val tvName = TextView(this).apply {
            text = name
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#22223B"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(tvName)
        if (isSelected) {
            header.addView(TextView(this).apply {
                text = "✓ Selected"
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#3F3D9E"))
            })
        }
        row.addView(header)

        row.addView(TextView(this).apply {
            text = subtitle
            textSize = 13f
            setTextColor(Color.parseColor("#6B7280"))
        })

        row.setOnClickListener { selectChild(parentUid, studentId) }
        container.addView(row)
    }

    private fun selectChild(parentUid: String, studentId: String) {
        ChildLinkStore.setSelectedStudentId(this, parentUid, studentId)
        if (fromLogin) {
            val intent = Intent(this, ParentDashboardActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
        finish()
    }

    private fun showLinkChildDialog() {
        val padding = (20 * resources.displayMetrics.density).toInt()
        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        val etStudentId = EditText(this).apply { hint = "Student ID" }
        val etDob = EditText(this).apply {
            hint = "Student date of birth (YYYY-MM-DD)"
            inputType = InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_DATE
        }
        dialogLayout.addView(etStudentId)
        dialogLayout.addView(etDob)

        AlertDialog.Builder(this)
            .setTitle("Link another child")
            .setMessage("Enter the child's Student ID and date of birth to verify and link their record to your account.")
            .setView(dialogLayout)
            .setPositiveButton("Link") { dialog, _ ->
                val studentId = etStudentId.text.toString().trim()
                val dob = etDob.text.toString().trim()
                if (studentId.isEmpty() || dob.isEmpty()) {
                    Toast.makeText(this, "Enter both Student ID and date of birth.", Toast.LENGTH_SHORT).show()
                } else {
                    verifyAndLinkChild(studentId, dob)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun verifyAndLinkChild(studentId: String, dob: String) {
        val uid = auth.currentUser?.uid ?: return

        db.collection("students").document(studentId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Toast.makeText(this, "Student ID not found.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                val recordDob = doc.getString("dob")?.trim().orEmpty()
                if (recordDob.isEmpty() || recordDob != dob) {
                    Toast.makeText(this, "Student details do not match our records.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                val existingParentUid = doc.getString("parentUid")
                if (!existingParentUid.isNullOrEmpty() && existingParentUid != uid) {
                    Toast.makeText(this, "This student is already linked to another parent account.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                val parentRef = db.collection("users").document(uid)
                val studentRef = db.collection("students").document(studentId)
                db.runBatch { batch ->
                    batch.update(parentRef, "studentIds", FieldValue.arrayUnion(studentId))
                    batch.update(studentRef, mapOf(
                        "parentUid" to uid,
                        "linkedAt" to FieldValue.serverTimestamp()
                    ))
                }
                    .addOnSuccessListener {
                        Toast.makeText(this, "Child linked successfully.", Toast.LENGTH_SHORT).show()
                        ChildLinkStore.setSelectedStudentId(this, uid, studentId)
                        loadChildren()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to link child: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Unable to verify student details.", Toast.LENGTH_LONG).show()
            }
    }
}