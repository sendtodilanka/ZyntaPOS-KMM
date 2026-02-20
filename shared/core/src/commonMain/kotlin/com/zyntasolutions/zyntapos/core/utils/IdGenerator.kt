package com.zyntasolutions.zyntapos.core.utils

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Cross-platform UUID v4 generator for ZentaPOS entity IDs.
 *
 * Uses [kotlin.uuid.Uuid] (Kotlin 2.0+) which delegates to the platform's
 * secure random source:
 * - **JVM/Desktop:** `java.util.UUID.randomUUID()` backed by `SecureRandom`
 * - **Android:**     same JVM runtime
 *
 * ### Usage
 * ```kotlin
 * val productId: String = IdGenerator.newId()      // e.g. "550e8400-e29b-41d4-a716-446655440000"
 * val orderId: String   = IdGenerator.newId()
 * ```
 *
 * All generated IDs are lowercase hyphenated standard UUID v4 strings (36 chars).
 */
object IdGenerator {

    /**
     * Generates a new UUID v4 string.
     *
     * @return 36-character lowercase UUID v4 string, e.g. `"6ba7b810-9dad-11d1-80b4-00c04fd430c8"`.
     */
    @OptIn(ExperimentalUuidApi::class)
    fun newId(): String = Uuid.random().toString()

    /**
     * Generates a compact (no hyphens) UUID string for use in QR codes or barcodes.
     *
     * @return 32-character hex string without hyphens.
     */
    @OptIn(ExperimentalUuidApi::class)
    fun newCompactId(): String = Uuid.random().toString().replace("-", "")

    /**
     * Generates a prefixed ID for a given entity type.
     *
     * ```kotlin
     * IdGenerator.newPrefixedId("ORD")  // → "ORD-6ba7b810-9dad-11d1-80b4"
     * ```
     *
     * Only the first 3 UUID segments are used to keep the ID readable on receipts.
     */
    @OptIn(ExperimentalUuidApi::class)
    fun newPrefixedId(prefix: String): String {
        val uuid = Uuid.random().toString()
        val short = uuid.split("-").take(3).joinToString("-")
        return "$prefix-$short"
    }
}
