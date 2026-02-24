package com.zyntasolutions.zyntapos.domain.model

/**
 * A field-level CRDT conflict recorded when a local value diverges from the server value.
 *
 * Conflicts are written by [com.zyntasolutions.zyntapos.data.sync.SyncEngine] when it detects
 * that a field was modified locally and on the server between sync cycles.
 *
 * @property id           Unique identifier (UUID v4).
 * @property entityType   The entity type string (e.g., "products", "customers"). See [SyncOperation].
 * @property entityId     The entity's primary key.
 * @property fieldName    The field where the conflict occurred.
 * @property localValue   The value this device wrote (serialised as a string).
 * @property serverValue  The value the server holds (serialised as a string).
 * @property resolvedBy   Resolution strategy applied. Null = unresolved.
 * @property resolution   The final value chosen after resolution. Null = unresolved.
 * @property resolvedAt   Epoch millis when the conflict was resolved. Null = unresolved.
 * @property createdAt    Epoch millis when the conflict was detected.
 */
data class SyncConflict(
    val id: String,
    val entityType: String,
    val entityId: String,
    val fieldName: String,
    val localValue: String?,
    val serverValue: String?,
    val resolvedBy: Resolution? = null,
    val resolution: String? = null,
    val resolvedAt: Long? = null,
    val createdAt: Long,
) {
    /** Strategy used to resolve the conflict. */
    enum class Resolution {
        /** The local (device) value was kept. */
        LOCAL,
        /** The server value was used. */
        SERVER,
        /** A merge of local and server values was computed. */
        MERGE,
        /** A human administrator chose the resolution value. */
        MANUAL,
    }

    val isResolved: Boolean get() = resolvedAt != null
}
