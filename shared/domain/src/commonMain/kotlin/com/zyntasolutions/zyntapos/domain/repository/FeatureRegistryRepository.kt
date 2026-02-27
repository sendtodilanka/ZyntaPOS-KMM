package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.FeatureConfig
import com.zyntasolutions.zyntapos.domain.model.ZyntaFeature
import kotlinx.coroutines.flow.Flow

/**
 * Contract for persisting and observing [FeatureConfig] rows.
 *
 * Implementations store one row per [ZyntaFeature] in the local SQLite database.
 * The cloud sync engine may overwrite rows when a licence change is pulled from
 * the backend; local overrides made via [setEnabled] are always written first and
 * reconciled during the next sync cycle.
 */
interface FeatureRegistryRepository {

    /** Emits the full list of 23 feature configs whenever any row changes. */
    fun observeAll(): Flow<List<FeatureConfig>>

    /**
     * Emits the [FeatureConfig] for a single [feature].
     * Re-emits whenever that row is updated.
     */
    fun observe(feature: ZyntaFeature): Flow<FeatureConfig>

    /**
     * One-shot check for whether [feature] is currently enabled.
     *
     * Prefer [observe] when the calling composable/ViewModel needs reactivity.
     */
    suspend fun isEnabled(feature: ZyntaFeature): Boolean

    /**
     * Enables or disables a feature, recording [activatedAt] and optional [expiresAt].
     *
     * Implementations must persist the change atomically and enqueue a sync operation
     * so the state is pushed to the backend on the next sync cycle.
     *
     * @param feature     The feature to modify.
     * @param enabled     New enabled state.
     * @param activatedAt Epoch millis representing the moment of activation (pass current time).
     * @param expiresAt   Optional epoch millis for licence expiry; null means perpetual.
     * @return [Result.Success] on write success; [Result.Error] on DB failure.
     */
    suspend fun setEnabled(
        feature: ZyntaFeature,
        enabled: Boolean,
        activatedAt: Long,
        expiresAt: Long?,
    ): Result<Unit>

    /**
     * Idempotent seed: writes default [FeatureConfig] rows for all 23 features.
     *
     * Default policy:
     * - STANDARD and PREMIUM features → enabled
     * - ENTERPRISE features → disabled
     *
     * Safe to call on every app start; implementations should use INSERT OR IGNORE
     * (or equivalent) so existing rows are never overwritten.
     *
     * @param now Epoch millis used as [FeatureConfig.updatedAt] for seeded rows.
     * @return [Result.Success] when seeding completes; [Result.Error] on DB failure.
     */
    suspend fun initDefaults(now: Long): Result<Unit>
}
