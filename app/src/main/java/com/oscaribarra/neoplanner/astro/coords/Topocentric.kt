package com.oscaribarra.neoplanner.astro.coords

import android.os.Build
import androidx.annotation.RequiresApi
import com.oscaribarra.neoplanner.astro.spk.Vec3Km
import java.time.Instant

object Topocentric {

    /**
     * Compute Alt/Az + RA/Dec + cardinal + hint from a geocentric ECI vector (km).
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun solve(
        geocentricEciKm: Vec3Km,
        instantUtc: Instant,
        obsLatDeg: Double,
        obsLonDeg: Double,
        obsHeightMeters: Double
    ): TopocentricResult {
        val altAz = Frames.altAzFromGeocentricEci(
            geocentricEciKm = geocentricEciKm,
            instantUtc = instantUtc,
            obsLatDeg = obsLatDeg,
            obsLonDeg = obsLonDeg,
            obsHeightMeters = obsHeightMeters
        )

        val raDec = Frames.raDecFromEci(geocentricEciKm)
        val card = Pointing.cardinalFromAzDeg(altAz.azimuthDeg)
        val hint = Pointing.hintFromAltAz(altAz.altitudeDeg, altAz.azimuthDeg)

        return TopocentricResult(
            altAz = altAz,
            raDec = raDec,
            cardinal = card,
            hint = hint
        )
    }
}
