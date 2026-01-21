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
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

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
        if (parsed !=null) _state.value = _state.value.copy(twilightLimitDeg = parsed.coerceIn(-30.0, 5.0))
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
    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun fetchBodyAltAzFromHorizons(
        commandId: Int,                  // 301 Moon, 499 Mars, etc.
        bodyLabel: String,               // "Moon", "Mars", etc.
        obsLatDeg: Double,
        obsLonDeg: Double,
        obsHeightMeters: Double,
        labelTimeLocal: ZonedDateTime
    ): TargetAltAz = withContext(Dispatchers.IO) {

        val elevKm = obsHeightMeters / 1000.0

        // Use UTC timestamp for Horizons TLIST (it accepts many formats).
        val utc = ZonedDateTime.now(ZoneId.of("UTC"))
        val tlist = utc.format(DateTimeFormatter.ofPattern("yyyy-MMM-dd HH:mm")) // e.g. 2026-Jan-20 20:57

        val url = Uri.parse("https://ssd.jpl.nasa.gov/api/horizons.api").buildUpon()
            .appendQueryParameter("format", "json")
            .appendQueryParameter("MAKE_EPHEM", "YES")
            .appendQueryParameter("EPHEM_TYPE", "OBSERVER")
            .appendQueryParameter("COMMAND", commandId.toString())
            .appendQueryParameter("CENTER", "coord@399")
            .appendQueryParameter("COORD_TYPE", "GEODETIC")

            // IMPORTANT: no quotes around SITE_COORD in the API call
            // and lon can be negative (Horizons will convert to E-lon internally)
            .appendQueryParameter("SITE_COORD", "${obsLonDeg},${obsLatDeg},${"%.4f".format(elevKm)}")

            .appendQueryParameter("TLIST", tlist)
            .appendQueryParameter("QUANTITIES", "4") // Apparent AZ/EL
            .appendQueryParameter("CSV_FORMAT", "YES")
            .appendQueryParameter("ANG_FORMAT", "DEG")
            .appendQueryParameter("OBJ_DATA", "NO")
            .build()
            .toString()

        val jsonText = SimpleHttp.get(url)
        val obj = JSONObject(jsonText)
        val resultText = obj.optString("result")
        if (resultText.isBlank()) error("Horizons response missing result text.")

        val soe = resultText.indexOf("\$\$SOE")
        val eoe = resultText.indexOf("\$\$EOE")
        if (soe < 0 || eoe < 0 || eoe <= soe) error("Horizons response missing SOE/EOE block.")

        val block = resultText.substring(soe, eoe)
        val dataLine = block.lines()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && !it.startsWith("$$") && it.contains(",") }
            ?: error("No Horizons CSV data line found.")

        // Example line:
        // 2026-Jan-20 20:57:00.000,*,m, 169.824884,    38.068210,
        val parts = dataLine.split(",").map { it.trim() }

        // Robust: collect numeric tokens from the end, ignoring blanks and markers
        val nums = mutableListOf<Double>()
        for (i in parts.indices.reversed()) {
            val v = parts[i].toDoubleOrNull()
            if (v != null) {
                nums.add(v)
                if (nums.size == 2) break
            }
        }
        if (nums.size < 2) error("Could not parse AZ/EL from Horizons CSV line:\n$dataLine")

        val el = nums[0]       // last numeric token
        val az = nums[1]       // second-to-last numeric token

        val label = "$bodyLabel • ${labelTimeLocal.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z"))}"
        TargetAltAz(
            label = label,
            altitudeDeg = el,
            azimuthDegTrue = az
        )
    }

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
     * JPL Horizons API (GET):
     * - COMMAND='301' (Moon)
     * - EPHEM_TYPE='OBSERVER'
     * - CENTER='coord@399' + COORD_TYPE='GEODETIC'
     * - SITE_COORD='lon,lat,elev_km'  <-- IMPORTANT: QUOTED (single quotes)
     * - TLIST='YYYY-MMM-DD HH:MM'     <-- IMPORTANT: QUOTED
     * - QUANTITIES='4' (AZ/EL)
     * - CSV_FORMAT='YES'
     *
     * Parses "result" between $$SOE and $$EOE.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun fetchMoonAltAzFromHorizons(
        obsLatDeg: Double,
        obsLonDeg: Double,
        obsHeightMeters: Double,
        labelTimeLocal: ZonedDateTime
    ): TargetAltAz = withContext(Dispatchers.IO) {
        val elevKm = obsHeightMeters / 1000.0

        val utcNow = ZonedDateTime.now(ZoneId.of("UTC"))
        val tlistFmt = DateTimeFormatter.ofPattern("yyyy-MMM-dd HH:mm", Locale.US)
        val tlist = utcNow.format(tlistFmt)

        // Horizons wants lon,lat,elev(km)
        val siteCoord = String.format(Locale.US, "%.8f,%.8f,%.4f", obsLonDeg, obsLatDeg, elevKm)

        val url = Uri.parse("https://ssd.jpl.nasa.gov/api/horizons.api").buildUpon()
            .appendQueryParameter("format", "json")
            .appendQueryParameter("EPHEM_TYPE", "OBSERVER")
            .appendQueryParameter("COMMAND", "301")
            .appendQueryParameter("CENTER", "coord@399")
            .appendQueryParameter("COORD_TYPE", "GEODETIC")
            .appendQueryParameter("SITE_COORD", "'$siteCoord'") // IMPORTANT: quoted
            .appendQueryParameter("TLIST", "'$tlist'")           // IMPORTANT: quoted
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

        // Split CSV, remove empties (handles trailing comma), keep tokens
        val tokens = dataLine.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        // Find the last two numeric values in the line (AZ then EL for this output)
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
