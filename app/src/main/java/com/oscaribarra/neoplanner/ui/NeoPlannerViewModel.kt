package com.oscaribarra.neoplanner.ui

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oscaribarra.neoplanner.astro.orbit.NeoWsOrbitMapper
import com.oscaribarra.neoplanner.data.config.SettingsDataStore
import com.oscaribarra.neoplanner.data.geo.ObserverProvider
import com.oscaribarra.neoplanner.data.repo.NeoRepository
import com.oscaribarra.neoplanner.planner.PlanRequest
import com.oscaribarra.neoplanner.planner.VisibilityPlanner
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

    private val _state = kotlinx.coroutines.flow.MutableStateFlow(NeoPlannerUiState())
    val state = _state.asStateFlow()

    @RequiresApi(Build.VERSION_CODES.O)
    private val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE

    init {
        // Option A: load saved API key into state once (no suspend calls in UI)
        viewModelScope.launch {
            try {
                val savedKey = settings.getNeoWsApiKey()
                _state.value = _state.value.copy(
                    apiKey = savedKey,
                    apiKeySaved = savedKey.isNotBlank()
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = e.message ?: "Failed to load saved settings"
                )
            }
        }
    }

    // -------------------------
    // UI setters
    // -------------------------
    fun setHasLocationPermission(granted: Boolean) {
        _state.value = _state.value.copy(hasLocationPermission = granted)
    }

    fun updateApiKey(text: String) {
        _state.value = _state.value.copy(apiKey = text, apiKeySaved = false)
    }

    fun saveApiKey() = viewModelScope.launch {
        try {
            val key = _state.value.apiKey.trim()
            require(key.isNotBlank()) { "API key cannot be empty." }
            settings.setNeoWsApiKey(key)

            _state.value = _state.value.copy(apiKeySaved = true, error = null)
            android.util.Log.i("API-Key", "Key saved len=${key.length}")
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                apiKeySaved = false,
                error = e.message ?: "Failed to save API key"
            )
        }
    }

    fun updateHoursAhead(v: String) {
        val parsed = v.toIntOrNull()
        if (parsed != null) {
            _state.value = _state.value.copy(hoursAhead = min(168, max(1, parsed)))
        }
    }

    fun updateMaxNeos(v: String) {
        val parsed = v.toIntOrNull()
        if (parsed != null) {
            _state.value = _state.value.copy(maxNeos = min(50, max(1, parsed)))
        }
    }

    fun updateStepMinutes(v: String) {
        val parsed = v.toIntOrNull()
        if (parsed != null) {
            _state.value = _state.value.copy(stepMinutes = parsed.coerceIn(1, 240))
        }
    }

    fun updateMinAltDeg(v: String) {
        val parsed = v.toDoubleOrNull()
        if (parsed != null) {
            _state.value = _state.value.copy(minAltDeg = parsed.coerceIn(0.0, 89.0))
        }
    }

    fun updateTwilightLimitDeg(v: String) {
        val parsed = v.toDoubleOrNull()
        if (parsed != null) {
            _state.value = _state.value.copy(twilightLimitDeg = parsed.coerceIn(-30.0, 5.0))
        }
    }

    fun selectNeo(id: String) {
        _state.value = _state.value.copy(selectedNeoId = id)
    }

    // -------------------------
    // Planner
    // -------------------------
    @RequiresApi(Build.VERSION_CODES.O)
    fun planVisibility(appContext: Context, req: PlanRequest) = viewModelScope.launch {
        // IMPORTANT: set isBusy=true while working
        _state.value = _state.value.copy(isBusy = true, error = null)

        try {
            // Ensure we have observer + NEO orbits loaded
            if (_state.value.observer == null || _state.value.results.isEmpty()) {
                // Call the suspend fetch logic inline (instead of fetchNeosNow().join())
                fetchNeosNowInternal()
            }

            val obs = _state.value.observer ?: error("Observer not available.")
            val neos = _state.value.results
            val nowLocal = _state.value.nowLocal ?: ZonedDateTime.now(ZoneId.of(obs.timeZoneId))

            val planned = VisibilityPlanner(appContext).plan(
                observer = obs,
                neos = neos,
                request = req,
                nowLocal = nowLocal
            )

            _state.value = _state.value.copy(
                isBusy = false,
                planned = planned,
                error = null
            )
            android.util.Log.i("NEO-PLAN", "Planned=${planned.size} top=${planned.firstOrNull()?.neo?.name}")
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isBusy = false,
                planned = emptyList(),
                error = e.message ?: "Planning failed"
            )
        }
    }

    fun getSelectedPlanned(): com.oscaribarra.neoplanner.planner.PlannedNeoResult? {
        val id = _state.value.selectedNeoId ?: return null
        return _state.value.planned.firstOrNull { it.neo.id == id }
    }


    // -------------------------
    // Debug Alt/Az (NEO)
    // -------------------------
    fun debugFirstNeoAltAz(appContext: Context) {
        val obs = state.value.observer ?: return
        val first = state.value.results.firstOrNull() ?: return

        debugNeoAltAz(
            appContext = appContext,
            orbit = first.orbit,
            obsLat = obs.latitudeDeg,
            obsLon = obs.longitudeDeg,
            obsH = obs.elevationMeters
        )
    }

    fun debugNeoAltAz(
        appContext: Context,
        orbit: com.oscaribarra.neoplanner.data.model.OrbitElements,
        obsLat: Double,
        obsLon: Double,
        obsH: Double
    ) = viewModelScope.launch {
        try {
            val file = com.oscaribarra.neoplanner.astro.spk.De442sManager(appContext).ensureKernel()
            val eph = com.oscaribarra.neoplanner.astro.spk.De442sEphemeris(file)

            try {
                val el = NeoWsOrbitMapper.fromNeoWsOrbit(orbit)
                val now = java.time.Instant.now()

                val geo = com.oscaribarra.neoplanner.astro.orbit.GeoVector
                    .neoGeocentricEciKm(eph, el, now)
                    ?: error("NEO propagation returned null (e >= 1 or invalid elements)")

                val topo = com.oscaribarra.neoplanner.astro.coords.Topocentric.solve(
                    geocentricEciKm = geo,
                    instantUtc = now,
                    obsLatDeg = obsLat,
                    obsLonDeg = obsLon,
                    obsHeightMeters = obsH
                )

                val msg =
                    "NEO OK @ $now\nAlt=${"%.2f".format(topo.altAz.altitudeDeg)}° " +
                            "Az=${"%.2f".format(topo.altAz.azimuthDeg)}° (${topo.cardinal})\n${topo.hint}"
                android.util.Log.i("NEO-ALT", msg)
            } finally {
                eph.close()
            }
        } catch (e: Exception) {
            android.util.Log.e("NEO-ALT", "NEO ERROR: ${e.message}", e)
        }
    }

    // -------------------------
    // Fetch NEOs (debug)
    // -------------------------
    @RequiresApi(Build.VERSION_CODES.O)
    fun fetchNeosNow() = viewModelScope.launch {
        _state.value = _state.value.copy(isBusy = true, error = null, results = emptyList())
        try {
            fetchNeosNowInternal()
        } catch (e: Exception) {
            _state.value = _state.value.copy(isBusy = false, error = e.message ?: "Unknown error")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun fetchNeosNowInternal() {
        android.util.Log.i(
            "NEO-KEY",
            "Saved key len=${settings.getNeoWsApiKey().length}, typed len=${_state.value.apiKey.length}"
        )

        // Ensure API key exists (either typed in UI or already saved)
        val savedKey = settings.getNeoWsApiKey()
        if (savedKey.isBlank()) {
            val typed = _state.value.apiKey.trim()
            require(typed.isNotBlank()) { "NeoWs API key missing. Paste key and tap Save." }
            settings.setNeoWsApiKey(typed)
            _state.value = _state.value.copy(apiKeySaved = true)
        }

        val now = ZonedDateTime.now()
        val zone = ZoneId.systemDefault()

        val observer = observerProvider.getObserver()
        val (startDate, endDate) = computeFeedDates(now, _state.value.hoursAhead)

        val candidates = repo.fetchCandidates(startDate, endDate)
        val details = repo.fetchDetails(candidates, _state.value.maxNeos, zone)

        _state.value = _state.value.copy(
            isBusy = false,
            nowLocal = now,
            observer = observer,
            results = details,
            error = null
        )
    }

    // -------------------------
    // Helpers
    // -------------------------
    @RequiresApi(Build.VERSION_CODES.O)
    private fun computeFeedDates(now: ZonedDateTime, hoursAhead: Int): Pair<String, String> {
        val start = now.toLocalDate()
        val end = now.plusHours(hoursAhead.toLong()).toLocalDate()

        val clampedEnd = if (end.isAfter(start.plusDays(7))) start.plusDays(7) else end
        return start.format(dateFmt) to clampedEnd.format(dateFmt)
    }
}
