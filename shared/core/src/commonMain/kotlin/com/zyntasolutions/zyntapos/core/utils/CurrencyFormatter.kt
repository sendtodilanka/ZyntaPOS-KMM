package com.zyntasolutions.zyntapos.core.utils

import com.zyntasolutions.zyntapos.core.config.AppConfig
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * ZyntaPOS locale-aware currency formatter.
 *
 * Handles LKR, USD, EUR and any other ISO 4217 code with a configurable
 * symbol and decimal-place count. All monetary arithmetic uses HALF_UP rounding.
 *
 * ### Usage
 * ```kotlin
 * val formatter = CurrencyFormatter()
 * formatter.format(1234.5, "LKR")   // → "Rs. 1,234.50"
 * formatter.format(99.9)            // → "Rs. 99.90"  (uses default currency)
 * ```
 */
class CurrencyFormatter(
    /** Default ISO 4217 code used when no currency is passed to [format]. Mutable to allow runtime update from store settings. */
    var defaultCurrency: String = AppConfig.DEFAULT_CURRENCY_CODE,
    /** Default number of decimal places. */
    val defaultDecimals: Int = AppConfig.CURRENCY_DECIMAL_PLACES,
) {

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Formats [amount] as a currency string.
     *
     * ```kotlin
     * formatter.format(1234.5, "USD", 2)  // → "$ 1,234.50"
     * formatter.format(0.0, "LKR")        // → "Rs. 0.00"
     * formatter.format(-50.0, "EUR")      // → "€ -50.00"
     * ```
     *
     * @param amount       Monetary amount to format.
     * @param currencyCode ISO 4217 code (default = [defaultCurrency]).
     * @param decimals     Decimal places (default = [defaultDecimals]).
     */
    fun format(
        amount: Double,
        currencyCode: String = defaultCurrency,
        decimals: Int = defaultDecimals,
    ): String {
        val symbol  = symbolFor(currencyCode)
        val rounded = round(amount, decimals)
        val sign    = if (rounded < 0) "-" else ""
        val abs     = abs(rounded)
        val intPart = abs.toLong()
        val factor  = 10.0.pow(decimals)
        val fracPart = ((abs - intPart.toDouble()) * factor).roundToLong()
        val intFormatted  = formatThousands(intPart)
        val fracFormatted = fracPart.toString().padStart(decimals, '0')
        return "$symbol $sign$intFormatted.$fracFormatted"
    }

    /**
     * Formats [amount] without the currency symbol (useful for input fields).
     *
     * ```kotlin
     * formatter.formatPlain(1234.5)  // → "1,234.50"
     * ```
     */
    fun formatPlain(
        amount: Double,
        decimals: Int = defaultDecimals,
    ): String {
        val rounded  = round(amount, decimals)
        val sign     = if (rounded < 0) "-" else ""
        val abs      = abs(rounded)
        val intPart  = abs.toLong()
        val factor   = 10.0.pow(decimals)
        val fracPart = ((abs - intPart.toDouble()) * factor).roundToLong()
        return "$sign${formatThousands(intPart)}.${fracPart.toString().padStart(decimals, '0')}"
    }

    /**
     * Returns the display symbol for the given ISO 4217 [currencyCode].
     */
    fun symbolFor(currencyCode: String): String = currencyCodeToSymbol(currencyCode)

    /**
     * Returns the full display name for the given [currencyCode].
     */
    fun nameFor(currencyCode: String): String = when (currencyCode.uppercase()) {
        "LKR" -> "Sri Lankan Rupee"
        "USD" -> "US Dollar"
        "EUR" -> "Euro"
        "GBP" -> "British Pound"
        "INR" -> "Indian Rupee"
        else  -> currencyCode.uppercase()
    }

    // ── Supported currencies (Phase 1) ────────────────────────────────────────

    /** ISO 4217 codes supported. */
    val supportedCurrencies: List<String> = listOf("LKR", "USD", "EUR", "GBP", "INR", "JPY", "AUD", "CAD", "SGD")

    // ── Internal helpers ──────────────────────────────────────────────────────

    private fun round(value: Double, decimals: Int): Double {
        val factor = 10.0.pow(decimals)
        return (value * factor).roundToLong() / factor
    }

}
