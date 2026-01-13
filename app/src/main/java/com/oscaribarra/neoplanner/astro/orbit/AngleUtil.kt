package com.oscaribarra.neoplanner.astro.orbit

import kotlin.math.PI

object AngleUtil {
    fun degToRad(d: Double): Double = d * PI / 180.0
    fun radToDeg(r: Double): Double = r * 180.0 / PI

    fun normalizeRad0To2Pi(x: Double): Double {
        var v = x % AstroConstants.TWO_PI
        if (v < 0) v += AstroConstants.TWO_PI
        return v
    }
}