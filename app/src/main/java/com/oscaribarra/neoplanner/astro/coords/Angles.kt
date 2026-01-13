package com.oscaribarra.neoplanner.astro.coords

import kotlin.math.PI

object Angles {
    private const val TWO_PI = 2.0 * PI

    fun degToRad(deg: Double): Double = deg * PI / 180.0
    fun radToDeg(rad: Double): Double = rad * 180.0 / PI

    fun normalizeRad0To2Pi(rad: Double): Double {
        var x = rad % TWO_PI
        if (x < 0) x += TWO_PI
        return x
    }

    fun normalizeDeg0To360(deg: Double): Double {
        var x = deg % 360.0
        if (x < 0) x += 360.0
        return x
    }
}