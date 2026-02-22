package com.zyntasolutions.zyntapos.designsystem.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// ─────────────────────────────────────────────────────────────────────────────
// Android actual — zyntaDynamicColorScheme
//
// On Android 12+ (API 31+) Material You generates a dynamic color scheme
// derived from the user's wallpaper. On older APIs we return null so ZyntaTheme
// falls back to the static ZyntaPOS brand palette.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
actual fun zyntaDynamicColorScheme(isDark: Boolean): ColorScheme? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (isDark) dynamicDarkColorScheme(context)
        else        dynamicLightColorScheme(context)
    } else {
        null  // Pre-S: fall back to static ZyntaPOS brand palette
    }
}
