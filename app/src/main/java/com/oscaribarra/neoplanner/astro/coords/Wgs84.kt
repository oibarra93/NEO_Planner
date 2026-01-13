package com.oscaribarra.neoplanner.astro.coords

import com.oscaribarra.neoplanner.astro.spk.Vec3Km
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Wgs84 {
    // WGS-84 constants
    private const val A_M = 6378137.0
    private const val F = 1.0 / 298.257223563
    private const val E2 = F * (2.0 - F)

    /**
     * Convert geodetic lat/lon (deg) + height (meters) -> ECEF (km).
     * lonDeg is east-positive (Android Location uses this convention; west is negative).
     */
    fun ecefKm(latDeg: Double, lonDeg: Double, heightMeters: Double): Vec3Km {
        val lat = Angles.degToRad(latDeg)
        val lon = Angles.degToRad(lonDeg)

        val sinLat = sin(lat)
        val cosLat = cos(lat)
        val sinLon = sin(lon)
        val cosLon = cos(lon)

        val n = A_M / sqrt(1.0 - E2 * sinLat * sinLat)

        val x = (n + heightMeters) * cosLat * cosLon
        val y = (n + heightMeters) * cosLat * sinLon
        val z = (n * (1.0 - E2) + heightMeters) * sinLat

        // meters -> km
        return Vec3Km(x / 1000.0, y / 1000.0, z / 1000.0)
    }
}
