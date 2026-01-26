package com.oscaribarra.neoplanner.ui.camera

import android.graphics.Color
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.ui.unit.dp



/**
 * Simple overlay used during orientation calibration.  When active, the overlay
 * paints a centered green square on top of the camera preview and presents
 * confirm/cancel actions.  Users are instructed to align the Moon (or other
 * target) within the square before confirming.
 *
 * @param onConfirm invoked when the user taps the confirm button.  The caller
 * should capture the current orientation sample and compute offsets before
 * dismissing the overlay.
 * @param onCancel optional callback invoked when the user cancels calibration.
 * @param boxSize the size of the green calibration square.  Defaults to 200.dp.
 */
@Composable
fun CalibrationOverlay(
    onConfirm: () -> Unit,
    onCancel: (() -> Unit)? = null,
    boxSize: Dp = 200.dp
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .size(boxSize)
                .align(Alignment.Center)
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.medium
                )
        )

        // Instruction and confirm/cancel buttons at bottom of overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Align the Moon in the box, then confirm.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.size(8.dp))
            Row {
                onCancel?.let {
                    Button(onClick = it, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                }
                Button(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                    Text("Confirm")
                }
            }
        }
    }
}