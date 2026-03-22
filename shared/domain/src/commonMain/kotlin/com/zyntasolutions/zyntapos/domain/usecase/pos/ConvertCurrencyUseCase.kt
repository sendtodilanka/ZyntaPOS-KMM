package com.zyntasolutions.zyntapos.domain.usecase.pos

import com.zyntasolutions.zyntapos.domain.repository.ExchangeRateRepository

/**
 * Converts an amount from one currency to another using the effective exchange rate.
 *
 * Returns null if no exchange rate exists for the given currency pair.
 * Supports bidirectional conversion: if only A→B rate exists, B→A is derived as 1/rate.
 */
class ConvertCurrencyUseCase(
    private val exchangeRateRepository: ExchangeRateRepository,
) {

    data class Result(
        val convertedAmount: Double,
        val rate: Double,
        val sourceCurrency: String,
        val targetCurrency: String,
    )

    suspend operator fun invoke(
        amount: Double,
        sourceCurrency: String,
        targetCurrency: String,
    ): Result? {
        if (sourceCurrency == targetCurrency) {
            return Result(
                convertedAmount = amount,
                rate = 1.0,
                sourceCurrency = sourceCurrency,
                targetCurrency = targetCurrency,
            )
        }

        // Try direct rate: source → target
        val directRate = exchangeRateRepository.getEffectiveRate(sourceCurrency, targetCurrency)
        if (directRate != null) {
            return Result(
                convertedAmount = amount * directRate.rate,
                rate = directRate.rate,
                sourceCurrency = sourceCurrency,
                targetCurrency = targetCurrency,
            )
        }

        // Try inverse rate: target → source, then invert
        val inverseRate = exchangeRateRepository.getEffectiveRate(targetCurrency, sourceCurrency)
        if (inverseRate != null && inverseRate.rate != 0.0) {
            val invertedRate = 1.0 / inverseRate.rate
            return Result(
                convertedAmount = amount * invertedRate,
                rate = invertedRate,
                sourceCurrency = sourceCurrency,
                targetCurrency = targetCurrency,
            )
        }

        return null
    }
}
