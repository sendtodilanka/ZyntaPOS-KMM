package com.zyntasolutions.zyntapos.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
// ZyntaColors — Material 3 Color Tokens for ZyntaPOS Design System
//
// Seed colors (per UI/UX Master Blueprint §1.3 / §3):
//   Primary:   #1565C0  — Deep Blue  (primary actions, Pay button, main CTAs)
//   Secondary: #F57C00  — Amber      (warnings, low stock, pending sync)
//   Tertiary:  #2E7D32  — Green      (success, sync complete, payment success)
//   Error:     #C62828  — Red        (void, delete, failed sync)
//
// All roles derived via M3 Tonal Palette spec.
// ─────────────────────────────────────────────────────────────────────────────

// ── Raw Palette ─────────────────────────────────────────────────────────────

// Primary palette (Blue 900 seed)
private val Blue10  = Color(0xFF001B47)
private val Blue20  = Color(0xFF003065)
private val Blue30  = Color(0xFF004A96)
private val Blue40  = Color(0xFF1565C0)  // seed
private val Blue80  = Color(0xFFA8C7FA)
private val Blue90  = Color(0xFFD6E4FF)
private val Blue95  = Color(0xFFEBF1FF)
private val Blue99  = Color(0xFFFAFCFF)

// Secondary palette (Amber / Orange seed)
private val Amber10  = Color(0xFF2C1600)
private val Amber20  = Color(0xFF4A2800)
private val Amber30  = Color(0xFF6A3D00)
private val Amber40  = Color(0xFFF57C00)  // seed
private val Amber80  = Color(0xFFFFB77A)
private val Amber90  = Color(0xFFFFDCBB)
private val Amber95  = Color(0xFFFFEDD7)
private val Amber99  = Color(0xFFFFF8F5)

// Tertiary palette (Green seed)
private val Green10  = Color(0xFF002108)
private val Green20  = Color(0xFF003912)
private val Green30  = Color(0xFF00531D)
private val Green40  = Color(0xFF2E7D32)  // seed
private val Green80  = Color(0xFF7EDB7E)
private val Green90  = Color(0xFFB8F0BB)
private val Green95  = Color(0xFFD5F8D8)
private val Green99  = Color(0xFFF3FFF3)

// Error palette (Red seed)
private val Red10  = Color(0xFF410002)
private val Red20  = Color(0xFF690005)
private val Red30  = Color(0xFF93000A)
private val Red40  = Color(0xFFC62828)  // seed
private val Red80  = Color(0xFFFFB4AB)
private val Red90  = Color(0xFFFFDAD6)
private val Red95  = Color(0xFFFFEDEA)
private val Red99  = Color(0xFFFFFBFF)

// Neutral / Surface palette
private val Neutral10  = Color(0xFF1A1C22)
private val Neutral20  = Color(0xFF2F3038)
private val Neutral30  = Color(0xFF45464F)
private val Neutral40  = Color(0xFF5D5E67)
private val Neutral70  = Color(0xFF999BA5)
private val Neutral80  = Color(0xFFB5B6C1)
private val Neutral90  = Color(0xFFE1E2EE)
private val Neutral95  = Color(0xFFF0F0FC)
private val Neutral99  = Color(0xFFFAFAFF)

// Neutral-Variant palette
private val NeutralVar10  = Color(0xFF171B2C)
private val NeutralVar20  = Color(0xFF2C3042)
private val NeutralVar30  = Color(0xFF43475F)
private val NeutralVar40  = Color(0xFF5B5F78)
private val NeutralVar80  = Color(0xFFC4CAD9)
private val NeutralVar90  = Color(0xFFE0E7F5)
private val NeutralVar99  = Color(0xFFFAFCFF)

// ── Light Color Scheme ───────────────────────────────────────────────────────

/**
 * ZyntaPOS Material 3 light [ColorScheme].
 *
 * Primary = Deep Blue (#1565C0) for POS primary actions.
 * Secondary = Amber (#F57C00) for warnings and pending states.
 * Tertiary = Green (#2E7D32) for success and synced states.
 * Error = Red (#C62828) for destructive / failed operations.
 */
fun zentaLightColorScheme(): ColorScheme = lightColorScheme(
    primary             = Blue40,
    onPrimary           = Color.White,
    primaryContainer    = Blue90,
    onPrimaryContainer  = Blue10,
    inversePrimary      = Blue80,

    secondary             = Amber40,
    onSecondary           = Color.White,
    secondaryContainer    = Amber90,
    onSecondaryContainer  = Amber10,

    tertiary             = Green40,
    onTertiary           = Color.White,
    tertiaryContainer    = Green90,
    onTertiaryContainer  = Green10,

    error             = Red40,
    onError           = Color.White,
    errorContainer    = Red90,
    onErrorContainer  = Red10,

    background        = Blue99,
    onBackground      = Neutral10,

    surface           = Blue99,
    onSurface         = Neutral10,
    surfaceVariant    = NeutralVar90,
    onSurfaceVariant  = NeutralVar30,
    surfaceTint       = Blue40,
    inverseSurface    = Neutral20,
    inverseOnSurface  = Neutral95,

    outline           = NeutralVar40,
    outlineVariant    = NeutralVar80,
    scrim             = Color.Black,
)

// ── Dark Color Scheme ────────────────────────────────────────────────────────

/**
 * ZyntaPOS Material 3 dark [ColorScheme].
 *
 * Roles invert compared to light — containers become the darker tones,
 * role colors become the lighter tonal values for legibility on dark surfaces.
 */
fun zentaDarkColorScheme(): ColorScheme = darkColorScheme(
    primary             = Blue80,
    onPrimary           = Blue20,
    primaryContainer    = Blue30,
    onPrimaryContainer  = Blue90,
    inversePrimary      = Blue40,

    secondary             = Amber80,
    onSecondary           = Amber20,
    secondaryContainer    = Amber30,
    onSecondaryContainer  = Amber90,

    tertiary             = Green80,
    onTertiary           = Green20,
    tertiaryContainer    = Green30,
    onTertiaryContainer  = Green90,

    error             = Red80,
    onError           = Red20,
    errorContainer    = Red30,
    onErrorContainer  = Red90,

    background        = Neutral10,
    onBackground      = Neutral90,

    surface           = Neutral10,
    onSurface         = Neutral90,
    surfaceVariant    = NeutralVar30,
    onSurfaceVariant  = NeutralVar80,
    surfaceTint       = Blue80,
    inverseSurface    = Neutral90,
    inverseOnSurface  = Neutral20,

    outline           = NeutralVar40,
    outlineVariant    = NeutralVar30,
    scrim             = Color.Black,
)

// ── Semantic color convenience aliases (for use in documentation / previews) ─

/** Brand primary — Deep Blue. Main CTAs, Pay button. */
val ZyntaColorPrimary       = Blue40

/** Brand secondary — Amber. Warnings, low-stock, pending sync. */
val ZyntaColorSecondary     = Amber40

/** Brand tertiary — Green. Success, payment complete, synced. */
val ZyntaColorTertiary      = Green40

/** Brand error — Red. Void, delete, failed sync. */
val ZyntaColorError         = Red40
