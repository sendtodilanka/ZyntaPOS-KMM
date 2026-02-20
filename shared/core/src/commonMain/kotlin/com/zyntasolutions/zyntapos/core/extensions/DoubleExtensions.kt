package com.zyntasolutions.zyntapos.core.extensions

import com.zyntasolutions.zyntapos.core.config.AppConfig
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Extension functions for [Double] used across ZentaPOS financial calculations.
 *
 * All monetary rounding uses **HALF_UP** semantics (standard for retail POS systems)
 * to comply with common accounting expectations.
 */

// ── Currency / Financial ──────────────────────────────────────────────────────

/**
 * Formats this [Double] as a currency string using the given [currencyCode].
 *
 * ```kotlin
 * 1234.5.toCurrencyString("LKR")   // → "LKR 1,234.50"
 * 99.9.toCurrencyString("USD")     // → "USD 99.90"
 * ```
 *
 * @param currencyCode ISO 4217 code (LKR, USD, EUR supported in Phase 1).
 * @param decimals     Number of decimal places (defaults to [AppConfig.CURRENCY_DECIMAL_PLACES]).
 */
fun Double.toCurrencyString(
    currencyCode: String = AppConfig.DEFAULT_CURRENCY_CODE,
    decimals: Int = AppConfig.CURRENCY_DECIMAL_PLACES,
): String {
    val symbol = currencySymbol(currencyCode)
    val rounded = roundToCurrency(decimals)
    val intPart = rounded.toLong()
    val fracPart = ((abs(rounded) - abs(intPart.toDouble())) * 10.0.pow(decimals)).roundToLong()
    val intFormatted = formatWithThousandsSeparator(intPart)
    val fracFormatted = fracPart.toString().padStart(decimals, '0')
    return "$symbol $intFormatted.$fracFormatted"
}

/**
 * Rounds this value to [decimals] decimal places using HALF_UP rounding.
 *
 * ```kotlin
 * 1.005.roundToCurrency()  // → 1.01
 * 2.994.roundToCurrency()  // → 2.99
 * ```
 */
fun Double.roundToCurrency(decimals: Int = AppConfig.CURRENCY_DECIMAL_PLACES): Double {
    val factor = 10.0.pow(decimals)
    return (this * factor).roundToLong() / factor
}

/**
 * Formats this [Double] as a percentage string.
 *
 * ```kotlin
 * 0.15.toPercentage()           // → "15.00 %"
 * 8.5.toPercentage(asRate=false) // → "8.50 %"
 * ```
 *
 * @param asRate  If `true`, multiplies by 100 (treats value as a fractional rate like 0.15).
 *                If `false`, treats value as already percentage-form (like 15.0).
 * @param decimals Number of decimal places in the output.
 */
fun Double.toPercentage(asRate: Boolean = false, decimals: Int = 2): String {
    val pct = if (asRate) this * 100.0 else this
    val factor = 10.0.pow(decimals)
    val rounded = (pct * factor).roundToLong() / factor
    val intPart = rounded.toLong()
    val fracPart = ((abs(rounded) - abs(intPart.toDouble())) * factor).roundToLong()
    return "${intPart}.${fracPart.toString().padStart(decimals, '0')} %"
}

/**
 * Returns `true` if this value is strictly greater than zero.
 * Useful for validation guards on price / quantity fields.
 */
fun Double.isPositive(): Boolean = this > 0.0

/**
 * Returns `true` if this value is zero or greater.
 */
fun Double.isNonNegative(): Boolean = this >= 0.0

// ── Private Helpers ───────────────────────────────────────────────────────────

/** Maps ISO 4217 currency codes to display symbols. */
private fun currencySymbol(code: String): String = when (code.uppercase()) {
    "LKR" -> "Rs."
    "USD" -> "$"
    "EUR" -> "€"
    "GBP" -> "£"
    "INR" -> "₹"
    "JPY" -> "¥"
    else  -> code
}

/** Formats a [Long] with thousands separators (e.g., 1234567 → "1,234,567"). */
private fun formatWithThousandsSeparator(value: Long): String {
    val str = abs(value).toString()
    val sign = if (value < 0) "-" else ""
    val groups = mutableListOf<String>()
    var remaining = str
    while (remaining.length > 3) {
        groups.add(0, remaining.takeLast(3))
        remaining = remaining.dropLast(3)
    }
    if (remaining.isNotEmpty()) groups.add(0, remaining)
    return sign + groups.joinToString(",")
}
