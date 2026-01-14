package com.oscaribarra.neoplanner.ui

import android.Manifest
import androidx.compose.ui.platform.LocalUriHandler
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.oscaribarra.neoplanner.planner.PlanRequest
import com.oscaribarra.neoplanner.planner.PlannedNeoResult
import com.oscaribarra.neoplanner.ui.pointing.PointingTab
import com.oscaribarra.neoplanner.ui.pointing.TargetAltAz
import java.time.format.DateTimeFormatter

private enum class ResultsMode { Planned, Raw }
private enum class MainTab { Settings, Planner, Results, Pointing }

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeoPlannerScreen(vm: NeoPlannerViewModel) {
    val st by vm.state.collectAsState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current


    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> vm.setHasLocationPermission(granted) }

    val timeFmt = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z") }

    var tab by remember { mutableStateOf(MainTab.Planner) }
    var mode by remember { mutableStateOf(ResultsMode.Planned) }

    // Selected planned result (if any)
    val selectedPlanned = remember(st.planned, st.selectedNeoId) {
        st.planned.firstOrNull { it.neo.id == st.selectedNeoId }
    }

    // Build target for PointingTab if we have peak Alt/Az
    val pointingTarget: TargetAltAz? = remember(selectedPlanned) {
        val p = selectedPlanned ?: return@remember null
        val alt = p.peakAltitudeDeg ?: return@remember null
        val az = p.peakAzimuthDeg ?: return@remember null

        val whenStr = p.peakTimeLocal?.format(timeFmt) ?: "peak time"
        TargetAltAz(
            label = "${p.neo.name} • $whenStr",
            altitudeDeg = alt,
            azimuthDegTrue = az
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("NEO Telescope Planner") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = tab.ordinal) {
                Tab(
                    selected = tab == MainTab.Settings,
                    onClick = { tab = MainTab.Settings },
                    text = { Text("Settings") }
                )
                Tab(
                    selected = tab == MainTab.Planner,
                    onClick = { tab = MainTab.Planner },
                    text = { Text("Planner") }
                )
                Tab(
                    selected = tab == MainTab.Results,
                    onClick = { tab = MainTab.Results },
                    text = { Text("Results") }
                )
                Tab(
                    selected = tab == MainTab.Pointing,
                    onClick = { tab = MainTab.Pointing }, // ✅ no item here
                    text = { Text("Pointing") }
                )
            }

            when (tab) {
                MainTab.Settings -> SettingsTab(
                    st = st,
                    timeFmt = timeFmt,
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                    onSaveKey = vm::saveApiKey,
                    onUpdateKey = vm::updateApiKey
                )

                MainTab.Planner -> PlannerTab(
                    st = st,
                    timeFmt = timeFmt,
                    selected = selectedPlanned,
                    onFetch = vm::fetchNeosNow,
                    onPlan = {
                        vm.planVisibility(
                            appContext = context.applicationContext,
                            req = PlanRequest(
                                hoursAhead = st.hoursAhead,
                                stepMinutes = st.stepMinutes,
                                minAltDeg = st.minAltDeg,
                                twilightLimitDeg = st.twilightLimitDeg,
                                maxNeos = st.maxNeos
                            )
                        )
                        tab = MainTab.Results
                    },
                    onUpdateHoursAhead = vm::updateHoursAhead,
                    onUpdateStepMinutes = vm::updateStepMinutes,
                    onUpdateMinAlt = vm::updateMinAltDeg,
                    onUpdateTwilight = vm::updateTwilightLimitDeg,
                    onUpdateMaxNeos = vm::updateMaxNeos
                )

                MainTab.Results -> ResultsTab(
                    st = st,
                    timeFmt = timeFmt,
                    mode = mode,
                    onModeChange = { mode = it },
                    onPointNeo = { id ->
                        vm.selectNeo(id)
                        tab = MainTab.Pointing
                    },
                    onOpenNeoDetails = { id ->
                        // SBDB lookup works well for end users:
                        uriHandler.openUri("https://ssd.jpl.nasa.gov/tools/sbdb_lookup.html#/?sstr=$id")
                    }
                )


                MainTab.Pointing -> {
                    val obs = st.observer
                    PointingTab(
                        obsLatDeg = obs?.latitudeDeg,
                        obsLonDeg = obs?.longitudeDeg,
                        obsHeightMeters = obs?.elevationMeters,
                        targetAltAz = pointingTarget, // ✅ pass real target when available
                        onJumpToResults = { tab = MainTab.Results }
                    )
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun SettingsTab(
    st: NeoPlannerUiState,
    timeFmt: DateTimeFormatter,
    onRequestPermission: () -> Unit,
    onSaveKey: () -> Unit,
    onUpdateKey: (String) -> Unit
) {
    val scroll = rememberScrollState()
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    // NASA API signup page (NeoWs uses the same API key)
    val apiKeyUrl = "https://api.nasa.gov/"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("NASA NeoWs API Key", style = MaterialTheme.typography.titleMedium)

                OutlinedTextField(
                    value = st.apiKey,
                    onValueChange = onUpdateKey,
                    label = { Text("API key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Link to get a key
                TextButton(
                    onClick = { uriHandler.openUri(apiKeyUrl) },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Get a free NASA API key")
                }

                Text(
                    "Tip: After you request a key, paste it here and tap Save.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onSaveKey, enabled = !st.isBusy) { Text("Save") }
                    if (st.apiKeySaved) Text("Saved ✓", color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Location / Permissions", style = MaterialTheme.typography.titleMedium)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (st.hasLocationPermission) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (st.hasLocationPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (st.hasLocationPermission) "Granted" else "Not granted",
                        color = if (st.hasLocationPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )

                    Spacer(Modifier.width(12.dp))
                    Button(onClick = onRequestPermission, enabled = !st.isBusy) {
                        Text("Request Permission")
                    }
                }

                st.observer?.let { obs ->
                    Text("lat=${"%.5f".format(obs.latitudeDeg)}  lon=${"%.5f".format(obs.longitudeDeg)}  elev=${"%.0f".format(obs.elevationMeters)}m")
                    Text("Timezone: ${obs.timeZoneId}")
                } ?: Text("Observer: (not resolved yet — resolves when planning/fetching)")

                st.nowLocal?.let { now ->
                    Text("Now (local): ${now.format(timeFmt)}")
                }
            }
        }

        st.error?.let { err ->
            Card {
                Column(Modifier.padding(12.dp)) {
                    Text("Error", style = MaterialTheme.typography.titleMedium)
                    Text(err, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}


@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun PlannerTab(
    st: NeoPlannerUiState,
    timeFmt: DateTimeFormatter,
    selected: com.oscaribarra.neoplanner.planner.PlannedNeoResult?,
    onFetch: () -> Unit,
    onPlan: () -> Unit,
    onUpdateHoursAhead: (String) -> Unit,
    onUpdateStepMinutes: (String) -> Unit,
    onUpdateMinAlt: (String) -> Unit,
    onUpdateTwilight: (String) -> Unit,
    onUpdateMaxNeos: (String) -> Unit
) {
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Planner Controls", style = MaterialTheme.typography.titleMedium)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = st.hoursAhead.toString(),
                        onValueChange = onUpdateHoursAhead,
                        label = { Text("Hours ahead") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = st.stepMinutes.toString(),
                        onValueChange = onUpdateStepMinutes,
                        label = { Text("Step (min)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = st.minAltDeg.toString(),
                        onValueChange = onUpdateMinAlt,
                        label = { Text("Min alt (°)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = st.twilightLimitDeg.toString(),
                        onValueChange = onUpdateTwilight,
                        label = { Text("Twilight (°)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = st.maxNeos.toString(),
                    onValueChange = onUpdateMaxNeos,
                    label = { Text("Max NEOs") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onFetch, enabled = !st.isBusy) {
                        Text(if (st.isBusy) "Working…" else "Fetch (debug)")
                    }
                    Button(onClick = onPlan, enabled = !st.isBusy) {
                        Text("Plan Visibility")
                    }
                }
            }
        }

        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Pointing Info", style = MaterialTheme.typography.titleMedium)

                if (selected == null) {
                    Text("Go to Results tab and tap a planned result.")
                } else {
                    Text(selected.neo.name, style = MaterialTheme.typography.titleSmall)
                    Text("Peak time: ${selected.peakTimeLocal?.format(timeFmt) ?: "—"}")

                    val altStr = selected.peakAltitudeDeg?.let { "%.2f°".format(it) } ?: "—"
                    val azStr = selected.peakAzimuthDeg?.let { "%.2f°".format(it) } ?: "—"
                    val card = selected.peakCardinal ?: "—"
                    Text("Alt/Az @ peak: $altStr / $azStr ($card)")
                    selected.pointingHint?.let { Text(it) }
                }
            }
        }
    }
}

private enum class ResultsSort { Default, DistanceAu, PeakTime }

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultsTab(
    st: NeoPlannerUiState,
    timeFmt: DateTimeFormatter,
    mode: ResultsMode,
    onModeChange: (ResultsMode) -> Unit,
    onPointNeo: (String) -> Unit,
    onOpenNeoDetails: (String) -> Unit
) {
    var sheetNeoId by remember { mutableStateOf<String?>(null) }
    var sheetNeoName by remember { mutableStateOf<String?>(null) }

    // Bottom sheet (shows when sheetNeoId != null)
    if (sheetNeoId != null) {
        val id = sheetNeoId!!
        val name = sheetNeoName ?: id

        ModalBottomSheet(
            onDismissRequest = {
                sheetNeoId = null
                sheetNeoName = null
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(name, style = MaterialTheme.typography.titleLarge)
                Text("Choose an action:", style = MaterialTheme.typography.bodyMedium)

                Button(
                    onClick = {
                        sheetNeoId = null
                        sheetNeoName = null
                        onPointNeo(id)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Point")
                }

                OutlinedButton(
                    onClick = {
                        // Keep sheet open or close it — your preference. Closing feels nicer.
                        sheetNeoId = null
                        sheetNeoName = null
                        onOpenNeoDetails(id)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("View NASA/JPL Details")
                }

                Spacer(Modifier.height(6.dp))
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                when (mode) {
                    ResultsMode.Planned -> "Planned Results"
                    ResultsMode.Raw -> "Raw NeoWs Results"
                },
                style = MaterialTheme.typography.titleMedium
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode == ResultsMode.Planned,
                    onClick = { onModeChange(ResultsMode.Planned) },
                    label = { Text("Planned") }
                )
                FilterChip(
                    selected = mode == ResultsMode.Raw,
                    onClick = { onModeChange(ResultsMode.Raw) },
                    label = { Text("Raw") }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        when (mode) {
            ResultsMode.Planned -> {
                if (st.planned.isEmpty()) {
                    Text("No planned results yet. Go to Planner and tap “Plan Visibility”.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(st.planned) { item ->
                            ElevatedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        sheetNeoId = item.neo.id
                                        sheetNeoName = item.neo.name
                                    }
                            ) {
                                Column(
                                    Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(item.neo.name, style = MaterialTheme.typography.titleSmall)
                                    Text("Hazardous: ${if (item.neo.isHazardous) "Yes" else "No"} | H: ${item.neo.hMagnitude ?: "—"}")
                                    Text("Closest: ${item.neo.closestApproachAu ?: "—"} AU @ ${item.neo.closestApproachLocal?.format(timeFmt) ?: "—"}")
                                    Text("Best: ${item.bestStartLocal?.format(timeFmt) ?: "—"} → ${item.bestEndLocal?.format(timeFmt) ?: "—"}")

                                    val peakAlt = item.peakAltitudeDeg?.let { "%.2f°".format(it) } ?: "—"
                                    val peakAz = item.peakAzimuthDeg?.let { "%.2f°".format(it) } ?: "—"
                                    Text("Peak: $peakAlt @ ${item.peakTimeLocal?.format(timeFmt) ?: "—"}  |  Az: $peakAz ${item.peakCardinal ?: ""}")
                                }
                            }
                        }
                    }
                }
            }

            ResultsMode.Raw -> {
                if (st.results.isEmpty()) {
                    Text("No raw results yet. Go to Planner and tap “Fetch (debug)”.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(st.results) { neo ->
                            ElevatedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        sheetNeoId = neo.id
                                        sheetNeoName = neo.name
                                    }
                            ) {
                                Column(
                                    Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(neo.name, style = MaterialTheme.typography.titleSmall)
                                    Text("Hazardous: ${if (neo.isHazardous) "Yes" else "No"} | H: ${neo.hMagnitude ?: "—"}")
                                    Text("Closest: ${neo.closestApproachAu ?: "—"} AU @ ${neo.closestApproachLocal?.format(timeFmt) ?: "—"}")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
