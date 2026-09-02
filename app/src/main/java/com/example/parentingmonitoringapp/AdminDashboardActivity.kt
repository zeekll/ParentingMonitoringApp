package com.example.parentingmonitoringapp

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore

class AdminDashboardActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var tvAdminName: TextView
    private lateinit var tvWelcome: TextView
    private lateinit var tvTotalUsers: TextView
    private lateinit var tvTotalStudents: TextView
    private lateinit var tvPendingIssues: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_dashboard)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        tvAdminName = findViewById(R.id.tvAdminName)
        tvWelcome = findViewById(R.id.tvWelcome)
        tvTotalUsers = findViewById(R.id.tvTotalUsers)
        tvTotalStudents = findViewById(R.id.tvTotalStudents)
        tvPendingIssues = findViewById(R.id.tvPendingIssues)

        bindModuleClicks()
        findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener { logout() }

        verifyAdminAccess()
    }

    /**
     * Admin-level access control: only proceed if the signed-in user's Firestore
     * profile has role == "admin". Anyone else is signed out and sent back to
     * Login rather than being allowed to see the dashboard.
     */
    private fun verifyAdminAccess() {
        val user = auth.currentUser
        if (user == null) {
            redirectToLogin()
            return
        }

        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                val role = doc.getString("role")
                if (!doc.exists() || role != "admin") {
                    Toast.makeText(this, "Admin access only.", Toast.LENGTH_LONG).show()
                    redirectToLogin()
                    return@addOnSuccessListener
                }

                val name = doc.getString("name")
                tvAdminName.text = if (name.isNullOrBlank()) "Admin" else name
                tvWelcome.text = "Welcome, ${if (name.isNullOrBlank()) "Admin" else name.substringBefore(" ")}!"

                loadSummaryStats()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Unable to verify admin access. Check your connection.", Toast.LENGTH_LONG).show()
                redirectToLogin()
            }
    }

    private fun redirectToLogin() {
        auth.signOut()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    /**
     * Pulls live counts for the summary cards. Uses Firestore's server-side
     * count() aggregate so we don't have to download every document just to
     * count them. If a collection doesn't exist yet (e.g. no "issues"
     * collection set up), this simply returns 0 rather than erroring.
     */
    private fun loadSummaryStats() {
        db.collection("users").count().get(AggregateSource.SERVER)
            .addOnSuccessListener { tvTotalUsers.text = it.count.toString() }
            .addOnFailureListener { tvTotalUsers.text = "—" }

        db.collection("students").count().get(AggregateSource.SERVER)
            .addOnSuccessListener { tvTotalStudents.text = it.count.toString() }
            .addOnFailureListener { tvTotalStudents.text = "—" }

        db.collection("issues").whereEqualTo("status", "pending").count().get(AggregateSource.SERVER)
            .addOnSuccessListener { tvPendingIssues.text = it.count.toString() }
            .addOnFailureListener { tvPendingIssues.text = "—" }
    }

    private fun comingSoon(feature: String) {
        Toast.makeText(this, "$feature — coming soon", Toast.LENGTH_SHORT).show()
    }

    private fun bindModuleClicks() {
        findViewById<LinearLayout>(R.id.btnManageUsers).setOnClickListener {
            comingSoon("Manage Users")
        }
        findViewById<LinearLayout>(R.id.btnManageStudents).setOnClickListener {
            comingSoon("Manage Students")
        }
        findViewById<LinearLayout>(R.id.btnManageParents).setOnClickListener {
            comingSoon("Manage Parents")
        }
        findViewById<LinearLayout>(R.id.btnAttendance).setOnClickListener {
            startActivity(Intent(this, AttendanceRecordsActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.btnExamSchedule).setOnClickListener {
            startActivity(Intent(this, ExamScheduleActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.btnGrades).setOnClickListener {
            startActivity(Intent(this, ReportsActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.btnAnnouncements).setOnClickListener {
            startActivity(Intent(this, NoticeActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.btnNotifications).setOnClickListener {
            comingSoon("Notifications")
        }
        findViewById<LinearLayout>(R.id.btnSettings).setOnClickListener {
            comingSoon("Settings")
        }
    }

    private fun logout() {
        redirectToLogin()
    }
}