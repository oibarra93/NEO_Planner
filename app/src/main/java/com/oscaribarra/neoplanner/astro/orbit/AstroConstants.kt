package com.oscaribarra.neoplanner.astro.orbit

import kotlin.math.PI

object AstroConstants {
    // 1 AU in km (IAU 2012)
    const val AU_KM = 149_597_870.7

    // Gaussian gravitational constant (AU^1.5 / day)
    // Standard value used in celestial mechanics for solar system orbits.
    const val GAUSSIAN_K = 0.01720209895

    // Solar gravitational parameter in AU^3 / day^2 using k^2
    const val MU_SUN_AU3_PER_DAY2 = GAUSSIAN_K * GAUSSIAN_K

    // Obliquity of the ecliptic at J2000 (degrees)
    const val EPSILON_J2000_DEG = 23.439291111

    const val TWO_PI = 2.0 * PI
}