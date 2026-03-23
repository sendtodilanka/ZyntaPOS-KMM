package com.zyntasolutions.zyntapos.domain.usecase.staff

import com.zyntasolutions.zyntapos.domain.repository.EmployeeStoreAssignmentRepository

/**
 * Revokes an employee's assignment to an additional store (C3.4).
 *
 * Soft-deactivates the assignment (sets is_active = false).
 * The employee's primary store (`Employee.storeId`) is unaffected.
 */
class RevokeEmployeeStoreAssignmentUseCase(
    private val repository: EmployeeStoreAssignmentRepository,
) {
    suspend operator fun invoke(employeeId: String, storeId: String) {
        repository.deactivate(employeeId, storeId)
    }
}
