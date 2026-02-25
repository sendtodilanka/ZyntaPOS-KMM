package com.zyntasolutions.zyntapos.domain.usecase.staff

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.Employee
import com.zyntasolutions.zyntapos.domain.repository.EmployeeRepository

/**
 * Inserts or updates an employee profile.
 *
 * ### Business Rules
 * 1. [Employee.firstName] must not be blank.
 * 2. [Employee.lastName] must not be blank.
 * 3. [Employee.position] must not be blank.
 * 4. [Employee.commissionRate] must be within 0.0–100.0.
 * 5. [Employee.salary] must be null or non-negative.
 */
class SaveEmployeeUseCase(
    private val employeeRepository: EmployeeRepository,
) {
    suspend operator fun invoke(employee: Employee, isNew: Boolean): Result<Unit> {
        if (employee.firstName.isBlank()) {
            return Result.Error(
                ValidationException("First name must not be blank.", field = "firstName", rule = "REQUIRED"),
            )
        }
        if (employee.lastName.isBlank()) {
            return Result.Error(
                ValidationException("Last name must not be blank.", field = "lastName", rule = "REQUIRED"),
            )
        }
        if (employee.position.isBlank()) {
            return Result.Error(
                ValidationException("Position must not be blank.", field = "position", rule = "REQUIRED"),
            )
        }
        if (employee.commissionRate < 0.0 || employee.commissionRate > 100.0) {
            return Result.Error(
                ValidationException(
                    "Commission rate must be between 0 and 100.",
                    field = "commissionRate",
                    rule = "RANGE",
                ),
            )
        }
        if (employee.salary != null && employee.salary < 0.0) {
            return Result.Error(
                ValidationException("Salary must be non-negative.", field = "salary", rule = "MIN_VALUE"),
            )
        }

        return if (isNew) employeeRepository.insert(employee) else employeeRepository.update(employee)
    }
}
