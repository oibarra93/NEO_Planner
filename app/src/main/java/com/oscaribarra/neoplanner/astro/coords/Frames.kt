package com.oscaribarra.neoplanner.astro.coords

import com.oscaribarra.neoplanner.astro.spk.Vec3Km
import com.oscaribarra.neoplanner.astro.time.Julian
import com.oscaribarra.neoplanner.astro.time.SiderealTime
import java.time.Instant
import kotlin.math.*

object Frames {

    /**
     * Rotate inertial (ECI) vector into ECEF using GMST angle.
     *
     * Using a simple Z-rotation:
     *   r_ecef = R3(gmst) * r_eci
     *
     * where:
     *   x_ecef =  cosθ*x_eci + sinθ*y_eci
     *   y_ecef = -sinθ*x_eci + cosθ*y_eci
     *   z_ecef =  z_eci
     */
    fun eciToEcef(eciKm: Vec3Km, instantUtc: Instant): Vec3Km {
        val jdUtc = Julian.jdUtc(instantUtc)
        val theta = SiderealTime.gmstRadFromJdUtc(jdUtc)

        val c = cos(theta)
        val s = sin(theta)

        val x = c * eciKm.x + s * eciKm.y
        val y = -s * eciKm.x + c * eciKm.y
        val z = eciKm.z

        return Vec3Km(x, y, z)
    }

    /**
     * Convert an ECEF vector (km) to local ENU coordinates (km) at observer lat/lon.
     * Input is the vector FROM observer TO target in ECEF.
     */
    fun ecefToEnu(
        rhoEcefKm: Vec3Km,
        latDeg: Double,
        lonDeg: Double
    ): Triple<Double, Double, Double> {
        val lat = Angles.degToRad(latDeg)
        val lon = Angles.degToRad(lonDeg)

        val sinLat = sin(lat)
        val cosLat = cos(lat)
        val sinLon = sin(lon)
        val cosLon = cos(lon)

        // ENU basis vectors (ECEF)
        val eastX = -sinLon
        val eastY = cosLon
        val eastZ = 0.0

        val northX = -sinLat * cosLon
        val northY = -sinLat * sinLon
        val northZ = cosLat

        val upX = cosLat * cosLon
        val upY = cosLat * sinLon
        val upZ = sinLat

        val e = rhoEcefKm.x * eastX + rhoEcefKm.y * eastY + rhoEcefKm.z * eastZ
        val n = rhoEcefKm.x * northX + rhoEcefKm.y * northY + rhoEcefKm.z * northZ
        val u = rhoEcefKm.x * upX + rhoEcefKm.y * upY + rhoEcefKm.z * upZ

        return Triple(e, n, u)
    }

    /**
     * Compute topocentric Alt/Az from an inertial geocentric vector (km):
     *   r_eci = (target - Earth) in ECI (J2000-ish)
     *
     * Steps:
     *  - Rotate r_eci to ECEF
     *  - Compute observer ECEF
     *  - rho = r_target_ecef - r_obs_ecef  (here, target is Earth-centered so r_target_ecef = r_eci_ecef)
     *  - ENU projection
     *  - Alt/Az
     */
    fun altAzFromGeocentricEci(
        geocentricEciKm: Vec3Km,
        instantUtc: Instant,
        obsLatDeg: Double,
        obsLonDeg: Double,
        obsHeightMeters: Double
    ): AltAz {
        val rEcef = eciToEcef(geocentricEciKm, instantUtc)
        val obsEcef = Wgs84.ecefKm(obsLatDeg, obsLonDeg, obsHeightMeters)

        val rho = Vec3Km(
            rEcef.x - obsEcef.x,
            rEcef.y - obsEcef.y,
            rEcef.z - obsEcef.z
        )

        val (e, n, u) = ecefToEnu(rho, obsLatDeg, obsLonDeg)

        val horiz = sqrt(e * e + n * n)
        val altRad = atan2(u, horiz)
        val azRad = Angles.normalizeRad0To2Pi(atan2(e, n)) // 0=N, 90=E

        return AltAz(
            altitudeDeg = Angles.radToDeg(altRad),
            azimuthDeg = Angles.radToDeg(azRad)
        )
    }

    /**
     * RA/Dec from inertial vector (ECI), degrees.
     */
    fun raDecFromEci(eciKm: Vec3Km): RaDec {
        val r = sqrt(eciKm.x * eciKm.x + eciKm.y * eciKm.y + eciKm.z * eciKm.z)
        if (r == 0.0) return RaDec(raDeg = 0.0, decDeg = 0.0)

        val ra = Angles.normalizeRad0To2Pi(atan2(eciKm.y, eciKm.x))
        val dec = asin(eciKm.z / r)

        return RaDec(
            raDeg = Angles.radToDeg(ra),
            decDeg = Angles.radToDeg(dec)
        )
    }
}
