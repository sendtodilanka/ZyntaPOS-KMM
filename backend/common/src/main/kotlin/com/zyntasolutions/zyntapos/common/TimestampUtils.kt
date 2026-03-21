package com.zyntasolutions.zyntapos.common

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Centralized timestamp conversion utilities.
 *
 * ## Timestamp Contract
 *
 * All timestamps in the ZyntaPOS system follow these rules:
 *
 * 1. **Wire format (JSON):** epoch milliseconds (`Long`) for all POS API and sync payloads.
 *    Admin API uses ISO 8601 strings (`String`) for human-readable display timestamps.
 *
 * 2. **Database storage:** PostgreSQL `TIMESTAMP WITH TIME ZONE` columns (mapped to
 *    `OffsetDateTime` via Exposed). Sync metadata columns (`client_timestamp`,
 *    `local_timestamp`, `server_ts`) use `BIGINT` (epoch ms) for fast numeric comparison.
 *
 * 3. **Internal processing:** `java.time.Instant` (UTC) for all server-side computation.
 *    Never use `System.currentTimeMillis()` directly — use [nowEpochMs] instead.
 *
 * 4. **Clock skew tolerance:** 60 seconds for sync operations. Clients with clocks more
 *    than 60s ahead of the server are rejected by [SyncValidator].
 *
 * 5. **Minimum valid timestamp:** 2020-01-01T00:00:00Z (epoch ms = 1577836800000).
 *    Timestamps before this are considered invalid for ZyntaPOS data.
 */
object TimestampUtils {

    /** Epoch ms for 2020-01-01T00:00:00Z — minimum valid timestamp for ZyntaPOS data. */
    const val MIN_VALID_EPOCH_MS = 1_577_836_800_000L

    /** Maximum allowed clock skew (ms) for sync operations. */
    const val MAX_CLOCK_SKEW_MS = 60_000L

    /** Current time as epoch milliseconds (UTC). */
    fun nowEpochMs(): Long = Instant.now().toEpochMilli()

    /** Convert [OffsetDateTime] to epoch milliseconds. */
    fun toEpochMs(odt: OffsetDateTime): Long = odt.toInstant().toEpochMilli()

    /** Convert epoch milliseconds to [OffsetDateTime] (UTC). */
    fun fromEpochMs(epochMs: Long): OffsetDateTime =
        OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneOffset.UTC)

    /** Convert [OffsetDateTime] to ISO 8601 string (for admin API responses). */
    fun toIso8601(odt: OffsetDateTime): String = odt.toInstant().toString()

    /** Convert epoch milliseconds to ISO 8601 string. */
    fun epochMsToIso8601(epochMs: Long): String =
        Instant.ofEpochMilli(epochMs).toString()

    /**
     * Validate that an epoch-ms timestamp is within acceptable bounds.
     *
     * @param strict when true, also rejects timestamps before [MIN_VALID_EPOCH_MS].
     * @return null if valid, or an error message string if invalid.
     */
    fun validateEpochMs(
        epochMs: Long,
        fieldName: String = "timestamp",
        strict: Boolean = false,
    ): String? {
        if (epochMs < 0) return "$fieldName must be a non-negative epoch-ms value"
        if (strict && epochMs in 1 until MIN_VALID_EPOCH_MS) {
            return "$fieldName ($epochMs) is before 2020-01-01 — likely invalid"
        }
        val now = nowEpochMs()
        if (epochMs > now + MAX_CLOCK_SKEW_MS) {
            return "$fieldName is in the future (clock skew > ${MAX_CLOCK_SKEW_MS / 1000}s)"
        }
        return null
    }
}
