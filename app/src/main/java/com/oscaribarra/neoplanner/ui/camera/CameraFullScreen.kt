package com.oscaribarra.neoplanner.ui.camera

import android.content.ContentValues
import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.view.MotionEvent
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.oscaribarra.neoplanner.ui.pointing.OrientationMath
import com.oscaribarra.neoplanner.ui.pointing.OrientationTracker
import com.oscaribarra.neoplanner.ui.pointing.TargetAltAz
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.math.abs

private enum class ControlsTab { Zoom, Exposure, Focus, ISO }
private enum class SidePanel { None, Align, Controls }

/**
 * Fullscreen camera overlay (Dialog):
 * - Floating shutter always visible bottom-center
 * - ONLY ONE side panel open at a time (Align OR Controls)
 * - Each panel is scrollable to prevent overlapping text
 * - Each panel has "<" button to collapse ALL panels
 */
@Composable
fun CameraFullScreen(
    targetAltAz: TargetAltAz?,
    orientationSample: MutableState<OrientationTracker.OrientationSample?>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val previewViewState = remember { mutableStateOf<PreviewView?>(null) }
    val cameraState = remember { mutableStateOf<Camera?>(null) }
    val imageCaptureState = remember { mutableStateOf<ImageCapture?>(null) }

    val status = remember { mutableStateOf<String?>(null) }
    val error = remember { mutableStateOf<String?>(null) }
    val isCapturing = remember { mutableStateOf(false) }

    // ✅ One-panel-at-a-time state
    val openPanel = remember { mutableStateOf(SidePanel.Align) } // default open
    val controlsTab = remember { mutableStateOf(ControlsTab.Zoom) }

    // Auto-collapse after inactivity (collapses into icons: None)
    val lastInteractionMs = remember { mutableStateOf(System.currentTimeMillis()) }
    fun pingInteraction() { lastInteractionMs.value = System.currentTimeMillis() }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            val idleMs = System.currentTimeMillis() - lastInteractionMs.value
            if (idleMs > 10000 && openPanel.value != SidePanel.None) {
                openPanel.value = SidePanel.None
            }
        }
    }

    // Controls state (auto-apply)
    val zoom = remember { mutableFloatStateOf(1f) }
    val exposureComp = remember { mutableIntStateOf(0) }
    val autoFocus = remember { mutableStateOf(true) }
    val manualFocusDist = remember { mutableFloatStateOf(0f) }
    val iso = remember { mutableIntStateOf(200) }
    val exposureUs = remember { mutableIntStateOf(10_000) }

    // Bind camera when preview exists
    DisposableEffect(previewViewState.value, lifecycleOwner) {
        val pv = previewViewState.value ?: return@DisposableEffect onDispose { }
        val executor = ContextCompat.getMainExecutor(context)
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null

        providerFuture.addListener({
            try {
                provider = providerFuture.get()

                val preview = Preview.Builder().build().also { p ->
                    p.setSurfaceProvider(pv.surfaceProvider)
                }

                val cap = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                provider?.unbindAll()
                val cam = provider!!.bindToLifecycle(
                    lifecycleOwner,
                    androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    cap
                )

                cameraState.value = cam
                imageCaptureState.value = cap

                cam.cameraInfo.zoomState.value?.let { zs -> zoom.floatValue = zs.zoomRatio }
                exposureComp.intValue = cam.cameraInfo.exposureState.exposureCompensationIndex
                applyAutoFocusMode(cam, enabled = autoFocus.value)

                error.value = null
                status.value = null
            } catch (t: Throwable) {
                Log.e("CameraFullScreen", "Failed to bind camera", t)
                error.value = "Camera failed: ${t.message ?: t.javaClass.simpleName}"
            }
        }, executor)

        onDispose {
            try { provider?.unbindAll() } catch (_: Throwable) { }
        }
    }

    // Auto-apply zoom
    LaunchedEffect(Unit) {
        snapshotFlow { zoom.floatValue }.collect { z ->
            val cam = cameraState.value ?: return@collect
            val zs = cam.cameraInfo.zoomState.value ?: return@collect
            val clamped = z.coerceIn(zs.minZoomRatio, zs.maxZoomRatio)
            if (clamped != z) zoom.floatValue = clamped
            cam.cameraControl.setZoomRatio(clamped)
        }
    }

    // Auto-apply exposure comp
    LaunchedEffect(Unit) {
        snapshotFlow { exposureComp.intValue }.collect { idx ->
            val cam = cameraState.value ?: return@collect
            val expState = cam.cameraInfo.exposureState
            if (!expState.isExposureCompensationSupported) return@collect
            val r = expState.exposureCompensationRange
            val clamped = idx.coerceIn(r.lower, r.upper)
            if (clamped != idx) exposureComp.intValue = clamped
            cam.cameraControl.setExposureCompensationIndex(clamped)
        }
    }

    // Auto-apply autofocus toggle
    LaunchedEffect(Unit) {
        snapshotFlow { autoFocus.value }.collect { enabled ->
            applyAutoFocusMode(cameraState.value, enabled = enabled)
            if (enabled) manualFocusDist.floatValue = 0f
        }
    }

    // Auto-apply manual focus
    LaunchedEffect(Unit) {
        snapshotFlow { manualFocusDist.floatValue to autoFocus.value }.collect { (dist, afOn) ->
            if (afOn) return@collect
            applyManualFocusBestEffort(cameraState.value, dist)
        }
    }

    // Auto-apply ISO + exposure time (best-effort)
    LaunchedEffect(Unit) {
        snapshotFlow { Triple(iso.intValue, exposureUs.intValue, autoFocus.value) }.collect { (isoVal, expUs, _) ->
            applyIsoExposureBestEffort(cameraState.value, isoVal, expUs.toLong())
        }
    }

    val alignText by remember(targetAltAz, orientationSample.value) {
        derivedStateOf {
            val t = targetAltAz
            val s = orientationSample.value
            if (t == null || s == null) return@derivedStateOf "Select a target in Results."
            val dAz = OrientationMath.deltaAngleDeg(s.azimuthDegTrue, t.azimuthDegTrue)
            val dAlt = t.altitudeDeg - s.altitudeDegCamera
            val aligned = abs(dAz) < 3.0 && abs(dAlt) < 3.0
            if (aligned) "✅ Aligned" else OrientationMath.aimHint(dAz, dAlt)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            // ✅ Preview full screen
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        setOnTouchListener { _, event ->
                            if (event.action == MotionEvent.ACTION_UP) {
                                pingInteraction()
                                val cam = cameraState.value
                                if (cam != null) {
                                    val factory: MeteringPointFactory = meteringPointFactory
                                    val point = factory.createPoint(event.x, event.y)
                                    val action = FocusMeteringAction.Builder(point).build()
                                    cam.cameraControl.startFocusAndMetering(action)
                                    status.value = "Focusing…"
                                }
                            }
                            true
                        }
                    }.also { pv -> previewViewState.value = pv }
                }
            )

            // Top bar (minimal)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Full Screen", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    error.value?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    status.value?.let { Text(it, color = Color.White, style = MaterialTheme.typography.bodySmall) }
                }
                TextButton(onClick = onDismiss) { Text("Done") }
            }

            // Left icon (Align)
            if (openPanel.value != SidePanel.Align) {
                FloatingSideIcon(
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp),
                    icon = Icons.Default.CenterFocusStrong,
                    contentDesc = "Open Align",
                    onClick = {
                        openPanel.value = SidePanel.Align
                        pingInteraction()
                    }
                )
            }

            // Right icon (Controls)
            if (openPanel.value != SidePanel.Controls) {
                FloatingSideIcon(
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp),
                    icon = Icons.Default.Tune,
                    contentDesc = "Open Controls",
                    onClick = {
                        openPanel.value = SidePanel.Controls
                        pingInteraction()
                    }
                )
            }

            // ALIGN PANEL
            if (openPanel.value == SidePanel.Align) {
                SidePanelCard(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 10.dp)
                        .wrapContentHeight()
                        .animateContentSize()
                        .width(260.dp),
                    title = "Align",
                    onCollapseAll = {
                        openPanel.value = SidePanel.None
                        pingInteraction()
                    }
                ) {
                    val scroll = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .wrapContentHeight()
                            .verticalScroll(scroll),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(alignText, style = MaterialTheme.typography.bodyMedium)

                        val t = targetAltAz
                        val s = orientationSample.value
                        if (t != null && s != null) {
                            val dAz = OrientationMath.deltaAngleDeg(s.azimuthDegTrue, t.azimuthDegTrue)
                            val dAlt = t.altitudeDeg - s.altitudeDegCamera
                            Text("Turn: ${formatSignedDeg(dAz)}", style = MaterialTheme.typography.bodySmall)
                            Text("Tilt: ${formatSignedDeg(dAlt)}", style = MaterialTheme.typography.bodySmall)
                            Text(t.label, style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text("Pick a planned result first.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // CONTROLS PANEL
            if (openPanel.value == SidePanel.Controls) {
                SidePanelCard(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 10.dp)
                        .wrapContentHeight()
                        .animateContentSize()
                        .width(350.dp),
                    title = "Controls",
                    onCollapseAll = {
                        openPanel.value = SidePanel.None
                        pingInteraction()
                    }
                ) {
                    val scroll = rememberScrollState()

                    Column(
                        modifier = Modifier.wrapContentHeight(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PrimaryTabRow(selectedTabIndex = controlsTab.value.ordinal) {
                            Tab(
                                selected = controlsTab.value == ControlsTab.Zoom,
                                onClick = { controlsTab.value = ControlsTab.Zoom; pingInteraction() },
                                text = { Text("Zoom") }
                            )
                            Tab(
                                selected = controlsTab.value == ControlsTab.Exposure,
                                onClick = { controlsTab.value = ControlsTab.Exposure; pingInteraction() },
                                text = { Text("Exp") }
                            )
                            Tab(
                                selected = controlsTab.value == ControlsTab.Focus,
                                onClick = { controlsTab.value = ControlsTab.Focus; pingInteraction() },
                                text = { Text("Focus") }
                            )
                            Tab(
                                selected = controlsTab.value == ControlsTab.ISO,
                                onClick = { controlsTab.value = ControlsTab.ISO; pingInteraction() },
                                text = { Text("ISO") }
                            )
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(scroll),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            when (controlsTab.value) {
                                ControlsTab.Zoom -> {
                                    val cam = cameraState.value
                                    val zs = cam?.cameraInfo?.zoomState?.value
                                    if (cam == null || zs == null) {
                                        Text("Starting…", style = MaterialTheme.typography.bodySmall)
                                    } else {
                                        Text("Zoom: ${"%.2f".format(zoom.floatValue)}x", style = MaterialTheme.typography.bodySmall)
                                        Slider(
                                            value = zoom.floatValue,
                                            onValueChange = { zoom.floatValue = it; pingInteraction() },
                                            valueRange = zs.minZoomRatio..zs.maxZoomRatio
                                        )
                                    }
                                }

                                ControlsTab.Exposure -> {
                                    val cam = cameraState.value
                                    if (cam == null) {
                                        Text("Starting…", style = MaterialTheme.typography.bodySmall)
                                    } else {
                                        val expState = cam.cameraInfo.exposureState
                                        if (!expState.isExposureCompensationSupported) {
                                            Text("Exposure comp not supported.", style = MaterialTheme.typography.bodySmall)
                                        } else {
                                            val r = expState.exposureCompensationRange
                                            Text("Exposure comp: ${exposureComp.intValue}", style = MaterialTheme.typography.bodySmall)
                                            Slider(
                                                value = exposureComp.intValue.toFloat(),
                                                onValueChange = { exposureComp.intValue = it.toInt(); pingInteraction() },
                                                valueRange = r.lower.toFloat()..r.upper.toFloat(),
                                                steps = (r.upper - r.lower - 1).coerceAtLeast(0)
                                            )
                                        }
                                    }
                                }

                                ControlsTab.Focus -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Autofocus", style = MaterialTheme.typography.bodyMedium)
                                        TextButton(onClick = { autoFocus.value = !autoFocus.value; pingInteraction() }) {
                                            Text(if (autoFocus.value) "ON" else "OFF")
                                        }
                                    }

                                    Text(
                                        if (autoFocus.value) "Manual focus disabled while AF is ON."
                                        else "Manual focus distance (0=infinity; higher=closer)",
                                        style = MaterialTheme.typography.bodySmall
                                    )

                                    Slider(
                                        value = manualFocusDist.floatValue,
                                        onValueChange = { manualFocusDist.floatValue = it; pingInteraction() },
                                        valueRange = 0f..10f,
                                        enabled = !autoFocus.value
                                    )
                                }

                                ControlsTab.ISO -> {
                                    Text("Manual ISO / Exposure (best-effort)", style = MaterialTheme.typography.bodySmall)

                                    Text("ISO: ${iso.intValue}", style = MaterialTheme.typography.bodySmall)
                                    Slider(
                                        value = iso.intValue.toFloat(),
                                        onValueChange = { iso.intValue = it.toInt().coerceIn(50, 6400); pingInteraction() },
                                        valueRange = 50f..6400f
                                    )

                                    Text("Exposure: ${exposureUs.intValue} µs", style = MaterialTheme.typography.bodySmall)
                                    Slider(
                                        value = exposureUs.intValue.toFloat(),
                                        onValueChange = { exposureUs.intValue = it.toInt().coerceIn(200, 200_000); pingInteraction() },
                                        valueRange = 200f..200_000f
                                    )

                                    Text(
                                        "Some phones may ignore these unless full manual control is supported.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ✅ Floating shutter always visible (NOW WIRED)
            ShutterFab(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 18.dp),
                enabled = imageCaptureState.value != null && !isCapturing.value,
                onClick = {
                    pingInteraction()
                    val cap = imageCaptureState.value
                    if (cap == null) {
                        error.value = "Camera not ready yet."
                        return@ShutterFab
                    }
                    if (isCapturing.value) return@ShutterFab

                    isCapturing.value = true
                    error.value = null
                    status.value = "Saving photo…"

                    scope.launch {
                        val uri = takePhotoToMediaStore(
                            context = context,
                            imageCapture = cap,
                            executor = ContextCompat.getMainExecutor(context)
                        )
                        status.value = if (uri != null) "Saved ✅" else "Save failed ❌"
                        if (uri == null) error.value = "Failed to save photo."
                        isCapturing.value = false
                    }
                }
            )
        }
    }
}

/* ---------------- UI building blocks ---------------- */

@Composable
private fun SidePanelCard(
    modifier: Modifier,
    title: String,
    onCollapseAll: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.40f),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .wrapContentHeight()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = onCollapseAll) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Collapse panels")
                }
            }
            content()
        }
    }
}

