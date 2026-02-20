package com.zyntasolutions.zyntapos.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────────────────────
// ZentaTypography — Material 3 TypeScale for ZentaPOS
//
// Specification: UI/UX Master Blueprint §3.1 Typography Scale
// Font family: system sans-serif (no custom font bundle; keeps APK/JAR slim)
// All sizes and weights are per the M3 TypeScale spec.
//
// Usage context for each role (per §3.1 table):
//   displayLarge  → Dashboard hero numbers (daily revenue) — 57sp / W400
//   headlineLarge → Screen titles — 32sp / W400
//   headlineMedium→ Section headers — 28sp / W400
//   titleLarge    → Card titles — 22sp / W500
//   titleMedium   → List item primary text — 16sp / W500
//   bodyLarge     → Main content text — 16sp / W400
//   bodyMedium    → Secondary content, descriptions — 14sp / W400
//   labelLarge    → Button text, tab labels — 14sp / W500
//   labelMedium   → Badges, tags, chip labels — 12sp / W500
//   labelSmall    → Captions, timestamps — 11sp / W500
// ─────────────────────────────────────────────────────────────────────────────

private val ZentaFontFamily = FontFamily.SansSerif

/**
 * ZentaPOS Material 3 [Typography] scale.
 *
 * All styles use the system sans-serif font family so the design system
 * remains platform-agnostic without bundling custom font assets.
 * Line heights follow the M3 TypeScale specification exactly.
 */
val ZentaTypography: Typography = Typography(

    // ── Display ──────────────────────────────────────────────────────────────
    displayLarge = TextStyle(
        fontFamily  = ZentaFontFamily,
        fontWeight  = FontWeight.Normal,
        fontSize    = 57.sp,
        lineHeight  = 64.sp,
        letterSpacing = (-0.25).sp,
    ),
    displayMedium = TextStyle(
        fontFamily  = ZentaFontFamily,
        fontWeight  = FontWeight.Normal,
        fontSize    = 45.sp,
        lineHeight  = 52.sp,
        letterSpacing = 0.sp,
    ),
    displaySmall = TextStyle(
        fontFamily  = ZentaFontFamily,
        fontWeight  = FontWeight.Normal,
        fontSize    = 36.sp,
        lineHeight  = 44.sp,
        letterSpacing = 0.sp,
    ),

    // ── Headline ─────────────────────────────────────────────────────────────
    headlineLarge = TextStyle(
        fontFamily  = ZentaFontFamily,
        fontWeight  = FontWeight.Normal,
        fontSize    = 32.sp,
        lineHeight  = 40.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily  = ZentaFontFamily,
        fontWeight  = FontWeight.Normal,
        fontSize    = 28.sp,
        lineHeight  = 36.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily  = ZentaFontFamily,
        fontWeight  = FontWeight.Normal,
        fontSize    = 24.sp,
        lineHeight  = 32.sp,
        letterSpacing = 0.sp,
    ),

    // ── Title ─────────────────────────────────────────────────────────────────
    titleLarge = TextStyle(
        fontFamily  = ZentaFontFamily,
        fontWeight  = FontWeight.Medium,
        fontSize    = 22.sp,
        lineHeight  = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily  = ZentaFontFamily,
        fontWeight  = FontWeight.Medium,
        fontSize    = 16.sp,
        lineHeight  = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily  = ZentaFontFamily,
        fontWeight  = FontWeight.Medium,
        fontSize    = 14.sp,
        lineHeight  = 20.sp,
        letterSpacing = 0.1.sp,
    ),

    // ── Body ──────────────────────────────────────────────────────────────────
    bodyLarge = TextStyle(
        fontFamily  = ZentaFontFamily,
        fontWeight  = FontWeight.Normal,
        fontSize    = 16.sp,
        lineHeight  = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily  = ZentaFontFamily,
        fontWeight  = FontWeight.Normal,
        fontSize    = 14.sp,
        lineHeight  = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily  = ZentaFontFamily,
        fontWeight  = FontWeight.Normal,
        fontSize    = 12.sp,
        lineHeight  = 16.sp,
        letterSpacing = 0.4.sp,
    ),

    // ── Label ─────────────────────────────────────────────────────────────────
    labelLarge = TextStyle(
        fontFamily  = ZentaFontFamily,
        fontWeight  = FontWeight.Medium,
        fontSize    = 14.sp,
        lineHeight  = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily  = ZentaFontFamily,
        fontWeight  = FontWeight.Medium,
        fontSize    = 12.sp,
        lineHeight  = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily  = ZentaFontFamily,
        fontWeight  = FontWeight.Medium,
        fontSize    = 11.sp,
        lineHeight  = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)
