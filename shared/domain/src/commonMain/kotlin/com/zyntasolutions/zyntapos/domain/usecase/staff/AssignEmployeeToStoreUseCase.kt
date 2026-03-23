package com.zyntasolutions.zyntapos.domain.usecase.staff

import com.zyntasolutions.zyntapos.domain.model.EmployeeStoreAssignment
import com.zyntasolutions.zyntapos.domain.repository.EmployeeStoreAssignmentRepository

/**
 * Assigns an employee to an additional store (C3.4 Employee Roaming).
 *
 * Idempotent — re-assigning an existing pair reactivates the assignment.
 */
class AssignEmployeeToStoreUseCase(
    private val repository: EmployeeStoreAssignmentRepository,
) {
    suspend operator fun invoke(assignment: EmployeeStoreAssignment) {
        repository.upsert(assignment)
    }
}
