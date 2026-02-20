package com.zyntasolutions.zyntapos.domain.model

/**
 * A registered customer whose profile can be attached to a sale for loyalty tracking.
 *
 * @property id Unique identifier (UUID v4).
 * @property name Full customer name.
 * @property phone Primary contact phone. Unique within the store — used for fast lookup.
 * @property email Optional email address for digital receipts.
 * @property address Optional physical or mailing address.
 * @property groupId FK to a customer group (e.g., "Wholesale", "VIP"). Null = default group.
 * @property loyaltyPoints Accumulated loyalty points balance. Never negative.
 * @property notes Free-text internal notes (not shown on receipts).
 * @property isActive If false the customer profile is archived and hidden from POS search.
 */
data class Customer(
    val id: String,
    val name: String,
    val phone: String,
    val email: String? = null,
    val address: String? = null,
    val groupId: String? = null,
    val loyaltyPoints: Int = 0,
    val notes: String? = null,
    val isActive: Boolean = true,
) {
    init {
        require(loyaltyPoints >= 0) { "Loyalty points cannot be negative" }
    }
}
