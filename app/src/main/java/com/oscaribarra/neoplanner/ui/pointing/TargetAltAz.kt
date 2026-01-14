package com.oscaribarra.neoplanner.ui.pointing

/**
 * What the user should aim at (typically from your planner result: peak Alt/Az at peak time).
 * azimuthDegTrue: 0=N, 90=E, 180=S, 270=W (TRUE north)
 */
data class TargetAltAz(
    val label: String,
    val altitudeDeg: Double,
    val azimuthDegTrue: Double
)
