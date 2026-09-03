package com.example.parentingmonitoringapp

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Locale

class AllowanceFragment : Fragment() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private lateinit var tvCurrentAllowance: TextView
    private lateinit var etAllowanceAmount: EditText
    private lateinit var btnSaveAllowance: Button
    private lateinit var tvSaveStatus: TextView
    private lateinit var tvNoticesStatus: TextView
    private lateinit var noticesContainer: LinearLayout

    private var studentId: String? = null
    private var course: String? = null
    private var section: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_allowance, container, false)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        tvCurrentAllowance = view.findViewById(R.id.tvCurrentAllowance)
        etAllowanceAmount = view.findViewById(R.id.etAllowanceAmount)
        btnSaveAllowance = view.findViewById(R.id.btnSaveAllowance)
        tvSaveStatus = view.findViewById(R.id.tvSaveStatus)
        tvNoticesStatus = view.findViewById(R.id.tvNoticesStatus)
        noticesContainer = view.findViewById(R.id.noticesContainer)

        btnSaveAllowance.setOnClickListener { saveAllowance() }

        loadParentProfile()

        return view
    }

    private fun loadParentProfile() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            tvCurrentAllowance.text = "Not logged in."
            tvNoticesStatus.text = ""
            return
        }

        db.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                val sid = ChildLinkStore.resolveSelectedStudentId(requireContext(), uid, userDoc)
                if (sid == null) {
                    tvCurrentAllowance.text = "No linked student found."
                    tvNoticesStatus.text = ""
                    return@addOnSuccessListener
                }
                studentId = sid

                db.collection("students").document(sid).get()
                    .addOnSuccessListener { studentDoc ->
                        if (!isAdded) return@addOnSuccessListener
                        course = studentDoc.getString("course")
                        section = studentDoc.getString("section")
                        loadCurrentAllowance(sid)
                        loadNotices(course, section)
                    }
                    .addOnFailureListener {
                        if (!isAdded) return@addOnFailureListener
                        loadCurrentAllowance(sid)
                        tvNoticesStatus.text = "Unable to load child's course/section."
                    }
            }
            .addOnFailureListener {
                tvCurrentAllowance.text = "Failed to load profile: ${it.localizedMessage}"
            }
    }

    private fun loadCurrentAllowance(sid: String) {
        db.collection("allowance").document(sid).get()
            .addOnSuccessListener { doc ->
                val amount = doc.getDouble("amount")
                tvCurrentAllowance.text = if (amount != null) {
                    "Current allowance: ₱$amount"
                } else {
                    "No allowance set yet."
                }
            }
            .addOnFailureListener {
                tvCurrentAllowance.text = "Failed to load allowance: ${it.localizedMessage}"
            }
    }

    private fun saveAllowance() {
        val sid = studentId
        val uid = auth.currentUser?.uid
        if (sid == null || uid == null) {
            tvSaveStatus.text = "No linked student found."
            return
        }

        val amountText = etAllowanceAmount.text.toString().trim()
        val amount = amountText.toDoubleOrNull()
        if (amount == null || amount < 0) {
            tvSaveStatus.text = "Please enter a valid amount."
            return
        }

        val allowanceData = hashMapOf(
            "studentId" to sid,
            "course" to (course ?: ""),
            "section" to (section ?: ""),
            "amount" to amount,
            "setBy" to uid,
            "updatedAt" to Timestamp.now()
        )

        btnSaveAllowance.isEnabled = false
        db.collection("allowance").document(sid).set(allowanceData)
            .addOnSuccessListener {
                btnSaveAllowance.isEnabled = true
                tvSaveStatus.setTextColor(Color.parseColor("#2FD3A6"))
                tvSaveStatus.text = "Allowance saved."
                etAllowanceAmount.text.clear()
                loadCurrentAllowance(sid)
            }
            .addOnFailureListener {
                btnSaveAllowance.isEnabled = true
                tvSaveStatus.setTextColor(Color.parseColor("#E5484D"))
                tvSaveStatus.text = "Failed to save: ${it.localizedMessage}"
            }
    }

    private fun loadNotices(course: String?, section: String?) {
        if (course == null || section == null) {
            tvNoticesStatus.text = "No course/section on file."
            return
        }

        db.collection("expense_notices")
            .whereEqualTo("course", course)
            .whereEqualTo("section", section)
            .get()
            .addOnSuccessListener { docs ->
                noticesContainer.removeAllViews()

                if (docs.isEmpty) {
                    tvNoticesStatus.text = "No expense notices yet."
                    return@addOnSuccessListener
                }

                val sorted = docs.documents.sortedByDescending { it.getTimestamp("sentAt") }
                tvNoticesStatus.text = "${sorted.size} notice(s):"

                for (doc in sorted) {
                    val title = doc.getString("title") ?: "?"
                    val message = doc.getString("message") ?: ""
                    val amount = doc.getDouble("amount") ?: 0.0
                    val sentAt = doc.getTimestamp("sentAt")
                    noticesContainer.addView(buildNoticeView(title, message, amount, sentAt))
                }
            }
            .addOnFailureListener {
                tvNoticesStatus.text = "Failed to load notices: ${it.localizedMessage}"
            }
    }

    private fun buildNoticeView(title: String, message: String, amount: Double, sentAt: Timestamp?): LinearLayout {
        val box = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            background = androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.bg_rounded_row)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 10 }
        }

        val tvTitle = TextView(requireContext()).apply {
            text = if (amount > 0) "$title - ₱$amount" else title
            textSize = 15f
            setTextColor(Color.parseColor("#22223B"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val tvMessage = TextView(requireContext()).apply {
            text = message
            textSize = 13f
            setTextColor(Color.parseColor("#6B7280"))
        }
        val tvDate = TextView(requireContext()).apply {
            text = formatTimestamp(sentAt)
            textSize = 11f
            setTextColor(Color.parseColor("#9CA3AF"))
        }

        box.addView(tvTitle)
        box.addView(tvMessage)
        box.addView(tvDate)
        return box
    }

    private fun formatTimestamp(timestamp: Timestamp?): String {
        if (timestamp == null) return ""
        val sdf = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())
        return sdf.format(timestamp.toDate())
    }
}