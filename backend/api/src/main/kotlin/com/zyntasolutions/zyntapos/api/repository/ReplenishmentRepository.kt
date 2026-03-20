package com.zyntasolutions.zyntapos.api.repository

import com.zyntasolutions.zyntapos.api.db.ReplenishmentRules
import com.zyntasolutions.zyntapos.api.db.WarehouseStock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Data access for the `replenishment_rules` table and replenishment suggestions (C1.5 — V31).
 *
 * Used by [com.zyntasolutions.zyntapos.api.routes.AdminReplenishmentRoutes].
 */
class ReplenishmentRepository {

    // ── Rules CRUD ─────────────────────────────────────────────────────────────

    /** Returns all replenishment rules, optionally filtered by warehouseId. */
    suspend fun getRules(warehouseId: String? = null): List<ReplenishmentRuleRow> =
        newSuspendedTransaction {
            val query = ReplenishmentRules.selectAll()
            if (warehouseId != null) query.where { ReplenishmentRules.warehouseId eq warehouseId }
            query.map { it.toRow() }
        }

    /** Returns a single rule by [id], or null if not found. */
    suspend fun getRuleById(id: String): ReplenishmentRuleRow? =
        newSuspendedTransaction {
            ReplenishmentRules.selectAll()
                .where { ReplenishmentRules.id eq id }
                .firstOrNull()?.toRow()
        }

    /** Upserts a replenishment rule (insert or update on conflict). */
    suspend fun upsertRule(row: ReplenishmentRuleRow): Unit = newSuspendedTransaction {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val existing = ReplenishmentRules.selectAll()
            .where { ReplenishmentRules.id eq row.id }
            .firstOrNull()

        if (existing == null) {
            ReplenishmentRules.insert {
                it[id]           = row.id
                it[productId]    = row.productId
                it[warehouseId]  = row.warehouseId
                it[supplierId]   = row.supplierId
                it[reorderPoint] = BigDecimal.valueOf(row.reorderPoint)
                it[reorderQty]   = BigDecimal.valueOf(row.reorderQty)
                it[autoApprove]  = row.autoApprove
                it[isActive]     = row.isActive
                it[createdBy]    = row.createdBy
                it[createdAt]    = now
                it[updatedAt]    = now
            }
        } else {
            ReplenishmentRules.update({ ReplenishmentRules.id eq row.id }) {
                it[supplierId]   = row.supplierId
                it[reorderPoint] = BigDecimal.valueOf(row.reorderPoint)
                it[reorderQty]   = BigDecimal.valueOf(row.reorderQty)
                it[autoApprove]  = row.autoApprove
                it[isActive]     = row.isActive
                it[updatedAt]    = now
            }
        }
    }

    /** Deletes a replenishment rule by [id]. */
    suspend fun deleteRule(id: String): Int = newSuspendedTransaction {
        ReplenishmentRules.deleteWhere { ReplenishmentRules.id eq id }
    }

    // ── Replenishment Suggestions ──────────────────────────────────────────────

    /**
     * Returns products that are at or below their reorder point for a given warehouse.
     * Joins `warehouse_stock` with `replenishment_rules` to produce suggestions.
     *
     * @param warehouseId Filter to a specific warehouse; null returns all warehouses.
     */
    suspend fun getSuggestions(warehouseId: String? = null): List<ReplenishmentSuggestionRow> =
        newSuspendedTransaction {
            // Use explicit join condition because ReplenishmentRules and WarehouseStock share
            // column names (product_id, warehouse_id) and have no FK relationship — Exposed's
            // auto-join inference would fail with IllegalStateException without additionalConstraint.
            val join = ReplenishmentRules.join(
                WarehouseStock,
                JoinType.INNER,
                additionalConstraint = {
                    (ReplenishmentRules.productId eq WarehouseStock.productId) and
                    (ReplenishmentRules.warehouseId eq WarehouseStock.warehouseId)
                },
            )

            val query = join
                .select(
                    ReplenishmentRules.id,
                    ReplenishmentRules.productId,
                    ReplenishmentRules.warehouseId,
                    ReplenishmentRules.supplierId,
                    ReplenishmentRules.reorderPoint,
                    ReplenishmentRules.reorderQty,
                    ReplenishmentRules.autoApprove,
                    WarehouseStock.quantity,
                )
                .where {
                    (ReplenishmentRules.isActive eq true) and
                    (WarehouseStock.quantity.lessEq(ReplenishmentRules.reorderPoint))
                }

            if (warehouseId != null) query.andWhere { ReplenishmentRules.warehouseId eq warehouseId }

            query.map { row ->
                ReplenishmentSuggestionRow(
                    ruleId       = row[ReplenishmentRules.id],
                    productId    = row[ReplenishmentRules.productId],
                    warehouseId  = row[ReplenishmentRules.warehouseId],
                    supplierId   = row[ReplenishmentRules.supplierId],
                    currentStock = row[WarehouseStock.quantity].toDouble(),
                    reorderPoint = row[ReplenishmentRules.reorderPoint].toDouble(),
                    reorderQty   = row[ReplenishmentRules.reorderQty].toDouble(),
                    autoApprove  = row[ReplenishmentRules.autoApprove],
                )
            }
        }

    // ── Mapper ─────────────────────────────────────────────────────────────────

    private fun ResultRow.toRow() = ReplenishmentRuleRow(
        id           = this[ReplenishmentRules.id],
        productId    = this[ReplenishmentRules.productId],
        warehouseId  = this[ReplenishmentRules.warehouseId],
        supplierId   = this[ReplenishmentRules.supplierId],
        reorderPoint = this[ReplenishmentRules.reorderPoint].toDouble(),
        reorderQty   = this[ReplenishmentRules.reorderQty].toDouble(),
        autoApprove  = this[ReplenishmentRules.autoApprove],
        isActive     = this[ReplenishmentRules.isActive],
        createdBy    = this[ReplenishmentRules.createdBy],
        updatedAt    = this[ReplenishmentRules.updatedAt].toEpochSecond() * 1000L,
    )
}

// ── Row types ─────────────────────────────────────────────────────────────────

data class ReplenishmentRuleRow(
    val id: String,
    val productId: String,
    val warehouseId: String,
    val supplierId: String,
    val reorderPoint: Double,
    val reorderQty: Double,
    val autoApprove: Boolean,
    val isActive: Boolean,
    val createdBy: String?,
    val updatedAt: Long,
)

data class ReplenishmentSuggestionRow(
    val ruleId: String,
    val productId: String,
    val warehouseId: String,
    val supplierId: String,
    val currentStock: Double,
    val reorderPoint: Double,
    val reorderQty: Double,
    val autoApprove: Boolean,
)
