package com.zyntasolutions.zyntapos.api.repository

import com.zyntasolutions.zyntapos.api.db.PromotionsTable
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Read-only repository for the `promotions` table (C2.4).
 *
 * Used by [GET /v1/promotions] to serve active store promotions to POS devices for
 * offline-first evaluation. Write operations arrive via the sync engine EntityApplier.
 *
 * Per ADR-009: All write operations for promotions are store-level operations
 * performed from the KMM app, not the admin panel.
 */
class PromotionRepository {

    /**
     * Returns promotions applicable to [storeId] that are active and within the valid window.
     *
     * Includes:
     * - Global promotions (store_ids = '[]')
     * - Promotions that explicitly target [storeId] (store_ids JSON array contains [storeId])
     * - Promotions whose primary [PromotionsTable.storeId] matches [storeId]
     *
     * @param storeId     Store requesting promotions (extracted from JWT claim by caller)
     * @param nowEpochMs  Current time in epoch milliseconds for validity filtering
     */
    fun getActiveForStore(storeId: String, nowEpochMs: Long): List<PromotionRow> = transaction {
        PromotionsTable.selectAll()
            .where {
                (PromotionsTable.isActive eq true) and
                    (PromotionsTable.validFrom lessEq nowEpochMs or (PromotionsTable.validFrom.isNull())) and
                    (PromotionsTable.validTo greaterEq nowEpochMs or (PromotionsTable.validTo.isNull()))
            }
            .map { it.toRow() }
            .filter { row -> row.isApplicableTo(storeId) }
            .sortedByDescending { it.priority }
    }

    private fun PromotionRow.isApplicableTo(storeId: String): Boolean {
        if (this.storeId == storeId) return true
        if (storeIds == "[]" || storeIds.isBlank()) return true
        return runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(storeIds)
                .let { el ->
                    el is kotlinx.serialization.json.JsonArray &&
                        el.any { it.toString().trim('"') == storeId }
                }
        }.getOrDefault(false)
    }

    private fun ResultRow.toRow() = PromotionRow(
        id          = this[PromotionsTable.id],
        storeId     = this[PromotionsTable.storeId],
        name        = this[PromotionsTable.name],
        type        = this[PromotionsTable.type],
        config      = this[PromotionsTable.config],
        validFrom   = this[PromotionsTable.validFrom],
        validTo     = this[PromotionsTable.validTo],
        priority    = this[PromotionsTable.priority],
        isActive    = this[PromotionsTable.isActive],
        storeIds    = this[PromotionsTable.storeIds],
        syncVersion = this[PromotionsTable.syncVersion],
        updatedAt   = this[PromotionsTable.updatedAt].toString(),
    )
}

@Serializable
data class PromotionRow(
    val id          : String,
    val storeId     : String,
    val name        : String,
    /** Promotion type string — matches KMM PromotionType enum: BUY_X_GET_Y, BUNDLE, FLASH_SALE, SCHEDULED. */
    val type        : String,
    /** Typed config JSON matching KMM PromotionConfig sealed class variants. */
    val config      : String,
    val validFrom   : Long?,
    val validTo     : Long?,
    val priority    : Int,
    val isActive    : Boolean,
    /** JSON array of targeted store IDs; "[]" = global (applies to all stores). */
    val storeIds    : String,
    val syncVersion : Long,
    val updatedAt   : String,
)
