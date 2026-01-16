package com.oscaribarra.neoplanner.ui.camera

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.view.MotionEvent
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.oscaribarra.neoplanner.ui.pointing.OrientationMath
import com.oscaribarra.neoplanner.ui.pointing.OrientationTracker
import com.oscaribarra.neoplanner.ui.pointing.TargetAltAz
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.abs
import java.util.concurrent.Executor

/**
 * Camera Tab:
 * - Live preview (CameraX)
 * - Tap to focus
 * - Zoom + exposure quick controls in a *small bottom popup* (no scrolling)
 * - Floating shutter always visible (bottom-center over preview)
 * - Floating fullscreen icon (top-right over preview)
 *
 * Notes:
 * - We "rebind" the camera after closing fullscreen to avoid the preview not resuming on some devices.
 */
@Composable
fun CameraTab(
    obsLatDeg: Double?,
    obsLonDeg: Double?,
    obsHeightMeters: Double?,
    targetAltAz: TargetAltAz?,
    onBackToResults: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val previewViewState = remember { mutableStateOf<PreviewView?>(null) }
    val cameraState = remember { mutableStateOf<Camera?>(null) }
    val imageCaptureState = remember { mutableStateOf<ImageCapture?>(null) }

    val errorState = remember { mutableStateOf<String?>(null) }
    val statusState = remember { mutableStateOf<String?>(null) }

    // Live orientation for alignment
    val orientationSample = remember { mutableStateOf<OrientationTracker.OrientationSample?>(null) }

    // UI state
    val zoomRatio = remember { mutableFloatStateOf(1f) }
    val exposureIndex = remember { mutableIntStateOf(0) }
    val showFullScreen = remember { mutableStateOf(false) }

    // Small bottom popup controls
    val showQuickControls = remember { mutableStateOf(false) }
    val quickControlsLastTouchMs = remember { mutableStateOf(System.currentTimeMillis()) }
    fun pingQuickControls() { quickControlsLastTouchMs.value = System.currentTimeMillis() }

    // Auto-hide quick controls after a short timeout
    LaunchedEffect(Unit) {
        while (true) {
            delay(800)
            if (showQuickControls.value) {
                val idle = System.currentTimeMillis() - quickControlsLastTouchMs.value
                if (idle > 5000) showQuickControls.value = false
            }
        }
    }

    // A "rebind key" that we bump when returning from full screen to ensure camera re-binds.
    val rebindTick = remember { mutableIntStateOf(0) }

    // Start sensors
    LaunchedEffect(obsLatDeg, obsLonDeg, obsHeightMeters) {
        orientationSample.value = null
        errorState.value = null

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
            orientationSample.value = s
        }
    }

    // Bind camera once PreviewView exists (and rebind when rebindTick changes)
    DisposableEffect(previewViewState.value, lifecycleOwner, rebindTick.intValue) {
        val previewView = previewViewState.value ?: return@DisposableEffect onDispose { }

        val executor = ContextCompat.getMainExecutor(context)
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null

        providerFuture.addListener({
            try {
                provider = providerFuture.get()

                val preview = Preview.Builder().build().also { p ->
                    p.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val selector = CameraSelector.DEFAULT_BACK_CAMERA

                provider?.unbindAll()
                val cam = provider!!.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)

                cameraState.value = cam
                imageCaptureState.value = imageCapture

                cam.cameraInfo.zoomState.value?.let { zs ->
                    zoomRatio.floatValue = zs.zoomRatio
                }
                exposureIndex.intValue = cam.cameraInfo.exposureState.exposureCompensationIndex

                errorState.value = null
            } catch (t: Throwable) {
                Log.e("CameraTab", "Failed to bind camera", t)
                errorState.value = "Camera failed to start: ${t.message ?: t.javaClass.simpleName}"
            }
        }, executor)

        onDispose {
            try {
                provider?.unbindAll()
            } catch (_: Throwable) {
                // ignore
            }
        }
    }

    // Full-screen overlay (true fullscreen dialog)
    if (showFullScreen.value) {
        CameraFullScreen(
            targetAltAz = targetAltAz,
            orientationSample = orientationSample,
            onDismiss = {
                showFullScreen.value = false
                // Force a rebind on return (helps devices that "lose" preview after dialog)
                rebindTick.intValue = rebindTick.intValue + 1
                statusState.value = null
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Alignment at the top
        CameraAlignmentCard(
            sample = orientationSample.value,
            target = targetAltAz
        )

        statusState.value?.let { msg ->
            ElevatedCard {
                Column(Modifier.padding(12.dp)) {
                    Text(msg, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        errorState.value?.let { err ->
            ElevatedCard {
                Column(Modifier.padding(12.dp)) {
                    Text("Status", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(err, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // Camera preview + overlays
        Box(modifier = Modifier.fillMaxWidth()) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().padding(10.dp)) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f),
                        factory = { ctx ->
                            PreviewView(ctx).apply {
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                                setOnTouchListener { _, event ->
                                    if (event.action == MotionEvent.ACTION_UP) {
                                        val cam = cameraState.value
                                        if (cam != null) {
                                            val factory: MeteringPointFactory = meteringPointFactory
                                            val point = factory.createPoint(event.x, event.y)
                                            val action = FocusMeteringAction.Builder(point).build()
                                            cam.cameraControl.startFocusAndMetering(action)
                                        }
                                    }
                                    true
                                }
                            }.also { pv ->
                                previewViewState.value = pv
                            }
                        }
                    )
                }
            }

            // Fullscreen icon (top-right) — floating over preview
            FloatingIconButton(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
                icon = Icons.Default.Fullscreen,
                contentDesc = "Full screen",
                onClick = { showFullScreen.value = true }
            )

            // Quick-controls icon (bottom-right) — opens tiny popup
            FloatingIconButton(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(end = 14.dp, bottom = 14.dp),
                icon = Icons.Default.Tune,
                contentDesc = "Quick controls",
                onClick = {
                    showQuickControls.value = !showQuickControls.value
                    pingQuickControls()
                }
            )

            // Floating shutter always visible
            ShutterFab(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 14.dp),
                enabled = imageCaptureState.value != null,
                onClick = {
                    val cap = imageCaptureState.value ?: return@ShutterFab
                    scope.launch {
                        //statusState.value = "Saving photo…"
                        val uri = takePhotoToMediaStore(
                            context = context,
                            imageCapture = cap,
                            executor = ContextCompat.getMainExecutor(context)
                        )
                        //statusState.value = if (uri != null) "Saved ✅" else "Failed to save photo."
                    }
                }
            )

            // ✅ Explicit receiver to avoid the ColumnScope overload confusion
            androidx.compose.animation.AnimatedVisibility(
                visible = showQuickControls.value,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 76.dp) // sits above shutter
            ) {
                QuickControlsPopup(
                    camera = cameraState.value,
                    zoomRatio = zoomRatio.floatValue,
                    onZoomChange = { v ->
                        pingQuickControls()
                        val cam = cameraState.value ?: return@QuickControlsPopup
                        val zs = cam.cameraInfo.zoomState.value ?: return@QuickControlsPopup
                        val clamped = v.coerceIn(zs.minZoomRatio, zs.maxZoomRatio)
                        cam.cameraControl.setZoomRatio(clamped)
                        zoomRatio.floatValue = clamped
                    },
                    exposureIndex = exposureIndex.intValue,
                    onExposureChange = { idx ->
                        pingQuickControls()
                        val cam = cameraState.value ?: return@QuickControlsPopup
                        val exp = cam.cameraInfo.exposureState
                        if (!exp.isExposureCompensationSupported) return@QuickControlsPopup
                        val r = exp.exposureCompensationRange
                        val clamped = idx.coerceIn(r.lower, r.upper)
                        cam.cameraControl.setExposureCompensationIndex(clamped)
                        exposureIndex.intValue = clamped
                    },
                    onClose = {
                        pingQuickControls()
                        showQuickControls.value = false
                    }
                )
            }
        }

        // Bottom row (wrap content)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBackToResults) { Text("Results") }
            if (statusState.value != null) {
                TextButton(onClick = { statusState.value = null }) { Text("Clear") }
            }
        }
    }
}

@Composable
private fun CameraAlignmentCard(
    sample: OrientationTracker.OrientationSample?,
    target: TargetAltAz?
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Alignment", style = MaterialTheme.typography.titleMedium)

            if (target == null) {
                Text("Select a NEO in Results, then return here to aim.", style = MaterialTheme.typography.bodySmall)
                if (sample == null) Text("Waiting for sensors…", style = MaterialTheme.typography.bodySmall)
                return@Column
            }

            Text(target.label, style = MaterialTheme.typography.bodySmall)

            if (sample == null) {
                Text("Waiting for sensors…", style = MaterialTheme.typography.bodySmall)
            } else {
                val dAz = OrientationMath.deltaAngleDeg(sample.azimuthDegTrue, target.azimuthDegTrue)
                val dAlt = target.altitudeDeg - sample.altitudeDegCamera

                val aligned = abs(dAz) < 3.0 && abs(dAlt) < 3.0
                Text(
                    if (aligned) "✅ Aligned" else OrientationMath.aimHint(dAz, dAlt),
                    color = if (aligned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Turn: ${formatSignedDeg(dAz)}  •  Tilt: ${formatSignedDeg(dAlt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/* ---------------- Overlay UI ---------------- */

@Composable
private fun FloatingIconButton(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDesc: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.45f),
        tonalElevation = 0.dp
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = contentDesc, tint = Color.White)
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

@Composable
private fun QuickControlsPopup(
    camera: Camera?,
    zoomRatio: Float,
    onZoomChange: (Float) -> Unit,
    exposureIndex: Int,
    onExposureChange: (Int) -> Unit,
    onClose: () -> Unit
) {
    val zs = camera?.cameraInfo?.zoomState?.value
    val exp = camera?.cameraInfo?.exposureState
    val expSupported = exp?.isExposureCompensationSupported == true

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.55f),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .width(320.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Quick Controls", color = Color.White, style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            // Zoom
            if (zs == null) {
                Text("Zoom: starting…", color = Color.White, style = MaterialTheme.typography.bodySmall)
            } else {
                Text("Zoom: ${"%.2f".format(zoomRatio)}x", color = Color.White, style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = zoomRatio,
                    onValueChange = onZoomChange,
                    valueRange = zs.minZoomRatio..zs.maxZoomRatio
                )
            }

            // Exposure comp
            if (!expSupported || exp == null) {
                Text("Exposure comp: not supported", color = Color.White, style = MaterialTheme.typography.bodySmall)
            } else {
                val r = exp.exposureCompensationRange
                Text("Exposure: $exposureIndex", color = Color.White, style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = exposureIndex.toFloat(),
                    onValueChange = { onExposureChange(it.toInt()) },
                    valueRange = r.lower.toFloat()..r.upper.toFloat(),
                    steps = (r.upper - r.lower - 1).coerceAtLeast(0)
                )
            }
        }
    }
}

/* ---------------- Helpers ---------------- */

private fun formatSignedDeg(v: Double): String {
    val sign = if (v >= 0) "+" else "−"
    return "$sign${"%.1f".format(abs(v))}°"
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
                Log.e("CameraTab", "Photo capture failed", exception)
                if (!cont.isCompleted) cont.resume(null)
            }
        }
    )
}
