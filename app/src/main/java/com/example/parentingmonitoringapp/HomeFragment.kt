package com.example.parentingmonitoringapp

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Parent Dashboard - main view (Select Child / Main View).
 * Shows the signed-in parent's name and their linked child, plus quick-access
 * cards into the child's Attendance, Late Records, Exam Schedule, Grades and
 * Notifications.
 */
class HomeFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var linkedStudentId: String? = null
    private var linkedStudentName: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setupCard(view, R.id.cardMyChild, R.drawable.ic_family, "My Child") {
            openChildScreen(MyChildActivity::class.java)
        }
        setupCard(view, R.id.cardAttendance, R.drawable.ic_calendar, "Attendance") {
            openChildScreen(StudentAttendanceDetailActivity::class.java)
        }
        setupCard(view, R.id.cardLateRecords, R.drawable.ic_warning, "Late Records") {
            openChildScreen(LateRecordsActivity::class.java)
        }
        setupCard(view, R.id.cardExamSchedule, R.drawable.ic_badge, "Exam Schedule") {
            openChildScreen(ParentExamScheduleActivity::class.java)
        }
        setupCard(view, R.id.cardGrades, R.drawable.ic_grade, "Grades") {
            openChildScreen(GradesActivity::class.java)
        }
        setupCard(view, R.id.cardNotifications, R.drawable.ic_notification, "Notifications") {
            openChildScreen(ParentNotificationsActivity::class.java)
        }

        view.findViewById<ImageView>(R.id.ivParentAvatar).apply {
            clipToCircle()
            setOnClickListener { startActivity(Intent(requireContext(), ProfileActivity::class.java)) }
        }

        view.findViewById<TextView>(R.id.btnLogout).setOnClickListener {
            auth.signOut()
            val intent = Intent(requireContext(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        loadParentAndChild(view)
    }

    override fun onResume() {
        super.onResume()
        // Re-check in case the parent switched children on the Select Child screen.
        if (view != null && linkedStudentId != null) {
            loadParentAndChild(requireView())
        }
    }

    private fun setupCard(
        root: View, cardId: Int, iconRes: Int, label: String, onClick: () -> Unit
    ) {
        val card = root.findViewById<View>(cardId)
        card.findViewById<ImageView>(R.id.cardIcon).setImageResource(iconRes)
        card.findViewById<TextView>(R.id.cardLabel).text = label
        card.setOnClickListener { onClick() }
    }

    private fun openChildScreen(activityClass: Class<*>) {
        val studentId = linkedStudentId
        if (studentId == null) {
            Toast.makeText(requireContext(), "No linked child found yet.", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(requireContext(), activityClass)
        intent.putExtra("studentId", studentId)
        intent.putExtra("studentName", linkedStudentName)
        startActivity(intent)
    }

    private fun loadParentAndChild(view: View) {
        val uid = auth.currentUser?.uid ?: return
        val tvParentName = view.findViewById<TextView>(R.id.tvParentName)
        val tvMonitoring = view.findViewById<TextView>(R.id.tvMonitoringChild)

        db.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                if (!isAdded) return@addOnSuccessListener
                val parentName = userDoc.getString("name") ?: "Parent"
                tvParentName.text = parentName
                view.findViewById<ImageView>(R.id.ivParentAvatar).loadAvatar(userDoc.getString("photoUrl"))

                val linkedChildren = ChildLinkStore.getLinkedStudentIds(userDoc)
                val studentId = ChildLinkStore.resolveSelectedStudentId(requireContext(), uid, userDoc)
                linkedStudentId = studentId

                // With more than one linked child, the header doubles as a
                // "Select Child" entry point so the parent can switch views.
                if (linkedChildren.size > 1) {
                    tvMonitoring.setOnClickListener {
                        startActivity(Intent(requireContext(), SelectChildActivity::class.java))
                    }
                } else {
                    tvMonitoring.setOnClickListener(null)
                }

                if (studentId.isNullOrEmpty()) {
                    tvMonitoring.text = "No linked child found"
                    return@addOnSuccessListener
                }

                db.collection("students").document(studentId).get()
                    .addOnSuccessListener { studentDoc ->
                        if (!isAdded) return@addOnSuccessListener
                        val studentName = studentDoc.getString("studentName") ?: studentId
                        linkedStudentName = studentName
                        tvMonitoring.text = if (linkedChildren.size > 1) {
                            "Monitoring: $studentName  ▾ Switch"
                        } else {
                            "Monitoring: $studentName"
                        }
                    }
                    .addOnFailureListener {
                        if (!isAdded) return@addOnFailureListener
                        tvMonitoring.text = "Monitoring: $studentId"
                    }
            }
            .addOnFailureListener {
                if (!isAdded) return@addOnFailureListener
                tvParentName.text = "Parent"
                tvMonitoring.text = "Unable to load child info"
            }
    }
}