package com.oscaribarra.neoplanner.ui.pointing

import android.hardware.SensorManager
import kotlin.math.PI
import kotlin.math.abs

object OrientationMath {

    data class EulerDeg(
        val azimuthDegMagnetic: Double, // 0..360, magnetic north
        val pitchDeg: Double,           // [-180, 180]
        val rollDeg: Double             // [-180, 180]
    )

    fun normalize360(deg: Double): Double {
        var d = deg % 360.0
        if (d < 0) d += 360.0
        return d
    }

    /**
     * Smallest signed angular difference from 'fromDeg' to 'toDeg' in degrees.
     * Result in [-180, +180]. Positive means turn right (clockwise).
     */
    fun deltaAngleDeg(fromDeg: Double, toDeg: Double): Double {
        val a = normalize360(toDeg) - normalize360(fromDeg)
        var d = a % 360.0
        if (d > 180.0) d -= 360.0
        if (d < -180.0) d += 360.0
        return d
    }

    fun radToDeg(rad: Float): Double = rad * 180.0 / PI
    fun radToDeg(rad: Double): Double = rad * 180.0 / PI

    /**
     * Given a rotation matrix R (Android world->device), return azimuth/pitch/roll.
     * Uses SensorManager.getOrientation which returns radians.
     */
    fun rotationMatrixToEulerDeg(R: FloatArray): EulerDeg {
        val out = FloatArray(3)
        SensorManager.getOrientation(R, out)
        val az = normalize360(radToDeg(out[0]).toDouble())
        val pitch = radToDeg(out[1]).toDouble()
        val roll = radToDeg(out[2]).toDouble()
        return EulerDeg(
            azimuthDegMagnetic = az,
            pitchDeg = pitch,
            rollDeg = roll
        )
    }

    /**
     * Camera-pointing altitude approximation.
     *
     * For typical "hold phone upright, screen facing you, back camera facing sky" usage:
     * - Android pitch tends to be negative when you tilt the top of the phone upward.
     * - Using altitude ≈ -pitch gives a reasonable "aiming up/down" signal.
     *
     * This is an approximation; we can refine the mapping after you test on your device.
     */
    fun cameraAltitudeDegFromPitch(pitchDeg: Double): Double {
        val alt = -pitchDeg
        // Clamp to [ -90, +90 ] for sanity
        return alt.coerceIn(-90.0, 90.0)
    }

    fun cardinalFromAzimuth(azDegTrue: Double): String {
        val az = normalize360(azDegTrue)
        return when {
            az < 22.5 -> "N"
            az < 67.5 -> "NE"
            az < 112.5 -> "E"
            az < 157.5 -> "SE"
            az < 202.5 -> "S"
            az < 247.5 -> "SW"
            az < 292.5 -> "W"
            az < 337.5 -> "NW"
            else -> "N"
        }
    }

    /**
     * Angular "distance" heuristic for UI guidance.
     * Not a true spherical separation (we can add that later), but good enough for guidance:
     * sqrt( dAz^2 + dAlt^2 ) with dAz wrapped to [-180, 180].
     */
    fun aimErrorDeg(currAzTrue: Double, currAlt: Double, targetAzTrue: Double, targetAlt: Double): Double {
        val dAz = deltaAngleDeg(currAzTrue, targetAzTrue)
        val dAlt = targetAlt - currAlt
        return kotlin.math.sqrt(dAz * dAz + dAlt * dAlt)
    }

    fun aimHint(dAz: Double, dAlt: Double): String {
        val turn = when {
            abs(dAz) < 2.0 -> "Turn: aligned"
            dAz > 0 -> "Turn right ~${"%.0f".format(abs(dAz))}°"
            else -> "Turn left ~${"%.0f".format(abs(dAz))}°"
        }
        val tilt = when {
            abs(dAlt) < 2.0 -> "Tilt: aligned"
            dAlt > 0 -> "Tilt up ~${"%.0f".format(abs(dAlt))}°"
            else -> "Tilt down ~${"%.0f".format(abs(dAlt))}°"
        }
        return "$turn • $tilt"
    }
}
