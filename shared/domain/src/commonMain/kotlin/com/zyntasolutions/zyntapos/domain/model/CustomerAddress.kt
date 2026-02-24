package com.zyntasolutions.zyntapos.domain.model

/**
 * A named delivery/billing address belonging to a customer.
 *
 * @property id Unique identifier (UUID v4).
 * @property customerId FK to the owning [Customer].
 * @property label Short tag displayed in the UI (e.g., "Home", "Work").
 * @property addressLine Street address, building, floor.
 * @property city City or district.
 * @property postalCode Postal / ZIP code.
 * @property country ISO 3166-1 alpha-2 country code or full name.
 * @property isDefault If true this address is pre-selected on the POS order form.
 */
data class CustomerAddress(
    val id: String,
    val customerId: String,
    val label: String = "Home",
    val addressLine: String,
    val city: String? = null,
    val postalCode: String? = null,
    val country: String? = null,
    val isDefault: Boolean = false,
)
