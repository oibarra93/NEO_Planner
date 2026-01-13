package com.oscaribarra.neoplanner.ui

import com.oscaribarra.neoplanner.data.model.NeoWithOrbit
import com.oscaribarra.neoplanner.data.model.Observer
import java.time.ZonedDateTime
import com.oscaribarra.neoplanner.planner.PlannedNeoResult


data class NeoPlannerUiState(
    val apiKey: String = "",
    val apiKeySaved: Boolean = false,

    val hasLocationPermission: Boolean = false,
    val observer: Observer? = null,
    val nowLocal: ZonedDateTime? = null,

    val hoursAhead: Int = 24,
    val maxNeos: Int = 10,

    val isBusy: Boolean = false,
    val results: List<NeoWithOrbit> = emptyList(),
    val error: String? = null,

    val planned: List<PlannedNeoResult> = emptyList(),

    )
