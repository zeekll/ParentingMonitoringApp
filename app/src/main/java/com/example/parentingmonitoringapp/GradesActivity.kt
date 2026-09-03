package com.example.parentingmonitoringapp

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Read-only view of the linked child's posted grades, grouped by term.
 * Expects documents in the "grades" collection shaped like:
 *   studentId: String, subject: String, term: String, score: String/Number,
 *   remarks: String (optional), postedAt: Timestamp
 */
class GradesActivity : AppCompatActivity() {

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
        tvTitle.text = "Grades"

        val studentId = intent.getStringExtra("studentId")
        val studentName = intent.getStringExtra("studentName")
        if (studentId == null) {
            tvStatus.text = "No linked child found."
            return
        }
        tvStatus.text = if (studentName != null) "Loading grades for $studentName..." else "Loading grades..."
        loadGrades(studentId)
    }

    private fun loadGrades(studentId: String) {
        db.collection("grades")
            .whereEqualTo("studentId", studentId)
            .get()
            .addOnSuccessListener { docs ->
                if (docs.isEmpty) {
                    tvStatus.text = "No grades posted yet."
                    return@addOnSuccessListener
                }

                val sorted = docs.documents.sortedWith(
                    compareByDescending<com.google.firebase.firestore.DocumentSnapshot> { it.getTimestamp("postedAt")?.seconds ?: 0 }
                        .thenBy { it.getString("subject") ?: "" }
                )

                tvStatus.text = "${sorted.size} grade(s) posted:"
                container.removeAllViews()
                for (doc in sorted) {
                    val subject = doc.getString("subject") ?: "Subject"
                    val term = doc.getString("term") ?: ""
                    val score = doc.get("score")?.toString() ?: "-"
                    val remarks = doc.getString("remarks") ?: ""
                    addGradeRow(subject, term, score, remarks)
                }
            }
            .addOnFailureListener {
                tvStatus.text = "Failed to load grades: ${it.localizedMessage}"
            }
    }

    private fun addGradeRow(subject: String, term: String, score: String, remarks: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(Color.parseColor("#F6F4FE"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8 }
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val tvSubject = TextView(this).apply {
            text = subject
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#22223B"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvScore = TextView(this).apply {
            text = score
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#3F3D9E"))
        }
        header.addView(tvSubject)
        header.addView(tvScore)
        row.addView(header)

        if (term.isNotEmpty()) {
            row.addView(TextView(this).apply {
                text = term
                textSize = 13f
                setTextColor(Color.parseColor("#6B7280"))
            })
        }
        if (remarks.isNotEmpty()) {
            row.addView(TextView(this).apply {
                text = remarks
                textSize = 13f
                setTextColor(Color.parseColor("#6B7280"))
            })
        }
        container.addView(row)
    }
}