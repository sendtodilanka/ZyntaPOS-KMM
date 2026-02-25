package com.zyntasolutions.zyntapos.domain.usecase.staff

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Employee
import com.zyntasolutions.zyntapos.domain.repository.EmployeeRepository

/**
 * Retrieves a single employee by their unique ID.
 */
class GetEmployeeByIdUseCase(
    private val employeeRepository: EmployeeRepository,
) {
    suspend operator fun invoke(id: String): Result<Employee> =
        employeeRepository.getById(id)
}
