package com.zyntasolutions.zyntapos.domain.model

/**
 * Exchange rate between two currencies.
 *
 * Used for multi-currency support in multi-store environments where
 * different stores may operate in different currencies.
 *
 * @property id Unique identifier
 * @property sourceCurrency ISO 4217 source currency code (e.g., "USD")
 * @property targetCurrency ISO 4217 target currency code (e.g., "LKR")
 * @property rate Conversion rate: 1 unit of source = rate units of target
 * @property effectiveDate Epoch milliseconds when this rate became effective
 * @property expiresAt Optional epoch milliseconds when this rate expires (null = no expiry)
 * @property source Where this rate came from (e.g., "MANUAL", "ECB", "CBSL")
 * @property createdAt Epoch milliseconds of creation
 * @property updatedAt Epoch milliseconds of last update
 */
data class ExchangeRate(
    val id: String,
    val sourceCurrency: String,
    val targetCurrency: String,
    val rate: Double,
    val effectiveDate: Long,
    val expiresAt: Long? = null,
    val source: String = "MANUAL",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
