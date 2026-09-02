package com.example.parentingmonitoringapp

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class AttendanceRecordsActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore

    private lateinit var spinnerCourse: Spinner
    private lateinit var spinnerSection: Spinner
    private lateinit var tvStatus: TextView
    private lateinit var studentListContainer: LinearLayout

    private val courseSectionsMap = mutableMapOf<String, List<String>>()
    private val courseNames = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance_records)

        db = FirebaseFirestore.getInstance()

        spinnerCourse = findViewById(R.id.spinnerCourse)
        spinnerSection = findViewById(R.id.spinnerSection)
        tvStatus = findViewById(R.id.tvStatus)
        studentListContainer = findViewById(R.id.studentListContainer)

        loadCourses()
    }

    private fun loadCourses() {
        db.collection("courses").get()
            .addOnSuccessListener { docs ->
                courseNames.clear()
                courseSectionsMap.clear()

                for (doc in docs) {
                    val courseName = doc.getString("courseName") ?: doc.id
                    val sectionsRaw = doc.get("sections")
                    val sections: List<String> = when (sectionsRaw) {
                        is List<*> -> sectionsRaw.mapNotNull { it?.toString()?.trim() }
                        is String -> sectionsRaw.trim('[', ']').split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        else -> emptyList()
                    }
                    courseNames.add(courseName)
                    courseSectionsMap[courseName] = sections
                }

                val courseAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, courseNames)
                spinnerCourse.adapter = courseAdapter

                updateSectionSpinner(courseNames.firstOrNull())

                spinnerCourse.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                        updateSectionSpinner(courseNames.getOrNull(position))
                    }
                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                }

                spinnerSection.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                        loadStudents()
                    }
                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                }

                // Setting the initial adapters above happened before these listeners were attached,
                // so it won't have fired loadStudents() on its own — trigger it once manually here.
                loadStudents()
            }
            .addOnFailureListener {
                tvStatus.text = "Failed to load courses. Check your internet connection."
            }
    }

    private fun updateSectionSpinner(courseName: String?) {
        val sections = courseSectionsMap[courseName] ?: emptyList()
        val sectionAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sections)
        spinnerSection.adapter = sectionAdapter
    }

    private fun loadStudents() {
        val course = spinnerCourse.selectedItem?.toString() ?: return
        val section = spinnerSection.selectedItem?.toString() ?: return

        tvStatus.text = "Loading students..."
        studentListContainer.removeAllViews()

        db.collection("students")
            .whereEqualTo("course", course)
            .whereEqualTo("section", section)
            .get()
            .addOnSuccessListener { docs ->
                if (docs.isEmpty) {
                    tvStatus.text = "No students found in $course - $section."
                    return@addOnSuccessListener
                }

                tvStatus.text = "${docs.size()} student(s) - tap to view records:"

                for (doc in docs.documents) {
                    val studentId = doc.id
                    val studentName = doc.getString("studentName")
                    studentListContainer.addView(buildStudentRow(studentId, studentName))
                }
            }
            .addOnFailureListener {
                tvStatus.text = "Failed to load students: ${it.localizedMessage}"
            }
    }

    private fun buildStudentRow(studentId: String, studentName: String?): TextView {
        return TextView(this).apply {
            text = if (studentName != null) "$studentName ($studentId)" else studentId
            textSize = 15f
            setTextColor(Color.parseColor("#22223B"))
            setPadding(28, 28, 28, 28)
            setBackgroundColor(Color.parseColor("#F6F4FE"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 10 }
            setOnClickListener {
                val intent = Intent(this@AttendanceRecordsActivity, StudentAttendanceDetailActivity::class.java)
                intent.putExtra("studentId", studentId)
                intent.putExtra("studentName", studentName)
                startActivity(intent)
            }
        }
    }
}