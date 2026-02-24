package com.zyntasolutions.zyntapos.domain.model

/**
 * A single loyalty-points ledger entry for a customer.
 *
 * Positive [points] = earned; negative [points] = redeemed or expired.
 * The customer's balance is the sum of all entries.
 *
 * @property id Unique identifier (UUID v4).
 * @property customerId FK to the [Customer].
 * @property points Delta applied to the customer's balance (+/-).
 * @property balanceAfter Running balance after this entry was applied.
 * @property type Nature of the points change.
 * @property referenceType Optional source entity type (e.g., "ORDER").
 * @property referenceId Optional source entity ID.
 * @property note Human-readable description.
 * @property expiresAt Epoch millis after which these points expire. Null = no expiry.
 * @property createdAt Epoch millis when this entry was recorded.
 */
data class RewardPoints(
    val id: String,
    val customerId: String,
    val points: Int,
    val balanceAfter: Int,
    val type: PointsType,
    val referenceType: String? = null,
    val referenceId: String? = null,
    val note: String? = null,
    val expiresAt: Long? = null,
    val createdAt: Long,
) {
    enum class PointsType { EARNED, REDEEMED, EXPIRED, ADJUSTED }
}

/**
 * Defines a loyalty tier that customers qualify for based on their accumulated points.
 *
 * @property id Unique identifier (UUID v4).
 * @property name Display name (e.g., "Silver", "Gold", "Platinum").
 * @property minPoints Minimum cumulative points to reach this tier.
 * @property discountPercent Automatic discount percentage applied at POS.
 * @property pointsMultiplier Multiplier applied to points earned on purchases.
 * @property benefits Human-readable list of tier perks.
 */
data class LoyaltyTier(
    val id: String,
    val name: String,
    val minPoints: Int = 0,
    val discountPercent: Double = 0.0,
    val pointsMultiplier: Double = 1.0,
    val benefits: List<String> = emptyList(),
) {
    init {
        require(minPoints >= 0) { "Minimum points cannot be negative" }
        require(discountPercent in 0.0..100.0) { "Discount percent must be between 0 and 100" }
        require(pointsMultiplier > 0.0) { "Points multiplier must be positive" }
    }
}
