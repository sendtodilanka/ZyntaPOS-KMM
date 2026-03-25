package com.zyntasolutions.zyntapos.feature.staff

import com.zyntasolutions.zyntapos.domain.model.EmployeeStoreAssignment

/**
 * Immutable UI state for the Employee Store Assignments screen (C3.4 Employee Roaming).
 *
 * Displays the list of additional store assignments for a specific employee
 * and drives the add-assignment dialog.
 */
data class EmployeeRoamingState(
    val employeeId: String = "",
    val employeeName: String = "",
    val assignments: List<EmployeeStoreAssignment> = emptyList(),
    val showAddDialog: Boolean = false,
    val addForm: AssignmentFormState = AssignmentFormState(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
)

/** Mutable form state for creating a new store assignment. */
data class AssignmentFormState(
    val storeId: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val isTemporary: Boolean = false,
    val validationErrors: Map<String, String> = emptyMap(),
)
