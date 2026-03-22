package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.domain.model.ExchangeRate
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing exchange rates between currencies.
 *
 * Exchange rates are platform-level configuration managed by Zynta Solutions staff
 * via the admin panel (ADR-009 compliant — platform operation, not store operation).
 */
interface ExchangeRateRepository {

    /**
     * Get all exchange rates, optionally filtered by source or target currency.
     */
    fun getAll(): Flow<List<ExchangeRate>>

    /**
     * Get the effective exchange rate for a currency pair.
     * Returns the most recent rate that is currently effective (effectiveDate <= now, not expired).
     *
     * @param sourceCurrency ISO 4217 source currency code
     * @param targetCurrency ISO 4217 target currency code
     * @return The effective rate, or null if no rate exists for this pair
     */
    suspend fun getEffectiveRate(sourceCurrency: String, targetCurrency: String): ExchangeRate?

    /**
     * Get all rates for a given source currency.
     */
    suspend fun getRatesForCurrency(currencyCode: String): List<ExchangeRate>

    /**
     * Insert or update an exchange rate.
     */
    suspend fun upsert(rate: ExchangeRate)

    /**
     * Delete an exchange rate by ID.
     */
    suspend fun delete(id: String)
}
