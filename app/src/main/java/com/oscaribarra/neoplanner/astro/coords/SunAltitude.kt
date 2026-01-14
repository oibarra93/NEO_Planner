package com.oscaribarra.neoplanner.astro.coords

import android.os.Build
import androidx.annotation.RequiresApi
import com.oscaribarra.neoplanner.astro.spk.De442sEphemeris
import com.oscaribarra.neoplanner.astro.spk.Vec3Km
import com.oscaribarra.neoplanner.astro.time.Julian
import com.oscaribarra.neoplanner.astro.time.TimeScales
import java.time.Instant

object SunAltitude {

    /**
     * Convert UTC instant to ET seconds past J2000 (approx TDB seconds).
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun etSecondsFromUtc(instantUtc: Instant): Double {
        val jdUtc = Julian.jdUtc(instantUtc)
        val jdTdb = TimeScales.jdTdbFromJdUtcApprox(jdUtc)
        return (jdTdb - Julian.JD_J2000_TT) * 86400.0
    }

    /**
     * Returns Sun topocentric Alt/Az at observer (degrees).
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun sunAltAz(
        eph: De442sEphemeris,
        instantUtc: Instant,
        obsLatDeg: Double,
        obsLonDeg: Double,
        obsHeightMeters: Double
    ): AltAz {
        val et = etSecondsFromUtc(instantUtc)

        val sunWrtSsb = eph.position(10, 0, et)
        val earthWrtSsb = eph.earthWrtSsb(et)

        // Vector from Earth to Sun (geocentric), inertial
        val sunGeo = Vec3Km(
            sunWrtSsb.x - earthWrtSsb.x,
            sunWrtSsb.y - earthWrtSsb.y,
            sunWrtSsb.z - earthWrtSsb.z
        )

        return Frames.altAzFromGeocentricEci(
            geocentricEciKm = sunGeo,
            instantUtc = instantUtc,
            obsLatDeg = obsLatDeg,
            obsLonDeg = obsLonDeg,
            obsHeightMeters = obsHeightMeters
        )
    }

    /**
     * Convenience: Sun altitude only (degrees).
     */
    fun sunAltitudeDeg(
        eph: De442sEphemeris,
        instantUtc: Instant,
        obsLatDeg: Double,
        obsLonDeg: Double,
        obsHeightMeters: Double
    ): Double = sunAltAz(eph, instantUtc, obsLatDeg, obsLonDeg, obsHeightMeters).altitudeDeg
}
