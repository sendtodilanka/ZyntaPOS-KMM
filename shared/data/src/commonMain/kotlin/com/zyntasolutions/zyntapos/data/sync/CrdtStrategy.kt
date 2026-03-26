package com.zyntasolutions.zyntapos.data.sync

import com.zyntasolutions.zyntapos.domain.model.SyncOperation

/**
 * CRDT resolution strategy per entity type.
 *
 * Determines how conflicts are handled when two devices modify the same entity concurrently.
 *
 * ## Strategies
 * - **LWW** — Last-Write-Wins by timestamp with deviceId tiebreak. Default for most entities.
 * - **FIELD_MERGE** — LWW winner selection + field-level merge from loser (PRODUCT only).
 * - **APPEND_ONLY** — Both operations are valid; no conflict. Each device's writes are
 *   independent and additive (e.g., stock adjustments are ledger entries, not overwrites).
 */
enum class CrdtStrategy {
    /** Last-Write-Wins by timestamp + deviceId tiebreak. */
    LWW,

    /** LWW + field-level merge (non-null loser fields fill winner blanks). PRODUCT entities. */
    FIELD_MERGE,

    /** Append-only: both ops always accepted, no conflict possible. Stock adjustments, audit entries. */
    APPEND_ONLY,

    /**
     * OR-Set (Observed-Remove Set) for collection-type fields.
     *
     * Merges array fields by computing the union of both devices' additions,
     * preserving all unique items by ID. Removals are tracked via a
     * tombstone array (`_removed` suffix) and filtered from the final set.
     *
     * Used for entities with embedded collections (order items, coupon assignments).
     */
    OR_SET,
    ;

    companion object {
        private val STRATEGY_MAP = mapOf(
            SyncOperation.EntityType.PRODUCT to FIELD_MERGE,
            SyncOperation.EntityType.STOCK_ADJUSTMENT to APPEND_ONLY,
            SyncOperation.EntityType.ACCOUNTING_ENTRY to APPEND_ONLY,
            SyncOperation.EntityType.ORDER to OR_SET,
            SyncOperation.EntityType.COUPON to OR_SET,
        )

        /**
         * Returns the CRDT strategy for the given [entityType].
         * Defaults to [LWW] for unmapped entity types.
         */
        fun forEntityType(entityType: String): CrdtStrategy =
            STRATEGY_MAP[entityType] ?: LWW
    }
}
