package com.oscaribarra.neoplanner.ui

import android.content.Context
import android.net.Uri
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
import com.oscaribarra.neoplanner.ui.pointing.TargetAltAz
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * ViewModel responsible for orchestrating NEO feed retrieval, planning visibility
 * windows and computing alt/az for the Moon and planets via JPL Horizons.
 *
 * The Horizons integration here has been updated to use consistent quoting and
 * locale‑safe formatting when building API requests.  All site coordinates and
 * timestamps are quoted (single quotes) and formatted with {@link Locale#US}
 * regardless of the device locale to ensure decimals use the dot separator.
 * Times passed to Horizons are derived from {@code labelTimeLocal} and
 * converted to UTC via {@link ZoneOffset#UTC} so that the request time
 * matches the label displayed in the UI.
 */
class NeoPlannerViewModel(
    private val settings: SettingsDataStore,
    private val observerProvider: ObserverProvider,
    private val repo: NeoRepository
) : ViewModel() {

    private val _state = MutableStateFlow(NeoPlannerUiState())
    val state = _state.asStateFlow()

    @RequiresApi(Build.VERSION_CODES.O)
    private val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE

    init {
        // Load saved API key
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
        if (parsed != null) _state.value = _state.value.copy(hoursAhead = min(168, max(1, parsed)))
    }

    fun updateMaxNeos(v: String) {
        val parsed = v.toIntOrNull()
        if (parsed != null) _state.value = _state.value.copy(maxNeos = min(50, max(1, parsed)))
    }

    fun updateStepMinutes(v: String) {
        val parsed = v.toIntOrNull()
        if (parsed != null) _state.value = _state.value.copy(stepMinutes = parsed.coerceIn(1, 240))
    }

    fun updateMinAltDeg(v: String) {
        val parsed = v.toDoubleOrNull()
        if (parsed != null) _state.value = _state.value.copy(minAltDeg = parsed.coerceIn(0.0, 89.0))
    }

    fun updateTwilightLimitDeg(v: String) {
        val parsed = v.toDoubleOrNull()
        if (parsed != null) _state.value = _state.value.copy(twilightLimitDeg = parsed.coerceIn(-30.0, 5.0))
    }

    fun selectNeo(id: String) {
        _state.value = _state.value.copy(selectedNeoId = id)
    }

    fun getSelectedPlanned(): com.oscaribarra.neoplanner.planner.PlannedNeoResult? {
        val id = _state.value.selectedNeoId ?: return null
        return _state.value.planned.firstOrNull { it.neo.id == id }
    }

    // -------------------------
    // 🌙 Moon (Horizons)
    // -------------------------
    @RequiresApi(Build.VERSION_CODES.O)
    fun refreshMoonIfPossible() = viewModelScope.launch {
        val obs = _state.value.observer ?: return@launch
        refreshMoonInternal(obs)
    }

    fun selectPlanet(commandId: Int?) {
        _state.value = _state.value.copy(selectedPlanetCommand = commandId)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun refreshMoonInternal(obs: com.oscaribarra.neoplanner.data.model.Observer) {
        try {
            val nowLocal = _state.value.nowLocal ?: ZonedDateTime.now(ZoneId.of(obs.timeZoneId))
            val target = fetchMoonAltAzFromHorizons(
                obsLatDeg = obs.latitudeDeg,
                obsLonDeg = obs.longitudeDeg,
                obsHeightMeters = obs.elevationMeters,
                labelTimeLocal = nowLocal
            )

            _state.value = _state.value.copy(
                moonTarget = target,
                moonUpdatedLocal = nowLocal,
                moonError = null
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                moonTarget = null,
                moonUpdatedLocal = _state.value.nowLocal,
                moonError = e.message ?: "Failed to fetch Moon"
            )
        }
    }

    /**
     * Query Horizons for the apparent altitude/azimuth of a solar‑system body at the
     * specified time and observer location.  Uses single‑quoted values for
     * {@code SITE_COORD} and {@code TLIST}, and formats numeric values with
     * {@link Locale#US} to avoid locale‑dependent decimal separators.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun fetchBodyAltAzFromHorizons(
        commandId: Int,                  // 499 Mars, 299 Venus, etc.
        bodyLabel: String,               // "Mars", "Venus", etc.
        obsLatDeg: Double,
        obsLonDeg: Double,
        obsHeightMeters: Double,
        labelTimeLocal: ZonedDateTime
    ): TargetAltAz = withContext(Dispatchers.IO) {
        // Convert elevation to kilometres
        val elevKm = obsHeightMeters / 1000.0
        // Derive the request timestamp from the caller's label time and convert to UTC
        val utc = labelTimeLocal.withZoneSameInstant(ZoneOffset.UTC)
        val tlist = utc.format(DateTimeFormatter.ofPattern("yyyy-MMM-dd HH:mm", Locale.US))
        // Format lon,lat,elev(km) with US locale for consistent decimal separators
        val siteCoord = String.format(Locale.US, "%.8f,%.8f,%.4f", obsLonDeg, obsLatDeg, elevKm)
        // Build the Horizons request; quote SITE_COORD and TLIST
        val url = Uri.parse("https://ssd.jpl.nasa.gov/api/horizons.api").buildUpon()
            .appendQueryParameter("format", "json")
            .appendQueryParameter("MAKE_EPHEM", "YES")
            .appendQueryParameter("EPHEM_TYPE", "OBSERVER")
            .appendQueryParameter("COMMAND", commandId.toString())
            .appendQueryParameter("CENTER", "coord@399")
            .appendQueryParameter("COORD_TYPE", "GEODETIC")
            .appendQueryParameter("SITE_COORD", "'${siteCoord}'")
            .appendQueryParameter("TLIST", "'${tlist}'")
            .appendQueryParameter("QUANTITIES", "4") // Apparent AZ/EL
            .appendQueryParameter("CSV_FORMAT", "YES")
            .appendQueryParameter("ANG_FORMAT", "DEG")
            .appendQueryParameter("OBJ_DATA", "NO")
            .build()
            .toString()
        // Execute the HTTP request
        val jsonText = SimpleHttp.get(url)
        val obj = JSONObject(jsonText)
        val resultText = obj.optString("result")
        if (resultText.isBlank()) {
            val msg = obj.optString("message")
            error(if (msg.isNotBlank()) msg else "Horizons response missing result text.")
        }
        // Extract the $$SOE..$$EOE block
        val soeIdx = resultText.indexOf(SOE_MARK)
        val eoeIdx = resultText.indexOf(EOE_MARK)
        if (soeIdx < 0 || eoeIdx < 0 || eoeIdx <= soeIdx) {
            val preview = resultText.take(700)
            error("Horizons response missing SOE/EOE block. Preview:\n$preview")
        }
        val block = resultText.substring(soeIdx + SOE_MARK.length, eoeIdx)
        // Find the first non‑empty line containing commas and extract numeric values
        val dataLine = block.lines()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && it.contains(",") }
            ?: error("No Horizons CSV data line found between SOE/EOE.")
        val tokens = dataLine.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val nums = tokens.mapNotNull { it.toDoubleOrNull() }
        if (nums.size < 2) {
            error("Could not find numeric AZ/EL in Horizons CSV line:\n$dataLine")
        }
        val az = nums[nums.size - 2]
        val el = nums[nums.size - 1]
        val labelFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z", Locale.US)
        val label = "$bodyLabel • ${labelTimeLocal.format(labelFmt)}"
        TargetAltAz(
            label = label,
            altitudeDeg = el,
            azimuthDegTrue = az
        )
    }

    // List of planet command IDs (Mercury through Neptune) and labels
    private val PLANETS: List<Pair<Int, String>> = listOf(
        199 to "Mercury",
        299 to "Venus",
        499 to "Mars",
        599 to "Jupiter",
        699 to "Saturn",
        799 to "Uranus",
        899 to "Neptune"
    )

    @RequiresApi(Build.VERSION_CODES.O)
    fun refreshPlanetsIfPossible() = viewModelScope.launch {
        val obs = _state.value.observer ?: return@launch
        refreshPlanetsInternal(obs)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun refreshPlanetsInternal(obs: com.oscaribarra.neoplanner.data.model.Observer) {
        try {
            val nowLocal = _state.value.nowLocal ?: ZonedDateTime.now(ZoneId.of(obs.timeZoneId))
            val targets = mutableMapOf<Int, TargetAltAz>()
            for ((cmd, name) in PLANETS) {
                val t = fetchBodyAltAzFromHorizons(
                    commandId = cmd,
                    bodyLabel = name,
                    obsLatDeg = obs.latitudeDeg,
                    obsLonDeg = obs.longitudeDeg,
                    obsHeightMeters = obs.elevationMeters,
                    labelTimeLocal = nowLocal
                )
                targets[cmd] = t
            }
            _state.value = _state.value.copy(
                planetTargets = targets,
                planetUpdatedLocal = nowLocal,
                planetError = null
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                planetTargets = emptyMap(),
                planetUpdatedLocal = _state.value.nowLocal,
                planetError = e.message ?: "Failed to fetch planets"
            )
        }
    }

    /**
     * Fetch the Moon's altitude/azimuth via Horizons.  Quoting and locale‑safe
     * formatting are applied consistently with {@link #fetchBodyAltAzFromHorizons}.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun fetchMoonAltAzFromHorizons(
        obsLatDeg: Double,
        obsLonDeg: Double,
        obsHeightMeters: Double,
        labelTimeLocal: ZonedDateTime
    ): TargetAltAz = withContext(Dispatchers.IO) {
        val elevKm = obsHeightMeters / 1000.0
        val utcNow = labelTimeLocal.withZoneSameInstant(ZoneOffset.UTC)
        val tlistFmt = DateTimeFormatter.ofPattern("yyyy-MMM-dd HH:mm", Locale.US)
        val tlist = utcNow.format(tlistFmt)
        val siteCoord = String.format(Locale.US, "%.8f,%.8f,%.4f", obsLonDeg, obsLatDeg, elevKm)
        val url = Uri.parse("https://ssd.jpl.nasa.gov/api/horizons.api").buildUpon()
            .appendQueryParameter("format", "json")
            .appendQueryParameter("EPHEM_TYPE", "OBSERVER")
            .appendQueryParameter("COMMAND", "301")
            .appendQueryParameter("CENTER", "coord@399")
            .appendQueryParameter("COORD_TYPE", "GEODETIC")
            .appendQueryParameter("SITE_COORD", "'${siteCoord}'")
            .appendQueryParameter("TLIST", "'${tlist}'")
            .appendQueryParameter("QUANTITIES", "4")
            .appendQueryParameter("CSV_FORMAT", "YES")
            .appendQueryParameter("ANG_FORMAT", "DEG")
            .appendQueryParameter("OBJ_DATA", "NO")
            .appendQueryParameter("MAKE_EPHEM", "YES")
            .build()
            .toString()
        val jsonText = SimpleHttp.get(url)
        val obj = JSONObject(jsonText)
        val resultText = obj.optString("result")
        if (resultText.isBlank()) {
            val msg = obj.optString("message")
            error(if (msg.isNotBlank()) msg else "Horizons response missing result text.")
        }
        val soeIdx = resultText.indexOf(SOE_MARK)
        val eoeIdx = resultText.indexOf(EOE_MARK)
        if (soeIdx < 0 || eoeIdx < 0 || eoeIdx <= soeIdx) {
            val preview = resultText.take(700)
            error("Horizons response missing SOE/EOE block. Preview:\n$preview")
        }
        val block = resultText.substring(soeIdx + SOE_MARK.length, eoeIdx)
        val dataLine = block.lines()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && it.contains(",") }
            ?: error("No Horizons CSV data line found between SOE/EOE.")
        val tokens = dataLine.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val nums = tokens.mapNotNull { it.toDoubleOrNull() }
        if (nums.size < 2) {
            error("Could not find numeric AZ/EL in Horizons CSV line:\n$dataLine")
        }
        val az = nums[nums.size - 2]
        val el = nums[nums.size - 1]
        val labelFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z", Locale.US)
        val label = "Moon • ${labelTimeLocal.format(labelFmt)}"
        TargetAltAz(
            label = label,
            altitudeDeg = el,
            azimuthDegTrue = az
        )
    }

    // -------------------------
    // Planner
    // -------------------------
    @RequiresApi(Build.VERSION_CODES.O)
    fun planVisibility(appContext: Context, req: PlanRequest) = viewModelScope.launch {
        _state.value = _state.value.copy(isBusy = true, error = null)
        try {
            if (_state.value.observer == null || _state.value.results.isEmpty()) {
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
            // 🌙 refresh Moon whenever we have observer/time
            refreshMoonInternal(obs)
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

    // -------------------------
    // Debug Alt/Az (NEO)
    // -------------------------
    @RequiresApi(Build.VERSION_CODES.O)
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

    @RequiresApi(Build.VERSION_CODES.O)
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

    /**
     * Debug helper to evaluate each planet’s altitude and azimuth using the Horizons API
     * and log the intermediate steps.  This method iterates over all planet IDs in
     * {@link #PLANETS}, constructs the same Horizons request used for normal
     * processing, and writes detailed diagnostics (URL, tokens, altitude, azimuth)
     * to Logcat.  It is intended for developer troubleshooting when the UI
     * displays no planets in view but astronomical sources indicate planets should
     * be visible.  Requires API 26+.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun debugPlanets() = viewModelScope.launch {
        val obs = state.value.observer ?: run {
            android.util.Log.e("DEBUG-PLANETS", "Observer not available for debugPlanets()")
            return@launch
        }
        val nowLocal = state.value.nowLocal ?: ZonedDateTime.now(ZoneId.of(obs.timeZoneId))
        for ((cmd, name) in PLANETS) {
            debugFetchBodyAltAz(
                commandId = cmd,
                bodyLabel = name,
                obsLatDeg = obs.latitudeDeg,
                obsLonDeg = obs.longitudeDeg,
                obsHeightMeters = obs.elevationMeters,
                labelTimeLocal = nowLocal
            )
        }
    }

    /**
     * Low-level diagnostic: issues the Horizons request for a single body and
     * logs the URL, parsed CSV tokens and the extracted altitude/azimuth.  This
     * duplicates much of the logic in {@link #fetchBodyAltAzFromHorizons} but
     * avoids updating view state.  Use only for debugging.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun debugFetchBodyAltAz(
        commandId: Int,
        bodyLabel: String,
        obsLatDeg: Double,
        obsLonDeg: Double,
        obsHeightMeters: Double,
        labelTimeLocal: ZonedDateTime
    ) {
        withContext(Dispatchers.IO) {
            val elevKm = obsHeightMeters / 1000.0
            val utc = labelTimeLocal.withZoneSameInstant(ZoneOffset.UTC)
            val tlist = utc.format(DateTimeFormatter.ofPattern("yyyy-MMM-dd HH:mm", Locale.US))
            val siteCoord = String.format(Locale.US, "%.8f,%.8f,%.4f", obsLonDeg, obsLatDeg, elevKm)
            val url = Uri.parse("https://ssd.jpl.nasa.gov/api/horizons.api").buildUpon()
                .appendQueryParameter("format", "json")
                .appendQueryParameter("MAKE_EPHEM", "YES")
                .appendQueryParameter("EPHEM_TYPE", "OBSERVER")
                .appendQueryParameter("COMMAND", commandId.toString())
                .appendQueryParameter("CENTER", "coord@399")
                .appendQueryParameter("COORD_TYPE", "GEODETIC")
                .appendQueryParameter("SITE_COORD", "'${siteCoord}'")
                .appendQueryParameter("TLIST", "'${tlist}'")
                .appendQueryParameter("QUANTITIES", "4")
                .appendQueryParameter("CSV_FORMAT", "YES")
                .appendQueryParameter("ANG_FORMAT", "DEG")
                .appendQueryParameter("OBJ_DATA", "NO")
                .build()
                .toString()
            android.util.Log.i("DEBUG-PLANETS", "Requesting $bodyLabel: $url")
            try {
                val jsonText = SimpleHttp.get(url)
                val obj = JSONObject(jsonText)
                val resultText = obj.optString("result")
                if (resultText.isBlank()) {
                    val msg = obj.optString("message")
                    android.util.Log.e("DEBUG-PLANETS", "${bodyLabel}: no result – ${msg}")
                    return@withContext
                }
                val soeIdx = resultText.indexOf(SOE_MARK)
                val eoeIdx = resultText.indexOf(EOE_MARK)
                if (soeIdx < 0 || eoeIdx < 0 || eoeIdx <= soeIdx) {
                    android.util.Log.e("DEBUG-PLANETS", "$bodyLabel: missing SOE/EOE markers")
                    return@withContext
                }
                val block = resultText.substring(soeIdx + SOE_MARK.length, eoeIdx)
                val dataLine = block.lines()
                    .map { it.trim() }
                    .firstOrNull { it.isNotBlank() && it.contains(",") }
                if (dataLine == null) {
                    android.util.Log.e("DEBUG-PLANETS", "$bodyLabel: no CSV line found")
                    return@withContext
                }
                val tokens = dataLine.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                val nums = tokens.mapNotNull { it.toDoubleOrNull() }
                if (nums.size < 2) {
                    android.util.Log.e("DEBUG-PLANETS", "$bodyLabel: insufficient numeric tokens: $tokens")
                    return@withContext
                }
                val az = nums[nums.size - 2]
                val el = nums[nums.size - 1]
                android.util.Log.i(
                    "DEBUG-PLANETS",
                    "$bodyLabel – tokens=$tokens, nums=$nums, az=$az, el=$el"
                )
            } catch (e: Exception) {
                android.util.Log.e("DEBUG-PLANETS", "$bodyLabel: exception ${e.message}", e)
            }
        }
    }

    // -------------------------
    // Fetch NEOs (debug)
    // -------------------------
    @RequiresApi(Build.VERSION_CODES.O)
    fun fetchNeosNow() = viewModelScope.launch {
        _state.value = _state.value.copy(isBusy = true, error = null, results = emptyList())
        try {
            // Pull the latest NEO feed and details.  This will update _state.nowLocal and
            // _state.observer, which are prerequisites for computing planet positions.
            fetchNeosNowInternal()
            // Immediately after fetching NEOs, trigger a verbose Horizons query for each
            // planet.  This invokes debugPlanets(), which iterates through the planet list
            // and logs altitude/azimuth diagnostics.  We wrap the call in an SDK version
            // check because debugPlanets() is annotated with @RequiresApi(O).  Doing this
            // here ensures the debug helper runs automatically whenever the app starts or
            // when the user presses the Fetch button.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                debugPlanets()
            }
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
        // 🌙 refresh Moon too
        refreshMoonInternal(observer)
        // 🪐 refresh planets and update planetTargets after fetching NEOs.  This call
        // computes the apparent altitude/azimuth for each planet via Horizons and
        // stores the results in _state.planetTargets, enabling the Results tab to
        // show planets in view.  It is guarded by an API level check because
        // refreshPlanetsInternal() is annotated with @RequiresApi(O).
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            refreshPlanetsInternal(observer)
        }
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

    companion object {
        // Escape $ to avoid Kotlin string interpolation parsing issues
        private const val SOE_MARK = "\$\$SOE"
        private const val EOE_MARK = "\$\$EOE"
    }
}