package com.example.cs_528_project_3

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import com.example.cs_528_project_3.Constants.Companion.TAG_RECOGNITION
import com.example.cs_528_project_3.Constants.Companion.ACTION_ACTIVITY_RECOGNITION
import com.example.cs_528_project_3.Constants.Companion.EXTRA_ACTIVITY_STRING

class ActivityRecognitionBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        // Extract activity recognition result from intent
        Log.d(TAG_RECOGNITION, "In receiver")
        if (intent != null && ActivityRecognitionResult.hasResult(intent)) {
            val result = ActivityRecognitionResult.extractResult(intent)
            val mostProbableActivity = result?.mostProbableActivity
            val activityString = getActivityString(mostProbableActivity?.type)
            Log.d(TAG_RECOGNITION, "Detected activity: $activityString")

            ApplicationState.setActivity(activityString)
        }
    }

    private fun getActivityString(type: Int?): String {
        // Returns corresponding string representation of activity
        return when (type) {
            DetectedActivity.IN_VEHICLE -> "In vehicle"
            DetectedActivity.RUNNING -> "Running"
            DetectedActivity.STILL -> "Still"
            DetectedActivity.WALKING -> "Walking"
            else -> "Unknown"
        }
    }
}
