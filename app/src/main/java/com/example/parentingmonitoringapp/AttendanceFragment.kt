package com.example.parentingmonitoringapp

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

class AttendanceFragment : Fragment() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var tvStatus: TextView
    private lateinit var listContainer: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_attendance, container, false)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        tvStatus = view.findViewById(R.id.tvStatus)
        listContainer = view.findViewById(R.id.listContainer)

        loadAttendance()

        return view
    }

    private fun loadAttendance() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            tvStatus.text = "Not logged in."
            return
        }

        tvStatus.text = "Loading attendance records..."

        // Step 1: kunin ang studentId na naka-link sa parent na ito
        db.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                val studentId = ChildLinkStore.resolveSelectedStudentId(requireContext(), uid, userDoc)
                if (studentId == null) {
                    tvStatus.text = "No linked student found."
                    return@addOnSuccessListener
                }

                // Step 2: kunin lahat ng attendance records ng student na ito
                db.collection("attendance")
                    .whereEqualTo("studentId", studentId)
                    .get()
                    .addOnSuccessListener { docs ->
                        listContainer.removeAllViews()

                        if (docs.isEmpty) {
                            tvStatus.text = "Wala pang naitatalang attendance para dito."
                            return@addOnSuccessListener
                        }

                        // I-sort by timestamp, pinaka-bago muna
                        val sortedDocs = docs.documents.sortedByDescending {
                            it.getTimestamp("timestamp")
                        }

                        tvStatus.text = "${sortedDocs.size} record(s) found:"

                        for (doc in sortedDocs) {
                            val type = doc.getString("type") ?: "?"
                            val timestamp = doc.getTimestamp("timestamp")
                            val formattedTime = formatTimestamp(timestamp)

                            listContainer.addView(buildEntryView(type, formattedTime))
                        }
                    }
                    .addOnFailureListener {
                        tvStatus.text = "Failed to load attendance: ${it.localizedMessage}"
                    }
            }
            .addOnFailureListener {
                tvStatus.text = "Failed to load profile: ${it.localizedMessage}"
            }
    }

    private fun formatTimestamp(timestamp: Timestamp?): String {
        if (timestamp == null) return "Unknown time"
        val sdf = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())
        return sdf.format(timestamp.toDate())
    }

    private fun buildEntryView(type: String, formattedTime: String): LinearLayout {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8 }
        }

        val tvType = TextView(requireContext()).apply {
            text = if (type == "IN") "🟢 TIME IN" else "🔴 TIME OUT"
            setTextColor(if (type == "IN") Color.parseColor("#2E7D32") else Color.parseColor("#D32F2F"))
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvTime = TextView(requireContext()).apply {
            text = formattedTime
            textSize = 13f
            setTextColor(Color.parseColor("#555555"))
        }

        row.addView(tvType)
        row.addView(tvTime)
        return row
    }
}