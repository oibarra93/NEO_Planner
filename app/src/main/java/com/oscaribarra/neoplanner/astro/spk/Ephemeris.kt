package com.oscaribarra.neoplanner.astro.spk

/**
 * Ephemeris API: position of target relative to center at epoch ET (seconds past J2000, TDB-ish).
 */
interface Ephemeris {
    fun position(target: Int, center: Int, etSeconds: Double): Vec3Km
}
