package com.zyntasolutions.zyntapos.designsystem.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────────────────────
// ZyntaSyncIndicator — Maps SyncStatus enum to visual dot/spinner indicator.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Sync status states used across the application.
 * Mirrors [com.zyntasolutions.zyntapos.domain.model.SyncStatus.State].
 */
enum class SyncStatus { SYNCED, SYNCING, OFFLINE, FAILED }

/**
 * Visual sync status indicator chip.
 *
 * @param status Current sync state.
 * @param modifier Optional [Modifier].
 * @param showLabel When true, displays a text label next to the indicator.
 */
@Composable
fun ZyntaSyncIndicator(
    status: SyncStatus,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    val syncedColor = MaterialTheme.colorScheme.tertiary
    val syncingColor = MaterialTheme.colorScheme.primary
    val offlineColor = MaterialTheme.colorScheme.secondary
    val failedColor = MaterialTheme.colorScheme.error

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when (status) {
            SyncStatus.SYNCED -> {
                Icon(
                    imageVector = Icons.Default.CloudDone,
                    contentDescription = "Synced",
                    tint = syncedColor,
                    modifier = Modifier.size(16.dp),
                )
                if (showLabel) Text("Synced", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium), color = syncedColor)
            }

            SyncStatus.SYNCING -> {
                // Animated spinning sync icon
                val rotation by rememberInfiniteTransition(label = "sync").animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                    ),
                    label = "syncRotation",
                )
                Box(modifier = Modifier.size(16.dp)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        rotate(rotation, pivot = Offset(size.width / 2, size.height / 2)) {
                            drawCircle(color = syncingColor, radius = size.width / 2)
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Syncing",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (showLabel) Text("Syncing…", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium), color = syncingColor)
            }

            SyncStatus.OFFLINE -> {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = "Offline",
                    tint = offlineColor,
                    modifier = Modifier.size(16.dp),
                )
                if (showLabel) Text("Offline", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium), color = offlineColor)
            }

            SyncStatus.FAILED -> {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = "Sync failed",
                    tint = failedColor,
                    modifier = Modifier.size(16.dp),
                )
                if (showLabel) Text("Sync Failed", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium), color = failedColor)
            }
        }
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@org.jetbrains.compose.ui.tooling.preview.Preview
@androidx.compose.runtime.Composable
private fun ZyntaSyncIndicatorPreview() {
    com.zyntasolutions.zyntapos.designsystem.theme.ZyntaTheme {
        ZyntaSyncIndicator(status = SyncStatus.SYNCED)
    }
}
