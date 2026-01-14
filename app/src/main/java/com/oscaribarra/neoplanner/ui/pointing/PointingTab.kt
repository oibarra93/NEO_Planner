package com.oscaribarra.neoplanner.ui.pointing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Camera pointing UI:
 * - Shows current camera Alt/Az (true)
 * - Shows target Alt/Az (true)
 * - Provides strong alignment indicators + simple instructions
 *
 * No debug telemetry shown (pitch/roll/declination/etc).
 */
@Composable
fun PointingTab(
    obsLatDeg: Double?,
    obsLonDeg: Double?,
    obsHeightMeters: Double?,
    targetAltAz: TargetAltAz?,
    onJumpToResults: () -> Unit
) {
    val context = LocalContext.current

    val sampleState = remember { mutableStateOf<OrientationTracker.OrientationSample?>(null) }
    val errorState = remember { mutableStateOf<String?>(null) }

    LaunchedEffect(obsLatDeg, obsLonDeg, obsHeightMeters) {
        errorState.value = null
        sampleState.value = null

        val lat = obsLatDeg
        val lon = obsLonDeg
        val h = obsHeightMeters

        if (lat == null || lon == null || h == null) {
            errorState.value = "Observer not available yet. Fetch location first."
            return@LaunchedEffect
        }

        val tracker = OrientationTracker(context)
        tracker.samples(
            obsLatDeg = lat,
            obsLonDeg = lon,
            obsHeightMeters = h
        ).collect { s ->
            sampleState.value = s
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Header(onJumpToResults = onJumpToResults)

        errorState.value?.let { err ->
            ElevatedCard {
                Column(Modifier.padding(12.dp)) {
                    Text("Status", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(err, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        val s = sampleState.value

        // If no target, show simple instruction.
        if (targetAltAz == null) {
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Pointing", style = MaterialTheme.typography.titleMedium)
                    Text("Select a NEO in Results, then we’ll guide you to its peak Alt/Az.")
                    if (s == null) {
                        Text("Waiting for sensors…")
                    } else {
                        val azStr = "%.1f°".format(s.azimuthDegTrue)
                        val altStr = "%.1f°".format(s.altitudeDegCamera)
                        Text("Current: Az $azStr • Alt $altStr")
                    }
                }
            }
            return@Column
        }

        // Target + live cards
        TargetAndLiveCards(
            sample = s,
            target = targetAltAz
        )

        // Guidance
        GuidanceCard(
            sample = s,
            target = targetAltAz
        )

        // Short safety / calibration hint (small + non-debug)
        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Tips", style = MaterialTheme.typography.titleMedium)
                Text(
                    "If azimuth seems off, calibrate your compass (figure-8) and keep away from metal or magnets.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "For best results: hold the phone upright and aim using the back camera.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun Header(onJumpToResults: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("Phone Pointing", style = MaterialTheme.typography.titleMedium)
            Text(
                "Aim with the back camera. Align azimuth then altitude.",
                style = MaterialTheme.typography.bodySmall
            )
        }}
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ){
        OutlinedButton(onClick = onJumpToResults) {
            Text("Back to Results")
        }
    }
}

@Composable
private fun TargetAndLiveCards(
    sample: OrientationTracker.OrientationSample?,
    target: TargetAltAz
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(modifier = Modifier.weight(1f)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Target", style = MaterialTheme.typography.titleMedium)
                Text(target.label, style = MaterialTheme.typography.bodySmall)
                Text("Az: ${"%.1f".format(target.azimuthDegTrue)}°")
                Text("Alt: ${"%.1f".format(target.altitudeDeg)}°")
            }
        }

        Card(modifier = Modifier.weight(1f)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Current", style = MaterialTheme.typography.titleMedium)
                if (sample == null) {
                    Text("Waiting…")
                } else {
                    Text("Az: ${"%.1f".format(sample.azimuthDegTrue)}°")
                    Text("Alt: ${"%.1f".format(sample.altitudeDegCamera)}°")
                }
            }
        }
    }
}

@Composable
private fun GuidanceCard(
    sample: OrientationTracker.OrientationSample?,
    target: TargetAltAz
) {
    if (sample == null) {
        ElevatedCard {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Guidance", style = MaterialTheme.typography.titleMedium)
                Text("Waiting for sensors…")
            }
        }
        return
    }

    val dAz = OrientationMath.deltaAngleDeg(sample.azimuthDegTrue, target.azimuthDegTrue)
    val dAlt = target.altitudeDeg - sample.altitudeDegCamera

    val err = OrientationMath.aimErrorDeg(
        currAzTrue = sample.azimuthDegTrue,
        currAlt = sample.altitudeDegCamera,
        targetAzTrue = target.azimuthDegTrue,
        targetAlt = target.altitudeDeg
    )

    // Thresholds you can tune
    val azTol = 3.0
    val altTol = 3.0
    val aligned = abs(dAz) < azTol && abs(dAlt) < altTol

    val statusText = if (aligned) "✅ Aligned" else OrientationMath.aimHint(dAz, dAlt)
    val statusTone = if (aligned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

    // Convert deltas to 0..1 "closeness" meters for progress bars.
    // Full scale: within 30° counts as "close"; clamp beyond that.
    val azClose = closeness01(abs(dAz), fullScaleDeg = 30.0)
    val altClose = closeness01(abs(dAlt), fullScaleDeg = 30.0)

    // Overall closeness from aim error; 0..1
    val errClose = closeness01(err, fullScaleDeg = 20.0)

    ElevatedCard {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Guidance", style = MaterialTheme.typography.titleMedium)

            // Big status pill
            Surface(
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.large
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(statusText, color = statusTone, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Turn: ${formatSignedDeg(dAz)} • Tilt: ${formatSignedDeg(dAlt)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Az closeness
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Azimuth", style = MaterialTheme.typography.bodyMedium)
                    Text("${abs(dAz).format1()}° off", style = MaterialTheme.typography.bodySmall)
                }
                LinearProgressIndicator(progress = azClose.toFloat(), modifier = Modifier.fillMaxWidth())
            }

            // Alt closeness
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Altitude", style = MaterialTheme.typography.bodyMedium)
                    Text("${abs(dAlt).format1()}° off", style = MaterialTheme.typography.bodySmall)
                }
                LinearProgressIndicator(progress = altClose.toFloat(), modifier = Modifier.fillMaxWidth())
            }

            // Overall error
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Overall", style = MaterialTheme.typography.bodyMedium)
                    Text("${err.format1()}° error", style = MaterialTheme.typography.bodySmall)
                }
                LinearProgressIndicator(progress = errClose.toFloat(), modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** closeness=1 when delta=0; closeness=0 when delta>=fullScaleDeg */
private fun closeness01(deltaDeg: Double, fullScaleDeg: Double): Double {
    val d = abs(deltaDeg)
    val t = 1.0 - (d / fullScaleDeg)
    return min(1.0, max(0.0, t))
}

private fun formatSignedDeg(v: Double): String {
    val sign = if (v >= 0) "+" else "−"
    return "$sign${abs(v).format1()}°"
}

private fun Double.format1(): String = "%.1f".format(this)
