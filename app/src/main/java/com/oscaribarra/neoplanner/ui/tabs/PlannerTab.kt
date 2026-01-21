package com.oscaribarra.neoplanner.ui.tabs

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.oscaribarra.neoplanner.planner.PlannedNeoResult
import com.oscaribarra.neoplanner.ui.NeoPlannerUiState
import java.time.format.DateTimeFormatter

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun PlannerTab(
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
