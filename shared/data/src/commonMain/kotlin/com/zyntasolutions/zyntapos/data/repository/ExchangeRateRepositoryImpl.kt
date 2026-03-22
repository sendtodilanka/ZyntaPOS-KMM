package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.ExchangeRate
import com.zyntasolutions.zyntapos.domain.repository.ExchangeRateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * SQLDelight-backed implementation of [ExchangeRateRepository].
 *
 * Exchange rates are platform-level configuration synced from the backend.
 * No SyncEnqueuer integration — rates are managed server-side and pulled to client.
 */
class ExchangeRateRepositoryImpl(
    private val db: ZyntaDatabase,
) : ExchangeRateRepository {

    private val queries get() = db.exchange_ratesQueries

    override fun getAll(): Flow<List<ExchangeRate>> =
        queries.getAllRates()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun getEffectiveRate(
        sourceCurrency: String,
        targetCurrency: String,
    ): ExchangeRate? = withContext(Dispatchers.Default) {
        val now = Clock.System.now().toEpochMilliseconds()
        queries.getEffectiveRate(
            sourceCurrency = sourceCurrency,
            targetCurrency = targetCurrency,
            now = now,
        ).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun getRatesForCurrency(currencyCode: String): List<ExchangeRate> =
        withContext(Dispatchers.Default) {
            queries.getRatesForCurrency(currencyCode)
                .executeAsList()
                .map { it.toDomain() }
        }

    override suspend fun upsert(rate: ExchangeRate) = withContext(Dispatchers.Default) {
        val now = Clock.System.now().toEpochMilliseconds()
        queries.upsertRate(
            id = rate.id,
            sourceCurrency = rate.sourceCurrency,
            targetCurrency = rate.targetCurrency,
            rate = rate.rate,
            effectiveDate = rate.effectiveDate,
            expiresAt = rate.expiresAt,
            source = rate.source,
            createdAt = rate.createdAt.takeIf { it > 0L } ?: now,
            updatedAt = now,
        )
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.Default) {
        queries.deleteRate(id)
    }

    private fun com.zyntasolutions.zyntapos.db.Exchange_rates.toDomain() = ExchangeRate(
        id = id,
        sourceCurrency = source_currency,
        targetCurrency = target_currency,
        rate = rate,
        effectiveDate = effective_date,
        expiresAt = expires_at,
        source = source,
        createdAt = created_at,
        updatedAt = updated_at,
    )
}
