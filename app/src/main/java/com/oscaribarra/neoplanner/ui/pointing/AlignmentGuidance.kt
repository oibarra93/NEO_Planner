package com.oscaribarra.neoplanner.ui.pointing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Shared alignment UI for both:
 * - Sensor-only pointing screen
 * - Camera preview screen
 */
@Composable
fun AlignmentGuidanceCard(
    sample: OrientationTracker.OrientationSample?,
    target: TargetAltAz,
    modifier: Modifier = Modifier
) {
    if (sample == null) {
        ElevatedCard(modifier) {
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

    // 0..1: 1 is centered (perfect), 0 is far off
    val azClose = closeness01(abs(dAz), fullScaleDeg = 30.0)
    val altClose = closeness01(abs(dAlt), fullScaleDeg = 30.0)
    val errClose = closeness01(err, fullScaleDeg = 20.0)

    ElevatedCard(modifier) {
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
                }
            }

            // These are “centered = best” meters (visual closeness).
            MetricBar(
                title = "Azimuth",
                offText = "${abs(dAz).format1()}° off",
                progress01 = azClose
            )
            MetricBar(
                title = "Altitude",
                offText = "${abs(dAlt).format1()}° off",
                progress01 = altClose
            )
            MetricBar(
                title = "Overall",
                offText = "${err.format1()}° error",
                progress01 = errClose
            )
        }
    }
}

@Composable
private fun MetricBar(
    title: String,
    offText: String,
    progress01: Double
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(offText, style = MaterialTheme.typography.bodySmall)
        }
        LinearProgressIndicator(progress = progress01.toFloat(), modifier = Modifier.fillMaxWidth())
    }
}

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
