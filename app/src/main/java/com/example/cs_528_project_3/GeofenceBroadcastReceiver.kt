package com.example.cs_528_project_3

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent


private var TAG = "GEO_RECIEVER"

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    @Override
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d(TAG, "in reciever")
        Log.e(TAG, "The intent is $intent")
        if(intent == null) return
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        Log.e(TAG, "The geo event is $geofencingEvent")
        if (geofencingEvent != null) {
            if (geofencingEvent.hasError()) {
                val errorMessage = GeofenceStatusCodes
                    .getStatusCodeString(geofencingEvent.errorCode)
                Log.e(TAG, errorMessage)
                return
            }
        }

        // Get the transition type.
        val geofenceTransition = geofencingEvent?.geofenceTransition
        val message = "You have $geofenceTransition the geofence"
        val toast = Toast.makeText(context, message, Toast.LENGTH_SHORT)
        toast.show()
        // Test that the reported transition was of interest.
        Log.e(TAG, "The geo transition is $geofenceTransition")

        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_DWELL) {

            // Get the geofences that were triggered. A single event can trigger
            // multiple geofences.
            val triggeringGeofence = geofencingEvent?.triggeringGeofences

            val idsList = ArrayList<Any>()
            for (geofence in triggeringGeofence!!) {
                idsList.add(geofence.requestId)
            }
            val triggeringGeofenceIdsString: String =
                TextUtils.join(", ", idsList)


            val index = when(triggeringGeofenceIdsString){
                "Gordon Library" -> 0
                "Fuller Labs" -> 1
                else -> 0
            }
            ApplicationState.incrementGeoCounter(index)
            sendMessage(context, triggeringGeofenceIdsString)

        } else {
            // Log the error.
            Log.e(TAG, "ERROR IN RECEIVER")
        }
    }

    private fun sendMessage(context: Context?, location: String){
        val message = "You have been inside the $location Geofence for 10 seconds, incrementing counter"
        val toast = Toast.makeText(context, message, Toast.LENGTH_SHORT)
        toast.show()
    }
}
