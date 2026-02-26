package com.zyntasolutions.zyntapos.designsystem.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing

// ─────────────────────────────────────────────────────────────────────────────
// ZyntaEmptyState — Vector icon + title + subtitle + optional CTA ZyntaButton
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A standardised empty-state screen or section.
 *
 * @param title Primary heading (e.g. "No products found").
 * @param modifier Optional [Modifier]; defaults to filling available space.
 * @param icon Optional vector icon displayed above the title.
 * @param subtitle Secondary descriptive text.
 * @param ctaLabel Optional call-to-action button label. If null, no button is shown.
 * @param onCtaClick Required when [ctaLabel] is non-null.
 * @param ctaVariant Button variant for the CTA (default: Primary).
 */
@Composable
fun ZyntaEmptyState(
    title: String,
    modifier: Modifier = Modifier.fillMaxSize(),
    icon: ImageVector? = null,
    subtitle: String? = null,
    ctaLabel: String? = null,
    onCtaClick: (() -> Unit)? = null,
    ctaVariant: ZyntaButtonVariant = ZyntaButtonVariant.Primary,
) {
    Column(
        modifier = modifier.padding(ZyntaSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(ZyntaSpacing.xxl),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(Modifier.height(ZyntaSpacing.md))
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )

        if (subtitle != null) {
            Spacer(Modifier.height(ZyntaSpacing.xs))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        if (ctaLabel != null && onCtaClick != null) {
            Spacer(Modifier.height(ZyntaSpacing.lg))
            ZyntaButton(
                text = ctaLabel,
                onClick = onCtaClick,
                variant = ctaVariant,
                size = ZyntaButtonSize.Medium,
            )
        }
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@org.jetbrains.compose.ui.tooling.preview.Preview
@androidx.compose.runtime.Composable
private fun ZyntaEmptyStatePreview() {
    com.zyntasolutions.zyntapos.designsystem.theme.ZyntaTheme {
        ZyntaEmptyState(
            title = "No products found",
            subtitle = "Add your first product to get started.",
            ctaLabel = "Add Product",
            onCtaClick = {},
        )
    }
}
