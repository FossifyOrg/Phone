package org.fossify.phone.helpers

import android.Manifest
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.telecom.TelecomManager
import androidx.annotation.RequiresPermission

class FlipToMuteHelper(private val context: Context) {
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var isListening = false
    private var hasBeenFaceUp = false // Mémorise si le téléphone a d'abord été à l'endroit

    private val sensorListener = object : SensorEventListener {
        @RequiresPermission(Manifest.permission.MODIFY_PHONE_STATE)
        override fun onSensorChanged(event: SensorEvent?) {
            if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
                val z = event.values[2]

                // Si le téléphone est levé ou face vers le haut, on le mémorise
                if (z > 3.0f) {
                    hasBeenFaceUp = true
                }

                // S'il est retourné face vers le bas ET qu'il a été vu à l'endroit avant
                if (z < -8.0f && hasBeenFaceUp) {
                    muteRinger()
                    stopListening()
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    fun startListening() {
        if (isListening) return
        hasBeenFaceUp = false // On réinitialise l'état pour chaque nouvel appel
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometer?.let {
            sensorManager?.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL)
            isListening = true
        }
    }

    fun stopListening() {
        if (!isListening) return
        sensorManager?.unregisterListener(sensorListener)
        isListening = false
    }

    @RequiresPermission(Manifest.permission.MODIFY_PHONE_STATE)
    private fun muteRinger() {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        telecomManager.silenceRinger()
    }
}
