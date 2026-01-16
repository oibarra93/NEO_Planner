package com.oscaribarra.neoplanner.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.oscaribarra.neoplanner.planner.PlanRequest
import com.oscaribarra.neoplanner.planner.PlannedNeoResult
import com.oscaribarra.neoplanner.ui.camera.CameraTab
import com.oscaribarra.neoplanner.ui.pointing.PointingTab
import com.oscaribarra.neoplanner.ui.pointing.TargetAltAz
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private enum class ResultsMode { Planned, Raw }
private enum class MainTab { Planner, Results, Pointing, Camera }
private enum class PlannedSort { BestPeakAlt, DistanceAu, PeakTime }

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeoPlannerScreen(vm: NeoPlannerViewModel) {
    val st by vm.state.collectAsState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val permissionLauncherLocation = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted: Boolean -> vm.setHasLocationPermission(granted) }

    // Kept for later use if you decide to request permission from UI.
    // CameraTab can also just fail gracefully and show an error if permission is missing.
    val permissionLauncherCamera = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _: Boolean -> /* no-op */ }

    val timeFmt = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z") }

    var tab by remember { mutableStateOf(MainTab.Planner) }
    var mode by remember { mutableStateOf(ResultsMode.Planned) }
    var plannedSort by remember { mutableStateOf(PlannedSort.BestPeakAlt) }

    // ✅ Settings sheet state
    var showSettings by remember { mutableStateOf(false) }

    // Selected planned result (if any)
    val selectedPlanned = remember(st.planned, st.selectedNeoId) {
        st.planned.firstOrNull { it.neo.id == st.selectedNeoId }
    }

    // Build target for Pointing/Camera tabs from selected planned peak Alt/Az
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

    // ✅ Settings as a modal bottom sheet
    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false }
        ) {
            SettingsTab(
                st = st,
                timeFmt = timeFmt,
                onRequestPermission = { permissionLauncherLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                onSaveKey = vm::saveApiKey,
                onUpdateKey = vm::updateApiKey
            )
            Spacer(Modifier.height(12.dp))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NEO Telescope Planner") },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PrimaryTabRow(selectedTabIndex = tab.ordinal) {
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
                    onClick = { tab = MainTab.Pointing },
                    text = { Text("Pointing") }
                )
                Tab(
                    selected = tab == MainTab.Camera,
                    onClick = { tab = MainTab.Camera },
                    text = { Text("Camera") }
                )
            }

            when (tab) {
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
                    plannedSort = plannedSort,
                    onPlannedSortChange = { plannedSort = it },
                    onOpenPointing = { id ->
                        vm.selectNeo(id)
                        tab = MainTab.Pointing
                    },
                    onOpenCamera = { id ->
                        vm.selectNeo(id)
                        // Optional: request permission as you enter Camera tab
                        permissionLauncherCamera.launch(Manifest.permission.CAMERA)
                        tab = MainTab.Camera
                    },
                    onOpenNeoDetails = { id ->
                        uriHandler.openUri("https://ssd.jpl.nasa.gov/tools/sbdb_lookup.html#/?sstr=$id")
                    }
                )

                MainTab.Pointing -> {
                    val obs = st.observer
                    PointingTab(
                        obsLatDeg = obs?.latitudeDeg,
                        obsLonDeg = obs?.longitudeDeg,
                        obsHeightMeters = obs?.elevationMeters,
                        targetAltAz = pointingTarget,
                        onJumpToResults = { tab = MainTab.Results },
                        onOpenCameraTab = {
                            permissionLauncherCamera.launch(Manifest.permission.CAMERA)
                            tab = MainTab.Camera
                        }
                    )
                }

                MainTab.Camera -> {
                    val obs = st.observer
                    CameraTab(
                        obsLatDeg = obs?.latitudeDeg,
                        obsLonDeg = obs?.longitudeDeg,
                        obsHeightMeters = obs?.elevationMeters,
                        targetAltAz = pointingTarget,
                        onBackToResults = { tab = MainTab.Results }
                    )
                }
            }
        }
    }
}

/* -------------------- Settings UI (unchanged) -------------------- */

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
    val uriHandler = LocalUriHandler.current

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

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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

/* -------------------- Planner / Results (same as your current) -------------------- */

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun PlannerTab(
    st: NeoPlannerUiState,
    timeFmt: DateTimeFormatter,
    selected: PlannedNeoResult?,
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
                    Button(onClick = onPlan, enabled = !st.isBusy) { Text("Plan Visibility") }
                }
            }
        }

        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Pointing Info", style = MaterialTheme.typography.titleMedium)

                if (selected == null) {
                    Text("Go to Results tab and choose a planned result.")
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

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultsTab(
    st: NeoPlannerUiState,
    timeFmt: DateTimeFormatter,
    mode: ResultsMode,
    onModeChange: (ResultsMode) -> Unit,
    plannedSort: PlannedSort,
    onPlannedSortChange: (PlannedSort) -> Unit,
    onOpenPointing: (String) -> Unit,
    onOpenCamera: (String) -> Unit,
    onOpenNeoDetails: (String) -> Unit
) {
    var sheetNeoId by remember { mutableStateOf<String?>(null) }
    var sheetNeoName by remember { mutableStateOf<String?>(null) }

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
                        onOpenPointing(id)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Point (Sensors)") }

                OutlinedButton(
                    onClick = {
                        sheetNeoId = null
                        sheetNeoName = null
                        onOpenCamera(id)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Open Camera View") }

                OutlinedButton(
                    onClick = { onOpenNeoDetails(id) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("View NASA/JPL Details") }

                Spacer(Modifier.height(6.dp))
            }
        }
    }

    val plannedSorted = remember(st.planned, plannedSort) {
        when (plannedSort) {
            PlannedSort.BestPeakAlt ->
                st.planned.sortedWith(
                    compareByDescending<PlannedNeoResult> { it.peakAltitudeDeg ?: Double.NEGATIVE_INFINITY }
                        .thenBy { it.neo.name }
                )

            PlannedSort.DistanceAu ->
                st.planned.sortedWith(
                    compareBy<PlannedNeoResult> { it.neo.closestApproachAu ?: Double.POSITIVE_INFINITY }
                        .thenBy { it.neo.name }
                )

            PlannedSort.PeakTime ->
                st.planned.sortedWith(
                    compareBy<PlannedNeoResult> { it.peakTimeLocal ?: ZonedDateTime.now().plusYears(100) }
                        .thenBy { it.neo.name }
                )
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

        if (mode == ResultsMode.Planned && st.planned.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Sort:", style = MaterialTheme.typography.bodyMedium)
                FilterChip(
                    selected = plannedSort == PlannedSort.BestPeakAlt,
                    onClick = { onPlannedSortChange(PlannedSort.BestPeakAlt) },
                    label = { Text("Best") }
                )
                FilterChip(
                    selected = plannedSort == PlannedSort.DistanceAu,
                    onClick = { onPlannedSortChange(PlannedSort.DistanceAu) },
                    label = { Text("Distance") }
                )
                FilterChip(
                    selected = plannedSort == PlannedSort.PeakTime,
                    onClick = { onPlannedSortChange(PlannedSort.PeakTime) },
                    label = { Text("Peak time") }
                )
            }

            Spacer(Modifier.height(8.dp))
        }

        when (mode) {
            ResultsMode.Planned -> {
                if (st.planned.isEmpty()) {
                    Text("No planned results yet. Go to Planner and tap “Plan Visibility”.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(plannedSorted) { item ->
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
