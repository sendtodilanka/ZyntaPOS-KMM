package com.zyntasolutions.zyntapos.domain.model

/**
 * A registered customer whose profile can be attached to a sale for loyalty tracking.
 *
 * @property id Unique identifier (UUID v4).
 * @property name Full customer name.
 * @property phone Primary contact phone. Unique within the store — used for fast lookup.
 * @property email Optional email address for digital receipts.
 * @property address Optional physical or mailing address (legacy single-line field).
 * @property groupId FK to a [CustomerGroup] (e.g., "Wholesale", "VIP"). Null = default group.
 * @property loyaltyPoints Accumulated loyalty points balance. Never negative.
 * @property notes Free-text internal notes (not shown on receipts).
 * @property isActive If false the customer profile is archived and hidden from POS search.
 * @property creditLimit Maximum credit allowed for installment purchases. 0 = no credit.
 * @property creditEnabled Whether credit/installment payments are enabled for this customer.
 * @property gender Optional gender indicator (free-text to support locale variation).
 * @property birthday ISO 8601 date string (YYYY-MM-DD) of the customer's birthday.
 * @property isWalkIn If true this is a one-time walk-in customer without a persistent profile.
 * @property storeId The store that owns this customer record. Null = local single-store.
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
    // Phase 2 CRM fields
    val creditLimit: Double = 0.0,
    val creditEnabled: Boolean = false,
    val gender: String? = null,
    val birthday: String? = null,
    val isWalkIn: Boolean = false,
    val storeId: String? = null,
) {
    init {
        require(loyaltyPoints >= 0) { "Loyalty points cannot be negative" }
        require(creditLimit >= 0.0) { "Credit limit cannot be negative" }
    }
}
