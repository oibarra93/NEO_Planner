package com.oscaribarra.neoplanner.ui.tabs

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.oscaribarra.neoplanner.planner.PlannedNeoResult
import com.oscaribarra.neoplanner.ui.NeoPlannerUiState
import com.oscaribarra.neoplanner.ui.pointing.TargetAltAz
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.clickable // <-- add this

// Enums for tab state and sort order
enum class ResultsMode { Planned, Raw }
enum class PlannedSort { BestPeakAlt, DistanceAu, PeakTime }

// Map of planet command IDs to human‑friendly names
private val PLANET_NAMES: Map<Int, String> = mapOf(
    199 to "Mercury",
    299 to "Venus",
    499 to "Mars",
    599 to "Jupiter",
    699 to "Saturn",
    799 to "Uranus",
    899 to "Neptune",
    999 to "Pluto"
)

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsTab(
    st: NeoPlannerUiState,
    timeFmt: DateTimeFormatter,
    mode: ResultsMode,
    onModeChange: (ResultsMode) -> Unit,
    plannedSort: PlannedSort,
    onPlannedSortChange: (PlannedSort) -> Unit,
    onOpenPointing: (String) -> Unit,
    onOpenCamera: (String) -> Unit,
    onOpenNeoDetails: (String) -> Unit,
    selectedPlanetCommand: Int?,        // <— NEW
    onSelectPlanet: (Int?) -> Unit
) {
    // Bottom sheet selection (NEOs + Moon + Planets)
    var sheetNeoId by remember { mutableStateOf<String?>(null) }
    var sheetNeoName by remember { mutableStateOf<String?>(null) }

    // Local planet selection for the dropdown (keeps this file self-contained)
    //var selectedPlanetCommand by remember { mutableStateOf<Int?>(null) }

    // Show bottom sheet when a NEO/Moon/planet is selected
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

                // NASA/JPL details only makes sense for NEOs (not Moon/planets in this UI)
                if (id != "MOON" && !id.startsWith("PLANET_")) {
                    OutlinedButton(
                        onClick = { onOpenNeoDetails(id) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("View NASA/JPL Details") }
                }

                Spacer(Modifier.height(6.dp))
            }
        }
    }

    // Sort the planned results based on current sort selection
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

    // Build a list of planets above the horizon (altitudeDeg > 0)
    val planetsInView: List<Pair<Int, TargetAltAz>> = remember(st.planetTargets) {
        val out = ArrayList<Pair<Int, TargetAltAz>>()
        for ((cmd, altAz) in st.planetTargets) {
            if (PLANET_NAMES.containsKey(cmd) && altAz.altitudeDeg > 0.0) {
                out.add(cmd to altAz)
            }
        }
        out.sortBy { (cmd, _) -> PLANET_NAMES[cmd] ?: cmd.toString() }
        out
    }

    // Reset selected planet if it drops below horizon or is missing
    LaunchedEffect(planetsInView, selectedPlanetCommand) {
        val sel = selectedPlanetCommand ?: return@LaunchedEffect
        val stillThere = planetsInView.any { it.first == sel }
        if (!stillThere) onSelectPlanet(null)

    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Header: Title + mode chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
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

        // Moon card
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    sheetNeoId = "MOON"
                    sheetNeoName = "Moon"
                }
        ) {
            Column(
                Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Moon", style = MaterialTheme.typography.titleSmall)
                val mt = st.moonTarget
                if (mt != null) {
                    Text(mt.label, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Alt: ${"%.2f".format(mt.altitudeDeg)}°  |  Az: ${"%.2f".format(mt.azimuthDegTrue)}°",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    val err = st.moonError
                    Text(
                        err ?: "Fetching Moon… (requires observer/location)",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (err != null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Planets dropdown (only enabled if there are planets above the horizon)
        PlanetDropdown(
            planetsInView = planetsInView,
            selectedCommand = selectedPlanetCommand,
            onSelect = { cmd -> onSelectPlanet(cmd) },
            modifier = Modifier.fillMaxWidth()
        )

        // Selected planet card (shows details if a planet is chosen)
        val selectedPlanetAltAz: TargetAltAz? = remember(selectedPlanetCommand, st.planetTargets) {
            val cmd = selectedPlanetCommand ?: return@remember null
            st.planetTargets[cmd]
        }
        if (selectedPlanetCommand != null && selectedPlanetAltAz != null) {
            Spacer(Modifier.height(8.dp))
            val cmd = selectedPlanetCommand!!
            val name = PLANET_NAMES[cmd] ?: "Planet $cmd"
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        sheetNeoId = "PLANET_$cmd"
                        sheetNeoName = name
                    }
            ) {
                Column(
                    Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(name, style = MaterialTheme.typography.titleSmall)
                    Text(selectedPlanetAltAz.label, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Alt: ${"%.2f".format(selectedPlanetAltAz.altitudeDeg)}°  |  Az: ${"%.2f".format(selectedPlanetAltAz.azimuthDegTrue)}°",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Sort chips for planned results
        if (mode == ResultsMode.Planned && st.planned.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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

        // Results list depending on mode
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

/**
 * A composable displaying a read‑only dropdown for selecting a planet in view.  Uses
 * ExposedDropdownMenuBox/ExposedDropdownMenu from Material3 to properly anchor the
 * menu to the text field.  The dropdown is disabled if there are no planets above
 * the horizon.  Selecting an entry invokes [onSelect] with the planet’s command ID
 * (or null for “None”).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanetDropdown(
    planetsInView: List<Pair<Int, TargetAltAz>>,
    selectedCommand: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    // Determine the display label based on selection and visibility
    val label = when {
        planetsInView.isEmpty() -> "No planets in view"
        selectedCommand == null -> "Select planet (in view)"
        else -> PLANET_NAMES[selectedCommand] ?: "Planet $selectedCommand"
    }
    // Wrap field and menu in ExposedDropdownMenuBox for proper anchoring
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            enabled = planetsInView.isNotEmpty(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // Always provide a “None” option
            DropdownMenuItem(
                text = { Text("None") },
                onClick = {
                    onSelect(null)
                    expanded = false
                }
            )
            // List each planet above the horizon
            for ((cmd, altAz) in planetsInView) {
                val name = PLANET_NAMES[cmd] ?: "Planet $cmd"
                val sub = "Az ${"%.1f".format(altAz.azimuthDegTrue)}° • Alt ${"%.1f".format(altAz.altitudeDeg)}°"
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(name)
                            Text(sub, style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    onClick = {
                        onSelect(cmd)
                        expanded = false
                    }
                )
            }
        }
    }
}