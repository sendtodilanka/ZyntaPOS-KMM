package com.zyntasolutions.zyntapos.feature.staff

import androidx.lifecycle.viewModelScope
import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.domain.model.EmployeeStoreAssignment
import com.zyntasolutions.zyntapos.domain.usecase.staff.AssignEmployeeToStoreUseCase
import com.zyntasolutions.zyntapos.domain.usecase.staff.GetEmployeeStoresUseCase
import com.zyntasolutions.zyntapos.domain.usecase.staff.RevokeEmployeeStoreAssignmentUseCase
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * ViewModel for the Employee Store Assignments screen (C3.4 Employee Roaming).
 *
 * Kept separate from [StaffViewModel] to avoid further bloating a constructor
 * that already has 22+ parameters.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class EmployeeRoamingViewModel(
    private val getEmployeeStoresUseCase: GetEmployeeStoresUseCase,
    private val assignEmployeeToStoreUseCase: AssignEmployeeToStoreUseCase,
    private val revokeEmployeeStoreAssignmentUseCase: RevokeEmployeeStoreAssignmentUseCase,
) : BaseViewModel<EmployeeRoamingState, EmployeeRoamingIntent, EmployeeRoamingEffect>(
    EmployeeRoamingState()
) {

    private val _employeeId = MutableStateFlow("")

    init {
        _employeeId
            .flatMapLatest { id ->
                if (id.isBlank()) flowOf(emptyList()) else getEmployeeStoresUseCase(id)
            }
            .onEach { list -> updateState { copy(assignments = list) } }
            .launchIn(viewModelScope)
    }

    override suspend fun handleIntent(intent: EmployeeRoamingIntent) {
        when (intent) {
            is EmployeeRoamingIntent.LoadAssignments -> {
                _employeeId.value = intent.employeeId
                updateState { copy(employeeId = intent.employeeId, employeeName = intent.employeeName) }
            }

            EmployeeRoamingIntent.ShowAddDialog ->
                updateState { copy(showAddDialog = true, addForm = AssignmentFormState()) }

            EmployeeRoamingIntent.HideAddDialog ->
                updateState { copy(showAddDialog = false, addForm = AssignmentFormState()) }

            is EmployeeRoamingIntent.UpdateField -> updateField(intent.field, intent.value)
            EmployeeRoamingIntent.ToggleTemporary ->
                updateState { copy(addForm = addForm.copy(isTemporary = !addForm.isTemporary)) }

            EmployeeRoamingIntent.ConfirmAssignment -> confirmAssignment()
            is EmployeeRoamingIntent.RevokeAssignment -> revokeAssignment(intent.storeId)

            EmployeeRoamingIntent.DismissError -> updateState { copy(error = null) }
            EmployeeRoamingIntent.DismissSuccess -> updateState { copy(successMessage = null) }
        }
    }

    private fun updateField(field: String, value: String) {
        updateState {
            val form = when (field) {
                "storeId" -> addForm.copy(storeId = value)
                "startDate" -> addForm.copy(startDate = value)
                "endDate" -> addForm.copy(endDate = value)
                else -> addForm
            }
            copy(addForm = form.copy(validationErrors = emptyMap()))
        }
    }

    private fun confirmAssignment() {
        val state = currentState
        val form = state.addForm
        val errors = mutableMapOf<String, String>()
        if (form.storeId.isBlank()) errors["storeId"] = "Store ID is required"
        if (form.startDate.isBlank()) errors["startDate"] = "Start date is required"
        if (errors.isNotEmpty()) {
            updateState { copy(addForm = addForm.copy(validationErrors = errors)) }
            return
        }

        // Guard: don't duplicate an existing active assignment
        val alreadyAssigned = state.assignments.any { it.storeId == form.storeId && it.isActive }
        if (alreadyAssigned) {
            updateState { copy(addForm = addForm.copy(validationErrors = mapOf("storeId" to "Employee is already assigned to this store"))) }
            return
        }

        viewModelScope.launch {
            updateState { copy(isLoading = true) }
            val now = Clock.System.now()
            val assignment = EmployeeStoreAssignment(
                id = IdGenerator.newId(),
                employeeId = state.employeeId,
                storeId = form.storeId.trim(),
                startDate = form.startDate.trim(),
                endDate = form.endDate.trim().ifBlank { null },
                isTemporary = form.isTemporary,
                isActive = true,
                createdAt = now,
                updatedAt = now,
            )
            try {
                assignEmployeeToStoreUseCase(assignment)
                updateState {
                    copy(
                        isLoading = false,
                        showAddDialog = false,
                        addForm = AssignmentFormState(),
                        successMessage = "Store assignment added.",
                    )
                }
            } catch (e: Exception) {
                updateState { copy(isLoading = false, error = e.message ?: "Failed to add assignment") }
            }
        }
    }

    private fun revokeAssignment(storeId: String) {
        viewModelScope.launch {
            updateState { copy(isLoading = true) }
            try {
                revokeEmployeeStoreAssignmentUseCase(currentState.employeeId, storeId)
                updateState { copy(isLoading = false, successMessage = "Assignment revoked.") }
            } catch (e: Exception) {
                updateState { copy(isLoading = false, error = e.message ?: "Failed to revoke assignment") }
            }
        }
    }
}
