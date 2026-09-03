package com.example.parentingmonitoringapp

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Read-only view of exam schedules posted for the linked child's
 * course/section, from the "exam_schedules" collection (see
 * ExamScheduleActivity, the admin-side screen that creates these entries).
 */
class ParentExamScheduleActivity : AppCompatActivity() {

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
        tvTitle.text = "Exam Schedule"

        val studentId = intent.getStringExtra("studentId")
        if (studentId == null) {
            tvStatus.text = "No linked child found."
            return
        }
        tvStatus.text = "Loading exam schedule..."
        loadExamSchedule(studentId)
    }

    private fun loadExamSchedule(studentId: String) {
        db.collection("students").document(studentId).get()
            .addOnSuccessListener { studentDoc ->
                val course = studentDoc.getString("course")
                val section = studentDoc.getString("section")
                if (course == null || section == null) {
                    tvStatus.text = "Child's course/section is not set yet."
                    return@addOnSuccessListener
                }

                db.collection("exam_schedules")
                    .whereEqualTo("course", course)
                    .whereEqualTo("section", section)
                    .get()
                    .addOnSuccessListener { docs ->
                        if (docs.isEmpty) {
                            tvStatus.text = "No exam schedule posted yet."
                            return@addOnSuccessListener
                        }

                        val sorted = docs.documents.sortedWith(
                            compareBy(
                                { it.getString("examDate") ?: "" },
                                { it.getString("examTime") ?: "" }
                            )
                        )

                        tvStatus.text = "${sorted.size} exam(s) scheduled:"
                        container.removeAllViews()
                        for (doc in sorted) {
                            val subject = doc.getString("subject") ?: "Subject"
                            val examDate = doc.getString("examDate") ?: ""
                            val examTime = doc.getString("examTime") ?: ""
                            val room = doc.getString("room") ?: ""
                            val notes = doc.getString("notes") ?: ""
                            addExamRow(subject, "$examDate $examTime".trim(), room, notes)
                        }
                    }
                    .addOnFailureListener {
                        tvStatus.text = "Failed to load exam schedule: ${it.localizedMessage}"
                    }
            }
            .addOnFailureListener {
                tvStatus.text = "Failed to load child details: ${it.localizedMessage}"
            }
    }

    private fun addExamRow(subject: String, whenText: String, room: String, notes: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(Color.parseColor("#F6F4FE"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8 }
        }
        row.addView(TextView(this).apply {
            text = subject
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#22223B"))
        })
        if (whenText.isNotEmpty()) {
            row.addView(TextView(this).apply {
                text = whenText
                textSize = 13f
                setTextColor(Color.parseColor("#3F3D9E"))
            })
        }
        if (room.isNotEmpty()) {
            row.addView(TextView(this).apply {
                text = "Room: $room"
                textSize = 13f
                setTextColor(Color.parseColor("#6B7280"))
            })
        }
        if (notes.isNotEmpty()) {
            row.addView(TextView(this).apply {
                text = notes
                textSize = 13f
                setTextColor(Color.parseColor("#6B7280"))
            })
        }
        container.addView(row)
    }
}