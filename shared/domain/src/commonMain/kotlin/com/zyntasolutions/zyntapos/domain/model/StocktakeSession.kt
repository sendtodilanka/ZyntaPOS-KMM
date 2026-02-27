package com.zyntasolutions.zyntapos.domain.model

/**
 * Status of a physical inventory stocktake session.
 */
enum class StocktakeStatus {
    /** Counting is in progress; items are being scanned or manually entered. */
    IN_PROGRESS,

    /** Counting is complete; variances have been applied to stock levels. */
    COMPLETED,

    /** The session was cancelled before completion; no adjustments applied. */
    CANCELLED,
}

/**
 * A stocktake (physical inventory count) session initiated by a staff member.
 *
 * Each session captures the counted quantities for a subset (or all) of
 * products. On completion the system calculates variances against the current
 * book quantity and creates [StockAdjustment] records.
 *
 * @property id          UUID for this session.
 * @property startedBy   User ID of the staff member who initiated the count.
 * @property startedAt   Session start timestamp (epoch millis).
 * @property status      Current state — [StocktakeStatus].
 * @property counts      Map of `productId` → counted quantity for all items
 *                       scanned/entered so far.
 * @property completedAt Timestamp when the session was completed or cancelled;
 *                       `null` while in progress.
 */
data class StocktakeSession(
    val id: String,
    val startedBy: String,
    val startedAt: Long,
    val status: StocktakeStatus = StocktakeStatus.IN_PROGRESS,
    val counts: Map<String, Int> = emptyMap(),
    val completedAt: Long? = null,
)
