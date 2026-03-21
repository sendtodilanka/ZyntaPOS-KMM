package com.zyntasolutions.zyntapos.api.repository

import com.zyntasolutions.zyntapos.api.db.PricingRules
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Data access for pricing rules (C2.1 Region-Based Pricing).
 *
 * Provides CRUD for store-specific and time-bounded product price overrides.
 */
class PricingRuleRepository {

    fun getRules(productId: String? = null, storeId: String? = null): List<PricingRuleRow> = transaction {
        val query = PricingRules.selectAll()
        productId?.let { query.andWhere { PricingRules.productId eq UUID.fromString(it) } }
        storeId?.let { query.andWhere { PricingRules.storeId eq UUID.fromString(it) } }
        query.orderBy(PricingRules.priority, SortOrder.DESC)
            .map { it.toRow() }
    }

    fun getRuleById(id: String): PricingRuleRow? = transaction {
        PricingRules.selectAll()
            .where { PricingRules.id eq UUID.fromString(id) }
            .firstOrNull()?.toRow()
    }

    fun upsertRule(
        id: String? = null,
        productId: String,
        storeId: String?,
        price: Double,
        costPrice: Double?,
        priority: Int,
        validFrom: OffsetDateTime?,
        validTo: OffsetDateTime?,
        isActive: Boolean,
        description: String,
    ): PricingRuleRow = transaction {
        val ruleId = id?.let { UUID.fromString(it) } ?: UUID.randomUUID()
        val now = OffsetDateTime.now()

        PricingRules.upsert(PricingRules.id) {
            it[PricingRules.id] = ruleId
            it[PricingRules.productId] = UUID.fromString(productId)
            it[PricingRules.storeId] = storeId?.let { s -> UUID.fromString(s) }
            it[PricingRules.price] = price.toBigDecimal()
            it[PricingRules.costPrice] = costPrice?.toBigDecimal()
            it[PricingRules.priority] = priority
            it[PricingRules.validFrom] = validFrom
            it[PricingRules.validTo] = validTo
            it[PricingRules.isActive] = isActive
            it[PricingRules.description] = description
            it[PricingRules.createdAt] = now
            it[PricingRules.updatedAt] = now
        }

        getRuleById(ruleId.toString())!!
    }

    fun deleteRule(id: String): Boolean = transaction {
        PricingRules.deleteWhere { PricingRules.id eq UUID.fromString(id) } > 0
    }

    private fun ResultRow.toRow() = PricingRuleRow(
        id = this[PricingRules.id].toString(),
        productId = this[PricingRules.productId].toString(),
        storeId = this[PricingRules.storeId]?.toString(),
        price = this[PricingRules.price].toDouble(),
        costPrice = this[PricingRules.costPrice]?.toDouble(),
        priority = this[PricingRules.priority],
        validFrom = this[PricingRules.validFrom]?.toString(),
        validTo = this[PricingRules.validTo]?.toString(),
        isActive = this[PricingRules.isActive],
        description = this[PricingRules.description],
        createdAt = this[PricingRules.createdAt].toString(),
        updatedAt = this[PricingRules.updatedAt].toString(),
    )
}

@Serializable
data class PricingRuleRow(
    val id: String,
    val productId: String,
    val storeId: String?,
    val price: Double,
    val costPrice: Double?,
    val priority: Int,
    val validFrom: String?,
    val validTo: String?,
    val isActive: Boolean,
    val description: String,
    val createdAt: String,
    val updatedAt: String,
)
