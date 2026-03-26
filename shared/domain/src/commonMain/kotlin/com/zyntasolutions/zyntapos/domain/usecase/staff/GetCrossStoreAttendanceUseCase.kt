package com.zyntasolutions.zyntapos.domain.usecase.staff

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.AttendanceStatus
import com.zyntasolutions.zyntapos.domain.model.Employee
import com.zyntasolutions.zyntapos.domain.repository.AttendanceRepository

/**
 * C3.4: Aggregates attendance records across all stores for a list of employees.
 *
 * Uses the optimized cross-store SQL JOIN query instead of N x M loops.
 * Returns per-employee-per-store summary rows.
 */
class GetCrossStoreAttendanceUseCase(
    private val attendanceRepository: AttendanceRepository,
) {

    /**
     * @param employees List of employees to query.
     * @param from ISO datetime prefix (inclusive).
     * @param to ISO datetime prefix (inclusive).
     * @return Aggregated rows grouped by (employee, store).
     */
    suspend operator fun invoke(
        employees: List<Employee>,
        from: String,
        to: String,
    ): Result<List<CrossStoreAttendanceSummary>> {
        val allRows = mutableListOf<CrossStoreAttendanceSummary>()

        for (employee in employees) {
            when (val result = attendanceRepository.getByEmployeeAcrossStores(employee.id, from, to)) {
                is Result.Success -> {
                    // Group by storeId and aggregate
                    val grouped = result.data.groupBy { (record, _) -> record.storeId }
                    for ((storeId, entries) in grouped) {
                        val storeName = entries.firstOrNull()?.second ?: storeId ?: "Unknown"
                        val totalHours = entries.sumOf { (record, _) -> record.totalHours ?: 0.0 }
                        val lateCount = entries.count { (record, _) ->
                            record.status == AttendanceStatus.LATE
                        }
                        allRows.add(
                            CrossStoreAttendanceSummary(
                                employeeId = employee.id,
                                employeeName = "${employee.firstName} ${employee.lastName}",
                                storeId = storeId ?: "",
                                storeName = storeName,
                                totalDays = entries.size,
                                totalHoursWorked = totalHours,
                                lateArrivals = lateCount,
                            ),
                        )
                    }
                }
                is Result.Error -> return Result.Error(result.exception)
                is Result.Loading -> return Result.Loading
            }
        }

        return Result.Success(allRows)
    }
}

/**
 * Summary row for cross-store attendance reporting.
 */
data class CrossStoreAttendanceSummary(
    val employeeId: String,
    val employeeName: String,
    val storeId: String,
    val storeName: String,
    val totalDays: Int,
    val totalHoursWorked: Double,
    val lateArrivals: Int,
)
