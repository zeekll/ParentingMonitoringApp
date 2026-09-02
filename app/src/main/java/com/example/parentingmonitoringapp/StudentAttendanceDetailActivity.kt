package com.example.parentingmonitoringapp

import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Locale

class StudentAttendanceDetailActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var tvTitle: TextView
    private lateinit var tvStatus: TextView
    private lateinit var recordsContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_attendance_detail)

        db = FirebaseFirestore.getInstance()
        tvTitle = findViewById(R.id.tvTitle)
        tvStatus = findViewById(R.id.tvStatus)
        recordsContainer = findViewById(R.id.recordsContainer)

        val studentId = intent.getStringExtra("studentId")
        val studentName = intent.getStringExtra("studentName")

        if (studentId == null) {
            tvStatus.text = "No student selected."
            return
        }

        tvTitle.text = if (studentName != null) "$studentName ($studentId)" else studentId
        loadRecords(studentId)
    }

    private fun loadRecords(studentId: String) {
        tvStatus.text = "Loading attendance records..."

        db.collection("attendance")
            .whereEqualTo("studentId", studentId)
            .get()
            .addOnSuccessListener { docs ->
                if (docs.isEmpty) {
                    tvStatus.text = "No attendance records found."
                    return@addOnSuccessListener
                }

                val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

                // Group records by calendar date
                val grouped = LinkedHashMap<String, MutableList<DocumentSnapshot>>()
                val sortedAll = docs.documents.sortedBy { it.getTimestamp("timestamp")?.seconds ?: 0 }
                for (doc in sortedAll) {
                    val ts = doc.getTimestamp("timestamp") ?: continue
                    val dateKey = dateFormat.format(ts.toDate())
                    grouped.getOrPut(dateKey) { mutableListOf() }.add(doc)
                }

                tvStatus.text = "${grouped.size} day(s) with records:"

                // Show most recent date first
                for (dateKey in grouped.keys.reversed()) {
                    val recordsForDate = grouped[dateKey] ?: continue
                    addDateHeader(dateKey)

                    var lastInTimestamp: Timestamp? = null
                    for (doc in recordsForDate) {
                        val type = doc.getString("type") ?: "?"
                        val ts = doc.getTimestamp("timestamp")
                        val timeText = ts?.let { timeFormat.format(it.toDate()) } ?: "Unknown time"

                        if (type == "IN") {
                            lastInTimestamp = ts
                            addRecordRow("🟢 TIME IN", timeText, "#2FD3A6")
                        } else {
                            // Prefer the duration stored on the record; fall back to pairing with the last IN seen
                            var durationMinutes = doc.getLong("durationMinutes")
                            if (durationMinutes == null && lastInTimestamp != null && ts != null) {
                                durationMinutes = (ts.seconds - lastInTimestamp.seconds) / 60
                            }
                            val durationText = durationMinutes?.let { formatDuration(it) } ?: ""
                            addRecordRow("🔴 TIME OUT", "$timeText  $durationText", "#E5484D")
                        }
                    }
                }
            }
            .addOnFailureListener {
                tvStatus.text = "Failed to load records: ${it.localizedMessage}"
            }
    }

    private fun formatDuration(totalMinutes: Long): String {
        if (totalMinutes < 0) return ""
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return "(${hours}h ${minutes}m)"
    }

    private fun addDateHeader(dateText: String) {
        val tv = TextView(this).apply {
            text = dateText
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            textSize = 16f
            setTextColor(Color.parseColor("#22223B"))
            setPadding(0, 24, 0, 8)
        }
        recordsContainer.addView(tv)
    }

    private fun addRecordRow(label: String, timeText: String, colorHex: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(Color.parseColor("#F6F4FE"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8 }
        }
        val tvLabel = TextView(this).apply {
            text = label
            setTextColor(Color.parseColor(colorHex))
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvTime = TextView(this).apply {
            text = timeText
            textSize = 13f
            setTextColor(Color.parseColor("#6B7280"))
        }
        row.addView(tvLabel)
        row.addView(tvTime)
        recordsContainer.addView(row)
    }
}