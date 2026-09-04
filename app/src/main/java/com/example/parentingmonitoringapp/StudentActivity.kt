package com.example.parentingmonitoringapp

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Student Dashboard - main hub for a signed-in student.
 * Shows the student's own name/ID and quick-access cards into their own
 * Profile, Attendance, Exam Schedule, Grades and Notifications (all
 * read-only, scoped to their own studentId only - see Firestore rules).
 *
 * Also owns the background location/geofence setup that was previously the
 * sole purpose of this screen: tracking now starts automatically as soon as
 * the dashboard loads, instead of requiring a separate button tap.
 */
class StudentActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var geofencingClient: GeofencingClient
    private lateinit var tvLocationStatus: TextView

    private lateinit var tvStudentName: TextView
    private lateinit var tvStudentId: TextView

    private var locationCallback: LocationCallback? = null
    private var isTracking = false

    // The signed-in student's own studentId/name, resolved from Firestore.
    private var linkedStudentId: String? = null
    private var linkedStudentName: String? = null

    private val FINE_LOCATION_REQUEST_CODE = 100
    private val BACKGROUND_LOCATION_REQUEST_CODE = 101
    private val GEOFENCE_ID = "SCHOOL_GEOFENCE"

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(this, GeofenceBroadcastReceiver::class.java)
        intent.action = "com.example.parentingmonitoringapp.ACTION_GEOFENCE_EVENT"
        PendingIntent.getBroadcast(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        geofencingClient = LocationServices.getGeofencingClient(this)

        tvStudentName = findViewById(R.id.tvStudentName)
        tvStudentId = findViewById(R.id.tvStudentId)
        tvLocationStatus = findViewById(R.id.tvLocationStatus)

        setupCard(R.id.cardProfile) { openOwnRecord(MyChildActivity::class.java, "My Profile") }
        setupCard(R.id.cardAttendance) { openOwnRecord(StudentAttendanceDetailActivity::class.java) }
        setupCard(R.id.cardExamSchedule) { openOwnRecord(ParentExamScheduleActivity::class.java) }
        setupCard(R.id.cardGrades) { openOwnRecord(GradesActivity::class.java) }
        setupCard(R.id.cardNotifications) { openOwnRecord(ParentNotificationsActivity::class.java) }

        findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener {
            auth.signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        loadOwnStudentInfo()

        // Tracking now starts automatically - no separate "Enable Tracking"
        // tap required, matching the dashboard-first flow.
        checkAndRequestPermissions()
    }

    private fun setupCard(cardId: Int, onClick: () -> Unit) {
        findViewById<android.view.View>(cardId).setOnClickListener { onClick() }
    }

    /** Launches a read-only record screen scoped to this student's own data. */
    private fun openOwnRecord(activityClass: Class<*>, screenTitle: String? = null) {
        val studentId = linkedStudentId
        if (studentId == null) {
            Toast.makeText(this, "Still loading your student record. Please wait.", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, activityClass)
        intent.putExtra("studentId", studentId)
        intent.putExtra("studentName", linkedStudentName)
        if (screenTitle != null) intent.putExtra("screenTitle", screenTitle)
        startActivity(intent)
    }

    /**
     * Resolves this signed-in student's own studentId from their users/{uid}
     * profile (set up by an admin when the student account is created), then
     * loads their name from the students collection for the header.
     */
    private fun loadOwnStudentInfo() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                val studentId = userDoc.getString("studentId")
                if (studentId.isNullOrEmpty()) {
                    tvStudentName.text = "Student"
                    tvStudentId.text = "No student record linked to this account."
                    return@addOnSuccessListener
                }
                linkedStudentId = studentId
                tvStudentId.text = "ID: $studentId"

                db.collection("students").document(studentId).get()
                    .addOnSuccessListener { studentDoc ->
                        val name = studentDoc.getString("studentName") ?: studentId
                        linkedStudentName = name
                        tvStudentName.text = name
                    }
                    .addOnFailureListener {
                        tvStudentName.text = studentId
                    }
            }
            .addOnFailureListener {
                tvStudentName.text = "Student"
                tvStudentId.text = "Unable to load student info."
            }
    }

    private fun checkAndRequestPermissions() {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineLocationGranted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                FINE_LOCATION_REQUEST_CODE
            )
        } else {
            checkBackgroundPermission()
        }
    }

    private fun checkBackgroundPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val backgroundGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!backgroundGranted) {
                Toast.makeText(
                    this,
                    "Sa susunod na dialog, piliin ang 'Allow all the time' para gumana ang background tracking",
                    Toast.LENGTH_LONG
                ).show()
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                    BACKGROUND_LOCATION_REQUEST_CODE
                )
                return
            }
        }
        onAllPermissionsGranted()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            FINE_LOCATION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    checkBackgroundPermission()
                } else {
                    tvLocationStatus.text = "Location permission needed for attendance tracking."
                    Toast.makeText(this, "Kailangan ng location permission", Toast.LENGTH_LONG).show()
                }
            }
            BACKGROUND_LOCATION_REQUEST_CODE -> {
                onAllPermissionsGranted() // tuloy pa rin kahit tumanggi sa background (foreground na lang gagana)
            }
        }
    }

    private fun onAllPermissionsGranted() {
        tvLocationStatus.text = "Permissions granted. Setting up geofence…"
        startLiveLocationDisplay()
        setupGeofence()
    }

    // Live display lang para makita ng student yung distance nila (foreground)
    private fun startLiveLocationDisplay() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return
        if (isTracking) return

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 5000L
        ).setMinUpdateIntervalMillis(3000L).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                displayDistance(location)
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest, locationCallback as LocationCallback, Looper.getMainLooper()
        )
        isTracking = true
    }

    private fun displayDistance(location: Location) {
        db.collection("settings").document("geofence").get()
            .addOnSuccessListener { doc ->
                val schoolLat = doc.getDouble("schoolLat") ?: return@addOnSuccessListener
                val schoolLng = doc.getDouble("schoolLng") ?: return@addOnSuccessListener
                val radius = doc.getDouble("radiusMeters") ?: 100.0

                val schoolLocation = Location("school").apply {
                    latitude = schoolLat
                    longitude = schoolLng
                }
                val distance = location.distanceTo(schoolLocation)

                tvLocationStatus.text = if (distance <= radius) {
                    "✅ INSIDE school area (${distance.toInt()}m) — Background tracking ON"
                } else {
                    "🚶 OUTSIDE school area (${distance.toInt()}m) — Background tracking ON"
                }
            }
    }

    // Ito ang totoong background geofence — gagana kahit sarado ang app
    private fun setupGeofence() {
        db.collection("settings").document("geofence").get()
            .addOnSuccessListener { doc ->
                val schoolLat = doc.getDouble("schoolLat") ?: return@addOnSuccessListener
                val schoolLng = doc.getDouble("schoolLng") ?: return@addOnSuccessListener
                val radius = (doc.getDouble("radiusMeters") ?: 100.0).toFloat()

                val geofence = Geofence.Builder()
                    .setRequestId(GEOFENCE_ID)
                    .setCircularRegion(schoolLat, schoolLng, radius)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                    .build()

                val geofencingRequest = GeofencingRequest.Builder()
                    .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                    .addGeofence(geofence)
                    .build()

                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) return@addOnSuccessListener

                geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Background geofence activated!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Geofence setup failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }

                // I-save ang studentId + UID sa SharedPreferences para magamit ng Receiver later
                saveStudentInfoForReceiver()
            }
    }

    private fun saveStudentInfoForReceiver() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val studentId = doc.getString("studentId") ?: return@addOnSuccessListener
                val parentUid = doc.getString("parentUid") ?: return@addOnSuccessListener

                val prefs = getSharedPreferences("student_prefs", MODE_PRIVATE)
                prefs.edit()
                    .putString("studentId", studentId)
                    .putString("parentUid", parentUid)
                    .apply()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        // Hindi natin tinatanggal ang geofence dito - dapat tumuloy kahit closed ang activity
    }
}