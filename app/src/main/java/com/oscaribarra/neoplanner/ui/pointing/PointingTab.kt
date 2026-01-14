package com.oscaribarra.neoplanner.ui.pointing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
    onJumpToResults: () -> Unit
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
        horizontalArrangement = Arrangement.Absolute.Left
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
        horizontalArrangement = Arrangement.Absolute.Right
    ) {
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

    // Centered bar tuning:
    // - rangeDeg: full-scale offset in either direction (beyond this clamps to ends)
    // - tolDeg: half-width of the "good" zone centered at 0
    val azRangeDeg = 30.0
    val altRangeDeg = 30.0
    val azTolDeg = azTol
    val altTolDeg = altTol

    // Overall: use a smaller range so it feels more responsive
    val errRangeDeg = 20.0
    val errTolDeg = 3.0

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

            CenteredGaugeRow(
                label = "Azimuth",
                deltaDeg = dAz,
                rangeDeg = azRangeDeg,
                tolDeg = azTolDeg,
                trailing = "${abs(dAz).format1()}° off"
            )

            CenteredGaugeRow(
                label = "Altitude",
                deltaDeg = dAlt,
                rangeDeg = altRangeDeg,
                tolDeg = altTolDeg,
                trailing = "${abs(dAlt).format1()}° off"
            )

            // Overall uses aim error (always positive)
            CenteredGaugeRow(
                label = "Overall",
                deltaDeg = err,          // treat as "how far from center" to the right
                rangeDeg = errRangeDeg,
                tolDeg = errTolDeg,
                trailing = "${err.format1()}° error",
                // For error gauge: show center as "0", but error is magnitude only.
                // We'll push marker right only (0.5..1.0) to avoid implying direction.
                onlyPositive = true
            )
        }
    }
}

@Composable
private fun CenteredGaugeRow(
    label: String,
    deltaDeg: Double,
    rangeDeg: Double,
    tolDeg: Double,
    trailing: String,
    onlyPositive: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(trailing, style = MaterialTheme.typography.bodySmall)
        }

        CenteredIndicatorBar(
            valueDeg = deltaDeg,
            rangeDeg = rangeDeg,
            tolDeg = tolDeg,
            onlyPositive = onlyPositive,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * A centered bar where:
 * - center (x=0.5) is the target
 * - marker moves left/right based on signed valueDeg
 * - a tolerance window is drawn around the center
 *
 * If onlyPositive=true, we treat valueDeg as a magnitude and only move marker from center to the right.
 */
@Composable
private fun CenteredIndicatorBar(
    valueDeg: Double,
    rangeDeg: Double,
    tolDeg: Double,
    onlyPositive: Boolean,
    modifier: Modifier = Modifier
) {
    val v = if (onlyPositive) abs(valueDeg) else valueDeg

    // Map degrees to [0..1] with center at 0.5
    val raw = if (onlyPositive) {
        // 0..range maps to 0.5..1.0
        0.5 + (clamp(v, 0.0, rangeDeg) / (2.0 * rangeDeg))
    } else {
        0.5 + (clamp(v, -rangeDeg, rangeDeg) / (2.0 * rangeDeg))
    }
    val pos = clamp(raw, 0.0, 1.0).toFloat()

    val inTol = if (onlyPositive) abs(v) <= tolDeg else abs(v) <= tolDeg

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val goodZoneColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    val centerLineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    val markerColor = if (inTol) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val markerStroke = MaterialTheme.colorScheme.onSurface

    Canvas(
        modifier = modifier
            .height(18.dp)
    ) {
        val w = size.width
        val h = size.height

        // Track
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, 0f),
            size = Size(w, h),
            cornerRadius = CornerRadius(h / 2f, h / 2f)
        )

        // Good zone band around center
        val tol = clamp(tolDeg, 0.0, rangeDeg)
        val halfBand = (tol / (2.0 * rangeDeg)).toFloat() * w
        val cx = 0.5f * w
        val bandLeft = (cx - halfBand).coerceAtLeast(0f)
        val bandRight = (cx + halfBand).coerceAtMost(w)

        drawRoundRect(
            color = goodZoneColor,
            topLeft = Offset(bandLeft, 0f),
            size = Size(bandRight - bandLeft, h),
            cornerRadius = CornerRadius(h / 2f, h / 2f)
        )

        // Center line
        drawLine(
            color = centerLineColor,
            start = Offset(cx, 2f),
            end = Offset(cx, h - 2f),
            strokeWidth = 3f
        )

        // Marker dot
        val mx = pos * w
        val r = (h * 0.42f)
        drawCircle(
            color = markerColor,
            radius = r,
            center = Offset(mx, h / 2f)
        )
        // subtle outline for contrast
        drawCircle(
            color = markerStroke.copy(alpha = 0.35f),
            radius = r,
            center = Offset(mx, h / 2f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
        )
    }
}

private fun clamp(v: Double, lo: Double, hi: Double): Double = min(hi, max(lo, v))

private fun formatSignedDeg(v: Double): String {
    val sign = if (v >= 0) "+" else "−"
    return "$sign${abs(v).format1()}°"
}

private fun Double.format1(): String = "%.1f".format(this)
