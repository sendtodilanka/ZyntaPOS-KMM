package com.zyntasolutions.zyntapos.api.repository

import com.zyntasolutions.zyntapos.api.db.ExchangeRates
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Data access for exchange rates (C2.2 Multi-Currency).
 *
 * Exchange rates are platform-level configuration managed by Zynta Solutions admin staff.
 * Per ADR-009: exchange rates are a platform operation (not store-level), so admin panel
 * GET + PUT endpoints are appropriate.
 */
class ExchangeRateRepository {

    fun getRates(): List<ExchangeRateRow> = transaction {
        ExchangeRates.selectAll()
            .orderBy(ExchangeRates.sourceCurrency, SortOrder.ASC)
            .map { it.toRow() }
    }

    fun getEffectiveRate(sourceCurrency: String, targetCurrency: String): ExchangeRateRow? = transaction {
        val now = OffsetDateTime.now()
        ExchangeRates.selectAll()
            .where {
                (ExchangeRates.sourceCurrency eq sourceCurrency) and
                (ExchangeRates.targetCurrency eq targetCurrency) and
                (ExchangeRates.effectiveDate lessEq now) and
                (ExchangeRates.expiresAt.isNull() or (ExchangeRates.expiresAt greater now))
            }
            .orderBy(ExchangeRates.effectiveDate, SortOrder.DESC)
            .limit(1)
            .firstOrNull()?.toRow()
    }

    fun upsertRate(
        sourceCurrency: String,
        targetCurrency: String,
        rate: Double,
        source: String = "MANUAL",
    ): ExchangeRateRow = transaction {
        val now = OffsetDateTime.now()
        val existing = ExchangeRates.selectAll()
            .where {
                (ExchangeRates.sourceCurrency eq sourceCurrency) and
                (ExchangeRates.targetCurrency eq targetCurrency)
            }
            .firstOrNull()

        if (existing != null) {
            ExchangeRates.update({
                (ExchangeRates.sourceCurrency eq sourceCurrency) and
                (ExchangeRates.targetCurrency eq targetCurrency)
            }) {
                it[ExchangeRates.rate] = rate.toBigDecimal()
                it[ExchangeRates.source] = source
                it[ExchangeRates.updatedAt] = now
            }
        } else {
            ExchangeRates.insert {
                it[ExchangeRates.id] = UUID.randomUUID()
                it[ExchangeRates.sourceCurrency] = sourceCurrency
                it[ExchangeRates.targetCurrency] = targetCurrency
                it[ExchangeRates.rate] = rate.toBigDecimal()
                it[ExchangeRates.effectiveDate] = now
                it[ExchangeRates.source] = source
                it[ExchangeRates.createdAt] = now
                it[ExchangeRates.updatedAt] = now
            }
        }

        ExchangeRates.selectAll()
            .where {
                (ExchangeRates.sourceCurrency eq sourceCurrency) and
                (ExchangeRates.targetCurrency eq targetCurrency)
            }
            .first().toRow()
    }

    fun deleteRate(sourceCurrency: String, targetCurrency: String): Boolean = transaction {
        ExchangeRates.deleteWhere {
            (ExchangeRates.sourceCurrency eq sourceCurrency) and
            (ExchangeRates.targetCurrency eq targetCurrency)
        } > 0
    }

    private fun ResultRow.toRow() = ExchangeRateRow(
        id = this[ExchangeRates.id].toString(),
        sourceCurrency = this[ExchangeRates.sourceCurrency],
        targetCurrency = this[ExchangeRates.targetCurrency],
        rate = this[ExchangeRates.rate].toDouble(),
        effectiveDate = this[ExchangeRates.effectiveDate].toString(),
        expiresAt = this[ExchangeRates.expiresAt]?.toString(),
        source = this[ExchangeRates.source],
        createdAt = this[ExchangeRates.createdAt].toString(),
        updatedAt = this[ExchangeRates.updatedAt].toString(),
    )
}

@Serializable
data class ExchangeRateRow(
    val id: String,
    val sourceCurrency: String,
    val targetCurrency: String,
    val rate: Double,
    val effectiveDate: String,
    val expiresAt: String? = null,
    val source: String,
    val createdAt: String,
    val updatedAt: String,
)
