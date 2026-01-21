package com.oscaribarra.neoplanner.ui.tabs

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.oscaribarra.neoplanner.ui.NeoPlannerUiState
import java.time.format.DateTimeFormatter

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun SettingsSheet(
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
