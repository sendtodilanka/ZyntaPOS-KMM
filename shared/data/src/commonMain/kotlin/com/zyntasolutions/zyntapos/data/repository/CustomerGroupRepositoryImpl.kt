package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.Customer_groups
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.CustomerGroup
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.repository.CustomerGroupRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock

class CustomerGroupRepositoryImpl(
    private val db: ZyntaDatabase,
    private val syncEnqueuer: SyncEnqueuer,
) : CustomerGroupRepository {

    private val q get() = db.customer_groupsQueries

    override fun getAll(): Flow<List<CustomerGroup>> =
        q.getAllCustomerGroups()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::toDomain) }

    override suspend fun getById(id: String): Result<CustomerGroup> = withContext(Dispatchers.IO) {
        runCatching {
            q.getCustomerGroupById(id).executeAsOneOrNull()
                ?: return@withContext Result.Error(
                    DatabaseException("CustomerGroup not found: $id")
                )
        }.fold(
            onSuccess = { row -> Result.Success(toDomain(row)) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun insert(group: CustomerGroup): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            q.insertCustomerGroup(
                id = group.id,
                name = group.name,
                description = group.description,
                discount_type = group.discountType?.name,
                discount_value = group.discountValue,
                price_type = group.priceType.name,
                created_at = now,
                updated_at = now,
                sync_status = "PENDING",
            )
            syncEnqueuer.enqueue(SyncOperation.EntityType.CUSTOMER_GROUP, group.id, SyncOperation.Operation.INSERT)
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Insert failed", cause = t)) },
        )
    }

    override suspend fun update(group: CustomerGroup): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            q.updateCustomerGroup(
                name = group.name,
                description = group.description,
                discount_type = group.discountType?.name,
                discount_value = group.discountValue,
                price_type = group.priceType.name,
                updated_at = now,
                sync_status = "PENDING",
                id = group.id,
            )
            syncEnqueuer.enqueue(SyncOperation.EntityType.CUSTOMER_GROUP, group.id, SyncOperation.Operation.UPDATE)
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Update failed", cause = t)) },
        )
    }

    override suspend fun delete(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            q.softDeleteCustomerGroup(deleted_at = now, updated_at = now, id = id)
            syncEnqueuer.enqueue(SyncOperation.EntityType.CUSTOMER_GROUP, id, SyncOperation.Operation.DELETE)
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Delete failed", cause = t)) },
        )
    }

    private fun toDomain(row: Customer_groups) = CustomerGroup(
        id = row.id,
        name = row.name,
        description = row.description,
        discountType = row.discount_type?.let { runCatching { DiscountType.valueOf(it) }.getOrNull() },
        discountValue = row.discount_value,
        priceType = runCatching { CustomerGroup.PriceType.valueOf(row.price_type) }.getOrDefault(CustomerGroup.PriceType.RETAIL),
    )
}
