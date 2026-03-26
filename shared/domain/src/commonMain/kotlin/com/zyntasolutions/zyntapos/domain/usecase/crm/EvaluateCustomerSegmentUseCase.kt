package com.zyntasolutions.zyntapos.domain.usecase.crm

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Customer
import com.zyntasolutions.zyntapos.domain.model.CustomerSegment
import com.zyntasolutions.zyntapos.domain.model.SegmentField
import com.zyntasolutions.zyntapos.domain.model.SegmentOperator
import com.zyntasolutions.zyntapos.domain.model.SegmentRule

/**
 * Evaluates whether a customer matches ALL rules in a [CustomerSegment].
 *
 * Rule evaluation uses AND logic: every rule must pass for the customer to qualify.
 * Numeric comparisons (TOTAL_SPEND, ORDER_COUNT, etc.) parse [SegmentRule.value] as a Double.
 * String comparisons (LOYALTY_TIER, CITY, TAG) are case-insensitive.
 *
 * The caller must provide a [CustomerContext] with pre-aggregated statistics
 * (total spend, order count, days since last purchase) because these values
 * require cross-table queries that the domain layer cannot perform directly.
 */
class EvaluateCustomerSegmentUseCase {

    /**
     * Pre-aggregated customer statistics required for segment rule evaluation.
     *
     * @property totalSpend Lifetime total spend across all orders.
     * @property orderCount Total number of completed orders.
     * @property lastPurchaseDaysAgo Days elapsed since the customer's most recent order. Null if never purchased.
     * @property loyaltyTier Current loyalty tier name (e.g., "GOLD"), or null if no tier assigned.
     * @property city City extracted from the customer's address, or null.
     * @property tags List of tags associated with the customer (empty if none).
     */
    data class CustomerContext(
        val customer: Customer,
        val totalSpend: Double = 0.0,
        val orderCount: Int = 0,
        val lastPurchaseDaysAgo: Int? = null,
        val loyaltyTier: String? = null,
        val city: String? = null,
        val tags: List<String> = emptyList(),
    )

    /**
     * @param segment The segment whose rules to evaluate.
     * @param context The customer and their aggregated statistics.
     * @return [Result.Success] with `true` if the customer matches ALL rules, `false` otherwise.
     */
    operator fun invoke(segment: CustomerSegment, context: CustomerContext): Result<Boolean> {
        if (segment.rules.isEmpty()) return Result.Success(true)
        val matches = segment.rules.all { rule -> evaluateRule(rule, context) }
        return Result.Success(matches)
    }

    /**
     * Batch evaluation: filters a list of customers, returning only those matching ALL segment rules.
     *
     * @param segment The segment whose rules to evaluate.
     * @param contexts All candidate customers with their aggregated statistics.
     * @return [Result.Success] with the list of matching customers.
     */
    fun filterMatching(
        segment: CustomerSegment,
        contexts: List<CustomerContext>,
    ): Result<List<Customer>> {
        if (segment.rules.isEmpty()) {
            return Result.Success(contexts.map { it.customer })
        }
        val matching = contexts.filter { ctx ->
            segment.rules.all { rule -> evaluateRule(rule, ctx) }
        }.map { it.customer }
        return Result.Success(matching)
    }

    // ── Rule evaluation ───────────────────────────────────────────────────

    private fun evaluateRule(rule: SegmentRule, ctx: CustomerContext): Boolean = when (rule.field) {
        SegmentField.TOTAL_SPEND ->
            compareNumeric(ctx.totalSpend, rule.operator, rule.value)

        SegmentField.ORDER_COUNT ->
            compareNumeric(ctx.orderCount.toDouble(), rule.operator, rule.value)

        SegmentField.LAST_PURCHASE_DAYS_AGO -> {
            val days = ctx.lastPurchaseDaysAgo ?: return false
            compareNumeric(days.toDouble(), rule.operator, rule.value)
        }

        SegmentField.LOYALTY_POINTS ->
            compareNumeric(ctx.customer.loyaltyPoints.toDouble(), rule.operator, rule.value)

        SegmentField.LOYALTY_TIER ->
            compareString(ctx.loyaltyTier ?: "", rule.operator, rule.value)

        SegmentField.CITY ->
            compareString(ctx.city ?: "", rule.operator, rule.value)

        SegmentField.TAG ->
            evaluateTagRule(ctx.tags, rule.operator, rule.value)
    }

    private fun compareNumeric(actual: Double, operator: SegmentOperator, threshold: String): Boolean {
        val target = threshold.toDoubleOrNull() ?: return false
        return when (operator) {
            SegmentOperator.GREATER_THAN -> actual > target
            SegmentOperator.LESS_THAN -> actual < target
            SegmentOperator.EQUALS -> actual == target
            SegmentOperator.NOT_EQUALS -> actual != target
            SegmentOperator.CONTAINS -> false // CONTAINS is not meaningful for numeric fields
        }
    }

    private fun compareString(actual: String, operator: SegmentOperator, target: String): Boolean {
        val a = actual.lowercase()
        val t = target.lowercase()
        return when (operator) {
            SegmentOperator.EQUALS -> a == t
            SegmentOperator.NOT_EQUALS -> a != t
            SegmentOperator.CONTAINS -> a.contains(t)
            SegmentOperator.GREATER_THAN -> a > t
            SegmentOperator.LESS_THAN -> a < t
        }
    }

    private fun evaluateTagRule(tags: List<String>, operator: SegmentOperator, target: String): Boolean {
        val t = target.lowercase()
        return when (operator) {
            SegmentOperator.EQUALS -> tags.any { it.lowercase() == t }
            SegmentOperator.NOT_EQUALS -> tags.none { it.lowercase() == t }
            SegmentOperator.CONTAINS -> tags.any { it.lowercase().contains(t) }
            SegmentOperator.GREATER_THAN -> false // Not meaningful for tags
            SegmentOperator.LESS_THAN -> false // Not meaningful for tags
        }
    }
}
