package com.oscaribarra.neoplanner.astro.coords

data class AltAz(
    val altitudeDeg: Double,
    val azimuthDeg: Double
)

data class RaDec(
    val raDeg: Double,
    val decDeg: Double
)

data class TopocentricResult(
    val altAz: AltAz,
    val raDec: RaDec,
    val cardinal: String,
    val hint: String
)
