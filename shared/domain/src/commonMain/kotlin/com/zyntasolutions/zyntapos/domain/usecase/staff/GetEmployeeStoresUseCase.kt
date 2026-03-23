package com.zyntasolutions.zyntapos.domain.usecase.staff

import com.zyntasolutions.zyntapos.domain.model.EmployeeStoreAssignment
import com.zyntasolutions.zyntapos.domain.repository.EmployeeStoreAssignmentRepository
import kotlinx.coroutines.flow.Flow

/**
 * Returns all active store assignments for an employee (C3.4 Employee Roaming).
 *
 * This includes additional stores beyond the employee's primary store
 * (`Employee.storeId`). The primary store is NOT included in this list.
 */
class GetEmployeeStoresUseCase(
    private val repository: EmployeeStoreAssignmentRepository,
) {
    operator fun invoke(employeeId: String): Flow<List<EmployeeStoreAssignment>> =
        repository.getAssignmentsForEmployee(employeeId)
}
