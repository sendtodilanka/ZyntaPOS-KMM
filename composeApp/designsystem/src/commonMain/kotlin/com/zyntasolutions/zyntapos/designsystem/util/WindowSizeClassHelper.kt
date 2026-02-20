package com.zyntasolutions.zyntapos.designsystem.util

import androidx.compose.runtime.Composable

// ─────────────────────────────────────────────────────────────────────────────
// WindowSizeClassHelper — Responsive Breakpoint Utility for ZentaPOS
//
// Per UI/UX Master Blueprint §2.1 WindowSizeClass Mapping:
//
//   COMPACT  < 600 dp  — Android phone (4"–6.5")
//                        Single-pane, bottom nav bar, stacked layouts
//   MEDIUM   600–840dp — Android tablet (7"–10"), small laptop
//                        Two-pane master/detail, navigation rail
//   EXPANDED > 840 dp  — Desktop monitors (13"+), large tablets landscape
//                        Multi-pane split views, persistent side drawer
//
// Platform implementations:
//   Android  → delegates to androidx.compose.material3.adaptive
//               `currentWindowAdaptiveInfo()` + `WindowWidthSizeClass`
//   Desktop  → reads `LocalWindowInfo.current.containerSize` in dp
//               using the same numeric thresholds
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Responsive window size class for ZentaPOS adaptive layouts.
 *
 * Maps directly to the three Material 3 WindowSizeClass width buckets.
 * Consume via [currentWindowSize] inside any `@Composable` function.
 *
 * ```kotlin
 * val windowSize = currentWindowSize()
 * when (windowSize) {
 *     WindowSize.COMPACT  -> SinglePaneLayout()
 *     WindowSize.MEDIUM   -> MasterDetailLayout()
 *     WindowSize.EXPANDED -> FullDashboardLayout()
 * }
 * ```
 */
enum class WindowSize {
    /** Width < 600 dp — phones, portrait narrow tablets. */
    COMPACT,

    /** Width 600–840 dp — large phones landscape, 7"–10" tablets, small laptops. */
    MEDIUM,

    /** Width > 840 dp — desktop monitors, large tablets landscape. */
    EXPANDED,
}

/**
 * Returns the current [WindowSize] based on the window width.
 *
 * This is a composable function so it can observe window-resize events at
 * runtime (e.g., when the user resizes a desktop window or folds/unfolds a
 * foldable device).
 *
 * Implemented via `expect/actual`:
 * - **Android** — delegates to `currentWindowAdaptiveInfo()` from
 *   `androidx.compose.material3.adaptive` for Jetpack-backed accuracy.
 * - **Desktop** — reads `LocalWindowInfo.current.containerSize` converted to dp.
 */
@Composable
expect fun currentWindowSize(): WindowSize
