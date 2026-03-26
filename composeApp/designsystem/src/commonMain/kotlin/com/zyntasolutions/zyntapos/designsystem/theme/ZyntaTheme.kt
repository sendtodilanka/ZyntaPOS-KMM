package com.zyntasolutions.zyntapos.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import com.zyntasolutions.zyntapos.designsystem.tokens.LocalSpacing
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacingTokens

// ─────────────────────────────────────────────────────────────────────────────
// ZyntaTheme — Root Theme Composable for ZyntaPOS
//
// Responsibilities:
//   1. Wraps MaterialTheme with ZyntaPOS color, typography, and shape tokens.
//   2. Handles system dark-mode detection via isSystemInDarkTheme().
//   3. Supports manual dark/light toggle via LocalThemeMode CompositionLocal.
//   4. Provides LocalSpacing (ZyntaSpacingTokens) to the composition tree.
//   5. On Android 12+ enables Material You dynamic color via
//      dynamicDarkColorScheme() / dynamicLightColorScheme() — platform-specific
//      expect/actual call delegated to DynamicColorScheme.kt.
//
// Consumer access pattern:
//   val colors  = MaterialTheme.colorScheme
//   val typo    = MaterialTheme.typography
//   val shapes  = MaterialTheme.shapes
//   val spacing = LocalSpacing.current
//   val isDark  = LocalThemeMode.current == ThemeMode.DARK
// ─────────────────────────────────────────────────────────────────────────────

// ── Theme Mode ────────────────────────────────────────────────────────────────

/**
 * Represents the user-selectable or system-driven theme mode.
 *
 * [SYSTEM] follows the OS preference (default).
 * [LIGHT] and [DARK] force a specific mode regardless of OS setting.
 */
enum class ThemeMode {
    /** Follow the operating system light/dark preference. */
    SYSTEM,

    /** Force light mode. */
    LIGHT,

    /** Force dark mode. */
    DARK,

    /** High-contrast light mode for accessibility (WCAG AAA). */
    HIGH_CONTRAST_LIGHT,

    /** High-contrast dark mode for accessibility (WCAG AAA). */
    HIGH_CONTRAST_DARK,
}

/**
 * [CompositionLocal] that holds the current [ThemeMode].
 *
 * Override at the root of your composition to enable a manual theme toggle:
 * ```kotlin
 * CompositionLocalProvider(LocalThemeMode provides ThemeMode.DARK) {
 *     ZyntaTheme { /* ... */ }
 * }
 * ```
 */
val LocalThemeMode = compositionLocalOf { ThemeMode.SYSTEM }

// ── Dynamic Color (expect/actual per platform) ────────────────────────────────

/**
 * Platform-specific entry point for Material You dynamic color support.
 *
 * Android 12+ actual: returns [dynamicDarkColorScheme] / [dynamicLightColorScheme].
 * Desktop actual: returns null (dynamic color not supported on JVM).
 *
 * [ZyntaTheme] falls back to the static ZyntaPOS brand palette when this
 * returns null.
 *
 * @param isDark Whether the dark variant should be returned.
 * @return A platform dynamic [androidx.compose.material3.ColorScheme], or null.
 */
@Composable
expect fun zyntaDynamicColorScheme(isDark: Boolean): androidx.compose.material3.ColorScheme?

// ── ZyntaTheme ────────────────────────────────────────────────────────────────

/**
 * Root theme composable for ZyntaPOS.
 *
 * Apply once at the top of the composition tree (in `App.kt` or each
 * platform entry point). All nested Composables will inherit the ZyntaPOS
 * [MaterialTheme] tokens and [LocalSpacing].
 *
 * @param themeMode      Explicit mode override; defaults to [LocalThemeMode] ambient.
 * @param dynamicColor   Enable Material You dynamic color on supported platforms (Android 12+).
 *                       Ignored on Desktop. Default `true`.
 * @param content        The composable tree to theme.
 */
@Composable
fun ZyntaTheme(
    themeMode: ThemeMode    = LocalThemeMode.current,
    dynamicColor: Boolean   = true,
    content: @Composable () -> Unit,
) {
    val isSystemDark = isSystemInDarkTheme()

    val isHighContrast = themeMode == ThemeMode.HIGH_CONTRAST_LIGHT ||
        themeMode == ThemeMode.HIGH_CONTRAST_DARK

    val isDark = when (themeMode) {
        ThemeMode.LIGHT               -> false
        ThemeMode.DARK                -> true
        ThemeMode.HIGH_CONTRAST_LIGHT -> false
        ThemeMode.HIGH_CONTRAST_DARK  -> true
        ThemeMode.SYSTEM              -> isSystemDark
    }

    // Resolve color scheme: high-contrast overrides dynamic color.
    val colorScheme = if (isHighContrast) {
        if (isDark) zyntaHighContrastDarkColorScheme() else zyntaHighContrastLightColorScheme()
    } else {
        val dynamicScheme = if (dynamicColor) zyntaDynamicColorScheme(isDark) else null
        dynamicScheme ?: if (isDark) zentaDarkColorScheme() else zentaLightColorScheme()
    }

    CompositionLocalProvider(
        LocalThemeMode provides themeMode,
        LocalSpacing   provides ZyntaSpacingTokens(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = ZyntaTypography,
            shapes      = ZyntaShapes,
            content     = content,
        )
    }
}

// ── Convenience extension accessors ──────────────────────────────────────────

/** Shorthand accessor — `ZyntaTheme.spacing` inside composable scope. */
object ZyntaTheme {
    /** Current [ZyntaSpacingTokens] from [LocalSpacing]. */
    val spacing: ZyntaSpacingTokens
        @Composable @ReadOnlyComposable
        get() = LocalSpacing.current

    /** Current [ThemeMode] from [LocalThemeMode]. */
    val themeMode: ThemeMode
        @Composable @ReadOnlyComposable
        get() = LocalThemeMode.current

    /** Whether the current theme is dark. */
    val isDark: Boolean
        @Composable @ReadOnlyComposable
        get() = when (themeMode) {
            ThemeMode.LIGHT               -> false
            ThemeMode.DARK                -> true
            ThemeMode.HIGH_CONTRAST_LIGHT -> false
            ThemeMode.HIGH_CONTRAST_DARK  -> true
            ThemeMode.SYSTEM              -> isSystemInDarkTheme()
        }

    /** Whether the current theme uses high-contrast colors for accessibility. */
    val isHighContrast: Boolean
        @Composable @ReadOnlyComposable
        get() = themeMode == ThemeMode.HIGH_CONTRAST_LIGHT ||
            themeMode == ThemeMode.HIGH_CONTRAST_DARK
}
