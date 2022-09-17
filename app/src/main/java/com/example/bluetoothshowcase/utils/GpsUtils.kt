package com.example.bluetoothshowcase.utils

import android.app.Activity
import android.content.Context
import android.content.IntentSender.SendIntentException
import android.location.LocationManager
import android.util.Log
import android.widget.Toast
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*

private const val TAG = "GpsUtils"
// src https://qna.vbagetech.com/question/407/How-to-Enable-GPS-location-programmatically-in-android

class GpsUtils(private val context: Activity){

    private val mSettingsClient: SettingsClient = LocationServices.getSettingsClient(context)
    private val mLocationSettingsRequest: LocationSettingsRequest
    private val locationManager: LocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    init {
        val locationRequest: LocationRequest = LocationRequest
            .create()
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
        mLocationSettingsRequest = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(true)
            .build()
    }

    fun turnGPSOn() {
        if (!isGpsOn()) {
            mSettingsClient.checkLocationSettings(mLocationSettingsRequest)
                .addOnCompleteListener { task ->
                    try {
                        task.getResult(ApiException::class.java)
                    } catch (e: ApiException) {
                        when (e.statusCode) {
                            // resolution required will open the dialog to turn on location
                            LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> try {
                                with(e as ResolvableApiException) {
                                    startResolutionForResult(
                                        context,
                                        LocationRequest.PRIORITY_HIGH_ACCURACY
                                    )
                                }
                            } catch (e: SendIntentException) {
                                Log.i(TAG, "PendingIntent unable to execute request.", e)
                            }
                            // location settings are not satisfied. However, we have no way to fix the settings
                            LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> {
                                val errorMessage =
                                    "Location settings are inadequate, and cannot be " +
                                            "fixed here. Fix in Settings."
                                Log.e(TAG, errorMessage)
                                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
        }
    }

    private fun isGpsOn(): Boolean = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
}
