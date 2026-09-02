package com.example.parentingmonitoringapp

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Locale

class ReportsActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore

    private lateinit var spinnerCourse: Spinner
    private lateinit var spinnerSection: Spinner
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var resultsContainer: LinearLayout

    private val courseSectionsMap = mutableMapOf<String, List<String>>()
    private val courseNames = mutableListOf<String>()

    private val ALL_COURSES = "All Courses"
    private val ALL_SECTIONS = "All Sections"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reports)

        db = FirebaseFirestore.getInstance()

        spinnerCourse = findViewById(R.id.spinnerCourse)
        spinnerSection = findViewById(R.id.spinnerSection)
        progressBar = findViewById(R.id.progressBar)
        tvStatus = findViewById(R.id.tvStatus)
        resultsContainer = findViewById(R.id.resultsContainer)

        findViewById<Button>(R.id.btnRoster).setOnClickListener { loadRoster() }
        findViewById<Button>(R.id.btnAttendance).setOnClickListener { loadAttendance() }
        findViewById<Button>(R.id.btnExams).setOnClickListener { loadExams() }
        findViewById<Button>(R.id.btnAllowance).setOnClickListener { loadAllowance() }
        findViewById<Button>(R.id.btnAbsences).setOnClickListener { loadAbsences() }

        loadCourses()
    }

    private fun loadCourses() {
        db.collection("courses").get()
            .addOnSuccessListener { docs ->
                courseNames.clear()
                courseSectionsMap.clear()
                courseNames.add(ALL_COURSES)
                courseSectionsMap[ALL_COURSES] = emptyList()

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

                updateSectionSpinner(ALL_COURSES)

                spinnerCourse.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                        updateSectionSpinner(courseNames.getOrNull(position))
                    }
                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                }
            }
            .addOnFailureListener {
                tvStatus.text = "Failed to load courses. Check your internet connection."
            }
    }

    private fun updateSectionSpinner(courseName: String?) {
        val sections = mutableListOf(ALL_SECTIONS)
        sections.addAll(courseSectionsMap[courseName] ?: emptyList())
        val sectionAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sections)
        spinnerSection.adapter = sectionAdapter
    }

    private fun selectedCourse() = spinnerCourse.selectedItem?.toString() ?: ALL_COURSES
    private fun selectedSection() = spinnerSection.selectedItem?.toString() ?: ALL_SECTIONS

    /** Fetches student docs matching the current course/section filter. */
    private fun getTargetStudents(onResult: (List<DocumentSnapshot>) -> Unit) {
        val course = selectedCourse()
        val section = selectedSection()

        val query = when {
            course == ALL_COURSES -> db.collection("students")
            section == ALL_SECTIONS -> db.collection("students").whereEqualTo("course", course)
            else -> db.collection("students").whereEqualTo("course", course).whereEqualTo("section", section)
        }

        query.get()
            .addOnSuccessListener { onResult(it.documents) }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                tvStatus.text = "Failed to load students: ${it.localizedMessage}"
            }
    }

    private fun startLoading(label: String) {
        progressBar.visibility = View.VISIBLE
        tvStatus.text = "Loading $label..."
        resultsContainer.removeAllViews()
    }

    private fun finishLoading(headerText: String) {
        progressBar.visibility = View.GONE
        tvStatus.text = headerText
    }

    // ---------- Roster ----------
    private fun loadRoster() {
        startLoading("roster")
        getTargetStudents { students ->
            finishLoading("Student Roster (${students.size} total) - ${selectedCourse()} / ${selectedSection()}")

            val counts = LinkedHashMap<String, Int>()
            for (doc in students) {
                val course = doc.getString("course") ?: "Unknown"
                val section = doc.getString("section") ?: "Unknown"
                val key = "$course - $section"
                counts[key] = (counts[key] ?: 0) + 1
            }
            for ((key, count) in counts) {
                addResultRow(key, "$count student(s)")
            }
        }
    }

    // ---------- Attendance ----------
    private fun loadAttendance() {
        startLoading("attendance")
        getTargetStudents { students ->
            if (students.isEmpty()) {
                finishLoading("No students found for this filter.")
                return@getTargetStudents
            }
            val studentIds = students.map { it.id }

            db.collection("attendance")
                .whereIn("studentId", studentIds.take(30))
                .get()
                .addOnSuccessListener { docs ->
                    var inCount = 0
                    var outCount = 0
                    val perStudent = LinkedHashMap<String, Pair<Int, Int>>()

                    for (doc in docs) {
                        val studentId = doc.getString("studentId") ?: continue
                        val type = doc.getString("type") ?: continue
                        val current = perStudent[studentId] ?: (0 to 0)
                        if (type == "IN") {
                            inCount++
                            perStudent[studentId] = (current.first + 1) to current.second
                        } else {
                            outCount++
                            perStudent[studentId] = current.first to (current.second + 1)
                        }
                    }

                    finishLoading("Attendance Summary - Total IN: $inCount, Total OUT: $outCount")
                    if (studentIds.size > 30) {
                        addResultRow("Note", "Only first 30 students shown (Firestore query limit)")
                    }
                    for (studentId in studentIds) {
                        val (ins, outs) = perStudent[studentId] ?: (0 to 0)
                        addResultRow(studentId, "$ins IN / $outs OUT")
                    }
                }
                .addOnFailureListener {
                    finishLoading("Failed to load attendance: ${it.localizedMessage}")
                }
        }
    }

    // ---------- Exams ----------
    private fun loadExams() {
        startLoading("exam schedules")
        val course = selectedCourse()
        val section = selectedSection()

        val query = when {
            course == ALL_COURSES -> db.collection("exam_schedules")
            section == ALL_SECTIONS -> db.collection("exam_schedules").whereEqualTo("course", course)
            else -> db.collection("exam_schedules").whereEqualTo("course", course).whereEqualTo("section", section)
        }

        query.get()
            .addOnSuccessListener { docs ->
                val sorted = docs.documents.sortedByDescending { it.getTimestamp("sentAt") }
                finishLoading("Exam Schedule (${sorted.size} entries) - $course / $section")

                if (sorted.isEmpty()) {
                    addResultRow("No exams found", "")
                }
                for (doc in sorted) {
                    val subject = doc.getString("subject") ?: "?"
                    val examDate = doc.getString("examDate") ?: "?"
                    val examTime = doc.getString("examTime") ?: ""
                    val docCourse = doc.getString("course") ?: ""
                    val docSection = doc.getString("section") ?: ""
                    addResultRow("$subject ($docCourse - $docSection)", "$examDate $examTime")
                }
            }
            .addOnFailureListener {
                finishLoading("Failed to load exams: ${it.localizedMessage}")
            }
    }

    // ---------- Allowance ----------
    private fun loadAllowance() {
        startLoading("allowance data")
        getTargetStudents { students ->
            if (students.isEmpty()) {
                finishLoading("No students found for this filter.")
                return@getTargetStudents
            }
            val studentIds = students.map { it.id }

            db.collection("allowance")
                .whereIn(FieldPath.documentId(), studentIds.take(30))
                .get()
                .addOnSuccessListener { docs ->
                    var total = 0.0
                    val amounts = LinkedHashMap<String, Double>()
                    for (doc in docs) {
                        val amount = doc.getDouble("amount") ?: 0.0
                        amounts[doc.id] = amount
                        total += amount
                    }

                    finishLoading("Allowance Summary - Total: ₱$total (${amounts.size} of ${studentIds.size} students set)")
                    if (studentIds.size > 30) {
                        addResultRow("Note", "Only first 30 students shown (Firestore query limit)")
                    }
                    for (studentId in studentIds) {
                        val amount = amounts[studentId]
                        addResultRow(studentId, if (amount != null) "₱$amount" else "Not set")
                    }

                    loadExpenseNoticesSection()
                }
                .addOnFailureListener {
                    finishLoading("Failed to load allowance: ${it.localizedMessage}")
                }
        }
    }

    private fun loadExpenseNoticesSection() {
        val course = selectedCourse()
        val section = selectedSection()

        val query = when {
            course == ALL_COURSES -> db.collection("expense_notices")
            section == ALL_SECTIONS -> db.collection("expense_notices").whereEqualTo("course", course)
            else -> db.collection("expense_notices").whereEqualTo("course", course).whereEqualTo("section", section)
        }

        query.get()
            .addOnSuccessListener { docs ->
                val sorted = docs.documents.sortedByDescending { it.getTimestamp("sentAt") }
                addSectionHeader("Expense Notices Sent (${sorted.size})")
                for (doc in sorted) {
                    val title = doc.getString("title") ?: "?"
                    val amount = doc.getDouble("amount") ?: 0.0
                    val sentAt = doc.getTimestamp("sentAt")
                    val formatted = formatTimestamp(sentAt)
                    val amountText = if (amount > 0) " - ₱$amount" else ""
                    addResultRow("$title$amountText", formatted)
                }
            }
    }

    // ---------- Absences ----------
    private fun loadAbsences() {
        val course = selectedCourse()
        val section = selectedSection()

        if (course == ALL_COURSES || section == ALL_SECTIONS) {
            startLoading("absences")
            finishLoading("Please select a specific Course and Section for absence tracking.")
            return
        }

        startLoading("absences")
        val scheduleId = "${course}_$section"

        db.collection("section_schedule").document(scheduleId).get()
            .addOnSuccessListener { doc ->
                val days = (doc.get("days") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                if (days.isEmpty()) {
                    finishLoading("No schedule set yet for $course - $section.")
                    showScheduleSetup(scheduleId, course, section)
                } else {
                    finishLoading("Absences for $course - $section (${getMonthName()})")
                    computeAbsences(course, section, days)
                }
            }
            .addOnFailureListener {
                finishLoading("Failed to load schedule: ${it.localizedMessage}")
            }
    }

    private val weekdayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    private fun showScheduleSetup(scheduleId: String, course: String, section: String) {
        addSectionHeader("Set class days for $course - $section")

        val checkboxes = mutableListOf<android.widget.CheckBox>()
        for (label in weekdayLabels) {
            val cb = android.widget.CheckBox(this).apply {
                text = label
                isChecked = label in listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            }
            checkboxes.add(cb)
            resultsContainer.addView(cb)
        }

        val saveBtn = Button(this).apply {
            text = "Save Schedule"
            setTextColor(Color.parseColor("#FFFFFF"))
            setBackgroundColor(Color.parseColor("#7B6EF6"))
            setOnClickListener {
                val selectedDays = checkboxes.filter { it.isChecked }.map { it.text.toString() }
                if (selectedDays.isEmpty()) {
                    tvStatus.text = "Please select at least one day."
                    return@setOnClickListener
                }
                val data = hashMapOf(
                    "course" to course,
                    "section" to section,
                    "days" to selectedDays
                )
                db.collection("section_schedule").document(scheduleId).set(data)
                    .addOnSuccessListener {
                        finishLoading("Schedule saved. Loading absences...")
                        computeAbsences(course, section, selectedDays)
                    }
                    .addOnFailureListener {
                        tvStatus.text = "Failed to save schedule: ${it.localizedMessage}"
                    }
            }
        }
        resultsContainer.addView(saveBtn)
    }

    private fun getMonthName(): String {
        val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        return sdf.format(java.util.Date())
    }

    private fun computeAbsences(course: String, section: String, scheduledDays: List<String>) {
        getTargetStudents { students ->
            if (students.isEmpty()) {
                finishLoading("No students found in $course - $section.")
                return@getTargetStudents
            }
            val studentIds = students.map { it.id }

            // Build the list of school days from the 1st of this month up to today
            val calendar = java.util.Calendar.getInstance()
            calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
            val today = java.util.Calendar.getInstance()
            val weekdayFormat = SimpleDateFormat("EEE", Locale.ENGLISH)
            val dateKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            val schoolDaysSoFar = mutableListOf<String>() // yyyy-MM-dd, only scheduled weekdays
            while (!calendar.after(today)) {
                val weekday = weekdayFormat.format(calendar.time)
                if (weekday in scheduledDays) {
                    schoolDaysSoFar.add(dateKeyFormat.format(calendar.time))
                }
                calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
            }

            if (schoolDaysSoFar.isEmpty()) {
                finishLoading("No school days have occurred yet this month for $course - $section.")
                return@getTargetStudents
            }

            db.collection("attendance")
                .whereIn("studentId", studentIds.take(30))
                .get()
                .addOnSuccessListener { docs ->
                    // Build a set of "studentId|yyyy-MM-dd" for every day a student had a TIME IN
                    val presentSet = mutableSetOf<String>()
                    for (attDoc in docs) {
                        val sid = attDoc.getString("studentId") ?: continue
                        val type = attDoc.getString("type") ?: continue
                        val ts = attDoc.getTimestamp("timestamp") ?: continue
                        if (type != "IN") continue
                        val dateKey = dateKeyFormat.format(ts.toDate())
                        presentSet.add("$sid|$dateKey")
                    }

                    finishLoading("Absences for $course - $section (${getMonthName()}) - ${schoolDaysSoFar.size} school day(s) so far")
                    if (studentIds.size > 30) {
                        addResultRow("Note", "Only first 30 students shown (Firestore query limit)")
                    }

                    for (sid in studentIds) {
                        val absentCount = schoolDaysSoFar.count { dateKey -> "$sid|$dateKey" !in presentSet }
                        addResultRow(sid, "$absentCount absence(s) / ${schoolDaysSoFar.size} school day(s)")
                    }
                }
                .addOnFailureListener {
                    finishLoading("Failed to load attendance: ${it.localizedMessage}")
                }
        }
    }

    private fun formatTimestamp(timestamp: Timestamp?): String {
        if (timestamp == null) return ""
        val sdf = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())
        return sdf.format(timestamp.toDate())
    }

    // ---------- UI helpers ----------
    private fun addSectionHeader(text: String) {
        val tv = TextView(this).apply {
            this.text = text
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            textSize = 15f
            setTextColor(Color.parseColor("#22223B"))
            setPadding(0, 24, 0, 8)
        }
        resultsContainer.addView(tv)
    }

    private fun addResultRow(left: String, right: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(20, 16, 20, 16)
            setBackgroundColor(Color.parseColor("#F6F4FE"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8 }
        }
        val tvLeft = TextView(this).apply {
            text = left
            textSize = 13f
            setTextColor(Color.parseColor("#22223B"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvRight = TextView(this).apply {
            text = right
            textSize = 13f
            setTextColor(Color.parseColor("#6B7280"))
        }
        row.addView(tvLeft)
        row.addView(tvRight)
        resultsContainer.addView(row)
    }
}