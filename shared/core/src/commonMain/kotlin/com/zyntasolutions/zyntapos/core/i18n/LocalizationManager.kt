package com.zyntasolutions.zyntapos.core.i18n

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Central localization manager for ZyntaPOS.
 *
 * Holds the current locale and the loaded translation table. UI layers observe
 * [currentLocale] to recompose when the language changes.
 *
 * ### Usage
 *
 * ```kotlin
 * // Resolve a string resource
 * val label = localizationManager.getString(StringResource.POS_CART_EMPTY)
 *
 * // With positional placeholders (%1, %2, ...)
 * val msg = localizationManager.getString(StringResource.ERROR_GENERIC, "details here")
 *
 * // Switch language (triggers recomposition)
 * localizationManager.setLocale(SupportedLocale.SI)
 * ```
 *
 * ### Thread Safety
 * All public methods are safe to call from any thread. The underlying maps are
 * replaced atomically — no locking required.
 */
class LocalizationManager {

    private val _currentLocale = MutableStateFlow(SupportedLocale.EN)

    /** Observable current locale. Compose UIs should collect this to recompose on change. */
    val currentLocale: StateFlow<SupportedLocale> = _currentLocale.asStateFlow()

    /**
     * Translation tables keyed by locale.
     * Each table maps [StringResource] → translated string.
     */
    private val tables = mutableMapOf<SupportedLocale, Map<StringResource, String>>()

    init {
        // Register the built-in English table by default
        tables[SupportedLocale.EN] = EnglishStrings.table
    }

    /**
     * Register a translation table for [locale].
     *
     * Call this during app initialization for each supported locale.
     * Replaces any previously registered table for the same locale.
     */
    fun registerStrings(locale: SupportedLocale, strings: Map<StringResource, String>) {
        tables[locale] = strings
    }

    /**
     * Switch the active locale.
     *
     * The locale must have a registered translation table (via [registerStrings]).
     * Falls back to [SupportedLocale.EN] if the requested locale has no table.
     */
    fun setLocale(locale: SupportedLocale) {
        _currentLocale.value = if (tables.containsKey(locale)) locale else SupportedLocale.EN
    }

    /**
     * Resolve a [StringResource] to a translated string.
     *
     * @param key The string resource key.
     * @param args Optional positional arguments. Placeholders `%1`, `%2`, etc. in
     *   the translation string are replaced in order.
     * @return The translated string, or the key name (e.g. `"POS_CART_EMPTY"`) if
     *   no translation is found (useful for catching missing keys during development).
     */
    fun getString(key: StringResource, vararg args: Any): String {
        val locale = _currentLocale.value
        val table = tables[locale] ?: tables[SupportedLocale.EN]
        var result = table?.get(key) ?: return key.name

        args.forEachIndexed { index, arg ->
            result = result.replace("%${index + 1}", arg.toString())
        }
        return result
    }

    /**
     * Check if a locale has a registered translation table.
     */
    fun hasLocale(locale: SupportedLocale): Boolean = tables.containsKey(locale)

    /**
     * Returns the set of [StringResource] keys that are missing from the given [locale].
     *
     * Useful for debug builds to validate translation completeness.
     */
    fun missingKeys(locale: SupportedLocale): Set<StringResource> {
        val table = tables[locale] ?: return StringResource.entries.toSet()
        return StringResource.entries.filter { it !in table }.toSet()
    }
}
