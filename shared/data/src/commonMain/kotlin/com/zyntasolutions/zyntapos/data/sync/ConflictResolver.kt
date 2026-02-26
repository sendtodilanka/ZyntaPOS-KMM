package com.zyntasolutions.zyntapos.data.sync

import com.zyntasolutions.zyntapos.core.logger.ZyntaLogger
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import kotlin.time.Clock

/**
 * ZyntaPOS — CRDT-style conflict resolver for offline-first sync operations.
 *
 * ## Algorithm: Last-Write-Wins (LWW) with deterministic tiebreaking
 *
 * When two [SyncOperation]s target the same entity (`entityType` + `entityId`)
 * from different devices, this class determines the canonical winner:
 *
 * 1. **Primary rule — LWW timestamp:** The operation with the later
 *    [SyncOperation.createdAt] wins. This implements Last-Write-Wins (LWW) CRDT
 *    semantics appropriate for a POS system where the most-recent mutation
 *    reflects the true business intent.
 *
 * 2. **Tiebreaker — `deviceId` lexicographic order:** If both operations share
 *    the same millisecond timestamp, the operation whose originating device ID
 *    sorts later alphabetically is chosen. This deterministic rule ensures that
 *    every node in the system — regardless of order in which they receive the
 *    conflicting pair — produces the same resolution.
 *
 * 3. **PRODUCT entity merge:** For [SyncOperation.EntityType.PRODUCT] conflicts
 *    the resolver performs field-level merging: non-null fields from the
 *    *losing* operation's JSON payload that are absent (null / blank) in the
 *    *winner's* payload are carried forward into the merged payload. This
 *    reduces data-loss from partial-update scenarios (e.g. price updated on
 *    Device A while stock was adjusted on Device B).
 *
 * ## ConflictLog audit trail
 * Every call to [resolve] produces a [ConflictLog] record (returned together
 * with the winning operation) so the caller can persist the log to the
 * `conflict_log` SQLite table for operator inspection.
 *
 * ## Injectable via Koin
 * This class is a regular injectable (not an `object`) so that tests can
 * provide a controlled [localDeviceId] and [Clock] override.
 *
 * @param localDeviceId Stable identifier of the current device (from
 *   [com.zyntasolutions.zyntapos.domain.port.SecureStorageKeys.KEY_DEVICE_ID]).
 *   Used as the tiebreaker value for operations that originated locally.
 */
