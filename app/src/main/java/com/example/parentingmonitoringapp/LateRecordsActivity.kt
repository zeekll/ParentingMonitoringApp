package com.example.parentingmonitoringapp

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Surfaces "late arrival" attendance for the linked child: any "IN" record
 * from the "attendance" collection whose time-of-day is after the school's
 * cutoff hour/minute below. There's no dedicated "late" flag stored on
 * attendance records today, so this cutoff is the single source of truth for
 * what counts as late; adjust it here if the school's start time changes.
 */
class LateRecordsActivity : AppCompatActivity() {

    companion object {
        private const val LATE_CUTOFF_HOUR = 8
        private const val LATE_CUTOFF_MINUTE = 0
    }

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
        tvTitle.text = "Late Records"

        val studentId = intent.getStringExtra("studentId")
        if (studentId == null) {
            tvStatus.text = "No linked child found."
            return
        }
        tvStatus.text = "Loading late records..."
        loadLateRecords(studentId)
    }

    private fun loadLateRecords(studentId: String) {
        db.collection("attendance")
            .whereEqualTo("studentId", studentId)
            .whereEqualTo("type", "IN")
            .get()
            .addOnSuccessListener { docs ->
                if (docs.isEmpty) {
                    tvStatus.text = "No attendance records found."
                    return@addOnSuccessListener
                }

                val cal = Calendar.getInstance()
                val lateDocs = docs.documents.filter { doc ->
                    val ts = doc.getTimestamp("timestamp") ?: return@filter false
                    cal.time = ts.toDate()
                    val hour = cal.get(Calendar.HOUR_OF_DAY)
                    val minute = cal.get(Calendar.MINUTE)
                    hour > LATE_CUTOFF_HOUR || (hour == LATE_CUTOFF_HOUR && minute > LATE_CUTOFF_MINUTE)
                }.sortedByDescending { it.getTimestamp("timestamp")?.seconds ?: 0 }

                if (lateDocs.isEmpty()) {
                    tvStatus.text = "No late arrivals recorded. \uD83C\uDF89"
                    return@addOnSuccessListener
                }

                val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

                tvStatus.text = "${lateDocs.size} late arrival(s):"
                container.removeAllViews()
                for (doc in lateDocs) {
                    val ts = doc.getTimestamp("timestamp") ?: continue
                    addLateRow(dateFormat.format(ts.toDate()), timeFormat.format(ts.toDate()))
                }
            }
            .addOnFailureListener {
                tvStatus.text = "Failed to load late records: ${it.localizedMessage}"
            }
    }

    private fun addLateRow(dateText: String, timeText: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(Color.parseColor("#FDECEC"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8 }
        }
        row.addView(TextView(this).apply {
            text = "\uD83D\uDD34 $dateText"
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#D94B4B"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(this).apply {
            text = timeText
            textSize = 13f
            setTextColor(Color.parseColor("#6B7280"))
        })
        container.addView(row)
    }
}