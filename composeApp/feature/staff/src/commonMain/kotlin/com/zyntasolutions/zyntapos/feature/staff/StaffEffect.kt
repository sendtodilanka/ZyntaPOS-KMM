package com.zyntasolutions.zyntapos.feature.staff

/**
 * One-shot side-effect events emitted by [StaffViewModel].
 *
 * Collected via `LaunchedEffect(Unit) { viewModel.effects.collect { … } }`.
 */
sealed interface StaffEffect {

    /** Navigate to the employee detail/edit screen. Null means create new. */
    data class NavigateToEmployeeDetail(val employeeId: String?) : StaffEffect

    /** Navigate back from employee detail to the employee list. */
    data object NavigateToEmployeeList : StaffEffect

    /** Show a transient error Snackbar. */
    data class ShowError(val msg: String) : StaffEffect

    /** Show a transient success Snackbar. */
    data class ShowSuccess(val msg: String) : StaffEffect

    /** Navigate to the Employee Store Assignments screen (C3.4). */
    data class NavigateToEmployeeStores(val employeeId: String) : StaffEffect

    /**
     * Carries an attendance CSV export for the platform to save or share.
     *
     * @property csvContent Full UTF-8 string of CSV rows.
     * @property fileName   Suggested file name (e.g., `attendance_1711324800000.csv`).
     */
    data class ShareAttendanceExport(
        val csvContent: String,
        val fileName: String,
    ) : StaffEffect
}
