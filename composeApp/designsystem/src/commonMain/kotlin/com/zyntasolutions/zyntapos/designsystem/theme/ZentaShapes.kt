package com.zyntasolutions.zyntapos.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────────────────────
// ZentaShapes — Material 3 Shape Scale for ZentaPOS
//
// Scale: ExtraSmall(4dp) → Small(8dp) → Medium(12dp) → Large(16dp) → ExtraLarge(28dp)
// Per M3 spec, shape roles are assigned to component categories:
//   ExtraSmall  → Chips, small badges, tooltips
//   Small       → Text fields, small buttons, small cards
//   Medium      → Cards, dialogs, menus, date pickers
//   Large       → Large FABs, nav drawers, side sheets
//   ExtraLarge  → Bottom sheets, large dialogs
//
// ZentaPOS additions:
//   Full (50%) → Circular indicators, avatar badges, numeric pad keys
// ─────────────────────────────────────────────────────────────────────────────

/** ZentaPOS Material 3 [Shapes] scale. */
val ZentaShapes: Shapes = Shapes(
    /** 4 dp — chips, small badges, tooltips. */
    extraSmall  = RoundedCornerShape(4.dp),

    /** 8 dp — text fields, small buttons. */
    small       = RoundedCornerShape(8.dp),

    /** 12 dp — cards, menus, date pickers. */
    medium      = RoundedCornerShape(12.dp),

    /** 16 dp — FABs, dialogs, bottom nav. */
    large       = RoundedCornerShape(16.dp),

    /** 28 dp — bottom sheets, large containers. */
    extraLarge  = RoundedCornerShape(28.dp),
)

// Convenience shape aliases for bespoke POS components ────────────────────────

/** Fully circular shape (50% corner radius) — numeric pad keys, avatar badges. */
val ShapeFull = RoundedCornerShape(percent = 50)

/** 0 dp — sharp corners for edge-to-edge panels / split-pane dividers. */
val ShapeNone = RoundedCornerShape(0.dp)
