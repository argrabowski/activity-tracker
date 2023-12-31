package com.example.cs_528_project_3

import android.util.Log
import androidx.lifecycle.MutableLiveData

object ApplicationState {
    private val geoCounter = MutableLiveData<MutableList<Int>>( mutableListOf<Int>(0, 0))
    private val activity: MutableLiveData<String> = MutableLiveData()


    fun incrementGeoCounter(index: Int) {
        var list = geoCounter.value
        list?.set(index, list[index] + 1)
        geoCounter.value = list
    }

    fun getGeoCounter(): MutableLiveData<MutableList<Int>> {
        return geoCounter
    }

    fun getActivity(): MutableLiveData<String> {
        return activity
    }

    fun setActivity(new:String) {
        activity.value = new
    }

}