package com.example.parentingmonitoringapp

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Admin > Manage Users > Edit User.
 *
 * Scope is intentionally limited to what the client SDK can safely change:
 * display name, and (for students) their course/section. Email, password and
 * role are locked - changing those for someone else's account needs the
 * Firebase Admin SDK, which isn't available from a mobile client.
 */
class EditUserActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var uid: String
    private var role: String = "parent"
    private var studentId: String? = null

    private lateinit var tvRoleBadge: TextView
    private lateinit var etFullName: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var groupStudentFields: LinearLayout
    private lateinit var etCourse: TextInputEditText
    private lateinit var etSection: TextInputEditText
    private lateinit var tvError: TextView
    private lateinit var btnSave: MaterialButton
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_user)

        db = FirebaseFirestore.getInstance()
        uid = intent.getStringExtra("uid") ?: run {
            Toast.makeText(this, "Missing user.", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        tvRoleBadge = findViewById(R.id.tvRoleBadge)
        etFullName = findViewById(R.id.etFullName)
        etEmail = findViewById(R.id.etEmail)
        groupStudentFields = findViewById(R.id.groupStudentFields)
        etCourse = findViewById(R.id.etCourse)
        etSection = findViewById(R.id.etSection)
        tvError = findViewById(R.id.tvError)
        btnSave = findViewById(R.id.btnSave)
        progressBar = findViewById(R.id.progressBar)

        btnSave.setOnClickListener { saveChanges() }

        loadUser()
    }

    private fun loadUser() {
        setLoading(true)
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                setLoading(false)
                if (!doc.exists()) {
                    Toast.makeText(this, "User not found.", Toast.LENGTH_SHORT).show()
                    finish(); return@addOnSuccessListener
                }
                role = doc.getString("role") ?: "parent"
                studentId = doc.getString("studentId")

                tvRoleBadge.text = "Role: ${role.replaceFirstChar { it.uppercase() }}"
                etFullName.setText(doc.getString("name") ?: "")
                etEmail.setText(doc.getString("email") ?: "")

                if (role == "student" && !studentId.isNullOrEmpty()) {
                    groupStudentFields.visibility = View.VISIBLE
                    loadStudentRecord(studentId!!)
                }
            }
            .addOnFailureListener {
                setLoading(false)
                Toast.makeText(this, "Failed to load user: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                finish()
            }
    }

    private fun loadStudentRecord(id: String) {
        db.collection("students").document(id).get()
            .addOnSuccessListener { doc ->
                etCourse.setText(doc.getString("course") ?: "")
                etSection.setText(doc.getString("section") ?: "")
            }
    }

    private fun setLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnSave.isEnabled = !isLoading
    }

    private fun showError(message: String) {
        tvError.text = message
        tvError.visibility = View.VISIBLE
    }

    private fun saveChanges() {
        val fullName = etFullName.text.toString().trim()
        tvError.visibility = View.GONE

        if (fullName.isEmpty()) {
            showError("Name can't be empty.")
            return
        }

        setLoading(true)
        val batch = db.batch()
        batch.update(db.collection("users").document(uid), mapOf("name" to fullName))

        if (role == "student" && !studentId.isNullOrEmpty()) {
            val course = etCourse.text.toString().trim()
            val section = etSection.text.toString().trim()
            batch.update(
                db.collection("students").document(studentId!!),
                mapOf("studentName" to fullName, "course" to course, "section" to section)
            )
        }

        batch.commit()
            .addOnSuccessListener {
                setLoading(false)
                Toast.makeText(this, "Changes saved.", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                setLoading(false)
                showError("Failed to save: ${it.localizedMessage}")
            }
    }
}