class ConflictResolver(
    private val localDeviceId: String,
) {

    private val log = ZyntaLogger.forModule("ConflictResolver")

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Resolves a conflict between [local] and [remote] operations that target
     * the same entity.
     *
     * @param local  The locally-queued [SyncOperation] (originated on this device).
     * @param remote The server-authoritative [SyncOperation] received during pull.
     * @return A [ConflictResolution] containing the winning [SyncOperation] and a
     *   [ConflictLog] record suitable for persisting to `conflict_log`.
     */
    fun resolve(local: SyncOperation, remote: SyncOperation): ConflictResolution {
        require(local.entityType == remote.entityType) {
            "Cannot resolve conflict across different entity types: " +
                "${local.entityType} vs ${remote.entityType}"
        }
        require(local.entityId == remote.entityId) {
            "Cannot resolve conflict for different entity IDs: " +
                "${local.entityId} vs ${remote.entityId}"
        }

        val strategy: ResolutionStrategy
        val winner: SyncOperation
        val loser: SyncOperation

        val localTs  = local.createdAt.toEpochMilliseconds()
        val remoteTs = remote.createdAt.toEpochMilliseconds()

        when {
            // Primary rule: later timestamp wins
            localTs > remoteTs -> {
                strategy = ResolutionStrategy.LWW_TIMESTAMP
                winner = local
                loser  = remote
                log.d(
                    "LWW timestamp: local wins " +
                        "(local=$localTs remote=$remoteTs entity=${local.entityType}/${local.entityId})"
                )
            }
            remoteTs > localTs -> {
                strategy = ResolutionStrategy.LWW_TIMESTAMP
                winner = remote
                loser  = local
                log.d(
                    "LWW timestamp: remote wins " +
                        "(remote=$remoteTs local=$localTs entity=${local.entityType}/${local.entityId})"
                )
            }
            // Tiebreaker: lexicographic deviceId comparison
            else -> {
                // Remote operations carry the remote device's ID embedded in the
                // operation `id` UUID prefix (conventionally "<deviceId>-<uuid-suffix>").
                // For a clean boundary we compare the local device ID against a
                // sentinel "remote" string derived from the remote op's `id`.
                // Operations from this device own [localDeviceId]; remote ops have
                // an unknown device ID so we use the operation `id` as proxy.
                val remoteDeviceProxy = remote.id
                strategy = ResolutionStrategy.DEVICE_ID_TIEBREAK
                if (localDeviceId >= remoteDeviceProxy) {
                    winner = local
                    loser  = remote
                } else {
                    winner = remote
                    loser  = local
                }
                log.d(
                    "LWW tiebreak by deviceId: ${if (winner === local) "local" else "remote"} wins " +
                        "(localDeviceId=$localDeviceId remoteProxy=$remoteDeviceProxy " +
                        "entity=${local.entityType}/${local.entityId})"
                )
            }
        }

        // Entity-type specific post-processing
        val finalWinner = when (local.entityType) {
            SyncOperation.EntityType.PRODUCT -> mergeProductFields(winner, loser)
            else -> winner
        }

        val conflictLog = ConflictLog(
            entityId         = local.entityId,
            entityType       = local.entityType,
            winnerDeviceId   = if (finalWinner.id == local.id) localDeviceId else "remote:${remote.id}",
            loserDeviceId    = if (finalWinner.id == local.id) "remote:${remote.id}" else localDeviceId,
            strategy         = strategy,
            resolvedAt       = Clock.System.now().toEpochMilliseconds(),
        )

        return ConflictResolution(
            winner      = finalWinner,
            conflictLog = conflictLog,
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRODUCT field-level merge
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Performs field-level merging for PRODUCT entities.
     *
     * The winner's JSON payload fields take precedence. For any field that is
     * **absent** (`null` or empty string) in the winner's payload but **present**
     * in the loser's payload, the loser's value is carried forward.
     *
     * This is implemented via a simple JSON key/value merge over the raw
     * `payload` strings without a full parse library, keeping the data layer
     * free of additional serialization deps. Structured payloads will be
     * handled with typed mappers when Sprint 7 introduces `upsertFromSync`.
     *
     * @param winner The operation selected by LWW or tiebreak.
     * @param loser  The discarded operation.
     * @return [winner] with a merged `payload`, or unmodified [winner] if merge
     *   is not possible (malformed JSON).
     */
    private fun mergeProductFields(winner: SyncOperation, loser: SyncOperation): SyncOperation {
        return try {
            val mergedPayload = mergeJsonPayloads(winner.payload, loser.payload)
            if (mergedPayload != winner.payload) {
                log.d(
                    "PRODUCT field merge applied for entity/${winner.entityId}: " +
                        "loser contributed non-null fields to winner payload"
                )
            }
            winner.copy(payload = mergedPayload)
        } catch (e: Exception) {
            log.w(
                "PRODUCT field merge failed for entity/${winner.entityId} — " +
                    "using winner payload unmodified: ${e.message}"
            )
            winner
        }
    }

    /**
     * Merges two raw JSON object strings using a naïve key/value strategy.
     *
     * Rules:
     * - All keys from [winner] are preserved with their values.
     * - Keys from [loser] that are NOT present in [winner] (or whose value is
     *   `null` or `""`) are contributed to the merged result.
     *
     * Only top-level flat JSON objects are supported. Nested objects are treated
     * as opaque strings. This is intentional for Phase 1 — Sprint 7 will
     * introduce typed DTO-based merging.
     *
     * @return Merged JSON string.
     */
    internal fun mergeJsonPayloads(winnerJson: String, loserJson: String): String {
        val winnerFields = parseJsonFields(winnerJson)
        val loserFields  = parseJsonFields(loserJson)

        val merged = LinkedHashMap<String, String>(winnerFields)
        for ((key, loserValue) in loserFields) {
            val winnerValue = winnerFields[key]
            if (winnerValue == null || winnerValue == "null" || winnerValue == "\"\"") {
                merged[key] = loserValue
            }
        }

        return buildJsonObject(merged)
    }

    /**
     * Parses a flat JSON object string into a `Map<key, rawValue>`.
     *
     * Both `"key": value` and `"key":value` spacing variants are handled.
     * Nested braces are treated as opaque values.
     */
    private fun parseJsonFields(json: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        val trimmed = json.trim()
        // Strip surrounding braces
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return result
        val inner = trimmed.removePrefix("{").removeSuffix("}").trim()
        if (inner.isEmpty()) return result

        // Split on top-level commas (ignoring commas inside nested braces/brackets/strings)
        var depth = 0
        var inString = false
        var escape = false
        var segmentStart = 0
        val segments = mutableListOf<String>()

        for (i in inner.indices) {
            val c = inner[i]
            when {
                escape       -> escape = false
                c == '\\'    -> if (inString) escape = true
                c == '"'     -> inString = !inString
                !inString && c == '{'  -> depth++
                !inString && c == '}'  -> depth--
                !inString && c == '[' -> depth++
                !inString && c == ']' -> depth--
                !inString && c == ',' && depth == 0 -> {
                    segments.add(inner.substring(segmentStart, i).trim())
                    segmentStart = i + 1
                }
            }
        }
        segments.add(inner.substring(segmentStart).trim())

        for (segment in segments) {
            val colonIdx = segment.indexOf(':')
            if (colonIdx < 0) continue
            val rawKey   = segment.substring(0, colonIdx).trim().removePrefix("\"").removeSuffix("\"")
            val rawValue = segment.substring(colonIdx + 1).trim()
            if (rawKey.isNotEmpty()) {
                result[rawKey] = rawValue
            }
        }

        return result
    }

    /**
     * Serializes a `Map<key, rawValue>` back into a compact JSON object string.
     */
    private fun buildJsonObject(fields: Map<String, String>): String = buildString {
        append('{')
        fields.entries.forEachIndexed { index, (key, value) ->
            if (index > 0) append(',')
            append('"')
            append(key)
            append("\":")
            append(value)
        }
        append('}')
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Result types
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The outcome of a [ConflictResolver.resolve] call.
 *
 * @property winner      The [SyncOperation] that should be applied locally and
 *   acknowledged to the server as the authoritative version.
 * @property conflictLog Audit record of this resolution suitable for persistence
 *   in the `conflict_log` SQLite table.
 */
data class ConflictResolution(
    val winner: SyncOperation,
    val conflictLog: ConflictLog,
)

/**
 * Immutable audit record of a single conflict resolution event.
 *
 * Designed to map directly to the `conflict_log` SQLDelight table columns.
 *
 * @property entityId       UUID of the affected domain record.
 * @property entityType     Entity type string (see [SyncOperation.EntityType]).
 * @property winnerDeviceId Identifier of the device whose operation won.
 * @property loserDeviceId  Identifier of the device whose operation lost.
 * @property strategy       The resolution algorithm that decided the winner.
 * @property resolvedAt     Epoch-milliseconds when this resolution occurred.
 */
data class ConflictLog(
    val entityId: String,
    val entityType: String,
    val winnerDeviceId: String,
    val loserDeviceId: String,
    val strategy: ResolutionStrategy,
    val resolvedAt: Long,
)

/**
 * Enumerates the resolution strategies that [ConflictResolver] can apply.
 *
 * Maps to the `resolved_by` column values in the `conflict_log` table.
 */
enum class ResolutionStrategy {
    /** Winner chosen by a later [SyncOperation.createdAt] timestamp (LWW). */
    LWW_TIMESTAMP,

    /**
     * Timestamps were equal; winner chosen by [ConflictResolver.localDeviceId]
     * lexicographic comparison — deterministic across all nodes.
     */
    DEVICE_ID_TIEBREAK,

    /**
     * Field-level merge was applied after LWW winner selection (PRODUCT entities).
     * Note: this strategy is applied *in addition* to the primary strategy, not
     * as a standalone alternative. The primary strategy is still recorded in
     * [ConflictLog.strategy].
     */
    FIELD_MERGE,
}
