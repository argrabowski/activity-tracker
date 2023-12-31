package com.example.cs_528_project_3

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.cs_528_project_3.Constants.Companion.ACTION_ACTIVITY_RECOGNITION
import com.example.cs_528_project_3.Constants.Companion.ACTIVITY_DETECTION_INTERVAL_IN_MILLISECONDS
import com.example.cs_528_project_3.Constants.Companion.ACTIVITY_RECOGNITION_PERMISSION_CODE
import com.example.cs_528_project_3.Constants.Companion.ALPHA
import com.example.cs_528_project_3.Constants.Companion.BACKGROUND_LOCATION_PERMISSION_CODE
import com.example.cs_528_project_3.Constants.Companion.EXTRA_ACTIVITY_STRING
import com.example.cs_528_project_3.Constants.Companion.GEOFENCE_EXPIRATION_IN_MILLISECONDS
import com.example.cs_528_project_3.Constants.Companion.GEOFENCE_RADIUS_IN_METERS
import com.example.cs_528_project_3.Constants.Companion.TAG_MAIN
import com.example.cs_528_project_3.Constants.Companion.TAG_RECOGNITION
import com.example.cs_528_project_3.PermissionUtils.PermissionDeniedDialog.Companion.newInstance
import com.example.cs_528_project_3.PermissionUtils.isPermissionGranted
import com.example.cs_528_project_3.PermissionUtils.requestActivityRecognitionPermission
import com.example.cs_528_project_3.PermissionUtils.requestBackgoundLocationPermission
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity(),  OnMapReadyCallback, SensorEventListener,
    ActivityCompat.OnRequestPermissionsResultCallback, OnCompleteListener<Void> {

    private lateinit var map: GoogleMap
    private var permissionDenied = false
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var addressTextView: TextView
    private lateinit var geofencingClient: GeofencingClient
    private var mGeofenceList: ArrayList<Geofence>? = null
    private var mGeofencePendingIntent: PendingIntent? = null
    private lateinit var fullerTV: TextView
    private lateinit var gordonTV: TextView
    private lateinit var stepsTV: TextView
    private var initialMapZoom: Boolean = false
    private var stepData: ArrayList<Float>? = null
    private var lastStepTimestamp: Long? = null

    private var sensorManager: SensorManager? = null
    // Activity recognition TV, client, and intent
    private lateinit var activityTV: TextView
    private lateinit var activityIV: ImageView
    private lateinit var activityRecognitionClient: ActivityRecognitionClient
    private lateinit var activityRecognitionPendingIntent: PendingIntent
    private lateinit var activityRecognitionReceiver: BroadcastReceiver
    private var stepDataIndex:Int = 0
    private var stepCounter:Int = 0

    private var countingSteps: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        addressTextView = findViewById(R.id.address_tv)
        mGeofenceList = ArrayList()
        stepData = ArrayList()
        fullerTV = findViewById(R.id.fullerGeoCounter)
        gordonTV = findViewById(R.id.libraryGeoCounter)
        stepsTV = findViewById(R.id.stepsCounter)
        stepsTV.text = getString(R.string.steps_counter, "0")
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        generateGeofences()
        geofencingClient = LocationServices.getGeofencingClient(this)

        ApplicationState.getGeoCounter().observe(this ) { items ->
            fullerTV.text = getString(R.string.visits_to_fuller_labs_geofence, items[0].toString())
            gordonTV.text = getString(R.string.visits_to_library_geofence, items[1].toString())
        }

        ApplicationState.getActivity().observe(this ) { item ->
            Log.d(TAG_RECOGNITION, "Observed Change")
            val activityString = item.toString()
            activityTV.text = getString(R.string.current_activity, activityString)
            val imageResId = when(activityString) {
                "In vehicle" -> R.drawable.in_vehicle
                "Running" -> R.drawable.running
                "Still" -> R.drawable.still
                "Walking" -> R.drawable.walking
                else -> R.drawable.ic_launcher_background
            }
            activityIV.setImageResource(imageResId)
        }

        // Set up activity recognition variables
        initActivityRecognition()

        //setup step counter
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG_MAIN, "IN ON RESUMNE")
        countingSteps = true
        val stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (stepSensor == null) {
            Toast.makeText(this, "Unable to activate step sensor: No Sensor Available", Toast.LENGTH_SHORT).show()
        } else {
            Log.d(TAG_MAIN, "MAKING STEP SENSROr")
            sensorManager?.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_UI)
        }
    }


    private fun initActivityRecognition() {
        // Set-up activity recognition TV, client, and intent
        activityTV = findViewById(R.id.activity_tv)
        activityIV = findViewById(R.id.activity_iv)
        activityRecognitionClient = ActivityRecognition.getClient(this)
        activityRecognitionPendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            Intent(this, ActivityRecognitionBroadcastReceiver::class.java),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Set-up activity recognition receiver
        activityRecognitionReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Log.d(TAG_RECOGNITION, "In receiver for main")
                if (intent.action == ACTION_ACTIVITY_RECOGNITION) {
                    val activityString = intent.getStringExtra(EXTRA_ACTIVITY_STRING)
                    updateActivityText(activityString)
                }
            }
        }
    }

    private fun startTrackingActivity() {
        // Check if app has the ACTIVITY_RECOGNITION permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG_RECOGNITION, "Missing permission")
            return
        }
        // If permission is already granted, start tracking activity
        val task = activityRecognitionClient.requestActivityUpdates(
            ACTIVITY_DETECTION_INTERVAL_IN_MILLISECONDS,
            activityRecognitionPendingIntent
        )
        task.addOnSuccessListener {
            Log.d(TAG_RECOGNITION, "Successfully requested activity updates")
        }
        task.addOnFailureListener { e ->
            Log.e(TAG_RECOGNITION, "Requesting activity updates failed to start", e)
        }

    }

    fun updateActivityText(activityString: String?) {
        if (activityString != null) {
            activityTV.text = "Current activity: $activityString"
        } else {
            activityTV.text = "Activity recognition unavailable"
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        enablePermissions()
    }


    @SuppressLint("MissingPermission")
    private fun enablePermissions() {

        // 1. Check if permissions are granted, if so, enable the my location layer
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            map.isMyLocationEnabled = true
            Log.d("PERMISSIONS_CHECK", "OPTION 1")
            startLocationUpdates()
            addGeofencesFun()
            startTrackingActivity()
            return
        }

        // 2. If if a permission rationale dialog should be shown
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) || ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) || ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) ||
            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.ACTIVITY_RECOGNITION
            )
        ) {
            Log.d("PERMISSIONS_CHECK", "OPTION 2")
            PermissionUtils.RationaleDialog.newInstance(
                LOCATION_PERMISSION_REQUEST_CODE, true
            ).show(supportFragmentManager, "dialog")
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this@MainActivity,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                )
            ) {
                requestBackgoundLocationPermission(this, this@MainActivity)
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                    BACKGROUND_LOCATION_PERMISSION_CODE
                )
            }


            Log.d(TAG_RECOGNITION, "STARTING TO REQ POER")
            requestActivityRecognitionPermission(this, this@MainActivity)

            return
        }

        // 3. Otherwise, request permission
        Log.d("PERMISSIONS_CHECK", "OPTION 3")
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                Manifest.permission.ACTIVITY_RECOGNITION
            ),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    private fun addGeofencesFun() {
        //TODO: Add permissions check
        Log.d("MAIN", "Checking permissions  IN ADD GEOFENCES")
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d("GEO_MAIN", "MISSING FINE PERMISSIONS")
            return
        }
        if(ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) != PackageManager.PERMISSION_GRANTED){
            Log.d("GEO_MAIN", "MISSING BACKGROUND PERMISSIONS")
            return
        }
        Log.d("MAIN", "WORKING IN ADD GEOFENCES")
        var geoReq = getGeofencingRequest()
        var geoIntent = getGeofencePendingIntent
        if(geoReq != null && geoIntent != null){
            Log.d("MAIN_GEO", "ADDING")
            geofencingClient.addGeofences(geoReq, geoIntent)
                .addOnCompleteListener(this)
                .addOnSuccessListener {

                    mGeofenceList?.forEach{ item ->
                        Log.d("GEO_MAIN", "geo item is")
                        map.addCircle(
                            CircleOptions()
                                .center(LatLng(item.latitude, item.longitude))
                                .radius(item.radius.toDouble())
                                .strokeColor(ContextCompat.getColor(this, R.color.transparent_blue))
                                .fillColor(ContextCompat.getColor(this, R.color.transparent_blue))
                        )
                    }

                    Log.d("GEO_MAIN", "IN ON SUCCESS")
                }
                .addOnFailureListener { Log.d("GEO_MAIN", "IN ON FAILURE") }
        }
    }

    private val getGeofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(this, GeofenceBroadcastReceiver::class.java)
        // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when calling
        // addGeofences() and removeGeofences().
        PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun getGeofencingRequest(): GeofencingRequest? {
        val builder = GeofencingRequest.Builder()
        Log.d("MAIN", "In GEOFENCES REQUEST")
        builder.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_DWELL)

        // Add the geofences to be monitored by geofencing service.
        Log.d("GEO_REQ", mGeofenceList.toString())
        builder.addGeofences(mGeofenceList!!)

        // Return a GeofencingRequest.
        return builder.build()
    }


    override fun onResumeFragments() {
        super.onResumeFragments()
        if (permissionDenied) {
            // Permission was not granted, display error dialog.
            showMissingPermissionError()
            permissionDenied = false
        }

        Log.d("Main", "On Resume Fragment")
    }

    private fun startLocationUpdates() {
        Log.d("Main", "Starting location updates listener")
        val locationReq = createLocationRequest()
        var locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                Log.d("MAIN", "LOCATION RESULT")
                locationResult ?: return
                for (location in locationResult.locations){
                    // Update UI with location data
                    val loc = LatLng(location.latitude, location.longitude)
                    if(!initialMapZoom) {
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(loc, 15f))
                        initialMapZoom = true
                    }

                    val geocodeListener = Geocoder.GeocodeListener { addresses ->
                        setSemanticAddress(addresses)
                    }
                    //Set the address TextView
                    val geocoder = Geocoder(applicationContext, Locale.getDefault())
                    if (Build.VERSION.SDK_INT >= 33) {
                        geocoder.getFromLocation(location.latitude, location.longitude, 1, geocodeListener)
                    } else {
                        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        setSemanticAddress(addresses)
                    }



                }
            }
        }
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (locationReq != null) {
            fusedLocationClient.requestLocationUpdates(locationReq,
                locationCallback,
                Looper.getMainLooper())
        }

    }

    fun setSemanticAddress(addresses: MutableList<Address>?){
        Log.d("MAIN", "got Semantic Address")
        if(addresses == null){
            addressTextView.text = "Address not available "
            return
        }
        else {
            val address = addresses[0]
            var stringAddress = ""
            for (i in 0..address.maxAddressLineIndex) {
                stringAddress += address.getAddressLine(i)
            }
            Log.d("MAIN", stringAddress)
            addressTextView.text = "Address: " + stringAddress
        }
    }

    private fun createLocationRequest(): LocationRequest? {
        return LocationRequest.create().apply {
            interval = 100000
            fastestInterval = 5000
            priority = Priority.PRIORITY_HIGH_ACCURACY
        }
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode != LOCATION_PERMISSION_REQUEST_CODE) {
            super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
            )
            return
        }

        if (isPermissionGranted(
                permissions,
                grantResults,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) || isPermissionGranted(
                permissions,
                grantResults,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        ) {
            // Enable the my location layer if the permission has been granted.
            enablePermissions()
        } else {
            // Permission was denied. Display an error message
            // Display the missing permission error dialog when the fragments resume.
            permissionDenied = true
        }
    }

    /**
     * Displays a dialog with error message explaining that the location permission is missing.
     */
    private fun showMissingPermissionError() {
        newInstance(true).show(supportFragmentManager, "dialog")
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }

    override fun onComplete(task: Task<Void>) {
        //mPendingGeofenceTask = PendingGeofenceTask.NONE
        Log.d("GEO_MAIN", "IN ON COMPLETE")
        if (task.isSuccessful) {

            Log.d("MAIN", "TASK IS DONE")
            Log.d("MAIN", task.toString())

        } else {
            // Get the status code for the error and log it using a user-friendly message.
            val e = task.exception


            Log.w("GEO_MAIN", "ERROR IN ON COMPLETE")
            Log.w("GEO_MAIN", e)
            Log.d("GEO_MAIN", "Checking if ApiException ${(e is ApiException)}")
            if(e is ApiException){
                Log.d("GEO_MAIN", "Error status code is: ${e.statusCode}")
                when (e.statusCode) {
                    GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE -> Log.d("GEO_MAIN", "GEO UNAVAL")
                    GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES -> Log.d("GEO_MAIN", "TOO MANY REQ")
                    GeofenceStatusCodes.GEOFENCE_TOO_MANY_PENDING_INTENTS -> Log.d("GEO_MAIN", "TOO MANY PENDING INTENTS")
                    else -> Log.d("GEO_MAIN", "Unknown API Exception")
                }
            }
        }
    }

    private fun generateGeofences() {
        mGeofenceList!!.add(
            Geofence.Builder() // Set the request ID of the geofence. This is a string to identify this
                // geofence.
                .setRequestId("Test") // Set the circular region of this geofence.
                .setCircularRegion(
                    42.271870,
                    -71.805000,
                    GEOFENCE_RADIUS_IN_METERS
                ) // Set the expiration duration of the geofence. This geofence gets automatically
                // removed after this period of time.
                .setLoiteringDelay(10000)
                .setNotificationResponsiveness(1)
                .setExpirationDuration(GEOFENCE_EXPIRATION_IN_MILLISECONDS) // Set the transition types of interest. Alerts are only generated for these
                // transition. We track entry and exit transitions in this sample.
                .setTransitionTypes(
                    Geofence.GEOFENCE_TRANSITION_DWELL or
                    Geofence.GEOFENCE_TRANSITION_ENTER or
                    Geofence.GEOFENCE_TRANSITION_EXIT
                ) // Create the geofence.
                .build()
        )
        mGeofenceList!!.add(
            Geofence.Builder() // Set the request ID of the geofence. This is a string to identify this
                // geofence.
                .setRequestId("Gordon Library") // Set the circular region of this geofence.
                .setCircularRegion(
                    42.274261,
                    -71.806672,
                    GEOFENCE_RADIUS_IN_METERS
                ) // Set the expiration duration of the geofence. This geofence gets automatically
                // removed after this period of time.
                .setLoiteringDelay(10000)
                .setExpirationDuration(GEOFENCE_EXPIRATION_IN_MILLISECONDS) // Set the transition types of interest. Alerts are only generated for these
                // transition. We track entry and exit transitions in this sample.
                .setTransitionTypes(
                    Geofence.GEOFENCE_TRANSITION_DWELL
                ) // Create the geofence.
                .build()
        )
        mGeofenceList!!.add(
            Geofence.Builder() // Set the request ID of the geofence. This is a string to identify this
                // geofence.
                .setRequestId("Fuller Labs") // Set the circular region of this geofence.
                .setCircularRegion(
                    42.274871,
                    -71.806600,
                    GEOFENCE_RADIUS_IN_METERS
                ) // Set the expiration duration of the geofence. This geofence gets automatically
                // removed after this period of time.
                .setLoiteringDelay(10000)
                .setExpirationDuration(GEOFENCE_EXPIRATION_IN_MILLISECONDS) // Set the transition types of interest. Alerts are only generated for these
                // transition. We track entry and exit transitions in this sample.
                .setTransitionTypes(
                    Geofence.GEOFENCE_TRANSITION_DWELL
                ) // Create the geofence.
                .build()
        )

    }


    private fun smoothSensorData(data: ArrayList<Float>): ArrayList<Float> {
        if(data.size < 3) return data

        val newArray = ArrayList<Float>()
        for (i in data.indices) {
            var value: Float = when(i){
                0 -> (data[i] + data[i + 1]) / 2
                data.size - 1 -> (data[i] + data[i - 1]) / 2
                else -> (data[i] + data[i - 1] + data[i + 1]) / 3
            }
            newArray.add(value)
        }
        return newArray

    }

    private val gravity = FloatArray(3)
    private val linear_acceleration = FloatArray(3)

    override fun onSensorChanged(event: SensorEvent?) {
        //Log.d(TAG_MAIN, "SENSOR CHANGE")

        if(event !== null) {
            val type = event.sensor.type
            if (type == Sensor.TYPE_ACCELEROMETER) {


                gravity[0] = (ALPHA * gravity[0] + (1 - ALPHA) * event.values[0]).toFloat();
                gravity[1] = (ALPHA * gravity[1] + (1 - ALPHA) * event.values[1]).toFloat();
                gravity[2] = (ALPHA * gravity[2] + (1 - ALPHA) * event.values[2]).toFloat();

                linear_acceleration[0] = event.values[0] - gravity[0];
                linear_acceleration[1] = event.values[1] - gravity[1];
                linear_acceleration[2] = event.values[2] - gravity[2];
                val magnitude = linear_acceleration.max()
                if(stepData == null) return
                if(stepData!!.size < 10){
                    stepData!!.add(magnitude)
                }
                else {
                    stepData!!.removeAt(0)
                    stepData!!.add(magnitude)
                }
//                //Log.d(TAG_MAIN, magnitude.toString())
//                //Log.d(TAG_MAIN, stepData.toString())
                var smoothedArray = smoothSensorData(stepData!!)
                var newList = ArrayList<Float>()
                smoothedArray.forEach { newList.add(it) }
                smoothedArray = newList
                val maxThers = smoothedArray.max()
                val minThres = smoothedArray.min()
                val thres = (maxThers + minThres) / 2

                try{
                    val new = smoothedArray[smoothedArray.size - 1]
                    val old = smoothedArray[smoothedArray.size - 2]
                    val currentTime = System.currentTimeMillis()
                    val timeDifference = if(lastStepTimestamp == null) 200 else currentTime - lastStepTimestamp!!
                    if(timeDifference > 2000) {
                        //Log.d(TAG_MAIN, "Resetting last step")
                        lastStepTimestamp = null
                    }
                    //Log.d(TAG_MAIN, "The time diff is $timeDifference")
                    if(old - new < 0 && old - new > thres && 200 <= timeDifference && 2000 >= timeDifference) {
                        stepCounter++
                        Log.d(TAG_MAIN, "Step taken")
                        lastStepTimestamp = currentTime
                        stepsTV.text = getString(R.string.steps_counter, stepCounter.toString())
                    }
                }
                catch(e: Exception) {
                    Log.d(TAG_MAIN, e.toString())
                }

            }
        }



    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(TAG_MAIN, "Accuracy CHANGE")
    }
}