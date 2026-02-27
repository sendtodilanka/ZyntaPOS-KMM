package com.zyntasolutions.zyntapos.domain.usecase.feature

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.FeatureConfig
import com.zyntasolutions.zyntapos.domain.model.ZyntaEdition
import com.zyntasolutions.zyntapos.domain.model.ZyntaFeature
import com.zyntasolutions.zyntapos.domain.repository.FeatureRegistryRepository
import kotlinx.coroutines.flow.Flow

/**
 * Returns a reactive stream of all 23 [FeatureConfig] rows.
 *
 * Re-emits whenever any feature row is updated (e.g., via sync or [SetFeatureEnabledUseCase]).
 *
 * @param repo Persistence contract for feature flags.
 */
class GetAllFeatureConfigsUseCase(
    private val repo: FeatureRegistryRepository,
) {
    operator fun invoke(): Flow<List<FeatureConfig>> = repo.observeAll()
}

/**
 * One-shot check for whether a given [ZyntaFeature] is currently enabled.
 *
 * Prefer [GetAllFeatureConfigsUseCase] when the caller needs reactivity.
 *
 * @param repo Persistence contract for feature flags.
 */
class IsFeatureEnabledUseCase(
    private val repo: FeatureRegistryRepository,
) {
    suspend operator fun invoke(feature: ZyntaFeature): Boolean = repo.isEnabled(feature)
}

/**
 * Enables or disables a [ZyntaFeature], subject to edition guard.
 *
 * ### Business rules
 * - **STANDARD** features cannot be disabled — the system always requires them.
 *   Attempting to do so returns [Result.Error] with a [ValidationException].
 * - PREMIUM and ENTERPRISE features may be freely toggled by an authorised admin.
 *
 * @param repo Persistence contract for feature flags.
 */
class SetFeatureEnabledUseCase(
    private val repo: FeatureRegistryRepository,
) {
    /**
     * Toggles [feature] to [enabled].
     *
     * @param feature   The feature to modify.
     * @param enabled   Desired enabled state.
     * @param now       Epoch millis used as [FeatureConfig.activatedAt] and [FeatureConfig.updatedAt].
     * @param expiresAt Optional epoch millis when the licence expires; null = perpetual.
     * @return [Result.Success] on success; [Result.Error] wrapping [ValidationException] if
     *         attempting to disable a STANDARD feature, or a DB error on persistence failure.
     */
    suspend operator fun invoke(
        feature: ZyntaFeature,
        enabled: Boolean,
        now: Long,
        expiresAt: Long?,
    ): Result<Unit> {
        if (feature.edition == ZyntaEdition.STANDARD && !enabled) {
            return Result.Error(
                ValidationException(
                    message = "STANDARD feature '${feature.name}' cannot be disabled.",
                    field = "feature",
                    rule = "STANDARD_ALWAYS_ENABLED",
                ),
            )
        }
        return repo.setEnabled(feature, enabled, activatedAt = now, expiresAt = expiresAt)
    }
}
