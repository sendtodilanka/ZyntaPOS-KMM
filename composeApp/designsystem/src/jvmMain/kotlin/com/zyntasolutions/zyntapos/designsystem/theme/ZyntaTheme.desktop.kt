package com.zyntasolutions.zyntapos.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

// ─────────────────────────────────────────────────────────────────────────────
// Desktop (JVM) actual — zyntaDynamicColorScheme
//
// Material You dynamic color is an Android 12+ feature and has no equivalent
// on the JVM desktop platform. Always returns null so ZyntaTheme falls back
// to the static ZyntaPOS brand palette on all desktop targets.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
actual fun zyntaDynamicColorScheme(isDark: Boolean): ColorScheme? = null