@Composable
private fun FloatingSideIcon(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDesc: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.40f),
        tonalElevation = 0.dp
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = contentDesc)
        }
    }
}

@Composable
private fun ShutterFab(
    modifier: Modifier,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.92f else 0.55f),
        tonalElevation = 0.dp
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(
                imageVector = Icons.Default.PhotoCamera,
                contentDescription = "Take photo",
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

/* ---------------- Camera2 interop helpers (device-dependent) ---------------- */

private fun applyAutoFocusMode(camera: Camera?, enabled: Boolean) {
    if (camera == null) return
    try {
        val camera2 = Camera2CameraControl.from(camera.cameraControl)
        val opts = CaptureRequestOptions.Builder().apply {
            if (enabled) {
                setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                )
                setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, 0f)
            } else {
                setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            }
        }.build()
        camera2.setCaptureRequestOptions(opts)
    } catch (t: Throwable) {
        Log.w("CameraFullScreen", "AF mode not supported: ${t.message}")
    }
}

private fun applyManualFocusBestEffort(camera: Camera?, focusDistance: Float) {
    if (camera == null) return
    try {
        val camera2 = Camera2CameraControl.from(camera.cameraControl)
        val opts = CaptureRequestOptions.Builder()
            .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            .setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, focusDistance)
            .build()
        camera2.setCaptureRequestOptions(opts)
    } catch (t: Throwable) {
        Log.w("CameraFullScreen", "Manual focus not supported: ${t.message}")
    }
}

