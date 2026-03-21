package com.zyntasolutions.zyntapos.designsystem.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// ─────────────────────────────────────────────────────────────────────────────
// ZyntaTransferStatusBadge — Stock transfer status indicator.
//
// Wraps ZyntaStatusChip with transfer-specific status mapping.
// Statuses: PENDING → APPROVED → IN_TRANSIT → RECEIVED / CANCELLED
// ─────────────────────────────────────────────────────────────────────────────

/** Transfer status values matching the IST (Inter-Store Transfer) workflow. */
enum class TransferStatus {
    PENDING,
    APPROVED,
    IN_TRANSIT,
    RECEIVED,
    CANCELLED,
}

/**
 * A badge showing the current status of an inter-store stock transfer.
 *
 * Maps each [TransferStatus] to a semantic [StatusChipVariant] with an appropriate icon.
 *
 * @param status   Current transfer status.
 * @param modifier Optional [Modifier].
 */
@Composable
fun ZyntaTransferStatusBadge(
    status: TransferStatus,
    modifier: Modifier = Modifier,
) {
    val (label, variant, icon) = when (status) {
        TransferStatus.PENDING -> Triple(
            "Pending",
            StatusChipVariant.Warning,
            Icons.Default.HourglassEmpty,
        )
        TransferStatus.APPROVED -> Triple(
            "Approved",
            StatusChipVariant.Info,
            Icons.Default.ThumbUp,
        )
        TransferStatus.IN_TRANSIT -> Triple(
            "In Transit",
            StatusChipVariant.Info,
            Icons.Default.LocalShipping,
        )
        TransferStatus.RECEIVED -> Triple(
            "Received",
            StatusChipVariant.Success,
            Icons.Default.CheckCircle,
        )
        TransferStatus.CANCELLED -> Triple(
            "Cancelled",
            StatusChipVariant.Error,
            Icons.Default.Cancel,
        )
    }

    ZyntaStatusChip(
        label = label,
        variant = variant,
        icon = icon,
        modifier = modifier,
    )
}

/**
 * Convenience overload accepting a raw status string.
 *
 * Parses the string (case-insensitive, underscore-separated) to [TransferStatus].
 * Falls back to [TransferStatus.PENDING] for unknown values.
 */
@Composable
fun ZyntaTransferStatusBadge(
    statusString: String,
    modifier: Modifier = Modifier,
) {
    val status = try {
        TransferStatus.valueOf(statusString.uppercase().replace("-", "_"))
    } catch (_: IllegalArgumentException) {
        TransferStatus.PENDING
    }
    ZyntaTransferStatusBadge(status = status, modifier = modifier)
}
