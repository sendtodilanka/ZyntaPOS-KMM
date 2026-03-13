package com.zyntasolutions.zyntapos.api.repository

import com.zyntasolutions.zyntapos.api.db.Stores
import com.zyntasolutions.zyntapos.api.db.SyncQueue
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.math.ceil

class AdminStoresRepositoryImpl : AdminStoresRepository {

    override suspend fun list(
        search:   String?,
        isActive: Boolean?,
        page:     Int,
        size:     Int,
    ): StoreAdminPage = newSuspendedTransaction {
        var query = Stores.selectAll()

        if (!search.isNullOrBlank()) {
            val term = "%${search.lowercase()}%"
            query = query.adjustWhere {
                (Stores.name.lowerCase() like term) or
                (Stores.id.lowerCase() like term)
            }
        }
        if (isActive != null) {
            query = query.adjustWhere { Stores.isActive eq isActive }
        }

        val total = query.count()
        val items = query
            .orderBy(Stores.createdAt, SortOrder.DESC)
            .limit(size).offset((page * size).toLong())
            .map { it.toRow() }

        StoreAdminPage(
            data       = items,
            page       = page,
            size       = size,
            total      = total.toInt(),
            totalPages = ceil(total.toDouble() / size).toInt(),
        )
    }

    override suspend fun findById(storeId: String): StoreAdminRow? = newSuspendedTransaction {
        Stores.selectAll().where { Stores.id eq storeId }.singleOrNull()?.toRow()
    }

    override suspend fun update(storeId: String, patch: StoreConfigPatch): StoreAdminRow? =
        newSuspendedTransaction {
            val exists = Stores.selectAll().where { Stores.id eq storeId }.any()
            if (!exists) return@newSuspendedTransaction null

            val now = OffsetDateTime.now(ZoneOffset.UTC)
            Stores.update({ Stores.id eq storeId }) { stmt ->
                patch.timezone?.let { stmt[Stores.timezone] = it }
                patch.currency?.let { stmt[Stores.currency] = it }
                stmt[Stores.updatedAt] = now
            }
            Stores.selectAll().where { Stores.id eq storeId }.single().toRow()
        }

    override suspend fun countPendingOps(storeId: String): Int = newSuspendedTransaction {
        SyncQueue.selectAll()
            .where { (SyncQueue.storeId eq storeId) and (SyncQueue.isProcessed eq false) }
            .count()
            .toInt()
    }

    override suspend fun listAllWithPendingOps(): List<Pair<StoreAdminRow, Int>> =
        newSuspendedTransaction {
            val stores = Stores.selectAll().map { it.toRow() }
            stores.map { store ->
                val pending = SyncQueue.selectAll()
                    .where { (SyncQueue.storeId eq store.id) and (SyncQueue.isProcessed eq false) }
                    .count()
                    .toInt()
                store to pending
            }
        }

    override suspend fun findStoreNames(ids: List<String>): Map<String, String> =
        newSuspendedTransaction {
            if (ids.isEmpty()) emptyMap()
            else Stores.selectAll()
                .where { Stores.id inList ids }
                .associate { it[Stores.id] to it[Stores.name] }
        }

    // ── Mapping helper ────────────────────────────────────────────────────────

    private fun ResultRow.toRow() = StoreAdminRow(
        id         = this[Stores.id],
        name       = this[Stores.name],
        licenseKey = this[Stores.licenseKey],
        timezone   = this[Stores.timezone],
        currency   = this[Stores.currency],
        isActive   = this[Stores.isActive],
        createdAt  = this[Stores.createdAt].toInstant().toString(),
        updatedAt  = this[Stores.updatedAt].toInstant().toString(),
    )
}
