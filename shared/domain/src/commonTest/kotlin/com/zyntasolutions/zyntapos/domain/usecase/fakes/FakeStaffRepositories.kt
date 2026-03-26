package com.zyntasolutions.zyntapos.domain.usecase.fakes

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.AttendanceRecord
import com.zyntasolutions.zyntapos.domain.model.AttendanceSummary
import com.zyntasolutions.zyntapos.domain.model.Employee
import com.zyntasolutions.zyntapos.domain.model.LeaveRecord
import com.zyntasolutions.zyntapos.domain.model.LeaveRequest
import com.zyntasolutions.zyntapos.domain.model.LeaveRequestStatus
import com.zyntasolutions.zyntapos.domain.model.LeaveStatus
import com.zyntasolutions.zyntapos.domain.model.PayrollRecord
import com.zyntasolutions.zyntapos.domain.model.PayrollStatus
import com.zyntasolutions.zyntapos.domain.model.PayrollSummary
import com.zyntasolutions.zyntapos.domain.model.SalaryType
import com.zyntasolutions.zyntapos.domain.repository.AttendanceRepository
import com.zyntasolutions.zyntapos.domain.repository.EmployeeRepository
import com.zyntasolutions.zyntapos.domain.repository.LeaveRepository
import com.zyntasolutions.zyntapos.domain.repository.PayrollRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

// ─────────────────────────────────────────────────────────────────────────────
// Fixtures
// ─────────────────────────────────────────────────────────────────────────────

