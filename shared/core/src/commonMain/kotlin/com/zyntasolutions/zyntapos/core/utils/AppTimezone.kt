package com.zyntasolutions.zyntapos.core.utils

import kotlinx.datetime.TimeZone

/**
 * Application-wide timezone registry.
 *
 * All date/time display in ZyntaPOS is relative to the store's configured timezone.
 * This object acts as the single source of truth so that [DateTimeUtils] and
 * [com.zyntasolutions.zyntapos.core.extensions] automatically honour the store
 * setting without needing to thread a timezone parameter through every call site.
 *
 * ### Lifecycle
 * 1. On app startup, [SettingsViewModel] reads `"general.timezone"` from SQLite
 *    and calls [set] immediately after loading general settings.
 * 2. When the user saves a new timezone in Settings, [SettingsViewModel] calls [set]
 *    again so that subsequent display calls use the new value instantly.
 *
 * ### Default
 * Falls back to `Asia/Colombo` (UTC+5:30, Sri Lanka Standard Time) if no setting
 * has been persisted yet — correct for the primary market.
 *
 * ### Thread safety
 * Reads and writes are both on the main/UI thread via [SettingsViewModel]; no
 * concurrent mutation is expected in practice. If that changes, guard with a
 * `@Volatile` backing field or atomic reference.
 */
object AppTimezone {

    private val DEFAULT = TimeZone.of("Asia/Colombo")

    /**
     * The IANA timezone identifier to use throughout the app for display.
     *
     * Marked `@Volatile` so that any background thread reading this field (e.g.,
     * a sync coroutine formatting a timestamp) always sees the latest value written
     * by [SettingsViewModel] on the main thread.
     */
    @Volatile
    private var _current: TimeZone = DEFAULT

    val current: TimeZone
        get() = _current

    /**
     * Updates the active timezone from a stored IANA identifier string
     * (e.g. `"Asia/Colombo"`, `"America/New_York"`).
     *
     * Invalid identifiers are silently ignored and the current value is retained.
     */
    fun set(tzId: String) {
        _current = runCatching { TimeZone.of(tzId) }.getOrDefault(_current)
    }

    /** The IANA ID of the current timezone (for persistence / display). */
    val id: String get() = _current.id
}
