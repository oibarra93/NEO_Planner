package com.oscaribarra.neoplanner.ui

import com.oscaribarra.neoplanner.data.model.NeoWithOrbit
import com.oscaribarra.neoplanner.data.model.Observer
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

    // Planned output (Module 9)
    val planned: List<PlannedNeoResult> = emptyList(),

    // Selection
    val selectedNeoId: String? = null,

    val error: String? = null
)