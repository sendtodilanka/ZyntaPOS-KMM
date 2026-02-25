package com.zyntasolutions.zyntapos.domain.usecase.staff

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.repository.EmployeeRepository

/**
 * Soft-deletes an employee (sets deleted_at timestamp).
 *
 * Soft delete is used to preserve historical payroll and attendance records.
 */
class DeleteEmployeeUseCase(
    private val employeeRepository: EmployeeRepository,
) {
    suspend operator fun invoke(id: String): Result<Unit> =
        employeeRepository.delete(id)
}
