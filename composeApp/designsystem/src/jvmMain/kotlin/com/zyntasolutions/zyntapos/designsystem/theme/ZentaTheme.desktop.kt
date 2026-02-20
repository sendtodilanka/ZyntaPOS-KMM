package com.zyntasolutions.zyntapos.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

// ─────────────────────────────────────────────────────────────────────────────
// Desktop (JVM) actual — zentaDynamicColorScheme
//
// Material You dynamic color is an Android 12+ feature and has no equivalent
// on the JVM desktop platform. Always returns null so ZentaTheme falls back
// to the static ZentaPOS brand palette on all desktop targets.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
actual fun zentaDynamicColorScheme(isDark: Boolean): ColorScheme? = null
