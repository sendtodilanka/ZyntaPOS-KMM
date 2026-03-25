package com.zyntasolutions.zyntapos.feature.staff

/**
 * User actions for the Employee Store Assignments screen (C3.4).
 */
sealed interface EmployeeRoamingIntent {

    /** Load assignments for the given employee. */
    data class LoadAssignments(val employeeId: String, val employeeName: String) : EmployeeRoamingIntent

    /** Open the add-assignment dialog. */
    data object ShowAddDialog : EmployeeRoamingIntent

    /** Close the add-assignment dialog without saving. */
    data object HideAddDialog : EmployeeRoamingIntent

    /** Update a field in the add-assignment form. */
    data class UpdateField(val field: String, val value: String) : EmployeeRoamingIntent

    /** Toggle the isTemporary flag. */
    data object ToggleTemporary : EmployeeRoamingIntent

    /** Confirm and save the new assignment. */
    data object ConfirmAssignment : EmployeeRoamingIntent

    /** Revoke (soft-deactivate) an existing assignment. */
    data class RevokeAssignment(val storeId: String) : EmployeeRoamingIntent

    /** Dismiss a transient error. */
    data object DismissError : EmployeeRoamingIntent

    /** Dismiss a transient success message. */
    data object DismissSuccess : EmployeeRoamingIntent
}
