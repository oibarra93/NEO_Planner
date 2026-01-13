package com.oscaribarra.neoplanner.astro.orbit

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object KeplerSolver {

    /**
     * Solve Kepler's equation for elliptical orbits:
     *   M = E - e*sin(E)
     *
     * Returns eccentric anomaly E (radians) or null if e >= 1.
     */
    fun solveEllipticE(Mrad: Double, e: Double): Double? {
        if (e >= 1.0) return null

        // Normalize M into [0, 2pi)
        val M = AngleUtil.normalizeRad0To2Pi(Mrad)

        // Initial guess
        var E = if (e < 0.8) M else Math.PI

        // Newton-Raphson
        for (iter in 0 until 30) {
            val f = E - e * sin(E) - M
            val fp = 1.0 - e * cos(E)
            val dE = -f / fp
            E += dE
            if (abs(dE) < 1e-12) break
        }
        return E
    }
}