/** Builds an [AttendanceRecord] with sensible defaults (open — no clock-out). */
fun buildAttendanceRecord(
    id: String = "att-01",
    employeeId: String = "emp-01",
    clockIn: String = "2026-02-27T08:00:00",
    clockOut: String? = null,
    totalHours: Double? = null,
    overtimeHours: Double = 0.0,
    createdAt: Long = 1_000_000L,
    updatedAt: Long = 1_000_000L,
) = AttendanceRecord(
    id = id,
    employeeId = employeeId,
    clockIn = clockIn,
    clockOut = clockOut,
    totalHours = totalHours,
    overtimeHours = overtimeHours,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/** Builds an [Employee] with sensible defaults. */
fun buildEmployee(
    id: String = "emp-01",
    storeId: String = "store-01",
    firstName: String = "Jane",
    lastName: String = "Doe",
    position: String = "Cashier",
    salary: Double? = 1000.0,
    salaryType: SalaryType = SalaryType.MONTHLY,
    commissionRate: Double = 0.0,
    hireDate: String = "2025-01-01",
    createdAt: Long = 1_000_000L,
    updatedAt: Long = 1_000_000L,
) = Employee(
    id = id,
    storeId = storeId,
    firstName = firstName,
    lastName = lastName,
    position = position,
    salary = salary,
    salaryType = salaryType,
    commissionRate = commissionRate,
    hireDate = hireDate,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/** Builds a [LeaveRecord] with sensible defaults. */
fun buildLeaveRecord(
    id: String = "leave-01",
    employeeId: String = "emp-01",
    leaveType: com.zyntasolutions.zyntapos.domain.model.LeaveType = com.zyntasolutions.zyntapos.domain.model.LeaveType.ANNUAL,
    startDate: String = "2026-03-01",
    endDate: String = "2026-03-05",
    reason: String? = "Family trip",
    status: LeaveStatus = LeaveStatus.PENDING,
    createdAt: Long = 1_000_000L,
    updatedAt: Long = 1_000_000L,
) = LeaveRecord(
    id = id,
    employeeId = employeeId,
    leaveType = leaveType,
    startDate = startDate,
    endDate = endDate,
    reason = reason,
    status = status,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/** Builds an [AttendanceSummary] with sensible defaults. */
fun buildAttendanceSummary(
    employeeId: String = "emp-01",
    totalDays: Int = 20,
    presentDays: Int = 18,
    absentDays: Int = 2,
    lateDays: Int = 1,
    leaveDays: Int = 0,
    totalHours: Double = 144.0,
    overtimeHours: Double = 4.0,
) = AttendanceSummary(
    employeeId = employeeId,
    totalDays = totalDays,
    presentDays = presentDays,
    absentDays = absentDays,
    lateDays = lateDays,
    leaveDays = leaveDays,
    totalHours = totalHours,
    overtimeHours = overtimeHours,
)

// ─────────────────────────────────────────────────────────────────────────────
// FakeAttendanceRepository
// ─────────────────────────────────────────────────────────────────────────────

class FakeAttendanceRepository : AttendanceRepository {

    val records = mutableListOf<AttendanceRecord>()

    /** Controls what [getOpenRecord] returns. Set to an existing record to simulate already-clocked-in. */
    var openRecord: AttendanceRecord? = null

    var shouldFailInsert: Boolean = false
    var shouldFailClockOut: Boolean = false
    var shouldFailGetOpenRecord: Boolean = false

    /**
     * When set, [getSummary] returns this value instead of computing from [records].
     * Use this in tests to control the attendance summary fed into payroll calculations.
     */
    var summaryOverride: AttendanceSummary? = null

    /** Captured arguments from [clockOut] calls for assertion. */
    val clockOutCalls = mutableListOf<ClockOutCall>()

    data class ClockOutCall(
        val id: String,
        val clockOut: String,
        val totalHours: Double,
        val overtimeHours: Double,
        val updatedAt: Long,
    )

    override fun getByEmployee(employeeId: String): Flow<List<AttendanceRecord>> =
        flowOf(records.filter { it.employeeId == employeeId })

    override suspend fun getByEmployeeForPeriod(
        employeeId: String,
        from: String,
        to: String,
    ): Result<List<AttendanceRecord>> {
        val filtered = records.filter {
            it.employeeId == employeeId &&
                it.clockIn >= "${from}T00:00:00" &&
                it.clockIn <= "${to}T23:59:59"
        }
        return Result.Success(filtered)
    }

    override suspend fun getOpenRecord(employeeId: String): Result<AttendanceRecord?> {
        if (shouldFailGetOpenRecord) {
            return Result.Error(DatabaseException("DB error reading open record"))
        }
        return Result.Success(openRecord)
    }

    override suspend fun getTodayForStore(
        storeId: String,
        todayPrefix: String,
    ): Result<List<AttendanceRecord>> =
        Result.Success(records.filter { it.clockIn.startsWith(todayPrefix) })

    override suspend fun insert(record: AttendanceRecord): Result<Unit> {
        if (shouldFailInsert) return Result.Error(DatabaseException("Insert failed"))
        records.add(record)
        openRecord = record
        return Result.Success(Unit)
    }

    override suspend fun clockOut(
        id: String,
        clockOut: String,
        totalHours: Double,
        overtimeHours: Double,
        updatedAt: Long,
    ): Result<Unit> {
        if (shouldFailClockOut) return Result.Error(DatabaseException("Clock-out DB error"))
        val index = records.indexOfFirst { it.id == id }
        if (index != -1) {
            records[index] = records[index].copy(
                clockOut = clockOut,
                totalHours = totalHours,
                overtimeHours = overtimeHours,
                updatedAt = updatedAt,
            )
        }
        clockOutCalls.add(ClockOutCall(id, clockOut, totalHours, overtimeHours, updatedAt))
        this.openRecord = null
        return Result.Success(Unit)
    }

    override suspend fun updateNotes(id: String, notes: String, updatedAt: Long): Result<Unit> {
        val index = records.indexOfFirst { it.id == id }
        if (index == -1) return Result.Error(DatabaseException("Record not found"))
        records[index] = records[index].copy(notes = notes, updatedAt = updatedAt)
        return Result.Success(Unit)
    }

    override suspend fun getSummary(
        employeeId: String,
        from: String,
        to: String,
    ): Result<AttendanceSummary> =
        Result.Success(summaryOverride ?: buildAttendanceSummary(employeeId = employeeId))

    override suspend fun getByEmployeeAcrossStores(
        employeeId: String,
        from: String,
        to: String,
    ): Result<List<Pair<AttendanceRecord, String?>>> {
        val filtered = records.filter {
            it.employeeId == employeeId &&
                it.clockIn >= from &&
                it.clockIn <= to
        }.map { it to it.storeId }
        return Result.Success(filtered)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FakeEmployeeRepository
// ─────────────────────────────────────────────────────────────────────────────

class FakeEmployeeRepository : EmployeeRepository {

    val employees = mutableListOf<Employee>()
    var shouldFailInsert: Boolean = false
    var shouldFailUpdate: Boolean = false
    var shouldFailDelete: Boolean = false

    override fun getActive(storeId: String): Flow<List<Employee>> =
        flowOf(employees.filter { it.storeId == storeId && it.isActive })

    override fun getAll(storeId: String): Flow<List<Employee>> =
        flowOf(employees.filter { it.storeId == storeId })

    override suspend fun getById(id: String): Result<Employee> {
        val emp = employees.firstOrNull { it.id == id }
            ?: return Result.Error(DatabaseException("Employee '$id' not found"))
        return Result.Success(emp)
    }

    override suspend fun getByUserId(userId: String): Result<Employee?> =
        Result.Success(employees.firstOrNull { it.userId == userId })

    override suspend fun search(storeId: String, query: String): Result<List<Employee>> {
        val lower = query.lowercase()
        return Result.Success(
            employees.filter {
                it.storeId == storeId &&
                    (it.firstName.lowercase().contains(lower) ||
                        it.lastName.lowercase().contains(lower) ||
                        (it.email?.lowercase()?.contains(lower) == true) ||
                        it.position.lowercase().contains(lower))
            },
        )
    }

    override suspend fun insert(employee: Employee): Result<Unit> {
        if (shouldFailInsert) return Result.Error(DatabaseException("Insert failed"))
        employees.add(employee)
        return Result.Success(Unit)
    }

    override suspend fun update(employee: Employee): Result<Unit> {
        if (shouldFailUpdate) return Result.Error(DatabaseException("Update failed"))
        val index = employees.indexOfFirst { it.id == employee.id }
        if (index == -1) return Result.Error(DatabaseException("Employee not found"))
        employees[index] = employee
        return Result.Success(Unit)
    }

    override suspend fun setActive(id: String, isActive: Boolean): Result<Unit> {
        val index = employees.indexOfFirst { it.id == id }
        if (index == -1) return Result.Error(DatabaseException("Employee not found"))
        employees[index] = employees[index].copy(isActive = isActive)
        return Result.Success(Unit)
    }

    override suspend fun delete(id: String): Result<Unit> {
        if (shouldFailDelete) return Result.Error(DatabaseException("Delete failed"))
        val removed = employees.removeAll { it.id == id }
        return if (removed) Result.Success(Unit)
        else Result.Error(DatabaseException("Employee '$id' not found"))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FakeLeaveRepository
// ─────────────────────────────────────────────────────────────────────────────

class FakeLeaveRepository : LeaveRepository {

    val leaveRecords = mutableListOf<LeaveRecord>()
    var shouldFailInsert: Boolean = false
    var shouldFailUpdateStatus: Boolean = false

    /** Captured arguments from [updateStatus] calls for assertion. */
    val updateStatusCalls = mutableListOf<UpdateStatusCall>()

    data class UpdateStatusCall(
        val id: String,
        val status: LeaveStatus,
        val decidedBy: String,
        val decidedAt: Long,
        val rejectionReason: String?,
        val updatedAt: Long,
    )

    override fun getByEmployee(employeeId: String): Flow<List<LeaveRecord>> =
        flowOf(leaveRecords.filter { it.employeeId == employeeId })

    override fun getPendingForStore(storeId: String): Flow<List<LeaveRecord>> =
        flowOf(leaveRecords.filter { it.status == LeaveStatus.PENDING })

    override suspend fun getById(id: String): Result<LeaveRecord> {
        val record = leaveRecords.firstOrNull { it.id == id }
            ?: return Result.Error(DatabaseException("Leave record '$id' not found"))
        return Result.Success(record)
    }

    override suspend fun getByEmployeeAndPeriod(
        employeeId: String,
        from: String,
        to: String,
    ): Result<List<LeaveRecord>> {
        val overlapping = leaveRecords.filter {
            it.employeeId == employeeId &&
                it.startDate <= to &&
                it.endDate >= from
        }
        return Result.Success(overlapping)
    }

    override suspend fun insert(record: LeaveRecord): Result<Unit> {
        if (shouldFailInsert) return Result.Error(DatabaseException("Insert failed"))
        leaveRecords.add(record)
        return Result.Success(Unit)
    }

    override suspend fun updateStatus(
        id: String,
        status: LeaveStatus,
        decidedBy: String,
        decidedAt: Long,
        rejectionReason: String?,
        updatedAt: Long,
    ): Result<Unit> {
        if (shouldFailUpdateStatus) return Result.Error(DatabaseException("Update status failed"))
        val index = leaveRecords.indexOfFirst { it.id == id }
        if (index != -1) {
            leaveRecords[index] = leaveRecords[index].copy(
                status = status,
                approvedBy = decidedBy,
                approvedAt = decidedAt,
                rejectionReason = rejectionReason,
                updatedAt = updatedAt,
            )
        }
        updateStatusCalls.add(UpdateStatusCall(id, status, decidedBy, decidedAt, rejectionReason, updatedAt))
        return Result.Success(Unit)
    }

    // ── LeaveRequest workflow stubs ─────────────────────────────────────────

    val leaveRequests = mutableListOf<LeaveRequest>()

    override suspend fun getLeaveRequestById(id: String): Result<LeaveRequest?> =
        Result.Success(leaveRequests.firstOrNull { it.id == id })

    override fun getLeaveRequestsByEmployee(employeeId: String): Flow<List<LeaveRequest>> =
        flowOf(leaveRequests.filter { it.employeeId == employeeId })

    override fun getPendingLeaveRequests(): Flow<List<LeaveRequest>> =
        flowOf(leaveRequests.filter { it.status == LeaveRequestStatus.PENDING })

    override suspend fun insertLeaveRequest(request: LeaveRequest): Result<Unit> {
        leaveRequests.add(request)
        return Result.Success(Unit)
    }

    override suspend fun updateLeaveRequestStatus(
        id: String,
        status: LeaveRequestStatus,
        approverNotes: String?,
        updatedAt: Long,
    ): Result<Unit> {
        val index = leaveRequests.indexOfFirst { it.id == id }
        if (index != -1) {
            leaveRequests[index] = leaveRequests[index].copy(
                status = status,
                approverNotes = approverNotes,
                updatedAt = updatedAt,
            )
        }
        return Result.Success(Unit)
    }
}

/** Builds a [LeaveRequest] with sensible defaults. */
fun buildLeaveRequest(
    id: String = "leave-01",
    employeeId: String = "emp-01",
    leaveType: com.zyntasolutions.zyntapos.domain.model.LeaveRequestType = com.zyntasolutions.zyntapos.domain.model.LeaveRequestType.ANNUAL,
    startDate: String = "2026-03-01",
    endDate: String = "2026-03-05",
    reason: String = "Family trip",
    status: LeaveRequestStatus = LeaveRequestStatus.PENDING,
    approverNotes: String? = null,
    createdAt: Long = 1_000_000L,
    updatedAt: Long = 1_000_000L,
) = LeaveRequest(
    id = id,
    employeeId = employeeId,
    leaveType = leaveType,
    startDate = startDate,
    endDate = endDate,
    reason = reason,
    status = status,
    approverNotes = approverNotes,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

// ─────────────────────────────────────────────────────────────────────────────
// FakePayrollRepository
// ─────────────────────────────────────────────────────────────────────────────

class FakePayrollRepository : PayrollRepository {

    val payrollRecords = mutableListOf<PayrollRecord>()
    var shouldFailInsert: Boolean = false

    /** Pre-configured existing record to simulate a duplicate-period scenario. */
    var existingRecord: PayrollRecord? = null

    /**
     * When true, [getByEmployeeAndPeriod] returns a [Result.Error] instead of querying records.
     * Use this to simulate DB failures on the duplicate-check query.
     */
    var shouldFailGetByEmployeeAndPeriod: Boolean = false

    override fun getByEmployee(employeeId: String): Flow<List<PayrollRecord>> =
        flowOf(payrollRecords.filter { it.employeeId == employeeId })

    override suspend fun getByPeriodForStore(storeId: String, period: String): Result<List<PayrollRecord>> =
        Result.Success(payrollRecords.filter { it.periodStart.startsWith(period) })

    override suspend fun getByEmployeeAndPeriod(
        employeeId: String,
        periodStart: String,
    ): Result<PayrollRecord?> {
        if (shouldFailGetByEmployeeAndPeriod) {
            return Result.Error(DatabaseException("DB failure on period lookup"))
        }
        return Result.Success(
            existingRecord?.takeIf { it.employeeId == employeeId && it.periodStart == periodStart }
                ?: payrollRecords.firstOrNull {
                    it.employeeId == employeeId && it.periodStart == periodStart
                },
        )
    }

    override suspend fun getById(id: String): Result<PayrollRecord> {
        val record = payrollRecords.firstOrNull { it.id == id }
            ?: return Result.Error(DatabaseException("Payroll record '$id' not found"))
        return Result.Success(record)
    }

    override suspend fun getPending(storeId: String): Result<List<PayrollRecord>> =
        Result.Success(payrollRecords.filter { it.status == PayrollStatus.PENDING })

    override suspend fun insert(record: PayrollRecord): Result<Unit> {
        if (shouldFailInsert) return Result.Error(DatabaseException("Insert failed"))
        payrollRecords.add(record)
        return Result.Success(Unit)
    }

    override suspend fun markPaid(
        id: String,
        paidAt: Long,
        paymentRef: String?,
        updatedAt: Long,
    ): Result<Unit> {
        val index = payrollRecords.indexOfFirst { it.id == id }
        if (index == -1) return Result.Error(DatabaseException("Payroll record not found"))
        payrollRecords[index] = payrollRecords[index].copy(
            status = PayrollStatus.PAID,
            paidAt = paidAt,
            paymentRef = paymentRef,
            updatedAt = updatedAt,
        )
        return Result.Success(Unit)
    }

    override suspend fun updateCalculation(
        id: String,
        baseSalary: Double,
        overtimePay: Double,
        commission: Double,
        deductions: Double,
        netPay: Double,
        updatedAt: Long,
    ): Result<Unit> {
        val index = payrollRecords.indexOfFirst { it.id == id }
        if (index == -1) return Result.Error(DatabaseException("Payroll record not found"))
        payrollRecords[index] = payrollRecords[index].copy(
            baseSalary = baseSalary,
            overtimePay = overtimePay,
            commission = commission,
            deductions = deductions,
            netPay = netPay,
            updatedAt = updatedAt,
        )
        return Result.Success(Unit)
    }

    override suspend fun getSummary(storeId: String, period: String): Result<PayrollSummary> {
        val records = payrollRecords.filter { it.periodStart.startsWith(period) }
        val summary = PayrollSummary(
            period = period,
            totalEmployees = records.map { it.employeeId }.distinct().size,
            totalGrossPay = records.sumOf { it.grossPay },
            totalDeductions = records.sumOf { it.deductions },
            totalNetPay = records.sumOf { it.netPay },
            pendingCount = records.count { it.status == PayrollStatus.PENDING },
            paidCount = records.count { it.status == PayrollStatus.PAID },
        )
        return Result.Success(summary)
    }
}
