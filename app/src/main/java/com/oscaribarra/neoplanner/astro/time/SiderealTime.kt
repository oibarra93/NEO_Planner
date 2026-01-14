package com.oscaribarra.neoplanner.astro.time

import kotlin.math.PI

/**
 * Sidereal time utilities.
 *
 * We use GMST to rotate from inertial (J2000-ish ECI) to Earth-fixed (ECEF).
 * For our use, GMST based on JD(UTC) is a reasonable starting point.
 */
object SiderealTime {

    private const val TWO_PI = 2.0 * PI

    /**
     * Compute Greenwich Mean Sidereal Time (GMST) in radians.
     *
     * Formula (IAU 1982-ish) commonly used:
     * GMST (deg) = 280.46061837
     *            + 360.98564736629*(JD - 2451545.0)
     *            + 0.000387933*T^2
     *            - (T^3)/38710000
     * where T = (JD - 2451545.0)/36525
     *
     * Input: JD(UTC) (approx).
     */
    fun gmstRadFromJdUtc(jdUtc: Double): Double {
        val d = jdUtc - 2451545.0
        val t = d / 36525.0

        var gmstDeg =
            280.46061837 +
                    360.98564736629 * d +
                    0.000387933 * t * t -
                    (t * t * t) / 38710000.0

        gmstDeg = normalizeDeg(gmstDeg)
        return Math.toRadians(gmstDeg)
    }

    /**
     * Local Sidereal Time (radians) = GMST + longitude (east-positive).
     */
    fun lstRadFromJdUtc(jdUtc: Double, lonDegEast: Double): Double {
        val gmst = gmstRadFromJdUtc(jdUtc)
        val lonRad = Math.toRadians(lonDegEast)
        return normalizeRad(gmst + lonRad)
    }

    private fun normalizeDeg(deg: Double): Double {
        var x = deg % 360.0
        if (x < 0) x += 360.0
        return x
    }

    fun normalizeRad(rad: Double): Double {
        var x = rad % TWO_PI
        if (x < 0) x += TWO_PI
        return x
    }
}
