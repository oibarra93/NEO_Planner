package com.oscaribarra.neoplanner.ui

import com.oscaribarra.neoplanner.data.model.NeoWithOrbit
import com.oscaribarra.neoplanner.data.model.Observer
import com.oscaribarra.neoplanner.ui.pointing.TargetAltAz
import com.oscaribarra.neoplanner.planner.PlannedNeoResult
import java.time.ZonedDateTime

data class NeoPlannerUiState(
    val apiKey: String = "",
    val apiKeySaved: Boolean = false,

    val hasLocationPermission: Boolean = false,
    val observer: Observer? = null,
    val nowLocal: ZonedDateTime? = null,

    // Planner controls
    val hoursAhead: Int = 24,
    val stepMinutes: Int = 30,
    val minAltDeg: Double = 20.0,
    val twilightLimitDeg: Double = -12.0,
    val maxNeos: Int = 10,

    val isBusy: Boolean = false,

    // Raw NeoWs (debug)
    val results: List<NeoWithOrbit> = emptyList(),

    // Planned output
    val planned: List<PlannedNeoResult> = emptyList(),

    // Selection (NEO id, or special "MOON")
    val selectedNeoId: String? = null,

    // 🌙 Moon (from Horizons)
    val moonTarget: TargetAltAz? = null,
    val moonUpdatedLocal: ZonedDateTime? = null,
    val moonError: String? = null,

    val error: String? = null,

    val planetTargets: Map<Int, TargetAltAz> = emptyMap(), // commandId -> target
    val planetUpdatedLocal: java.time.ZonedDateTime? = null,
    val planetError: String? = null,

    // Optional if you want selection in UI:
    val selectedPlanetCommand: Int? = null,

    val azOffsetDeg: Double = 0.0,
    val altOffsetDeg: Double = 0.0,
    val isCalibrating: Boolean = false,


    )

