package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Contract for a typed key-value settings store.
 *
 * All values are persisted as [String] and converted to strongly-typed wrappers
 * at the use-case or ViewModel layer. This keeps the repository interface simple
 * and avoids coupling it to any serialisation library.
 *
 * Changes made via [set] are immediately observable through [observe].
 *
 * ### Recommended key constants
 * Define all known setting keys in a companion `object Keys` in the implementation
 * or in a standalone `SettingsKeys` object to prevent magic strings in callers.
 */
interface SettingsRepository {

    /**
     * Returns the value associated with [key], or `null` if the key has never been set.
     *
     * This is a one-shot read; for reactive observation use [observe].
     */
    suspend fun get(key: String): String?

    /**
     * Persists [value] for [key], overwriting any existing value.
     *
     * Triggers a new emission from any active [observe] collector for the same [key].
     *
     * @return [Result.Error] with [com.zyntasolutions.zyntapos.core.result.ZyntaException.DatabaseException]
     *         on a storage failure.
     */
    suspend fun set(key: String, value: String): Result<Unit>

    /**
     * Returns a snapshot of all currently-stored key–value pairs.
     *
     * Primarily used for backup/export and the Settings home screen summary.
     * Not reactive — call [observe] if live updates are needed for a specific key.
     */
    suspend fun getAll(): Map<String, String>

    /**
     * Returns a [Flow] that emits the current value for [key] on collection,
     * and re-emits whenever that key's value changes via [set].
     *
     * Emits `null` if the key does not (yet) exist.
     *
     * Example usage in a ViewModel:
     * ```kotlin
     * settingsRepository.observe(SettingsKeys.MAX_DISCOUNT_PERCENT)
     *     .map { it?.toDoubleOrNull() ?: 20.0 }
     *     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 20.0)
     * ```
     */
    fun observe(key: String): Flow<String?>
}
