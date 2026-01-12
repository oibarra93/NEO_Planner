package com.oscaribarra.neoplanner.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oscaribarra.neoplanner.data.config.SettingsDataStore
import com.oscaribarra.neoplanner.data.geo.ObserverProvider
import com.oscaribarra.neoplanner.data.repo.NeoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

class NeoPlannerViewModel(
    private val settings: SettingsDataStore,
    private val observerProvider: ObserverProvider,
    private val repo: NeoRepository
) : ViewModel() {

    private val _state = MutableStateFlow(NeoPlannerUiState())
    val state = _state.asStateFlow()

    private val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE

    fun setHasLocationPermission(granted: Boolean) {
        _state.value = _state.value.copy(hasLocationPermission = granted)
    }

    fun updateApiKey(text: String) {
        _state.value = _state.value.copy(apiKey = text, apiKeySaved = false)
    }

    fun saveApiKey() = viewModelScope.launch {
        runCatching {
            val key = _state.value.apiKey.trim()
            require(key.isNotBlank()) { "API key cannot be empty." }
            settings.setNeoWsApiKey(key)
        }.onSuccess {
            _state.value = _state.value.copy(apiKeySaved = true, error = null)
        }.onFailure { e ->
            _state.value = _state.value.copy(apiKeySaved = false, error = e.message ?: "Failed to save API key")
        }
    }

    fun updateHoursAhead(v: String) {
        val parsed = v.toIntOrNull()
        if (parsed != null) {
            _state.value = _state.value.copy(hoursAhead = min(168, max(1, parsed))) // clamp 1..168
        }
    }

    fun updateMaxNeos(v: String) {
        val parsed = v.toIntOrNull()
        if (parsed != null) {
            _state.value = _state.value.copy(maxNeos = min(50, max(1, parsed))) // clamp 1..50
        }
    }

    /**
     * This is the "Module 1 integration test" button:
     * - gets observer (fused -> ip)
     * - fetches feed for date window
     * - fetches details for top N
     * - shows parsed orbit elements
     */
    fun fetchNeosNow() = viewModelScope.launch {
        _state.value = _state.value.copy(isBusy = true, error = null, results = emptyList())

        runCatching {
            // Ensure API key exists (either typed in UI or already saved)
            val savedKey = settings.getNeoWsApiKey()
            if (savedKey.isBlank()) {
                // if user typed it but didn't click save yet, let them proceed (still store it for next time)
                val typed = _state.value.apiKey.trim()
                require(typed.isNotBlank()) { "NeoWs API key missing. Paste key and tap Save." }
                settings.setNeoWsApiKey(typed)
            }

            val now = ZonedDateTime.now()
            val zone = ZoneId.systemDefault()

            val observer = observerProvider.getObserver()

            val (startDate, endDate) = computeFeedDates(now, _state.value.hoursAhead)

            val candidates = repo.fetchCandidates(startDate, endDate)
            val details = repo.fetchDetails(candidates, _state.value.maxNeos, zone)

            Triple(now, observer, details)
        }.onSuccess { (now, observer, details) ->
            _state.value = _state.value.copy(
                isBusy = false,
                nowLocal = now,
                observer = observer,
                results = details,
                error = null
            )
        }.onFailure { e ->
            _state.value = _state.value.copy(
                isBusy = false,
                error = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * NeoWs feed is date-based (YYYY-MM-DD). If your window crosses midnight, we include both dates.
     * For simplicity: start = local date today, end = local date at now + hoursAhead (min 1 day range).
     */
    private fun computeFeedDates(now: ZonedDateTime, hoursAhead: Int): Pair<String, String> {
        val start = now.toLocalDate()
        val end = now.plusHours(hoursAhead.toLong()).toLocalDate()

        // NeoWs feed allows up to 7 days per request; we clamp end if needed for now.
        val clampedEnd = if (end.isAfter(start.plusDays(7))) start.plusDays(7) else end

        return start.format(dateFmt) to clampedEnd.format(dateFmt)
    }
}
