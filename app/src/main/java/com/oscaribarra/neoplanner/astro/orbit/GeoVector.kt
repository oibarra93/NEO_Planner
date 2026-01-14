package com.oscaribarra.neoplanner.astro.orbit

import android.os.Build
import androidx.annotation.RequiresApi
import com.oscaribarra.neoplanner.astro.spk.De442sEphemeris
import com.oscaribarra.neoplanner.astro.spk.Vec3Km
import com.oscaribarra.neoplanner.astro.time.Julian
import com.oscaribarra.neoplanner.astro.time.TimeScales
import java.time.Instant

object GeoVector {

    /**
     * UTC -> ET seconds past J2000 (approx TDB), consistent with Module 7 SunAltitude.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun etSecondsFromUtc(instantUtc: Instant): Double {
        val jdUtc = Julian.jdUtc(instantUtc)
        val jdTdb = TimeScales.jdTdbFromJdUtcApprox(jdUtc)
        return (jdTdb - Julian.JD_J2000_TT) * 86400.0
    }

    /**
     * Compute NEO geocentric inertial vector (km), compatible with Module 7:
     *
     * 1) NEO heliocentric (Sun-centered) from elements -> r_neo_sun
     * 2) Earth heliocentric from ephemeris:
     *      r_earth_sun = r_earth_ssb - r_sun_ssb
     * 3) Geocentric:
     *      r_neo_earth = r_neo_sun - r_earth_sun
     *
     * Returns null if propagation fails (e >= 1 etc).
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun neoGeocentricEciKm(
        eph: De442sEphemeris,
        el: OrbitElements,
        instantUtc: Instant
    ): Vec3Km? {
        val neoSun = OrbitPropagator.heliocentricEquatorialKm(el, instantUtc) ?: return null

        val et = etSecondsFromUtc(instantUtc)
        val earthSsb = eph.earthWrtSsb(et)
        val sunSsb = eph.position(10, 0, et)

        val earthSun = Vec3Km(
            earthSsb.x - sunSsb.x,
            earthSsb.y - sunSsb.y,
            earthSsb.z - sunSsb.z
        )

        return Vec3Km(
            neoSun.x - earthSun.x,
            neoSun.y - earthSun.y,
            neoSun.z - earthSun.z
        )
    }
}
