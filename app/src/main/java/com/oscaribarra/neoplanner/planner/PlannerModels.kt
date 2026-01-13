package com.oscaribarra.neoplanner.planner

import com.oscaribarra.neoplanner.data.model.NeoWithOrbit
import java.time.ZonedDateTime

data class PlanRequest(
    val hoursAhead: Int = 24,
    val stepMinutes: Int = 30,
    val minAltDeg: Double = 20.0,
    val twilightLimitDeg: Double = -12.0,
    val maxNeos: Int = 10
)

data class PlannedNeoResult(
    val neo: NeoWithOrbit,

    // Best window (local)
    val bestStartLocal: ZonedDateTime?,
    val bestEndLocal: ZonedDateTime?,
    val peakTimeLocal: ZonedDateTime?,
    val peakAltitudeDeg: Double?,

    // Pointing at peak
    val peakAzimuthDeg: Double?,
    val peakCardinal: String?,
    val pointingHint: String?,

    // For debug/inspection
    val visibleWindowCount: Int
)
