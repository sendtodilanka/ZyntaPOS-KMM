package com.zyntasolutions.zyntapos.domain.usecase.staff

import com.zyntasolutions.zyntapos.domain.model.Employee
import com.zyntasolutions.zyntapos.domain.repository.EmployeeRepository
import kotlinx.coroutines.flow.Flow

/**
 * Returns a reactive stream of active employees for the current store.
 */
class GetEmployeesUseCase(
    private val employeeRepository: EmployeeRepository,
) {
    operator fun invoke(storeId: String): Flow<List<Employee>> =
        employeeRepository.getActive(storeId)
}
