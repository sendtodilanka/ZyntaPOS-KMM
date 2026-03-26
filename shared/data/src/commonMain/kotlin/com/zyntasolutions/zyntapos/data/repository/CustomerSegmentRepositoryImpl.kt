package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.Customer_segments
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.CustomerSegment
import com.zyntasolutions.zyntapos.domain.model.SegmentField
import com.zyntasolutions.zyntapos.domain.model.SegmentOperator
import com.zyntasolutions.zyntapos.domain.model.SegmentRule
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.repository.CustomerSegmentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * Concrete implementation of [CustomerSegmentRepository].
 *
 * Segment rules are persisted as a JSON array in the `rules_json` TEXT column
 * and deserialized back to domain [SegmentRule] instances on read.
 */
class CustomerSegmentRepositoryImpl(
    private val db: ZyntaDatabase,
    private val syncEnqueuer: SyncEnqueuer,
) : CustomerSegmentRepository {

    private val q get() = db.customer_segmentsQueries

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ── Read ──────────────────────────────────────────────────────────────

    override fun getAll(): Flow<List<CustomerSegment>> =
        q.getAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::toDomain) }

    override suspend fun getById(id: String): Result<CustomerSegment> = withContext(Dispatchers.IO) {
        runCatching {
            q.getById(id).executeAsOneOrNull()
                ?: return@withContext Result.Error(
                    DatabaseException("Customer segment not found: $id", operation = "getById"),
                )
        }.fold(
            onSuccess = { Result.Success(toDomain(it)) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun getByName(name: String): Result<CustomerSegment> = withContext(Dispatchers.IO) {
        runCatching {
            q.getByName(name).executeAsOneOrNull()
                ?: return@withContext Result.Error(
                    DatabaseException("Customer segment not found: $name", operation = "getByName"),
                )
        }.fold(
            onSuccess = { Result.Success(toDomain(it)) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    // ── Write ─────────────────────────────────────────────────────────────

    override suspend fun insert(segment: CustomerSegment): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            q.insert(
                id = segment.id,
                name = segment.name,
                description = segment.description,
                rules_json = encodeRules(segment.rules),
                is_automatic = if (segment.isAutomatic) 1L else 0L,
                customer_count = segment.customerCount.toLong(),
                created_at = now,
                updated_at = now,
                sync_status = "PENDING",
            )
            syncEnqueuer.enqueue(
                SyncOperation.EntityType.CUSTOMER,
                segment.id,
                SyncOperation.Operation.INSERT,
            )
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Insert failed", cause = t)) },
        )
    }

    override suspend fun update(segment: CustomerSegment): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            q.update(
                name = segment.name,
                description = segment.description,
                rules_json = encodeRules(segment.rules),
                is_automatic = if (segment.isAutomatic) 1L else 0L,
                customer_count = segment.customerCount.toLong(),
                updated_at = now,
                sync_status = "PENDING",
                id = segment.id,
            )
            syncEnqueuer.enqueue(
                SyncOperation.EntityType.CUSTOMER,
                segment.id,
                SyncOperation.Operation.UPDATE,
            )
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Update failed", cause = t)) },
        )
    }

    override suspend fun delete(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            q.delete(id)
            syncEnqueuer.enqueue(
                SyncOperation.EntityType.CUSTOMER,
                id,
                SyncOperation.Operation.DELETE,
            )
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Delete failed", cause = t)) },
        )
    }

    // ── Mapping ───────────────────────────────────────────────────────────

    private fun toDomain(row: Customer_segments): CustomerSegment = CustomerSegment(
        id = row.id,
        name = row.name,
        description = row.description,
        rules = decodeRules(row.rules_json),
        isAutomatic = row.is_automatic == 1L,
        customerCount = row.customer_count.toInt(),
        createdAt = row.created_at,
        updatedAt = row.updated_at,
    )

    // ── JSON serialization (data-layer only) ──────────────────────────────

    @Serializable
    private data class SegmentRuleDto(
        val field: String,
        val operator: String,
        val value: String,
    )

    private fun encodeRules(rules: List<SegmentRule>): String {
        val dtos = rules.map { rule ->
            SegmentRuleDto(
                field = rule.field.name,
                operator = rule.operator.name,
                value = rule.value,
            )
        }
        return json.encodeToString(dtos)
    }

    private fun decodeRules(rulesJson: String): List<SegmentRule> {
        if (rulesJson.isBlank() || rulesJson == "[]") return emptyList()
        return runCatching {
            json.decodeFromString<List<SegmentRuleDto>>(rulesJson).mapNotNull { dto ->
                val field = runCatching { SegmentField.valueOf(dto.field) }.getOrNull() ?: return@mapNotNull null
                val operator = runCatching { SegmentOperator.valueOf(dto.operator) }.getOrNull() ?: return@mapNotNull null
                SegmentRule(field = field, operator = operator, value = dto.value)
            }
        }.getOrElse { emptyList() }
    }
}
