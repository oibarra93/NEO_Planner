package com.oscaribarra.neoplanner.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter

@Composable
fun NeoPlannerScreen(vm: NeoPlannerViewModel) {
    val st by vm.state.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        vm.setHasLocationPermission(granted)
    }

    // A simple time formatter for display
    val timeFmt = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z") }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("NEO Telescope Planner (Module 2)", style = MaterialTheme.typography.headlineSmall)

        // API Key Card
        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("API Key", style = MaterialTheme.typography.titleMedium)

                OutlinedTextField(
                    value = st.apiKey,
                    onValueChange = vm::updateApiKey,
                    label = { Text("NASA NeoWs API key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = vm::saveApiKey, enabled = !st.isBusy) {
                        Text("Save")
                    }
                    if (st.apiKeySaved) {
                        Text("Saved ✓", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        // Location Card
        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Location", style = MaterialTheme.typography.titleMedium)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                        enabled = !st.isBusy
                    ) { Text("Request Location Permission") }

                    Text(if (st.hasLocationPermission) "Granted" else "Not granted")
                }

                val obs = st.observer
                if (obs != null) {
                    Text("Observer: lat=${"%.5f".format(obs.latitudeDeg)} lon=${"%.5f".format(obs.longitudeDeg)} elev=${"%.0f".format(obs.elevationMeters)}m")
                    Text("Timezone: ${obs.timeZoneId}")
                } else {
                    Text("Observer: (not resolved yet — will resolve on Fetch)")
                }

                st.nowLocal?.let { now ->
                    Text("Now (local): ${now.format(timeFmt)}")
                }
            }
        }

        // Controls Card
        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Fetch Controls", style = MaterialTheme.typography.titleMedium)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = st.hoursAhead.toString(),
                        onValueChange = vm::updateHoursAhead,
                        label = { Text("Hours ahead") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = st.maxNeos.toString(),
                        onValueChange = vm::updateMaxNeos,
                        label = { Text("Max NEOs") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Button(onClick = vm::fetchNeosNow, enabled = !st.isBusy) {
                    Text(if (st.isBusy) "Fetching…" else "Fetch NEOs (feed + details)")
                }

                st.error?.let { err ->
                    Text(err, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // Results List
        Card(Modifier.fillMaxSize()) {
            Column(Modifier.padding(12.dp)) {
                Text("Results (NeoWs detail + orbit elements)", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                if (st.results.isEmpty()) {
                    Text("No results yet.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(st.results) { neo ->
                            ElevatedCard {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(neo.name, style = MaterialTheme.typography.titleSmall)
                                    Text("Hazardous: ${if (neo.isHazardous) "Yes" else "No"} | H: ${neo.hMagnitude ?: "—"}")
                                    Text("Closest: ${neo.closestApproachAu ?: "—"} AU @ ${neo.closestApproachLocal?.format(timeFmt) ?: "—"}")

                                    val o = neo.orbit
                                    Text("Orbit: epochJD=${o.epochJd}")
                                    Text("a=${o.aAu} AU, e=${o.e}, i=${o.iDeg}°")
                                    Text("Ω=${o.raanDeg}°, ω=${o.argPeriDeg}°, M=${o.meanAnomDeg}°")
                                    Text("n=${o.meanMotionDegPerDay ?: "—"} deg/day")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
