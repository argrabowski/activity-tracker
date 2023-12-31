package com.example.cs_528_project_3

import android.provider.ContactsContract

class Constants {
    companion object {
        // General
        const val TAG_MAIN = "MainActivity"
        const val TAG_RECOGNITION = "ActivityRecognitionIntentService"

        // Geofencing
        private const val GEOFENCE_EXPIRATION_IN_HOURS: Long = 12
        const val GEOFENCE_EXPIRATION_IN_MILLISECONDS: Long = GEOFENCE_EXPIRATION_IN_HOURS * 60 * 60 * 1000
        const val GEOFENCE_RADIUS_IN_METERS = 10f
        const val BACKGROUND_LOCATION_PERMISSION_CODE = 2

        // Activity recognition
        const val PERMISSIONS_REQUEST_ACTIVITY_RECOGNITION = 100
        const val ACTIVITY_DETECTION_INTERVAL_IN_MILLISECONDS = 1000L
        const val ACTION_ACTIVITY_RECOGNITION = "com.example.cs_528_project_3.ACTION_ACTIVITY_RECOGNITION"
        const val EXTRA_ACTIVITY_STRING = "com.example.cs_528_project_3.EXTRA_ACTIVITY_STRING"
        const val ACTIVITY_RECOGNITION_PERMISSION_CODE = 3

        const val MAX_TESTS_NUM = 200 * 60

        const val ALPHA = 0.8
    }

}