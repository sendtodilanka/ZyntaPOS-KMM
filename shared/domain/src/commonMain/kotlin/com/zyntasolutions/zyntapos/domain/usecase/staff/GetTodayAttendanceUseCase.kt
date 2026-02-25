package com.zyntasolutions.zyntapos.domain.usecase.staff

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.AttendanceRecord
import com.zyntasolutions.zyntapos.domain.repository.AttendanceRepository

/**
 * Returns today's attendance records for all employees in a store.
 *
 * @param storeId The store to query.
 * @param todayPrefix ISO date prefix: YYYY-MM-DD.
 */
class GetTodayAttendanceUseCase(
    private val attendanceRepository: AttendanceRepository,
) {
    suspend operator fun invoke(storeId: String, todayPrefix: String): Result<List<AttendanceRecord>> =
        attendanceRepository.getTodayForStore(storeId, todayPrefix)
}
