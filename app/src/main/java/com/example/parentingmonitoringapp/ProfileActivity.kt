package com.example.parentingmonitoringapp

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

/**
 * Profile & Settings - shared by Admin, Parent and Student accounts alike.
 *
 * Everyone gets: profile photo, editable display name, change password,
 * and logout. Parents additionally see a read-only list of their linked
 * children; students see their own roster info (ID/course/section/DOB).
 * Admins get just the basics, since they don't have any linked records.
 */
class ProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage

    private lateinit var ivAvatar: ImageView
    private lateinit var tvRoleBadge: TextView
    private lateinit var etFullName: TextInputEditText
    private lateinit var tvEmail: TextView
    private lateinit var btnSaveProfile: MaterialButton

    private lateinit var groupRoleInfo: LinearLayout
    private lateinit var tvRoleInfoHeader: TextView
    private lateinit var roleInfoContainer: LinearLayout

    private lateinit var etCurrentPassword: TextInputEditText
    private lateinit var etNewPassword: TextInputEditText
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var btnChangePassword: MaterialButton

    private lateinit var tvError: TextView
    private lateinit var tvSuccess: TextView
    private lateinit var progressBar: ProgressBar

    private var currentRole: String = "parent"

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) uploadAvatar(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        ivAvatar = findViewById(R.id.ivAvatar)
        tvRoleBadge = findViewById(R.id.tvRoleBadge)
        etFullName = findViewById(R.id.etFullName)
        tvEmail = findViewById(R.id.tvEmail)
        btnSaveProfile = findViewById(R.id.btnSaveProfile)

        groupRoleInfo = findViewById(R.id.groupRoleInfo)
        tvRoleInfoHeader = findViewById(R.id.tvRoleInfoHeader)
        roleInfoContainer = findViewById(R.id.roleInfoContainer)

        etCurrentPassword = findViewById(R.id.etCurrentPassword)
        etNewPassword = findViewById(R.id.etNewPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        btnChangePassword = findViewById(R.id.btnChangePassword)

        tvError = findViewById(R.id.tvError)
        tvSuccess = findViewById(R.id.tvSuccess)
        progressBar = findViewById(R.id.progressBar)

        ivAvatar.clipToCircle()
        ivAvatar.setOnClickListener { pickImageLauncher.launch("image/*") }
        findViewById<TextView>(R.id.tvChangePhoto).setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        btnSaveProfile.setOnClickListener { saveProfile() }
        btnChangePassword.setOnClickListener { changePassword() }
        findViewById<TextView>(R.id.btnLogout).setOnClickListener { logout() }

        loadProfile()
    }

    private fun loadProfile() {
        val uid = auth.currentUser?.uid ?: return
        tvEmail.text = auth.currentUser?.email ?: "—"

        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val role = doc.getString("role") ?: "parent"
                currentRole = role
                tvRoleBadge.text = role.replaceFirstChar { it.uppercase() }
                etFullName.setText(doc.getString("name") ?: "")
                ivAvatar.loadAvatar(doc.getString("photoUrl"))

                when (role) {
                    "student" -> loadStudentInfo(doc.getString("studentId"))
                    "parent" -> loadParentChildren(doc)
                }
            }
            .addOnFailureListener {
                showError("Failed to load your profile: ${it.localizedMessage}")
            }
    }

    private fun loadStudentInfo(studentId: String?) {
        if (studentId.isNullOrEmpty()) return
        db.collection("students").document(studentId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) return@addOnSuccessListener
                groupRoleInfo.visibility = View.VISIBLE
                tvRoleInfoHeader.text = "STUDENT INFO"
                roleInfoContainer.removeAllViews()
                addInfoRow("Student ID", studentId)
                addInfoRow("Course", doc.getString("course") ?: "-")
                addInfoRow("Section", doc.getString("section") ?: "-")
                addInfoRow("Date of Birth", doc.getString("dob") ?: "-")
            }
    }

    private fun loadParentChildren(userDoc: com.google.firebase.firestore.DocumentSnapshot) {
        val linkedIds = ChildLinkStore.getLinkedStudentIds(userDoc)
        if (linkedIds.isEmpty()) return

        groupRoleInfo.visibility = View.VISIBLE
        tvRoleInfoHeader.text = "LINKED CHILDREN"
        roleInfoContainer.removeAllViews()

        for (studentId in linkedIds) {
            db.collection("students").document(studentId).get()
                .addOnSuccessListener { doc ->
                    val name = doc.getString("studentName") ?: studentId
                    val course = doc.getString("course") ?: "-"
                    val section = doc.getString("section") ?: "-"
                    addInfoRow(name, "$course - $section")
                }
                .addOnFailureListener {
                    addInfoRow(studentId, "Details unavailable")
                }
        }
    }

    private fun addInfoRow(label: String, value: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(14, 14, 14, 14)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 6 }
            setBackgroundColor(resources.getColor(R.color.row_light, theme))
        }
        row.addView(TextView(this).apply {
            text = label
            setTextColor(resources.getColor(R.color.text_muted, theme))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(this).apply {
            text = value
            setTextColor(resources.getColor(R.color.ink, theme))
        })
        roleInfoContainer.addView(row)
    }

    private fun uploadAvatar(uri: Uri) {
        val uid = auth.currentUser?.uid ?: return
        ivAvatar.setImageURI(uri) // instant local preview while it uploads
        setLoading(true)
        clearMessages()

        val ref = storage.reference.child("profile_pictures/$uid.jpg")
        ref.putFile(uri)
            .addOnSuccessListener {
                ref.downloadUrl
                    .addOnSuccessListener { downloadUri ->
                        db.collection("users").document(uid)
                            .update("photoUrl", downloadUri.toString())
                            .addOnSuccessListener {
                                setLoading(false)
                                showSuccess("Profile photo updated.")
                            }
                            .addOnFailureListener {
                                setLoading(false)
                                showError("Photo uploaded but saving the link failed: ${it.localizedMessage}")
                            }
                    }
            }
            .addOnFailureListener {
                setLoading(false)
                showError("Failed to upload photo: ${it.localizedMessage}")
            }
    }

    private fun saveProfile() {
        val uid = auth.currentUser?.uid ?: return
        val name = etFullName.text.toString().trim()
        clearMessages()

        if (name.isEmpty()) {
            showError("Name cannot be empty.")
            return
        }

        setLoading(true)
        db.collection("users").document(uid).update("name", name)
            .addOnSuccessListener {
                setLoading(false)
                showSuccess("Profile updated.")
            }
            .addOnFailureListener {
                setLoading(false)
                showError("Failed to update profile: ${it.localizedMessage}")
            }
    }

    private fun changePassword() {
        val user = auth.currentUser
        val email = user?.email
        clearMessages()

        if (user == null || email == null) {
            showError("You need to be signed in to change your password.")
            return
        }

        val current = etCurrentPassword.text.toString()
        val newPass = etNewPassword.text.toString()
        val confirm = etConfirmPassword.text.toString()

        if (current.isEmpty() || newPass.isEmpty() || confirm.isEmpty()) {
            showError("Fill in all three password fields.")
            return
        }
        if (newPass.length < 6) {
            showError("New password must be at least 6 characters.")
            return
        }
        if (newPass != confirm) {
            showError("New password and confirmation don't match.")
            return
        }

        setLoading(true)
        val credential = EmailAuthProvider.getCredential(email, current)
        user.reauthenticate(credential)
            .addOnSuccessListener {
                user.updatePassword(newPass)
                    .addOnSuccessListener {
                        setLoading(false)
                        showSuccess("Password updated successfully.")
                        etCurrentPassword.setText("")
                        etNewPassword.setText("")
                        etConfirmPassword.setText("")
                    }
                    .addOnFailureListener {
                        setLoading(false)
                        showError("Failed to update password: ${it.localizedMessage}")
                    }
            }
            .addOnFailureListener {
                setLoading(false)
                showError("Current password is incorrect.")
            }
    }

    private fun logout() {
        auth.signOut()
        val intent = android.content.Intent(this, LoginActivity::class.java)
        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun setLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnSaveProfile.isEnabled = !isLoading
        btnChangePassword.isEnabled = !isLoading
    }

    private fun clearMessages() {
        tvError.visibility = View.GONE
        tvSuccess.visibility = View.GONE
    }

    private fun showError(message: String) {
        tvSuccess.visibility = View.GONE
        tvError.text = message
        tvError.visibility = View.VISIBLE
    }

    private fun showSuccess(message: String) {
        tvError.visibility = View.GONE
        tvSuccess.text = message
        tvSuccess.visibility = View.VISIBLE
    }
}