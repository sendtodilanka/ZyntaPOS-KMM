package com.zyntasolutions.zyntapos.designsystem.tokens

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────────────────────
// ZyntaSpacing — Spacing Token System for ZyntaPOS
//
// Based on a 4 dp base grid (per UI/UX Master Blueprint §2.1 Grid & Spacing).
// All layout measurements should snap to these tokens; never use raw dp literals
// in feature composables — always reference ZyntaSpacing via LocalSpacing.
//
// Token reference:
//   xs  =  4 dp  — Tight element separation (icon–label)
//   sm  =  8 dp  — Intra-component padding
//   md  = 16 dp  — Standard card padding, list item spacing
//   lg  = 24 dp  — Section separation
//   xl  = 32 dp  — Major layout gutters
//   xxl = 48 dp  — Page margins on Expanded breakpoint
//
// Additional POS-specific constants:
//   touchMin       = 48 dp  — WCAG / Material 3 minimum touch target
//   touchPreferred = 56 dp  — Preferred POS button height (§2.1)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Immutable spacing token set for ZyntaPOS.
 *
 * Consume via [LocalSpacing] in composable functions:
 * ```kotlin
 * val spacing = LocalSpacing.current
 * Spacer(modifier = Modifier.height(spacing.md))
 * ```
 */
data class ZyntaSpacingTokens(
    /** 4 dp — tight element separation, icon-to-label gap. */
    val xs: Dp = 4.dp,

    /** 8 dp — intra-component padding (inside chips, small buttons). */
    val sm: Dp = 8.dp,

    /** 16 dp — standard card padding, list-item vertical spacing. */
    val md: Dp = 16.dp,

    /** 24 dp — section separation between content groups. */
    val lg: Dp = 24.dp,

    /** 32 dp — major layout gutters, panel separation. */
    val xl: Dp = 32.dp,

    /** 48 dp — page margins on Expanded breakpoint. */
    val xxl: Dp = 48.dp,

    // ── POS-specific touch target constants ──────────────────────────────────

    /** 48 dp — WCAG 2.1 / Material 3 minimum interactive touch target. */
    val touchMin: Dp = 48.dp,

    /** 56 dp — Preferred POS button height for high-speed cashier operations. */
    val touchPreferred: Dp = 56.dp,
)

/**
 * [CompositionLocal] that provides [ZyntaSpacingTokens] down the composition tree.
 *
 * Provide a custom instance only when building previews or adaptive overrides.
 * In all production composables read via `LocalSpacing.current`.
 */
val LocalSpacing = compositionLocalOf { ZyntaSpacingTokens() }

/**
 * Convenience singleton providing static access to the default spacing scale.
 *
 * Use this for non-composable contexts or simple shorthand in composable lambdas:
 * ```kotlin
 * Modifier.padding(ZyntaSpacing.md)
 * ```
 * For dynamic / theme-overridden spacing use [LocalSpacing].current instead.
 */
val ZyntaSpacing = ZyntaSpacingTokens()
