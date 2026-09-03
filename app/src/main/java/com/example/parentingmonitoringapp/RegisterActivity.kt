package com.example.parentingmonitoringapp

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Patterns
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.button.MaterialButton
import android.widget.ProgressBar
import android.widget.TextView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var etFullName: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var etStudentId: TextInputEditText
    private lateinit var etStudentDob: TextInputEditText
    private lateinit var btnRegister: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView
    private lateinit var tvGoToLogin: TextView

    private val dobFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private var selectedDob: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        etFullName = findViewById(R.id.etFullName)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        etStudentId = findViewById(R.id.etStudentId)
        etStudentDob = findViewById(R.id.etStudentDob)
        btnRegister = findViewById(R.id.btnRegister)
        progressBar = findViewById(R.id.progressBar)
        tvError = findViewById(R.id.tvError)
        tvGoToLogin = findViewById(R.id.tvGoToLogin)

        etStudentDob.setOnClickListener { showDobPicker() }
        btnRegister.setOnClickListener { attemptRegister() }
        tvGoToLogin.setOnClickListener { finish() }
    }

    private fun showDobPicker() {
        val cal = Calendar.getInstance()
        // Default the picker to ~10 years ago, a reasonable starting point for a student's DOB.
        cal.add(Calendar.YEAR, -10)

        val dialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val picked = Calendar.getInstance()
                picked.set(year, month, dayOfMonth)
                selectedDob = dobFormat.format(picked.time)
                etStudentDob.setText(
                    SimpleDateFormat("MMM d, yyyy", Locale.US).format(picked.time)
                )
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
        // A student can't be born in the future.
        dialog.datePicker.maxDate = System.currentTimeMillis()
        dialog.show()
    }

    private fun showError(message: String) {
        tvError.setTextColor(android.graphics.Color.parseColor("#D94B4B"))
        tvError.text = message
        tvError.visibility = View.VISIBLE
    }

    private fun showSuccess(message: String) {
        tvError.setTextColor(android.graphics.Color.parseColor("#1F9D63"))
        tvError.text = message
        tvError.visibility = View.VISIBLE
    }

    private fun setLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnRegister.isEnabled = !isLoading
    }

    private fun attemptRegister() {
        val fullName = etFullName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()
        val confirmPassword = etConfirmPassword.text.toString().trim()
        val studentId = etStudentId.text.toString().trim()
        val studentDob = selectedDob

        tvError.visibility = View.GONE

        // --- Validate parent input fields ---
        if (fullName.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            showError("Please fill in all parent information fields.")
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Please enter a valid email address.")
            return
        }
        if (password.length < 6) {
            showError("Password must be at least 6 characters.")
            return
        }
        if (password != confirmPassword) {
            showError("Passwords do not match.")
            return
        }

        // --- Validate student verification fields ---
        if (studentId.isEmpty()) {
            showError("Please enter the Student ID.")
            return
        }
        if (studentDob.isEmpty()) {
            showError("Please select the Student's Date of Birth.")
            return
        }

        setLoading(true)
        verifyStudentThenRegister(fullName, email, password, studentId, studentDob)
    }

    /**
     * Verify the student record on the backend BEFORE creating any account or
     * writing any data. This restricts account creation/linking to cases where
     * verification actually succeeds.
     */
    private fun verifyStudentThenRegister(
        fullName: String,
        email: String,
        password: String,
        studentId: String,
        studentDob: String
    ) {
        db.collection("students").document(studentId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    setLoading(false)
                    showError("Student ID not found. Please check the ID or contact the school admin.")
                    return@addOnSuccessListener
                }

                val recordDob = doc.getString("dob")?.trim().orEmpty()
                if (recordDob.isEmpty() || recordDob != studentDob) {
                    setLoading(false)
                    showError("Student details do not match our records. Please double-check the ID and date of birth.")
                    return@addOnSuccessListener
                }

                // Prevent linking a student that's already linked to a different parent.
                val existingParentUid = doc.getString("parentUid")
                if (!existingParentUid.isNullOrEmpty()) {
                    setLoading(false)
                    showError("This student is already linked to a parent account.")
                    return@addOnSuccessListener
                }

                createParentAndLinkStudent(fullName, email, password, studentId)
            }
            .addOnFailureListener {
                setLoading(false)
                showError("Unable to verify student details. Check your internet connection and try again.")
            }
    }

    private fun createParentAndLinkStudent(
        fullName: String,
        email: String,
        password: String,
        studentId: String
    ) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val parentUid = result.user?.uid
                if (parentUid == null) {
                    setLoading(false)
                    showError("Something went wrong creating your account. Please try again.")
                    return@addOnSuccessListener
                }

                result.user?.sendEmailVerification()
                linkParentToStudent(parentUid, fullName, email, studentId)
            }
            .addOnFailureListener { e ->
                setLoading(false)
                showError(e.localizedMessage ?: "Unable to create account. Please try again.")
            }
    }

    /**
     * Writes the parent profile (role = parent) and the student's link back to
     * that parent in a single atomic batch, so the two records can't end up
     * out of sync (database integrity for the parent<->student link).
     */
    private fun linkParentToStudent(
        parentUid: String,
        fullName: String,
        email: String,
        studentId: String
    ) {
        val parentRef = db.collection("users").document(parentUid)
        val studentRef = db.collection("students").document(studentId)

        val parentData = hashMapOf(
            "name" to fullName,
            "email" to email,
            "role" to "parent",
            "studentId" to studentId,
            "studentIds" to listOf(studentId),
            "createdAt" to FieldValue.serverTimestamp()
        )

        val studentUpdate = hashMapOf<String, Any>(
            "parentUid" to parentUid,
            "linkedAt" to FieldValue.serverTimestamp()
        )

        db.runBatch { batch ->
            batch.set(parentRef, parentData)
            batch.update(studentRef, studentUpdate)
        }
            .addOnSuccessListener {
                setLoading(false)
                showSuccess("Account created and linked successfully! Please verify your email, then log in.")
                auth.signOut()
            }
            .addOnFailureListener {
                setLoading(false)
                // Account exists in Auth but the DB link failed - surface this clearly
                // rather than leaving the person thinking everything succeeded.
                showError("Account was created but linking to the student failed. Please contact support.")
            }
    }
}