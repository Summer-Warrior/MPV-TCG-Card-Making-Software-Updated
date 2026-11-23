package com.example.ui.workspace

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.settings.PhysicalCardProfile
import com.example.data.settings.StudioSettings
import com.example.ui.editor.PhysicalCardDimensionsPx

/**
 * Clean ModalBottomSheet housing the persistent Physical Card Calibration Slider,
 * calibration lock, and physical card profile selector.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationBottomSheet(
    settings: StudioSettings,
    physicalDim: PhysicalCardDimensionsPx,
    onUpdateCalibrationScale: (Float) -> Unit,
    onToggleCalibrationLock: (Boolean) -> Unit,
    onUpdatePhysicalProfile: (PhysicalCardProfile) -> Unit,
    onResetCalibration: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.calibration_label),
                    style = MaterialTheme.typography.titleLarge,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close calibration")
                }
            }

            Text(
                text = stringResource(R.string.calibration_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${"%.2f".format(settings.calibrationScale)}×",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.width(48.dp),
                )
                Slider(
                    value = settings.calibrationScale,
                    onValueChange = {
                        if (!settings.calibrationLocked) {
                            onUpdateCalibrationScale(it)
                        }
                    },
                    enabled = !settings.calibrationLocked,
                    valueRange = StudioSettings.MIN_CALIBRATION..StudioSettings.MAX_CALIBRATION,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("calibration_slider"),
                )
                IconButton(
                    onClick = { onToggleCalibrationLock(!settings.calibrationLocked) },
                    modifier = Modifier.testTag("calibration_lock_button"),
                ) {
                    Icon(
                        imageVector = if (settings.calibrationLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = if (settings.calibrationLocked) "Unlock calibration" else "Lock calibration",
                        tint = if (settings.calibrationLocked) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            Text(
                text = "Physical Card Size",
                style = MaterialTheme.typography.titleSmall,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PhysicalCardProfile.entries.forEach { profile ->
                    FilterChip(
                        selected = settings.physicalProfile == profile,
                        onClick = { onUpdatePhysicalProfile(profile) },
                        label = { Text(profile.label) },
                        modifier = Modifier.testTag("profile_chip_${profile.id}"),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${"%.1f".format(physicalDim.widthMm)} × ${"%.1f".format(physicalDim.heightMm)} mm • ${physicalDim.xdpi.toInt()}×${physicalDim.ydpi.toInt()} DPI",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = onResetCalibration,
                    enabled = !settings.calibrationLocked,
                    modifier = Modifier.testTag("calibration_reset_button"),
                ) {
                    Text("Reset 1.00×")
                }
            }

            Spacer(Modifier.height(4.dp))
        }
    }
}
