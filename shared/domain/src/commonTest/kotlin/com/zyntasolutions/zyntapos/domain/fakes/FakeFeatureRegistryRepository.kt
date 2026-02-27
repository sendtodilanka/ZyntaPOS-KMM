package com.zyntasolutions.zyntapos.domain.fakes

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.FeatureConfig
import com.zyntasolutions.zyntapos.domain.model.ZyntaEdition
import com.zyntasolutions.zyntapos.domain.model.ZyntaFeature
import com.zyntasolutions.zyntapos.domain.repository.FeatureRegistryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory test double for [FeatureRegistryRepository].
 *
 * Pre-populated in `init` with default states that mirror [FeatureRegistryRepository.initDefaults]:
 * - STANDARD features → enabled
 * - PREMIUM  features → enabled
 * - ENTERPRISE features → disabled
 *
 * Set [shouldFail] to `true` to force all mutating calls to return [Result.Error].
 */
class FakeFeatureRegistryRepository : FeatureRegistryRepository {

    /** Toggle to force every mutating operation to return [Result.Error]. */
    var shouldFail: Boolean = false

    /** Mutable backing store — one entry per [ZyntaFeature]. */
    val storage: MutableMap<ZyntaFeature, FeatureConfig> = mutableMapOf()

    private val _allFlow = MutableStateFlow<List<FeatureConfig>>(emptyList())

    init {
        val now = 0L
        ZyntaFeature.entries.forEach { feature ->
            val enabled = feature.edition != ZyntaEdition.ENTERPRISE
            val config = FeatureConfig(
                feature = feature,
                isEnabled = enabled,
                activatedAt = if (enabled) now else null,
                expiresAt = null,
                updatedAt = now,
            )
            storage[feature] = config
        }
        _allFlow.value = storage.values.toList()
    }

    override fun observeAll(): Flow<List<FeatureConfig>> = _allFlow

    override fun observe(feature: ZyntaFeature): Flow<FeatureConfig> =
        MutableStateFlow(
            storage[feature] ?: FeatureConfig(
                feature = feature,
                isEnabled = false,
                activatedAt = null,
                expiresAt = null,
                updatedAt = 0L,
            ),
        )

    override suspend fun isEnabled(feature: ZyntaFeature): Boolean =
        storage[feature]?.isEnabled ?: false

    override suspend fun setEnabled(
        feature: ZyntaFeature,
        enabled: Boolean,
        activatedAt: Long,
        expiresAt: Long?,
    ): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("Forced failure"))
        val updated = FeatureConfig(
            feature = feature,
            isEnabled = enabled,
            activatedAt = if (enabled) activatedAt else storage[feature]?.activatedAt,
            expiresAt = expiresAt,
            updatedAt = activatedAt,
        )
        storage[feature] = updated
        _allFlow.value = storage.values.toList()
        return Result.Success(Unit)
    }

    override suspend fun initDefaults(now: Long): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("Forced failure"))
        ZyntaFeature.entries.forEach { feature ->
            if (!storage.containsKey(feature)) {
                val enabled = feature.edition != ZyntaEdition.ENTERPRISE
                storage[feature] = FeatureConfig(
                    feature = feature,
                    isEnabled = enabled,
                    activatedAt = if (enabled) now else null,
                    expiresAt = null,
                    updatedAt = now,
                )
            }
        }
        _allFlow.value = storage.values.toList()
        return Result.Success(Unit)
    }
}
