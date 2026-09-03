package com.example.parentingmonitoringapp

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Dynamic notifications feed for the linked child: merges allowance/expense
 * notices and exam schedule postings sent for the child's course/section
 * (plus any entries in a general "notifications" collection keyed by
 * studentId), sorted newest first.
 */
class ParentNotificationsActivity : AppCompatActivity() {

    private data class Item(
        val type: String,
        val title: String,
        val message: String,
        val sentAt: Timestamp?
    )

    private lateinit var db: FirebaseFirestore
    private lateinit var tvTitle: TextView
    private lateinit var tvStatus: TextView
    private lateinit var container: LinearLayout

    private val items = mutableListOf<Item>()
    private var pendingLoads = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simple_list)

        db = FirebaseFirestore.getInstance()
        tvTitle = findViewById(R.id.tvTitle)
        tvStatus = findViewById(R.id.tvStatus)
        container = findViewById(R.id.recordsContainer)
        tvTitle.text = "Notifications"

        val studentId = intent.getStringExtra("studentId")
        if (studentId == null) {
            tvStatus.text = "No linked child found."
            return
        }
        tvStatus.text = "Loading notifications..."
        loadForStudent(studentId)
    }

    private fun loadForStudent(studentId: String) {
        // General, student-scoped notifications (e.g. late-arrival alerts).
        pendingLoads++
        db.collection("notifications")
            .whereEqualTo("studentId", studentId)
            .get()
            .addOnSuccessListener { docs ->
                for (doc in docs) {
                    items.add(
                        Item(
                            type = doc.getString("type") ?: "General",
                            title = doc.getString("title") ?: "Notification",
                            message = doc.getString("message") ?: "",
                            sentAt = doc.getTimestamp("sentAt")
                        )
                    )
                }
                onLoadFinished()
            }
            .addOnFailureListener { onLoadFinished() }

        // Course/section-scoped notices (allowance + exam schedule) require
        // looking up the child's course/section first.
        pendingLoads++
        db.collection("students").document(studentId).get()
            .addOnSuccessListener { studentDoc ->
                val course = studentDoc.getString("course")
                val section = studentDoc.getString("section")
                if (course == null || section == null) {
                    onLoadFinished()
                    return@addOnSuccessListener
                }

                pendingLoads++
                db.collection("expense_notices")
                    .whereEqualTo("course", course)
                    .whereEqualTo("section", section)
                    .get()
                    .addOnSuccessListener { docs ->
                        for (doc in docs) {
                            val amount = doc.get("amount")?.toString()
                            val message = doc.getString("message") ?: ""
                            items.add(
                                Item(
                                    type = "Allowance",
                                    title = doc.getString("title") ?: "Expense Notice",
                                    message = if (amount != null) "$message (₱$amount)" else message,
                                    sentAt = doc.getTimestamp("sentAt")
                                )
                            )
                        }
                        onLoadFinished()
                    }
                    .addOnFailureListener { onLoadFinished() }

                pendingLoads++
                db.collection("exam_schedules")
                    .whereEqualTo("course", course)
                    .whereEqualTo("section", section)
                    .get()
                    .addOnSuccessListener { docs ->
                        for (doc in docs) {
                            val subject = doc.getString("subject") ?: "Exam"
                            val examDate = doc.getString("examDate") ?: ""
                            val examTime = doc.getString("examTime") ?: ""
                            items.add(
                                Item(
                                    type = "Exam Schedule",
                                    title = subject,
                                    message = "$examDate $examTime".trim(),
                                    sentAt = doc.getTimestamp("sentAt")
                                )
                            )
                        }
                        onLoadFinished()
                    }
                    .addOnFailureListener { onLoadFinished() }

                onLoadFinished()
            }
            .addOnFailureListener { onLoadFinished() }
    }

    private fun onLoadFinished() {
        pendingLoads--
        if (pendingLoads > 0) return

        if (items.isEmpty()) {
            tvStatus.text = "No notifications yet."
            return
        }

        val sorted = items.sortedByDescending { it.sentAt?.seconds ?: 0 }
        val dateFormat = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())

        tvStatus.text = "${sorted.size} notification(s):"
        container.removeAllViews()
        for (item in sorted) {
            val whenText = item.sentAt?.let { dateFormat.format(it.toDate()) } ?: ""
            addNotificationRow(item.type, item.title, item.message, whenText)
        }
    }

    private fun addNotificationRow(type: String, title: String, message: String, whenText: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(Color.parseColor("#F6F4FE"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8 }
        }

        row.addView(TextView(this).apply {
            text = type.uppercase(Locale.getDefault())
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#3F3D9E"))
        })
        row.addView(TextView(this).apply {
            text = title
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#22223B"))
        })
        if (message.isNotEmpty()) {
            row.addView(TextView(this).apply {
                text = message
                textSize = 13f
                setTextColor(Color.parseColor("#6B7280"))
            })
        }
        if (whenText.isNotEmpty()) {
            row.addView(TextView(this).apply {
                text = whenText
                textSize = 12f
                setTextColor(Color.parseColor("#9CA3AF"))
            })
        }
        container.addView(row)
    }
}