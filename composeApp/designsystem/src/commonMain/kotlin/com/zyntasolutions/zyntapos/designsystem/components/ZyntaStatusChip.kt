package com.zyntasolutions.zyntapos.designsystem.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing

// ─────────────────────────────────────────────────────────────────────────────
// ZyntaStatusChip — Icon + Label chip with semantic color variants.
// Variants: Success, Warning, Error, Info, Neutral.
// Used for order status, stock status, sync state labels across rows/details.
// ─────────────────────────────────────────────────────────────────────────────

/** Semantic status variant for [ZyntaStatusChip]. */
enum class StatusChipVariant {
    Success, Warning, Error, Info, Neutral
}

/**
 * A compact, pill-shaped status indicator combining an optional icon with a label.
 *
 * Uses tonal container colors derived from [MaterialTheme.colorScheme] to ensure
 * proper contrast in both light and dark themes.
 *
 * @param label   Display text (e.g. "Completed", "Low Stock", "Syncing").
 * @param variant Semantic color variant controlling background/foreground tinting.
 * @param modifier Optional [Modifier].
 * @param icon    Optional leading [ImageVector] displayed before the label.
 */
@Composable
fun ZyntaStatusChip(
    label: String,
    variant: StatusChipVariant = StatusChipVariant.Neutral,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val (containerColor, contentColor) = when (variant) {
        StatusChipVariant.Success -> MaterialTheme.colorScheme.tertiaryContainer to
                MaterialTheme.colorScheme.onTertiaryContainer
        StatusChipVariant.Warning -> MaterialTheme.colorScheme.secondaryContainer to
                MaterialTheme.colorScheme.onSecondaryContainer
        StatusChipVariant.Error -> MaterialTheme.colorScheme.errorContainer to
                MaterialTheme.colorScheme.onErrorContainer
        StatusChipVariant.Info -> MaterialTheme.colorScheme.primaryContainer to
                MaterialTheme.colorScheme.onPrimaryContainer
        StatusChipVariant.Neutral -> MaterialTheme.colorScheme.surfaceVariant to
                MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier,
        color = containerColor,
        shape = RoundedCornerShape(ZyntaSpacing.sm),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = ZyntaSpacing.sm, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(ZyntaSpacing.xs))
            }
            Text(
                text = label,
                color = contentColor,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}
