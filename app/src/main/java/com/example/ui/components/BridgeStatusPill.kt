package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.BridgeHealthState
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusOfflineAmber

/**
 * Compact single-line Engine Status Pill for the Welcome Window header.
 * Green dot = Online, Amber dot = Offline. Tapping opens Bridge & Termux Diagnostics.
 */
@Composable
fun BridgeStatusPill(
    healthState: BridgeHealthState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dotColor = when (healthState) {
        is BridgeHealthState.Online -> StatusConnected
        is BridgeHealthState.Offline -> StatusOfflineAmber
        is BridgeHealthState.Checking -> MaterialTheme.colorScheme.secondary
    }
    val label = when (healthState) {
        is BridgeHealthState.Online -> stringResource(R.string.bridge_status_online)
        is BridgeHealthState.Offline -> stringResource(R.string.bridge_status_offline)
        is BridgeHealthState.Checking -> stringResource(R.string.bridge_status_checking)
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .testTag("bridge_status_pill"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (healthState is BridgeHealthState.Checking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(9.dp),
                    strokeWidth = 1.5.dp,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

/**
 * Ultra-compact clickable status dot button for the Set Workspace Top App Bar (48dp touch target).
 * Green = Connected, Amber = Offline.
 */
@Composable
fun BridgeStatusDotButton(
    healthState: BridgeHealthState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dotColor = when (healthState) {
        is BridgeHealthState.Online -> StatusConnected
        is BridgeHealthState.Offline -> StatusOfflineAmber
        is BridgeHealthState.Checking -> MaterialTheme.colorScheme.secondary
    }
    val description = when (healthState) {
        is BridgeHealthState.Online -> "MSE Bridge connected. Tap for diagnostics."
        is BridgeHealthState.Offline -> "MSE Bridge offline. Tap for Termux setup and diagnostics."
        is BridgeHealthState.Checking -> "Checking MSE Bridge connection."
    }

    IconButton(
        onClick = onClick,
        modifier = modifier
            .semantics { contentDescription = description }
            .testTag("topbar_bridge_status_dot"),
    ) {
        Surface(
            shape = CircleShape,
            color = dotColor.copy(alpha = 0.16f),
            border = BorderStroke(1.dp, dotColor.copy(alpha = 0.55f)),
            modifier = Modifier.size(26.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (healthState is BridgeHealthState.Checking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(11.dp),
                        strokeWidth = 1.5.dp,
                        color = dotColor,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(dotColor),
                    )
                }
            }
        }
    }
}
