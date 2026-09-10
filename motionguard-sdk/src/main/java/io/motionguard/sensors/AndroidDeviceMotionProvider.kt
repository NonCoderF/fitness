package io.motionguard.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import io.motionguard.core.DeviceMotionProvider
import io.motionguard.core.DeviceMotionSample
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class AndroidDeviceMotionProvider(context: Context) : DeviceMotionProvider {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    override fun observe(): Flow<DeviceMotionSample> = callbackFlow {
        var acceleration = 0f
        var rotation = 0f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val magnitude = magnitude(event.values)
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> acceleration = magnitude
                    Sensor.TYPE_GYROSCOPE -> rotation = magnitude
                }
                trySend(
                    DeviceMotionSample(
                        timestampMs = System.currentTimeMillis(),
                        accelerationMagnitude = acceleration,
                        rotationMagnitude = rotation,
                    ),
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
        }
        awaitClose { sensorManager.unregisterListener(listener) }
    }

    private fun magnitude(values: FloatArray): Float {
        var sum = 0f
        for (value in values) sum += value * value
        return kotlin.math.sqrt(sum)
    }
}
