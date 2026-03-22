package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.Regional_tax_overrides
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.RegionalTaxOverride
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.repository.RegionalTaxOverrideRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

class RegionalTaxOverrideRepositoryImpl(
    private val db: ZyntaDatabase,
    private val syncEnqueuer: SyncEnqueuer,
) : RegionalTaxOverrideRepository {

    @Serializable
    private data class RegionalTaxSyncPayload(
        val id: String,
        @SerialName("tax_group_id")             val taxGroupId: String,
        @SerialName("store_id")                 val storeId: String,
        @SerialName("effective_rate")           val effectiveRate: Double,
        @SerialName("jurisdiction_code")        val jurisdictionCode: String = "",
        @SerialName("tax_registration_number")  val taxRegistrationNumber: String = "",
        @SerialName("valid_from")               val validFrom: Long? = null,
        @SerialName("valid_to")                 val validTo: Long? = null,
        @SerialName("is_active")                val isActive: Boolean = true,
        @SerialName("updated_at")               val updatedAt: Long = 0L,
    )

    companion object {
        private val syncJson = Json { ignoreUnknownKeys = true; isLenient = true }
    }

    private val q get() = db.regional_tax_overridesQueries

    override fun getOverridesForStore(storeId: String): Flow<List<RegionalTaxOverride>> =
        q.getOverridesForStore(storeId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::toDomain) }

    override suspend fun getEffectiveOverride(
        taxGroupId: String,
        storeId: String,
        nowEpochMs: Long,
    ): Result<RegionalTaxOverride?> = withContext(Dispatchers.IO) {
        runCatching {
            q.getEffectiveOverride(taxGroupId, storeId, nowEpochMs, nowEpochMs)
                .executeAsOneOrNull()
        }.fold(
            onSuccess = { row -> Result.Success(row?.let(::toDomain)) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override fun getOverridesForTaxGroup(taxGroupId: String): Flow<List<RegionalTaxOverride>> =
        q.getOverridesForTaxGroup(taxGroupId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::toDomain) }

    override suspend fun upsert(override: RegionalTaxOverride): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                q.insertOverride(
                    id = override.id,
                    tax_group_id = override.taxGroupId,
                    store_id = override.storeId,
                    effective_rate = override.effectiveRate,
                    jurisdiction_code = override.jurisdictionCode,
                    tax_registration_number = override.taxRegistrationNumber,
                    valid_from = override.validFrom,
                    valid_to = override.validTo,
                    is_active = if (override.isActive) 1L else 0L,
                    created_at = override.createdAt.takeIf { it > 0 } ?: now,
                    updated_at = now,
                    sync_status = "PENDING",
                )
                syncEnqueuer.enqueue("regional_tax_override", override.id, SyncOperation.Operation.UPDATE)
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun delete(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            db.transaction {
                q.deleteOverride(id)
                syncEnqueuer.enqueue("regional_tax_override", id, SyncOperation.Operation.DELETE)
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    /**
     * Applies a server-authoritative regional tax override from a sync delta payload.
     * Uses INSERT OR REPLACE for idempotent upsert with sync_status = SYNCED.
     */
    suspend fun upsertFromSync(payload: String) = withContext(Dispatchers.IO) {
        val dto = syncJson.decodeFromString<RegionalTaxSyncPayload>(payload)
        val isActive = if (dto.isActive) 1L else 0L
        val now = Clock.System.now().toEpochMilliseconds()
        q.insertOverride(
            id = dto.id,
            tax_group_id = dto.taxGroupId,
            store_id = dto.storeId,
            effective_rate = dto.effectiveRate,
            jurisdiction_code = dto.jurisdictionCode,
            tax_registration_number = dto.taxRegistrationNumber,
            valid_from = dto.validFrom,
            valid_to = dto.validTo,
            is_active = isActive,
            created_at = dto.updatedAt.takeIf { it > 0 } ?: now,
            updated_at = dto.updatedAt,
            sync_status = "SYNCED",
        )
    }

    private fun toDomain(row: Regional_tax_overrides): RegionalTaxOverride = RegionalTaxOverride(
        id = row.id,
        taxGroupId = row.tax_group_id,
        storeId = row.store_id,
        effectiveRate = row.effective_rate,
        jurisdictionCode = row.jurisdiction_code,
        taxRegistrationNumber = row.tax_registration_number,
        validFrom = row.valid_from,
        validTo = row.valid_to,
        isActive = row.is_active == 1L,
        createdAt = row.created_at,
        updatedAt = row.updated_at,
    )
}
