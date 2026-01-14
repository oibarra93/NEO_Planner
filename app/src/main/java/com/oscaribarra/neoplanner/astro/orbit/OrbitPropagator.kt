package com.oscaribarra.neoplanner.astro.orbit

import android.os.Build
import androidx.annotation.RequiresApi
import com.oscaribarra.neoplanner.astro.orbit.OrbitalTransforms.Vec3Au
import com.oscaribarra.neoplanner.astro.spk.Vec3Km
import com.oscaribarra.neoplanner.astro.time.Julian
import com.oscaribarra.neoplanner.astro.time.TimeScales
import java.time.Instant
import kotlin.math.pow

object OrbitPropagator {

    /**
     * Convert Instant UTC -> JD(TDB) approximate using Module 4.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun jdTdbFromUtcApprox(instantUtc: Instant): Double {
        val jdUtc = Julian.jdUtc(instantUtc)
        return TimeScales.jdTdbFromJdUtcApprox(jdUtc)
    }

    /**
     * Mean motion n (rad/day).
     * Prefer meanMotionDegPerDay if provided; otherwise compute from a using k (Gaussian).
     */
    fun meanMotionRadPerDay(el: OrbitElements): Double {
        val mm = el.meanMotionDegPerDay
        return if (mm != null && mm.isFinite() && mm > 0.0) {
            AngleUtil.degToRad(mm)
        } else {
            // n = k / a^(3/2)  (rad/day) where k is Gaussian constant
            val a = el.aAu
            require(a > 0.0) { "Invalid semi-major axis a=$a" }
            AstroConstants.GAUSSIAN_K / a.pow(1.5)
        }
    }

    /**
     * Heliocentric position in Equatorial J2000, km.
     * Returns null if orbit is non-elliptic (e >= 1).
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun heliocentricEquatorialKm(el: OrbitElements, instantUtc: Instant): Vec3Km? {
        val jdTdb = jdTdbFromUtcApprox(instantUtc)
        val dtDays = jdTdb - el.epochJd

        if (el.e >= 1.0) return null // defensive; handle later with hyperbolic solver

        val n = meanMotionRadPerDay(el)
        val M0 = AngleUtil.degToRad(el.meanAnomalyDeg)
        val M = M0 + n * dtDays

        val E = KeplerSolver.solveEllipticE(M, el.e) ?: return null

        val rPf = OrbitalTransforms.perifocalPositionAu(el.aAu, el.e, E)

        val raan = AngleUtil.degToRad(el.raanDeg)
        val inc = AngleUtil.degToRad(el.iDeg)
        val argPeri = AngleUtil.degToRad(el.argPeriDeg)

        val rEcl: Vec3Au = OrbitalTransforms.perifocalToEclipticJ2000(rPf, raan, inc, argPeri)
        val rEq: Vec3Au = OrbitalTransforms.eclipticToEquatorialJ2000(rEcl)

        return OrbitalTransforms.auToKm(rEq)
    }
}
