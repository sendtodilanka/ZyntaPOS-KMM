package com.zyntasolutions.zyntapos.domain.usecase.staff

import com.zyntasolutions.zyntapos.domain.model.LeaveRecord
import com.zyntasolutions.zyntapos.domain.repository.LeaveRepository
import kotlinx.coroutines.flow.Flow

/** Returns a reactive stream of all pending leave requests for a store (for manager review). */
class GetPendingLeaveRequestsUseCase(
    private val leaveRepository: LeaveRepository,
) {
    operator fun invoke(storeId: String): Flow<List<LeaveRecord>> =
        leaveRepository.getPendingForStore(storeId)
}
