package com.zyntasolutions.zyntapos.core.extensions

/**
 * Extension functions for [String] used across ZentaPOS.
 */

// ── Validation ────────────────────────────────────────────────────────────────

/**
 * Returns `true` if this string is a syntactically valid email address.
 *
 * Uses a simple RFC-5322-compatible regex sufficient for POS user input.
 * Server-side validation is the authoritative check.
 */
fun String.isValidEmail(): Boolean {
    if (isBlank()) return false
    val emailRegex = Regex(
        "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$"
    )
    return emailRegex.matches(trim())
}

/**
 * Returns `true` if this string is a valid phone number.
 *
 * Accepts: optional leading `+`, digits, spaces, hyphens, parentheses.
 * Length: 7–15 digits (ITU-T E.164 range).
 */
fun String.isValidPhone(): Boolean {
    if (isBlank()) return false
    val digitsOnly = replace(Regex("[\\s\\-().+]"), "")
    return digitsOnly.length in 7..15 && digitsOnly.all { it.isDigit() }
}

// ── Transformation ────────────────────────────────────────────────────────────

/**
 * Truncates this string to [maxLength] characters, appending [ellipsis] if truncated.
 *
 * ```kotlin
 * "Hello, World!".truncate(5)       // → "He..."
 * "Hi".truncate(10)                  // → "Hi"
 * ```
 */
fun String.truncate(maxLength: Int, ellipsis: String = "..."): String {
    require(maxLength > ellipsis.length) {
        "maxLength ($maxLength) must be greater than ellipsis length (${ellipsis.length})"
    }
    return if (length <= maxLength) this
    else take(maxLength - ellipsis.length) + ellipsis
}

/**
 * Converts this string to Title Case (first letter of each word capitalised).
 *
 * ```kotlin
 * "hello world".toTitleCase()  // → "Hello World"
 * ```
 */
fun String.toTitleCase(): String =
    split(" ").joinToString(" ") { word ->
        word.lowercase().replaceFirstChar { it.uppercaseChar() }
    }

// ── Security / Privacy ───────────────────────────────────────────────────────

/**
 * Masks sensitive strings for logging / display, showing only the first [visibleChars]
 * and replacing the rest with [maskChar] characters.
 *
 * ```kotlin
 * "1234567890".maskSensitive()           // → "12••••••••"
 * "admin@zentapos.com".maskSensitive(3)  // → "adm•••••••••••••"
 * ```
 *
 * @param visibleChars Number of leading characters to leave visible.
 * @param maskChar     Character used for masking (default `•`).
 */
fun String.maskSensitive(visibleChars: Int = 2, maskChar: Char = '•'): String {
    if (length <= visibleChars) return maskChar.toString().repeat(length)
    return take(visibleChars) + maskChar.toString().repeat(length - visibleChars)
}

// ── Formatting ────────────────────────────────────────────────────────────────

/**
 * Returns `null` if this string is blank after trimming, otherwise returns the trimmed value.
 * Useful for mapping empty form inputs to `null` domain fields.
 */
fun String.nullIfBlank(): String? = trim().ifBlank { null }

/**
 * Ensures the string starts with the given [prefix], adding it if absent.
 *
 * ```kotlin
 * "example.com".ensurePrefix("https://")  // → "https://example.com"
 * "https://example.com".ensurePrefix("https://")  // unchanged
 * ```
 */
fun String.ensurePrefix(prefix: String): String =
    if (startsWith(prefix)) this else prefix + this

/**
 * Returns `true` if this string consists entirely of digits (useful for barcode validation).
 */
fun String.isNumeric(): Boolean = isNotEmpty() && all { it.isDigit() }
