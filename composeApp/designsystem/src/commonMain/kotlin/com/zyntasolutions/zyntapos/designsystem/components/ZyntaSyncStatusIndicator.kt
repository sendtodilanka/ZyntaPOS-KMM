package com.zyntasolutions.zyntapos.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp

/**
 * Sync status for display in the navigation drawer footer.
 *
 * Represents the current sync engine state visible to the user.
 */
enum class SyncDisplayStatus {
    /** Device is offline — no network connectivity. */
    OFFLINE,

    /** Online but sync is idle (no pending operations or last sync succeeded). */
    SYNCED,

    /** Sync cycle is actively running (push/pull in progress). */
    SYNCING,

    /** Last sync cycle failed — will retry automatically. */
    ERROR,
}

/**
 * Compact sync status indicator for the navigation drawer footer.
 *
 * Shows an icon + optional label reflecting the current sync state:
 * - [SyncDisplayStatus.OFFLINE]: Cloud-off icon, "Offline" label
 * - [SyncDisplayStatus.SYNCED]: Cloud-done icon, "Synced" label
 * - [SyncDisplayStatus.SYNCING]: Rotating sync icon, "Syncing..." label
 * - [SyncDisplayStatus.ERROR]: Cloud icon with error tint, "Sync Error" label
 *
 * @param status Current sync display status.
 * @param isMini When true, only the icon is shown (drawer mini mode).
 * @param pendingCount Number of operations waiting to sync (shown as badge when > 0).
 */
@Composable
fun ZyntaSyncStatusIndicator(
    status: SyncDisplayStatus,
    modifier: Modifier = Modifier,
    isMini: Boolean = false,
    pendingCount: Int = 0,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "SyncRotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "SyncIconRotation",
    )

    val (icon, tint, label) = when (status) {
        SyncDisplayStatus.OFFLINE -> Triple(
            Icons.Default.CloudOff,
            MaterialTheme.colorScheme.error,
            "Offline",
        )
        SyncDisplayStatus.SYNCED -> Triple(
            Icons.Default.CloudDone,
            MaterialTheme.colorScheme.primary,
            if (pendingCount > 0) "$pendingCount pending" else "Synced",
        )
        SyncDisplayStatus.SYNCING -> Triple(
            Icons.Default.Sync,
            MaterialTheme.colorScheme.tertiary,
            "Syncing...",
        )
        SyncDisplayStatus.ERROR -> Triple(
            Icons.Default.Cloud,
            MaterialTheme.colorScheme.error,
            "Sync Error",
        )
    }

    Row(
        modifier = modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier
                .size(16.dp)
                .then(
                    if (status == SyncDisplayStatus.SYNCING) Modifier.rotate(rotation)
                    else Modifier,
                ),
        )
        if (!isMini) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                maxLines = 1,
            )
        }
    }
}
