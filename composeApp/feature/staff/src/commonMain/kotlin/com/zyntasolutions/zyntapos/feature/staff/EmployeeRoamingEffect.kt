package com.zyntasolutions.zyntapos.feature.staff

/**
 * One-shot side effects for the Employee Store Assignments screen (C3.4).
 */
sealed interface EmployeeRoamingEffect {

    /** Show a transient error Snackbar. */
    data class ShowError(val msg: String) : EmployeeRoamingEffect

    /** Show a transient success Snackbar. */
    data class ShowSuccess(val msg: String) : EmployeeRoamingEffect

    /** Navigate back to the employee detail screen. */
    data object NavigateBack : EmployeeRoamingEffect
}
