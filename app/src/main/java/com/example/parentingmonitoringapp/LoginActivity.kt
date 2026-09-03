package com.example.parentingmonitoringapp

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: Button
    private lateinit var tvError: TextView
    private lateinit var tvGoToRegister: TextView
    private lateinit var tvForgotPassword: TextView

    // Signing-in loader
    private lateinit var signingInDialog: AlertDialog

    // Checking-account checklist
    private lateinit var checkingDialog: AlertDialog
    private lateinit var tvCheckCredentials: TextView
    private lateinit var tvCheckUserData: TextView
    private lateinit var tvCheckRole: TextView

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        tilEmail = findViewById(R.id.tilEmail)
        tilPassword = findViewById(R.id.tilPassword)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        tvError = findViewById(R.id.tvError)
        tvGoToRegister = findViewById(R.id.tvGoToRegister)
        tvForgotPassword = findViewById(R.id.tvForgotPassword)

        setupDialogs()

        // Clear the inline field error as soon as the person edits that field
        etEmail.addTextChangedListener(clearErrorOnEdit(tilEmail))
        etPassword.addTextChangedListener(clearErrorOnEdit(tilPassword))

        btnLogin.setOnClickListener { attemptLogin() }
        tvGoToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        tvForgotPassword.setOnClickListener { showForgotPasswordDialog() }
    }

    private fun setupDialogs() {
        val signingInView = LayoutInflater.from(this).inflate(R.layout.dialog_signing_in, null)
        signingInDialog = AlertDialog.Builder(this)
            .setView(signingInView)
            .setCancelable(false)
            .create()

        val checkingView = LayoutInflater.from(this).inflate(R.layout.dialog_checking_account, null)
        tvCheckCredentials = checkingView.findViewById(R.id.tvCheckCredentials)
        tvCheckUserData = checkingView.findViewById(R.id.tvCheckUserData)
        tvCheckRole = checkingView.findViewById(R.id.tvCheckRole)
        checkingDialog = AlertDialog.Builder(this)
            .setView(checkingView)
            .setCancelable(false)
            .create()
    }

    private fun clearErrorOnEdit(layout: TextInputLayout) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            layout.error = null
        }
    }

    /**
     * Validates the email/password fields locally before touching the network.
     * Returns true if the form is valid.
     */
    private fun validateInput(email: String, password: String): Boolean {
        var isValid = true

        if (email.isEmpty()) {
            tilEmail.error = "Email is required."
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.error = "Enter a valid email address."
            isValid = false
        }

        if (password.isEmpty()) {
            tilPassword.error = "Password is required."
            isValid = false
        }

        return isValid
    }

    private fun showBanner(message: String, isSuccess: Boolean = false) {
        tvError.text = message
        if (isSuccess) {
            tvError.setBackgroundResource(R.drawable.bg_banner_success)
            tvError.setTextColor(getColor(R.color.success))
        } else {
            tvError.setBackgroundResource(R.drawable.bg_banner_error)
            tvError.setTextColor(getColor(R.color.danger))
        }
        tvError.visibility = View.VISIBLE
    }

    private fun hideBanner() {
        tvError.visibility = View.GONE
        tvError.text = ""
    }

    private fun markChecked(row: TextView, label: String) {
        row.text = "✓  $label"
        row.setTextColor(getColor(R.color.success))
    }

    private fun resetChecklist() {
        tvCheckCredentials.text = "○  Verify credentials"
        tvCheckCredentials.setTextColor(getColor(R.color.ink_soft))
        tvCheckUserData.text = "○  Get user data"
        tvCheckUserData.setTextColor(getColor(R.color.ink_soft))
        tvCheckRole.text = "○  Check role"
        tvCheckRole.setTextColor(getColor(R.color.ink_soft))
    }

    private fun attemptLogin() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        tilEmail.error = null
        tilPassword.error = null
        hideBanner()

        if (!validateInput(email, password)) {
            return
        }

        btnLogin.isEnabled = false
        signingInDialog.show()

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val user = result.user
                val uid = user?.uid

                if (uid == null) {
                    signingInDialog.dismiss()
                    btnLogin.isEnabled = true
                    showBanner("Invalid email or password.")
                    return@addOnSuccessListener
                }

                // Credentials are verified at this point - move to the checklist screen
                signingInDialog.dismiss()
                resetChecklist()
                checkingDialog.show()
                markChecked(tvCheckCredentials, "Verify credentials")

                db.collection("users").document(uid).get()
                    .addOnSuccessListener { doc ->
                        markChecked(tvCheckUserData, "Get user data")

                        val role = doc.getString("role") ?: "parent"
                        markChecked(tvCheckRole, "Check role")

                        // Brief pause so the person can see the checklist complete before moving on
                        handler.postDelayed({
                            checkingDialog.dismiss()

                            // Admin accounts (made via Firebase Console) skip email verification
                            if (role == "parent" && !user.isEmailVerified) {
                                btnLogin.isEnabled = true
                                showBanner("Please verify your email first. Check your Gmail inbox.")
                                auth.signOut()
                                return@postDelayed
                            }

                            if (role == "parent" && ChildLinkStore.getLinkedStudentIds(doc).size > 1) {
                                val intent = Intent(this, SelectChildActivity::class.java)
                                intent.putExtra(SelectChildActivity.EXTRA_FROM_LOGIN, true)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                                finish()
                                return@postDelayed
                            }

                            val nextActivity = when (role) {
                                "admin" -> AdminDashboardActivity::class.java
                                "student" -> StudentActivity::class.java
                                else -> ParentDashboardActivity::class.java
                            }

                            startActivity(Intent(this, nextActivity))
                            finish()
                        }, 350)
                    }
                    .addOnFailureListener {
                        checkingDialog.dismiss()
                        btnLogin.isEnabled = true
                        showBanner("Failed to load user profile")
                    }
            }
            .addOnFailureListener { e ->
                signingInDialog.dismiss()
                btnLogin.isEnabled = true
                showBanner(e.localizedMessage ?: "Invalid email or password.")
            }
    }

    private fun showForgotPasswordDialog() {
        val input = EditText(this).apply {
            hint = "Email address"
            setText(etEmail.text?.toString().orEmpty())
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        AlertDialog.Builder(this)
            .setTitle("Reset password")
            .setMessage("Enter your account email and we'll send you a reset link.")
            .setView(input)
            .setPositiveButton("Send") { dialog, _ ->
                val email = input.text.toString().trim()
                if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    showBanner("Enter a valid email address to reset your password.")
                } else {
                    auth.sendPasswordResetEmail(email)
                        .addOnSuccessListener {
                            showBanner("Password reset email sent. Check your inbox.", isSuccess = true)
                        }
                        .addOnFailureListener { e ->
                            showBanner(e.localizedMessage ?: "Couldn't send reset email.")
                        }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}