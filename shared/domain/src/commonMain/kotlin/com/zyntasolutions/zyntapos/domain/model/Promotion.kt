package com.zyntasolutions.zyntapos.domain.model

/**
 * A structured promotion rule evaluated by the promotion engine.
 *
 * Promotion rules are evaluated in [priority] order (highest first). The first
 * matching promotion wins unless the type supports stacking.
 *
 * @property id Unique identifier (UUID v4).
 * @property name Human-readable promotion name.
 * @property type The promotion mechanic.
 * @property config Type-specific configuration serialized as JSON.
 *   See [PromotionConfig] for schema per type.
 * @property validFrom Epoch millis when the promotion becomes active.
 * @property validTo Epoch millis when the promotion expires.
 * @property priority Evaluation order (higher = evaluated first).
 * @property isActive Whether the promotion is enabled.
 */
data class Promotion(
    val id: String,
    val name: String,
    val type: PromotionType,
    val config: String,   // JSON string — parsed in feature layer
    val validFrom: Long,
    val validTo: Long,
    val priority: Int = 0,
    val isActive: Boolean = true,
    /** Store IDs this promotion targets. Empty = global (all stores). */
    val storeIds: List<String> = emptyList(),
) {
    init {
        require(validFrom < validTo) { "validFrom must be before validTo" }
    }
}

/** Identifies the promotion mechanic. Maps to SQLite `type` column. */
enum class PromotionType {
    /** Buy X units of a product, get Y free or discounted. */
    BUY_X_GET_Y,

    /** Bundle of specific products at a combined discounted price. */
    BUNDLE,

    /** Time-limited flash sale for selected categories or products. */
    FLASH_SALE,

    /** Scheduled recurring promotion (e.g., weekly deal on Mondays). */
    SCHEDULED,
}
