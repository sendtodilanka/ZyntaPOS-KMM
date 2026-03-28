package com.zyntasolutions.zyntapos.core.utils

/**
 * Shared internal helpers for currency formatting used by both
 * [CurrencyFormatter] and [com.zyntasolutions.zyntapos.core.extensions.DoubleExtensions].
 *
 * These are `internal` so they remain module-private and are not part of the
 * public API of `:shared:core`.
 */

/**
 * Maps an ISO 4217 [currencyCode] to its display symbol.
 * Returns the code itself (uppercased) for unknown currencies.
 */
internal fun currencyCodeToSymbol(currencyCode: String): String = when (currencyCode.uppercase()) {
    "LKR" -> "Rs."
    "USD" -> "$"
    "EUR" -> "€"
    "GBP" -> "£"
    "INR" -> "₹"
    "JPY" -> "¥"
    "AUD" -> "A$"
    "CAD" -> "C$"
    "SGD" -> "S$"
    else  -> currencyCode.uppercase()
}

/**
 * Formats a non-negative [Long] with thousands separators (e.g., 1234567 → "1,234,567").
 *
 * Callers are responsible for extracting the sign and passing the absolute value.
 */
internal fun formatThousands(absValue: Long): String {
    val str = absValue.toString()
    val groups = mutableListOf<String>()
    var rem = str
    while (rem.length > 3) {
        groups.add(0, rem.takeLast(3))
        rem = rem.dropLast(3)
    }
    if (rem.isNotEmpty()) groups.add(0, rem)
    return groups.joinToString(",")
}
