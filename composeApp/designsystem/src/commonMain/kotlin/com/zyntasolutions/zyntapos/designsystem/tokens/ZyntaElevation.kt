package com.zyntasolutions.zyntapos.designsystem.tokens

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────────────────────
// ZyntaElevation — Material 3 Elevation Token System for ZyntaPOS
//
// Per UI/UX Master Blueprint §3.2 Elevation System (M3 spec):
//
//   Level 0 — 0 dp  → Background surfaces (no shadow)
//   Level 1 — 1 dp  → Cards, navigation rail
//   Level 2 — 3 dp  → Floating action buttons, search bar
//   Level 3 — 6 dp  → Dialogs, bottom sheets
//   Level 4 — 8 dp  → Navigation drawers, modal side sheets
//   Level 5 — 12 dp → Context menus, autocomplete dropdowns
//
// In Material 3, elevation also controls the tonal color overlay applied
// to surfaces — higher elevation surfaces receive a stronger primary-color tint
// (via surfaceTint). This is handled automatically by M3 components; these
// tokens feed the `tonalElevation` and `shadowElevation` parameters.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Material 3 elevation levels for ZyntaPOS.
 *
 * Use the appropriate level for each component category:
 * ```kotlin
 * Card(elevation = CardDefaults.cardElevation(defaultElevation = ZyntaElevation.Level1))
 * ```
 */
object ZyntaElevation {

    /** 0 dp — flat background surfaces; no shadow, no tonal overlay. */
    val Level0: Dp = 0.dp

    /** 1 dp — cards, navigation rail, list items. */
    val Level1: Dp = 1.dp

    /** 3 dp — floating action buttons, search bar, elevated cards. */
    val Level2: Dp = 3.dp

    /** 6 dp — dialogs, bottom sheets, date picker surfaces. */
    val Level3: Dp = 6.dp

    /** 8 dp — navigation drawers, modal side sheets. */
    val Level4: Dp = 8.dp

    /** 12 dp — context menus, autocomplete dropdowns, tooltips. */
    val Level5: Dp = 12.dp
}
