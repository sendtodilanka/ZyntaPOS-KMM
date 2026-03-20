package com.zyntasolutions.zyntapos.api.repository

import com.zyntasolutions.zyntapos.api.db.WarehouseStock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Data access for the `warehouse_stock` table (V28 / C1.2).
 *
 * Used by:
 * - [com.zyntasolutions.zyntapos.api.sync.EntityApplier] to persist WAREHOUSE_STOCK sync ops.
 * - [com.zyntasolutions.zyntapos.api.routes.AdminInventoryRoutes] for the cross-store comparison view.
 */
class WarehouseStockRepository {

    /** All stock rows for a single warehouse, ordered by product ID. */
    suspend fun getByWarehouse(warehouseId: String): List<WarehouseStockRow> =
        newSuspendedTransaction {
            WarehouseStock.selectAll()
                .where { WarehouseStock.warehouseId eq warehouseId }
                .orderBy(WarehouseStock.productId)
                .map { it.toRow() }
        }

    /** All stock rows for a product across every warehouse (cross-store view). */
    suspend fun getByProduct(productId: String): List<WarehouseStockRow> =
        newSuspendedTransaction {
            WarehouseStock.selectAll()
                .where { WarehouseStock.productId eq productId }
                .orderBy(WarehouseStock.warehouseId)
                .map { it.toRow() }
        }

    /**
     * All stock rows scoped to [storeId], optionally filtered by [productId].
     * Used by [com.zyntasolutions.zyntapos.api.routes.AdminInventoryRoutes].
     */
    suspend fun getByStore(storeId: String, productId: String?): List<WarehouseStockRow> =
        newSuspendedTransaction {
            var q = WarehouseStock.selectAll().where { WarehouseStock.storeId eq storeId }
            if (productId != null) q = q.adjustWhere { WarehouseStock.productId eq productId }
            q.orderBy(WarehouseStock.warehouseId).map { it.toRow() }
        }

    /** Global stock rows across ALL stores, optionally filtered by [productId]. */
    suspend fun getGlobal(productId: String?): List<WarehouseStockRow> =
        newSuspendedTransaction {
            val q = if (productId != null)
                WarehouseStock.selectAll().where { WarehouseStock.productId eq productId }
            else
                WarehouseStock.selectAll()
            q.orderBy(WarehouseStock.storeId to SortOrder.ASC, WarehouseStock.warehouseId to SortOrder.ASC).map { it.toRow() }
        }

    /** Upsert a stock row (ON CONFLICT on id — replace fully). */
    suspend fun upsert(row: WarehouseStockRow): Unit = newSuspendedTransaction {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        WarehouseStock.upsert(WarehouseStock.id) {
            it[WarehouseStock.id]          = row.id
            it[WarehouseStock.warehouseId] = row.warehouseId
            it[WarehouseStock.productId]   = row.productId
            it[WarehouseStock.storeId]     = row.storeId
            it[WarehouseStock.quantity]    = row.quantity
            it[WarehouseStock.minQuantity] = row.minQuantity
            it[WarehouseStock.syncVersion] = row.syncVersion
            it[WarehouseStock.updatedAt]   = now
        }
    }

    private fun ResultRow.toRow() = WarehouseStockRow(
        id          = this[WarehouseStock.id],
        warehouseId = this[WarehouseStock.warehouseId],
        productId   = this[WarehouseStock.productId],
        storeId     = this[WarehouseStock.storeId],
        quantity    = this[WarehouseStock.quantity],
        minQuantity = this[WarehouseStock.minQuantity],
        syncVersion = this[WarehouseStock.syncVersion],
        updatedAt   = this[WarehouseStock.updatedAt].toInstant().toEpochMilli(),
    )
}

// ── Row type ──────────────────────────────────────────────────────────────────

data class WarehouseStockRow(
    val id: String,
    val warehouseId: String,
    val productId: String,
    val storeId: String,
    val quantity: BigDecimal,
    val minQuantity: BigDecimal,
    val syncVersion: Long,
    val updatedAt: Long,
) {
    val isLowStock: Boolean get() = minQuantity > BigDecimal.ZERO && quantity <= minQuantity
}
