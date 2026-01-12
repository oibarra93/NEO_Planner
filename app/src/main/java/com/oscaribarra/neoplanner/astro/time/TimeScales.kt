package com.oscaribarra.neoplanner.astro.time

import kotlin.math.cos
import kotlin.math.sin

/**
 * Time scale conversions.
 *
 * Notes:
 * - UTC->TT normally requires knowing TAI-UTC (leap seconds).
 *   For telescope planning, a simple constant is often "good enough" to start.
 * - We'll implement a pragmatic approach:
 *   TT ≈ UTC + (TAI-UTC + 32.184)s
 *
 * If you want higher fidelity later:
 * - add a leap second table and compute TAI-UTC properly
 * - optionally add ΔT models and more precise TDB
 */
object TimeScales {

    /**
     * Approximate TAI-UTC (leap seconds).
     * This WILL drift if leap seconds change.
     *
     * For now: default to 37 seconds (valid in recent years).
     * You can later replace with a real leap second table.
     */
    var taiMinusUtcSeconds: Double = 37.0

    private const val TT_MINUS_TAI = 32.184
    private const val SECONDS_PER_DAY = 86400.0

    /**
     * Convert JD(UTC) -> JD(TT) with an approximate constant offset.
     */
    fun jdTtFromJdUtc(jdUtc: Double): Double {
        val ttMinusUtc = taiMinusUtcSeconds + TT_MINUS_TAI
        return jdUtc + ttMinusUtc / SECONDS_PER_DAY
    }

    /**
     * Convert JD(TT) -> JD(TDB) approximately.
     *
     * A common approximation:
     *   TDB - TT ≈ 0.001657*sin(g) + 0.000022*sin(2g) seconds
     * where g is Earth's mean anomaly (in radians).
     *
     * This is sufficient for planning/pointing-level work.
     */
    fun jdTdbFromJdTtApprox(jdTt: Double): Double {
        val t = (jdTt - 2451545.0) / 36525.0 // centuries since J2000
        val gDeg = 357.5277233 + 35999.05034 * t
        val g = Math.toRadians(gDeg % 360.0)

        val tdbMinusTtSeconds =
            0.001657 * sin(g) + 0.000022 * sin(2.0 * g)

        return jdTt + tdbMinusTtSeconds / SECONDS_PER_DAY
    }

    /**
     * Convenience: JD(UTC) -> JD(TDB) approximate.
     */
    fun jdTdbFromJdUtcApprox(jdUtc: Double): Double {
        val jdTt = jdTtFromJdUtc(jdUtc)
        return jdTdbFromJdTtApprox(jdTt)
    }
}
