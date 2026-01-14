package com.oscaribarra.neoplanner.data.geo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Await the last known location (may be null).
 *
 * NOTE:
 * - Requires either FINE or COARSE to be granted, otherwise returns null.
 * - "lastLocation" can be null even with permissions (e.g., GPS off, fresh install, etc.)
 */
suspend fun FusedLocationProviderClient.lastLocationOrNull(
    context: Context
): Location? = suspendCancellableCoroutine { cont ->

    val fineGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    val coarseGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    if (!fineGranted && !coarseGranted) {
        // No permission -> return null cleanly
        cont.resume(null)
        return@suspendCancellableCoroutine
    }

    lastLocation
        .addOnSuccessListener { loc ->
            if (cont.isActive) cont.resume(loc)
        }
        .addOnFailureListener {
            if (cont.isActive) cont.resume(null)
        }

    // If coroutine is cancelled, do nothing. Task will just be ignored.
    cont.invokeOnCancellation { /* no-op */ }
}
