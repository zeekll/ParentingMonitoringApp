package com.example.parentingmonitoringapp

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Read-only profile view of the linked child, pulled from the "students"
 * collection: name, student ID, course/section, and date of birth.
 */
class MyChildActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var tvTitle: TextView
    private lateinit var tvStatus: TextView
    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simple_list)

        db = FirebaseFirestore.getInstance()
        tvTitle = findViewById(R.id.tvTitle)
        tvStatus = findViewById(R.id.tvStatus)
        container = findViewById(R.id.recordsContainer)
        tvTitle.text = intent.getStringExtra("screenTitle") ?: "My Child"

        val studentId = intent.getStringExtra("studentId")
        if (studentId == null) {
            tvStatus.text = "No linked child found."
            return
        }
        tvStatus.text = "Loading child details..."
        loadChild(studentId)
    }

    private fun loadChild(studentId: String) {
        db.collection("students").document(studentId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    tvStatus.text = "Child record not found."
                    return@addOnSuccessListener
                }

                val name = doc.getString("studentName") ?: studentId
                val course = doc.getString("course") ?: "-"
                val section = doc.getString("section") ?: "-"
                val dob = doc.getString("dob") ?: "-"

                tvStatus.text = "Profile details:"
                container.removeAllViews()
                addDetailRow("Name", name)
                addDetailRow("Student ID", studentId)
                addDetailRow("Course", course)
                addDetailRow("Section", section)
                addDetailRow("Date of Birth", dob)
            }
            .addOnFailureListener {
                tvStatus.text = "Failed to load child details: ${it.localizedMessage}"
            }
    }

    private fun addDetailRow(label: String, value: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(Color.parseColor("#F6F4FE"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8 }
        }
        row.addView(TextView(this).apply {
            text = label.uppercase()
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#3F3D9E"))
        })
        row.addView(TextView(this).apply {
            text = value
            textSize = 15f
            setTextColor(Color.parseColor("#22223B"))
        })
        container.addView(row)
    }
}