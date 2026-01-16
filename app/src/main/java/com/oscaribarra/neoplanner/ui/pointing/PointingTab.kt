package com.oscaribarra.neoplanner.ui.pointing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
    onJumpToResults: () -> Unit,
    onOpenCameraTab: (() -> Unit)? = null
) {
    val context = LocalContext.current

    val sampleState = remember { mutableStateOf<OrientationTracker.OrientationSample?>(null) }
    val errorState = remember { mutableStateOf<String?>(null) }

    LaunchedEffect(obsLatDeg, obsLonDeg, obsHeightMeters) {
        errorState.value = null
        sampleState.value = null

        if (obsLatDeg == null || obsLonDeg == null || obsHeightMeters == null) {
            errorState.value = "Observer not available yet. Fetch location first."
            return@LaunchedEffect
        }

        val tracker = OrientationTracker(context)
        tracker.samples(
            obsLatDeg = obsLatDeg,
            obsLonDeg = obsLonDeg,
            obsHeightMeters = obsHeightMeters
        ).collect { s ->
            sampleState.value = s
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // CONTENT (top)
        Header()

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

            Spacer(modifier = Modifier.weight(1f))

            BottomActions(
                onBack = onJumpToResults,
                onOpenCamera = onOpenCameraTab
            )
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

        // PUSH ACTIONS TO BOTTOM
        Spacer(modifier = Modifier.weight(1f))

        BottomActions(
            onBack = onJumpToResults,
            onOpenCamera = onOpenCameraTab
        )
    }
}

@Composable
private fun Header() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Phone Pointing", style = MaterialTheme.typography.titleMedium)
        Text(
            "Aim with the back camera. Align azimuth then altitude.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun BottomActions(
    onBack: () -> Unit,
    onOpenCamera: (() -> Unit)?
) {
    // A thumb-friendly bottom bar
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f)
            ) {
                Text("Back to Results")
            }

            if (onOpenCamera != null) {
                Button(
                    onClick = onOpenCamera,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Open Camera")
                }
            }
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

    val azTol = 3.0
    val altTol = 3.0
    val aligned = abs(dAz) < azTol && abs(dAlt) < altTol

    val statusText = if (aligned) "✅ Aligned" else OrientationMath.aimHint(dAz, dAlt)
    val statusTone = if (aligned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

    ElevatedCard {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Guidance", style = MaterialTheme.typography.titleMedium)

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
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Error: ${err.format1()}°",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatSignedDeg(v: Double): String {
    val sign = if (v >= 0) "+" else "−"
    return "$sign${abs(v).format1()}°"
}

private fun Double.format1(): String = "%.1f".format(this)

/** Included if you decide to reintroduce any normalized/clamped meters later. */
private fun clamp01(v: Double): Double = min(1.0, max(0.0, v))
