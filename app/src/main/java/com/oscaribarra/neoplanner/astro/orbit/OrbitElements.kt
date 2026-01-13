package com.oscaribarra.neoplanner.astro.orbit

data class OrbitElements(
    val epochJd: Double,                 // epoch of elements (Julian Date)
    val e: Double,                       // eccentricity
    val aAu: Double,                     // semi-major axis (AU)
    val iDeg: Double,                    // inclination (deg)
    val raanDeg: Double,                 // ascending node Ω (deg)
    val argPeriDeg: Double,              // argument of perihelion ω (deg)
    val meanAnomalyDeg: Double,          // mean anomaly at epoch (deg)
    val meanMotionDegPerDay: Double? = null // optional (deg/day)
)