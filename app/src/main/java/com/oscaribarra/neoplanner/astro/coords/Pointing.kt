package com.oscaribarra.neoplanner.astro.coords

import kotlin.math.roundToInt

object Pointing {

    private val cardinals8 = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

    fun cardinalFromAzDeg(azDeg: Double): String {
        val az = Angles.normalizeDeg0To360(azDeg)
        val idx = ((az / 45.0).roundToInt()) % 8
        return cardinals8[idx]
    }

    fun hintFromAltAz(altDeg: Double, azDeg: Double): String {
        val card = cardinalFromAzDeg(azDeg)
        val altRounded = altDeg.coerceIn(-90.0, 90.0)

        return if (altRounded <= 0.0) {
            "Object is below the horizon (face $card, altitude ${"%.1f".format(altRounded)}°)."
        } else {
            "Face $card, tilt up to ~${"%.1f".format(altRounded)}°."
        }
    }
}
