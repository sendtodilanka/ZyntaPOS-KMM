package com.zyntasolutions.zyntapos.domain.usecase.staff

import com.zyntasolutions.zyntapos.domain.model.AttendanceRecord
import com.zyntasolutions.zyntapos.domain.repository.AttendanceRepository
import kotlinx.coroutines.flow.Flow

/**
 * Returns a reactive stream of all attendance records for an employee.
 */
class GetAttendanceHistoryUseCase(
    private val attendanceRepository: AttendanceRepository,
) {
    operator fun invoke(employeeId: String): Flow<List<AttendanceRecord>> =
        attendanceRepository.getByEmployee(employeeId)
}
