package com.zyntasolutions.zyntapos.data.util

/**
 * Converts a user-typed search string into an FTS5 query with prefix matching.
 *
 * Each whitespace-separated token gets a trailing `*` wildcard so that partial
 * words match. Special characters that could confuse the FTS5 query parser are
 * stripped before tokenisation.
 *
 * Examples:
 *  - `"coff cak"` → `"coff* cak*"`
 *  - `"\"mocha\""` → `"mocha*"`
 *  - `"  espresso  "` → `"espresso*"`
 */
internal fun String.toFtsQuery(): String =
    trim()
        .replace("\"", "")
        .split("\\s+".toRegex())
        .filter { it.isNotBlank() }
        .joinToString(" ") { "$it*" }
