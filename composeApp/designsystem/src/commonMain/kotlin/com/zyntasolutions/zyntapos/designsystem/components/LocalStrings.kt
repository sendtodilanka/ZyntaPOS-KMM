package com.zyntasolutions.zyntapos.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.zyntasolutions.zyntapos.core.i18n.LocalizationManager
import com.zyntasolutions.zyntapos.core.i18n.StringResource

/**
 * CompositionLocal providing the [LocalizationManager] down the composition tree.
 *
 * Provided at the root in `App.kt` via `CompositionLocalProvider`.
 *
 * ### Usage in screen composables
 *
 * ```kotlin
 * @Composable
 * fun MyScreen() {
 *     val strings = LocalStrings.current
 *     Text(strings[StringResource.POS_CART_EMPTY])
 * }
 * ```
 */
val LocalStrings = staticCompositionLocalOf<StringResolver> {
    error("No StringResolver provided. Wrap your root composable with CompositionLocalProvider(LocalStrings provides ...)")
}

/**
 * Thin wrapper around [LocalizationManager] that provides a concise operator
 * syntax for resolving string resources in Composable functions.
 *
 * @property manager The backing [LocalizationManager] instance (from Koin).
 */
class StringResolver(private val manager: LocalizationManager) {

    /**
     * Resolve a [StringResource] to a translated string.
     *
     * ```kotlin
     * val strings = LocalStrings.current
     * Text(strings[StringResource.COMMON_SAVE])
     * ```
     */
    operator fun get(key: StringResource, vararg args: Any): String =
        manager.getString(key, *args)
}

/**
 * Convenience composable that returns the current [StringResolver].
 *
 * ```kotlin
 * @Composable
 * fun MyScreen() {
 *     val s = rememberStrings()
 *     Text(s[StringResource.COMMON_CANCEL])
 * }
 * ```
 */
@Composable
fun rememberStrings(): StringResolver = LocalStrings.current
