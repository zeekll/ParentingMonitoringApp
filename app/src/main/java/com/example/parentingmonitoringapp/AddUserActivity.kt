package com.example.parentingmonitoringapp

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Admin > Manage Users > Add User.
 *
 * Step 1: admin picks a role (Admin / Parent / Student).
 * Step 2: role-specific form, then "Create Account" writes both the Firebase
 * Auth account and the matching Firestore record(s) with role = the chosen role.
 *
 * IMPORTANT Firebase Auth caveat: the client SDK's createUserWithEmailAndPassword
 * signs in AS the newly created user on whichever FirebaseAuth instance it's
 * called on. To avoid kicking the signed-in admin out of their own session,
 * the new account is created on a SECONDARY FirebaseApp/FirebaseAuth instance
 * (see [createAuthAccountThen]), which is immediately signed out afterwards.
 * The admin's own primary-app session is never touched.
 *
 * Also note: fully deleting a user's Auth credential later requires the
 * Firebase Admin SDK / a Cloud Function - the client app can only remove the
 * Firestore records (see ManageUsersActivity).
 */
class AddUserActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var cardRoleAdmin: LinearLayout
    private lateinit var cardRoleParent: LinearLayout
    private lateinit var cardRoleStudent: LinearLayout

    private lateinit var formSection: LinearLayout
    private lateinit var tvSelectedRoleHeader: TextView
    private lateinit var groupStudentFields: LinearLayout
    private lateinit var groupParentFields: LinearLayout

    private lateinit var etFullName: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var etStudentId: TextInputEditText
    private lateinit var etStudentDob: TextInputEditText
    private lateinit var etCourse: TextInputEditText
    private lateinit var etSection: TextInputEditText
    private lateinit var etLinkStudentId: TextInputEditText

    private lateinit var tvError: TextView
    private lateinit var btnCreateAccount: MaterialButton
    private lateinit var progressBar: ProgressBar

    private var selectedRole: String? = null
    private val dobFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private var selectedDob: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_user)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        cardRoleAdmin = findViewById(R.id.cardRoleAdmin)
        cardRoleParent = findViewById(R.id.cardRoleParent)
        cardRoleStudent = findViewById(R.id.cardRoleStudent)

        formSection = findViewById(R.id.formSection)
        tvSelectedRoleHeader = findViewById(R.id.tvSelectedRoleHeader)
        groupStudentFields = findViewById(R.id.groupStudentFields)
        groupParentFields = findViewById(R.id.groupParentFields)

        etFullName = findViewById(R.id.etFullName)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        etStudentId = findViewById(R.id.etStudentId)
        etStudentDob = findViewById(R.id.etStudentDob)
        etCourse = findViewById(R.id.etCourse)
        etSection = findViewById(R.id.etSection)
        etLinkStudentId = findViewById(R.id.etLinkStudentId)

        tvError = findViewById(R.id.tvError)
        btnCreateAccount = findViewById(R.id.btnCreateAccount)
        progressBar = findViewById(R.id.progressBar)

        etStudentDob.setOnClickListener { showDobPicker() }

        cardRoleAdmin.setOnClickListener { selectRole("admin") }
        cardRoleParent.setOnClickListener { selectRole("parent") }
        cardRoleStudent.setOnClickListener { selectRole("student") }

        btnCreateAccount.setOnClickListener { attemptCreate() }
    }

    private fun selectRole(role: String) {
        selectedRole = role
        formSection.visibility = View.VISIBLE
        groupStudentFields.visibility = if (role == "student") View.VISIBLE else View.GONE
        groupParentFields.visibility = if (role == "parent") View.VISIBLE else View.GONE
        tvSelectedRoleHeader.text = when (role) {
            "admin" -> "ADMIN INFORMATION"
            "parent" -> "PARENT INFORMATION"
            else -> "STUDENT INFORMATION"
        }
        tvError.visibility = View.GONE
        highlightSelectedCard(role)
    }

    private fun highlightSelectedCard(role: String) {
        val selectedBg = ContextCompat.getColor(this, R.color.indigo_100)
        val unselectedBg = ContextCompat.getColor(this, R.color.card_white)
        cardRoleAdmin.setBackgroundColor(if (role == "admin") selectedBg else unselectedBg)
        cardRoleParent.setBackgroundColor(if (role == "parent") selectedBg else unselectedBg)
        cardRoleStudent.setBackgroundColor(if (role == "student") selectedBg else unselectedBg)
    }

    private fun showDobPicker() {
        val cal = Calendar.getInstance()
        cal.add(Calendar.YEAR, -10)

        val dialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val picked = Calendar.getInstance()
                picked.set(year, month, dayOfMonth)
                selectedDob = dobFormat.format(picked.time)
                etStudentDob.setText(SimpleDateFormat("MMM d, yyyy", Locale.US).format(picked.time))
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        )
        dialog.datePicker.maxDate = System.currentTimeMillis()
        dialog.show()
    }

    private fun showError(message: String) {
        tvError.text = message
        tvError.visibility = View.VISIBLE
    }

    private fun setLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnCreateAccount.isEnabled = !isLoading
    }

    private fun attemptCreate() {
        val role = selectedRole
        if (role == null) {
            showError("Please select a role first.")
            return
        }

        val fullName = etFullName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        tvError.visibility = View.GONE

        if (fullName.isEmpty() || email.isEmpty() || password.isEmpty()) {
            showError("Please fill in all required fields.")
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

        when (role) {
            "student" -> attemptCreateStudent(fullName, email, password)
            "parent" -> attemptCreateParent(fullName, email, password)
            else -> attemptCreateAdmin(fullName, email, password)
        }
    }

    private fun attemptCreateAdmin(fullName: String, email: String, password: String) {
        setLoading(true)
        createAuthAccountThen(email, password) { uid ->
            val userData = hashMapOf(
                "name" to fullName,
                "email" to email,
                "role" to "admin",
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("users").document(uid).set(userData)
                .addOnSuccessListener { onCreateSuccess("Admin account created successfully.") }
                .addOnFailureListener { onCreateFailure(it.localizedMessage) }
        }
    }

    private fun attemptCreateParent(fullName: String, email: String, password: String) {
        val linkStudentId = etLinkStudentId.text.toString().trim()

        if (linkStudentId.isEmpty()) {
            setLoading(true)
            createAuthAccountThen(email, password) { uid ->
                val userData = hashMapOf(
                    "name" to fullName,
                    "email" to email,
                    "role" to "parent",
                    "createdAt" to FieldValue.serverTimestamp()
                )
                db.collection("users").document(uid).set(userData)
                    .addOnSuccessListener { onCreateSuccess("Parent account created successfully.") }
                    .addOnFailureListener { onCreateFailure(it.localizedMessage) }
            }
            return
        }

        // A student ID was provided - verify it exists and isn't already linked
        // before creating the account (admin-trusted, so no DOB re-check here).
        setLoading(true)
        db.collection("students").document(linkStudentId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    setLoading(false)
                    showError("Student ID \"$linkStudentId\" was not found.")
                    return@addOnSuccessListener
                }
                val existingParentUid = doc.getString("parentUid")
                if (!existingParentUid.isNullOrEmpty()) {
                    setLoading(false)
                    showError("That student is already linked to a parent account.")
                    return@addOnSuccessListener
                }

                createAuthAccountThen(email, password) { uid ->
                    val userData = hashMapOf(
                        "name" to fullName,
                        "email" to email,
                        "role" to "parent",
                        "studentId" to linkStudentId,
                        "studentIds" to listOf(linkStudentId),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    db.runBatch { batch ->
                        batch.set(db.collection("users").document(uid), userData)
                        batch.update(
                            db.collection("students").document(linkStudentId),
                            mapOf("parentUid" to uid, "linkedAt" to FieldValue.serverTimestamp())
                        )
                    }
                        .addOnSuccessListener { onCreateSuccess("Parent account created and linked to $linkStudentId.") }
                        .addOnFailureListener { onCreateFailure(it.localizedMessage) }
                }
            }
            .addOnFailureListener {
                setLoading(false)
                showError("Unable to verify student ID. Check your connection and try again.")
            }
    }

    private fun attemptCreateStudent(fullName: String, email: String, password: String) {
        val studentId = etStudentId.text.toString().trim()
        val dob = selectedDob
        val course = etCourse.text.toString().trim()
        val section = etSection.text.toString().trim()

        if (studentId.isEmpty()) {
            showError("Please enter a Student ID.")
            return
        }
        if (dob.isEmpty()) {
            showError("Please select the student's Date of Birth.")
            return
        }

        setLoading(true)
        db.collection("students").document(studentId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    setLoading(false)
                    showError("Student ID \"$studentId\" already exists.")
                    return@addOnSuccessListener
                }

                createAuthAccountThen(email, password) { uid ->
                    val studentData = hashMapOf(
                        "studentName" to fullName,
                        "dob" to dob,
                        "course" to course,
                        "section" to section,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    val userData = hashMapOf(
                        "name" to fullName,
                        "email" to email,
                        "role" to "student",
                        "studentId" to studentId,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    db.runBatch { batch ->
                        batch.set(db.collection("students").document(studentId), studentData)
                        batch.set(db.collection("users").document(uid), userData)
                    }
                        .addOnSuccessListener { onCreateSuccess("Student account created: $studentId.") }
                        .addOnFailureListener { onCreateFailure(it.localizedMessage) }
                }
            }
            .addOnFailureListener {
                setLoading(false)
                showError("Unable to check Student ID. Check your connection and try again.")
            }
    }

    /**
     * Creates the Firebase Auth account on a secondary FirebaseApp instance so
     * the admin's own signed-in session (on the default app) is left untouched.
     */
    private fun createAuthAccountThen(email: String, password: String, onSuccess: (uid: String) -> Unit) {
        val secondaryApp = try {
            FirebaseApp.getInstance("AddUserSecondary")
        } catch (e: IllegalStateException) {
            FirebaseApp.initializeApp(this, FirebaseApp.getInstance().options, "AddUserSecondary")
        }
        val secondaryAuth = FirebaseAuth.getInstance(secondaryApp!!)

        secondaryAuth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid
                secondaryAuth.signOut()
                if (uid == null) {
                    setLoading(false)
                    showError("Something went wrong creating the account. Please try again.")
                    return@addOnSuccessListener
                }
                onSuccess(uid)
            }
            .addOnFailureListener { e ->
                setLoading(false)
                showError(e.localizedMessage ?: "Unable to create account. Please try again.")
            }
    }

    private fun onCreateSuccess(message: String) {
        setLoading(false)
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
        finish()
    }

    private fun onCreateFailure(message: String?) {
        setLoading(false)
        showError(message ?: "Account was created, but saving the profile failed. Please check Manage Users.")
    }
}