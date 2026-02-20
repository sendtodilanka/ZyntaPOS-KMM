package com.zyntasolutions.zyntapos.domain.model

/**
 * A vendor from whom the business purchases stock.
 *
 * @property id Unique identifier (UUID v4).
 * @property name Legal or trading name of the supplier.
 * @property contactPerson Primary contact's full name.
 * @property phone Contact phone number.
 * @property email Contact email address.
 * @property address Full mailing or physical address.
 * @property notes Internal notes (e.g., payment terms, lead times).
 * @property isActive If false the supplier is archived and hidden from dropdowns.
 */
data class Supplier(
    val id: String,
    val name: String,
    val contactPerson: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val notes: String? = null,
    val isActive: Boolean = true,
)
