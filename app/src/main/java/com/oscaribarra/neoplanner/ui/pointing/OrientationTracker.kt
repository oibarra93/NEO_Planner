package com.oscaribarra.neoplanner.ui.pointing

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import java.time.Instant
import kotlin.math.asin
import kotlin.math.atan2
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Tracks device orientation using TYPE_ROTATION_VECTOR (sensor-fused).
 *
 * Outputs:
 * - azimuthDegTrue: camera boresight azimuth in degrees referenced to TRUE north (0=N, 90=E)
 * - altitudeDegCamera: camera boresight altitude above horizon (degrees)
 *
 * Also provides:
 * - azimuthDegMagnetic: magnetic azimuth estimate (debug)
 * - declinationDeg: declination at observer/time (debug)
 * - pitch/roll: Euler angles derived from orientation matrix (debug only; not used for alt/az)
 */
class OrientationTracker(context: Context) {
    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVector: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    // Used to remap sensor axes based on screen rotation (portrait/landscape).
    private val windowManager: WindowManager? =
        appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager

    data class OrientationSample(
        val instantUtc: Instant,
        val azimuthDegTrue: Double,
        val azimuthDegMagnetic: Double,
        val altitudeDegCamera: Double,
        val pitchDeg: Double,
        val rollDeg: Double,
        val declinationDeg: Float,
        val accuracy: Int
    )

    fun samples(
        obsLatDeg: Double,
        obsLonDeg: Double,
        obsHeightMeters: Double,
        rateUs: Int = SensorManager.SENSOR_DELAY_GAME
    ): Flow<OrientationSample> = callbackFlow {
        val rv = rotationVector
        if (rv == null) {
            close(IllegalStateException("TYPE_ROTATION_VECTOR not available on this device."))
            return@callbackFlow
        }

        // Rotation matrix from rotation vector
        val R = FloatArray(9)

        // Remapped rotation matrix for display rotation
        val Rr = FloatArray(9)

        // Camera forward vector in *device* coordinates (back camera looks toward -Z).
        val camDevice = floatArrayOf(0f, 0f, -1f)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val now = Instant.now()

                // Build rotation matrix from the fused rotation vector.
                SensorManager.getRotationMatrixFromVector(R, event.values)

                // Remap based on current display rotation so "up" behaves consistently.
                remapForDisplayRotation(R, Rr)

                // DEBUG Euler angles (magnetic-like azimuth, pitch, roll). Not used for camera alt/az.
                val euler = OrientationMath.rotationMatrixToEulerDeg(Rr)
                val magAz = euler.azimuthDegMagnetic
                val pitch = euler.pitchDeg
                val roll = euler.rollDeg

                // Compute declination for debug (and for users who compare to compass apps).
                val gm = GeomagneticField(
                    obsLatDeg.toFloat(),
                    obsLonDeg.toFloat(),
                    obsHeightMeters.toFloat(),
                    now.toEpochMilli()
                )
                val decl = gm.declination // degrees (east positive)

                // Compute camera boresight vector in world coordinates: camWorld = Rr * camDevice
                val camWorldX = (Rr[0] * camDevice[0] + Rr[1] * camDevice[1] + Rr[2] * camDevice[2]).toDouble()
                val camWorldY = (Rr[3] * camDevice[0] + Rr[4] * camDevice[1] + Rr[5] * camDevice[2]).toDouble()
                val camWorldZ = (Rr[6] * camDevice[0] + Rr[7] * camDevice[1] + Rr[8] * camDevice[2]).toDouble()

                /*
                 * Interpret camWorld as ENU-like components after remap:
                 * east = x, north = y, up = z
                 *
                 * Then:
                 * az = atan2(east, north) in radians  -> degrees [0..360)
                 * alt = asin(up) in radians           -> degrees [-90..+90]
                 */
                val east = camWorldX
                val north = camWorldY
                val up = camWorldZ.coerceIn(-1.0, 1.0)

                val azRad = atan2(east, north)
                val altRad = asin(up)

                val trueAz = normalize360(Math.toDegrees(azRad))
                val altDeg = Math.toDegrees(altRad)

                // Optional: keep a declination-corrected "true az" derived from magnetic heading as debug comparison.
                // Not used as primary aiming azimuth anymore.
                val trueAzFromMag = normalize360(magAz + decl.toDouble())

                // Choose what you want to display as azimuthDegMagnetic:
                // - magAz is "azimuth from matrix" and often behaves like magnetic heading for debug.
                // - trueAzFromMag can show the corrected estimate.
                //
                // Here we keep magAz as "magnetic-ish" debug number.
                trySend(
                    OrientationSample(
                        instantUtc = now,
                        azimuthDegTrue = trueAz,              // <-- PRIMARY aiming azimuth (true north)
                        azimuthDegMagnetic = magAz,           // debug
                        altitudeDegCamera = altDeg,           // <-- PRIMARY aiming altitude
                        pitchDeg = pitch,                     // debug
                        rollDeg = roll,                       // debug
                        declinationDeg = decl,                // debug
                        accuracy = event.accuracy
                    )
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // Accuracy available via event.accuracy as well.
            }
        }

        sensorManager.registerListener(listener, rv, rateUs)

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }

    /**
     * Remap the coordinate system based on the current display rotation.
     *
     * This makes the orientation stable across portrait/landscape changes.
     */
    private fun remapForDisplayRotation(inR: FloatArray, outR: FloatArray) {
        val rotation = try {
            // display.rotation is deprecated in some API levels; this still works widely.
            windowManager?.defaultDisplay?.rotation ?: Surface.ROTATION_0
        } catch (_: Throwable) {
            Surface.ROTATION_0
        }

        when (rotation) {
            Surface.ROTATION_0 -> {
                // Natural portrait
                SensorManager.remapCoordinateSystem(
                    inR,
                    SensorManager.AXIS_X,
                    SensorManager.AXIS_Y,
                    outR
                )
            }

            Surface.ROTATION_90 -> {
                // Landscape left (device rotated 90° clockwise)
                SensorManager.remapCoordinateSystem(
                    inR,
                    SensorManager.AXIS_Y,
                    SensorManager.AXIS_MINUS_X,
                    outR
                )
            }

            Surface.ROTATION_180 -> {
                // Upside-down portrait
                SensorManager.remapCoordinateSystem(
                    inR,
                    SensorManager.AXIS_MINUS_X,
                    SensorManager.AXIS_MINUS_Y,
                    outR
                )
            }

            Surface.ROTATION_270 -> {
                // Landscape right (device rotated 270° clockwise)
                SensorManager.remapCoordinateSystem(
                    inR,
                    SensorManager.AXIS_MINUS_Y,
                    SensorManager.AXIS_X,
                    outR
                )
            }

            else -> {
                SensorManager.remapCoordinateSystem(
                    inR,
                    SensorManager.AXIS_X,
                    SensorManager.AXIS_Y,
                    outR
                )
            }
        }
    }

    private fun normalize360(deg: Double): Double {
        var d = deg % 360.0
        if (d < 0) d += 360.0
        return d
    }
}
