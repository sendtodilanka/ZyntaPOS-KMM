package com.zyntasolutions.zyntapos.designsystem.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import com.zyntasolutions.zyntapos.designsystem.tokens.ZentaSpacing

// ─────────────────────────────────────────────────────────────────────────────
// ZentaEmptyState — Vector icon + title + subtitle + optional CTA ZentaButton
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
fun ZentaEmptyState(
    title: String,
    modifier: Modifier = Modifier.fillMaxSize(),
    icon: ImageVector? = null,
    subtitle: String? = null,
    ctaLabel: String? = null,
    onCtaClick: (() -> Unit)? = null,
    ctaVariant: ZentaButtonVariant = ZentaButtonVariant.Primary,
) {
    Column(
        modifier = modifier.padding(ZentaSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(ZentaSpacing.xxl),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Spacer(Modifier.height(ZentaSpacing.md))
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )

        if (subtitle != null) {
            Spacer(Modifier.height(ZentaSpacing.xs))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        if (ctaLabel != null && onCtaClick != null) {
            Spacer(Modifier.height(ZentaSpacing.lg))
            ZentaButton(
                text = ctaLabel,
                onClick = onCtaClick,
                variant = ctaVariant,
                size = ZentaButtonSize.Medium,
            )
        }
    }
}
