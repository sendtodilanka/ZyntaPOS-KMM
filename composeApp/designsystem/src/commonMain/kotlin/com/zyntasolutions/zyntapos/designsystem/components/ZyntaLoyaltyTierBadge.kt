package com.zyntasolutions.zyntapos.designsystem.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing

// ─────────────────────────────────────────────────────────────────────────────
// ZyntaLoyaltyTierBadge — Pill badge showing the customer's loyalty tier name
// with a tier-specific color: Bronze=amber, Silver=gray, Gold=yellow,
// Platinum=purple, fallback=tertiary.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A small pill-shaped badge that displays a loyalty tier name with a
 * tier-specific semantic color.
 *
 * Color mapping:
 * - **Bronze** — amber (#B8860B)
 * - **Silver** — gray (onSurfaceVariant)
 * - **Gold** — yellow/amber (#DAA520)
 * - **Platinum** — purple (#7B1FA2)
 * - **Other** — tertiary from the current theme
 *
 * @param tierName Display name of the loyalty tier (e.g., "Gold").
 * @param modifier Optional [Modifier].
 */
@Composable
fun ZyntaLoyaltyTierBadge(
    tierName: String,
    modifier: Modifier = Modifier,
) {
    val color = tierColor(tierName)
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(ZyntaSpacing.sm),
    ) {
        Text(
            text = tierName,
            color = color,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = ZyntaSpacing.sm, vertical = 3.dp),
        )
    }
}

/**
 * Resolves the display color for a loyalty tier name (case-insensitive match).
 */
@Composable
private fun tierColor(tierName: String): Color = when (tierName.lowercase()) {
    "bronze" -> Color(0xFFB8860B)
    "silver" -> MaterialTheme.colorScheme.onSurfaceVariant
    "gold" -> Color(0xFFDAA520)
    "platinum" -> Color(0xFF7B1FA2)
    else -> MaterialTheme.colorScheme.tertiary
}
