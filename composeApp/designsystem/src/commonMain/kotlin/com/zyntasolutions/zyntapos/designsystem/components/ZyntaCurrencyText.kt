package com.zyntasolutions.zyntapos.designsystem.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.zyntasolutions.zyntapos.core.utils.CurrencyFormatter

// ─────────────────────────────────────────────────────────────────────────────
// ZyntaCurrencyText — Locale-aware currency display composable.
// Variants: Standard (body), Large (totals), Compact (table cells).
// Negative values render in MaterialTheme.colorScheme.error.
// ─────────────────────────────────────────────────────────────────────────────

/** Size variant controlling typography style for [ZyntaCurrencyText]. */
enum class CurrencyTextVariant {
    /** Standard body text for inline prices. */
    Standard,
    /** Large, bold text for order totals and KPIs. */
    Large,
    /** Compact text for dense table cells. */
    Compact,
}

/**
 * Composable that formats and displays a monetary value using the app's
 * configured [CurrencyFormatter], applying semantic coloring for negative amounts.
 *
 * @param amount   The monetary value to display.
 * @param modifier Optional [Modifier].
 * @param variant  Size variant: [CurrencyTextVariant.Standard], [Large], or [Compact].
 * @param textAlign Optional text alignment override.
 * @param formatter Currency formatter instance. Callers obtain this from Koin or provide directly.
 */
@Composable
fun ZyntaCurrencyText(
    amount: Double,
    formatter: CurrencyFormatter,
    modifier: Modifier = Modifier,
    variant: CurrencyTextVariant = CurrencyTextVariant.Standard,
    textAlign: TextAlign? = null,
) {
    val isNegative = amount < 0
    val color: Color = if (isNegative) {
        MaterialTheme.colorScheme.error
    } else {
        Color.Unspecified
    }

    val style: TextStyle = when (variant) {
        CurrencyTextVariant.Standard -> MaterialTheme.typography.bodyMedium
        CurrencyTextVariant.Large -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        CurrencyTextVariant.Compact -> MaterialTheme.typography.labelMedium
    }

    Text(
        text = formatter.format(amount),
        modifier = modifier,
        color = color,
        style = style,
        textAlign = textAlign,
    )
}
