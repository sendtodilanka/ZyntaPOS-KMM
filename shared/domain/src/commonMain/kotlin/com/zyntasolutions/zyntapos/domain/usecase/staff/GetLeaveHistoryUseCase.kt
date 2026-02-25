package com.zyntasolutions.zyntapos.domain.usecase.staff

import com.zyntasolutions.zyntapos.domain.model.LeaveRecord
import com.zyntasolutions.zyntapos.domain.repository.LeaveRepository
import kotlinx.coroutines.flow.Flow

/** Returns a reactive stream of all leave records for an employee. */
class GetLeaveHistoryUseCase(
    private val leaveRepository: LeaveRepository,
) {
    operator fun invoke(employeeId: String): Flow<List<LeaveRecord>> =
        leaveRepository.getByEmployee(employeeId)
}
