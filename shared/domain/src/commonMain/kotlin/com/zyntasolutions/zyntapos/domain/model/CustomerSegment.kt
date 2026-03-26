package com.zyntasolutions.zyntapos.domain.model

/**
 * A customer segment that groups customers by rule-based criteria for targeted promotions.
 *
 * Segments can be **automatic** (customers are evaluated against [rules] dynamically)
 * or **manual** (customers are explicitly assigned by a manager).
 *
 * @property id Unique identifier (UUID v4).
 * @property name Human-readable segment label (e.g., "High Spenders", "Inactive 90d").
 * @property description Optional longer explanation shown in the segment manager UI.
 * @property rules Ordered list of filter rules. All rules must match (AND logic) for a customer to qualify.
 * @property isAutomatic If true, membership is computed dynamically from [rules]. If false, membership is managed manually.
 * @property customerCount Cached count of customers currently matching this segment. May be stale between evaluations.
 * @property createdAt Epoch millis when the segment was created.
 * @property updatedAt Epoch millis when the segment was last modified.
 */
data class CustomerSegment(
    val id: String,
    val name: String,
    val description: String = "",
    val rules: List<SegmentRule> = emptyList(),
    val isAutomatic: Boolean = true,
    val customerCount: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/**
 * A single filter criterion within a [CustomerSegment].
 *
 * @property field The customer attribute to evaluate.
 * @property operator The comparison operation.
 * @property value The threshold or match value (always stored as a string; parsed to the appropriate type during evaluation).
 */
data class SegmentRule(
    val field: SegmentField,
    val operator: SegmentOperator,
    val value: String,
)

/**
 * Customer attributes available for segment rule evaluation.
 */
enum class SegmentField {
    /** Lifetime total spend amount (Double). */
    TOTAL_SPEND,

    /** Total number of completed orders (Int). */
    ORDER_COUNT,

    /** Days since last purchase (Int). */
    LAST_PURCHASE_DAYS_AGO,

    /** Current loyalty points balance (Int). */
    LOYALTY_POINTS,

    /** Loyalty tier name (String, e.g., "GOLD"). */
    LOYALTY_TIER,

    /** City from the customer's address (String). */
    CITY,

    /** Arbitrary tag associated with the customer (String). */
    TAG,
}

/**
 * Comparison operators for segment rule evaluation.
 */
enum class SegmentOperator {
    GREATER_THAN,
    LESS_THAN,
    EQUALS,
    NOT_EQUALS,
    CONTAINS,
}
