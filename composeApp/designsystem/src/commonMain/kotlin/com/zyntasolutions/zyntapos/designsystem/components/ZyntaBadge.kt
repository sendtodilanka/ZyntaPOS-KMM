package com.zyntasolutions.zyntapos.designsystem.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing

// ─────────────────────────────────────────────────────────────────────────────
// ZyntaBadge — Count badge (number in circle) + Status badge (color pill + label)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A circular count badge, typically overlaid on navigation icons.
 *
 * @param count The number to display. Numbers > 99 are shown as "99+".
 * @param modifier Optional [Modifier].
 * @param containerColor Background color of the badge (default: error color).
 * @param contentColor Text color of the badge.
 */
@Composable
fun ZyntaCountBadge(
    count: Int,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.error,
    contentColor: Color = MaterialTheme.colorScheme.onError,
) {
    val label = if (count > 99) "99+" else count.toString()
    Surface(
        modifier = modifier.defaultMinSize(minWidth = 20.dp, minHeight = 20.dp),
        color = containerColor,
        shape = CircleShape,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = contentColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

/** Semantic status for [ZyntaStatusBadge]. */
enum class BadgeStatus {
    Success, Warning, Error, Info, Neutral
}

/**
 * A pill-shaped status badge with a label and semantic color.
 *
 * @param label Text label.
 * @param status Semantic color variant.
 * @param modifier Optional [Modifier].
 * @param customColor Override color; when set, [status] is ignored.
 */
@Composable
fun ZyntaStatusBadge(
    label: String,
    status: BadgeStatus = BadgeStatus.Neutral,
    modifier: Modifier = Modifier,
    customColor: Color? = null,
) {
    val color = customColor ?: when (status) {
        BadgeStatus.Success -> MaterialTheme.colorScheme.tertiary
        BadgeStatus.Warning -> MaterialTheme.colorScheme.secondary
        BadgeStatus.Error -> MaterialTheme.colorScheme.error
        BadgeStatus.Info -> MaterialTheme.colorScheme.primary
        BadgeStatus.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(ZyntaSpacing.sm),
    ) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = ZyntaSpacing.sm, vertical = 3.dp),
        )
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@org.jetbrains.compose.ui.tooling.preview.Preview
@androidx.compose.runtime.Composable
private fun ZyntaBadgePreview() {
    com.zyntasolutions.zyntapos.designsystem.theme.ZyntaTheme {
        androidx.compose.foundation.layout.Column(
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(
                com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing.sm
            ),
        ) {
            ZyntaCountBadge(count = 5)
            ZyntaStatusBadge(label = "Active", status = BadgeStatus.Success)
        }
    }
}
