package com.zyntasolutions.zyntapos.domain.model

import kotlinx.datetime.Instant

/**
 * Tracks an employee's assignment to a store for multi-store roaming (C3.4).
 *
 * An employee has a primary store (`Employee.storeId`) and may have
 * additional temporary or permanent assignments to other stores.
 */
data class EmployeeStoreAssignment(
    val id: String,
    val employeeId: String,
    val storeId: String,
    /** ISO date: YYYY-MM-DD. */
    val startDate: String,
    /** ISO date: YYYY-MM-DD. Null means open-ended / permanent. */
    val endDate: String? = null,
    /** Whether this is a temporary assignment (e.g., covering for another store). */
    val isTemporary: Boolean = false,
    val isActive: Boolean = true,
    val createdAt: Instant,
    val updatedAt: Instant,
)
