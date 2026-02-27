package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.Feature_config
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.FeatureConfig
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.model.ZyntaEdition
import com.zyntasolutions.zyntapos.domain.model.ZyntaFeature
import com.zyntasolutions.zyntapos.domain.repository.FeatureRegistryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Concrete implementation of [FeatureRegistryRepository] backed by the
 * `feature_config` SQLite table (SQLDelight).
 *
 * One row per [ZyntaFeature] enum entry (23 total).
 *
 * Thread-safety: all DB calls are dispatched on [Dispatchers.IO].
 * Idempotency: [initDefaults] uses `INSERT OR IGNORE` so re-running it
 * on every app launch is safe — existing rows are never overwritten.
 */
class FeatureRegistryRepositoryImpl(
    private val db: ZyntaDatabase,
    private val syncEnqueuer: SyncEnqueuer,
) : FeatureRegistryRepository {

    private val q get() = db.feature_configQueries

    // ── Read ──────────────────────────────────────────────────────────────────

    override fun observeAll(): Flow<List<FeatureConfig>> =
        q.getAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toModel() } }

    override fun observe(feature: ZyntaFeature): Flow<FeatureConfig> =
        q.getById(feature.name)
            .asFlow()
            .mapToOne(Dispatchers.IO)
            .map { it.toModel() }

    override suspend fun isEnabled(feature: ZyntaFeature): Boolean =
        withContext(Dispatchers.IO) {
            q.getById(feature.name).executeAsOneOrNull()?.is_enabled == 1L
        }

    // ── Write ─────────────────────────────────────────────────────────────────

    override suspend fun setEnabled(
        feature: ZyntaFeature,
        enabled: Boolean,
        activatedAt: Long,
        expiresAt: Long?,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            db.transaction {
                q.upsert(
                    feature_id   = feature.name,
                    is_enabled   = if (enabled) 1L else 0L,
                    edition      = feature.edition.name,
                    activated_at = if (enabled) activatedAt else null,
                    expires_at   = expiresAt,
                    updated_at   = activatedAt,
                )
                syncEnqueuer.enqueue(
                    SyncOperation.EntityType.SETTINGS,
                    feature.name,
                    SyncOperation.Operation.UPDATE,
                )
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t ->
                Result.Error(DatabaseException(t.message ?: "setEnabled failed", operation = "upsert feature_config", cause = t))
            },
        )
    }

    override suspend fun initDefaults(now: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            db.transaction {
                ZyntaFeature.entries.forEach { feature ->
                    val enabledDefault: Long = when (feature.edition) {
                        ZyntaEdition.STANDARD    -> 1L
                        ZyntaEdition.PREMIUM     -> 1L
                        ZyntaEdition.ENTERPRISE  -> 0L
                    }
                    q.initDefault(
                        feature_id   = feature.name,
                        is_enabled   = enabledDefault,
                        edition      = feature.edition.name,
                        activated_at = if (enabledDefault == 1L) now else null,
                        updated_at   = now,
                    )
                }
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t ->
                Result.Error(DatabaseException(t.message ?: "initDefaults failed", operation = "initDefault feature_config", cause = t))
            },
        )
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private fun Feature_config.toModel() = FeatureConfig(
        feature     = ZyntaFeature.valueOf(feature_id),
        isEnabled   = is_enabled == 1L,
        activatedAt = activated_at,
        expiresAt   = expires_at,
        updatedAt   = updated_at,
    )
}