private fun applyIsoExposureBestEffort(camera: Camera?, iso: Int, exposureTimeUs: Long) {
    if (camera == null) return
    try {
        val camera2 = Camera2CameraControl.from(camera.cameraControl)
        val exposureNs = exposureTimeUs * 1000L

        val opts = CaptureRequestOptions.Builder()
            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, iso)
            .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureNs)
            .build()

        camera2.setCaptureRequestOptions(opts)
    } catch (t: Throwable) {
        Log.w("CameraFullScreen", "ISO/Exposure not supported: ${t.message}")
    }
}

/* ---------------- Save photo helpers ---------------- */

private suspend fun takePhotoToMediaStore(
    context: Context,
    imageCapture: ImageCapture,
    executor: Executor
): Uri? = withContext(Dispatchers.Main) {
    val name = "NEO_${System.currentTimeMillis()}.jpg"
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/NEOPlanner")
        }
    }

    val outputOptions = ImageCapture.OutputFileOptions.Builder(
        context.contentResolver,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        contentValues
    ).build()

    suspendTakePicture(imageCapture, outputOptions, executor)
}

private suspend fun suspendTakePicture(
    imageCapture: ImageCapture,
    outputOptions: ImageCapture.OutputFileOptions,
    executor: Executor
): Uri? = suspendCancellableCoroutine { cont ->
    imageCapture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                if (!cont.isCompleted) cont.resume(outputFileResults.savedUri)
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("CameraFullScreen", "Photo capture failed", exception)
                if (!cont.isCompleted) cont.resume(null)
            }
        }
    )
}

private fun formatSignedDeg(v: Double): String {
    val sign = if (v >= 0) "+" else "−"
    return "$sign${"%.1f".format(abs(v))}°"
}
