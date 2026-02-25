package com.zyntasolutions.zyntapos.domain.usecase.staff

import com.zyntasolutions.zyntapos.domain.model.PayrollRecord
import com.zyntasolutions.zyntapos.domain.repository.PayrollRepository
import kotlinx.coroutines.flow.Flow

/** Returns a reactive stream of all payroll records for an employee. */
class GetPayrollHistoryUseCase(
    private val payrollRepository: PayrollRepository,
) {
    operator fun invoke(employeeId: String): Flow<List<PayrollRecord>> =
        payrollRepository.getByEmployee(employeeId)
}
