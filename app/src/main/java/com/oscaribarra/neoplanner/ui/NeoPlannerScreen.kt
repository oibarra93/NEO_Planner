package com.oscaribarra.neoplanner.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.oscaribarra.neoplanner.planner.PlanRequest
import com.oscaribarra.neoplanner.ui.camera.CameraTab
import com.oscaribarra.neoplanner.ui.pointing.PointingTab
import com.oscaribarra.neoplanner.ui.pointing.TargetAltAz
import com.oscaribarra.neoplanner.ui.tabs.PlannerTab
import com.oscaribarra.neoplanner.ui.tabs.PlannedSort
import com.oscaribarra.neoplanner.ui.tabs.ResultsMode
import com.oscaribarra.neoplanner.ui.tabs.ResultsTab
import com.oscaribarra.neoplanner.ui.tabs.SettingsSheet
import java.time.format.DateTimeFormatter

private enum class MainTab { Planner, Results, Pointing, Camera }

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeoPlannerScreen(vm: NeoPlannerViewModel) {
    val st by vm.state.collectAsState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val permissionLauncherLocation = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        vm.setHasLocationPermission(granted)
    }

    val permissionLauncherCamera = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _: Boolean -> /* no-op */ }

    val timeFmt = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z") }

    var tab by remember { mutableStateOf(MainTab.Planner) }
    var mode by remember { mutableStateOf(ResultsMode.Planned) }
    var plannedSort by remember { mutableStateOf(PlannedSort.BestPeakAlt) }
    var showSettings by remember { mutableStateOf(false) }

    // Auto-refresh Moon when observer becomes available
    LaunchedEffect(st.observer?.timeZoneId, st.observer?.latitudeDeg, st.observer?.longitudeDeg) {
        vm.refreshMoonIfPossible()
    }

    // Selected planned result (if any)
    val selectedPlanned = remember(st.planned, st.selectedNeoId) {
        st.planned.firstOrNull { it.neo.id == st.selectedNeoId }
    }

    // Selected planet target (if any) from ViewModel state
    val selectedPlanetTarget: TargetAltAz? = remember(st.selectedPlanetCommand, st.planetTargets) {
        val cmd = st.selectedPlanetCommand ?: return@remember null
        st.planetTargets[cmd]
    }

    // Build target for Pointing/Camera tabs
    val pointingTarget: TargetAltAz? = remember(
        st.selectedNeoId, selectedPlanned, st.moonTarget,
        st.selectedPlanetCommand, selectedPlanetTarget
    ) {
        when {
            st.selectedNeoId == "MOON" -> st.moonTarget
            selectedPlanetTarget != null -> selectedPlanetTarget
            else -> {
                val p = selectedPlanned ?: return@remember null
                val alt = p.peakAltitudeDeg ?: return@remember null
                val az = p.peakAzimuthDeg ?: return@remember null
                val whenStr = p.peakTimeLocal?.format(timeFmt) ?: "peak time"
                TargetAltAz("${p.neo.name} • $whenStr", alt, az)
            }
        }
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }) {
            SettingsSheet(
                st = st,
                timeFmt = timeFmt,
                onRequestPermission = {
                    permissionLauncherLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                },
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
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
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
                        permissionLauncherCamera.launch(Manifest.permission.CAMERA)
                        tab = MainTab.Camera
                    },
                    onOpenNeoDetails = { id ->
                        uriHandler.openUri("https://ssd.jpl.nasa.gov/tools/sbdb_lookup.html#/?sstr=$id")
                    },
                    selectedPlanetCommand = st.selectedPlanetCommand,      // <-- pass ViewModel state down
                    onSelectPlanet = { cmd -> vm.selectPlanet(cmd) }       // <-- update ViewModel when user picks a planet
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