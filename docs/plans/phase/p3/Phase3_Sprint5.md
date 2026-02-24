# ZyntaPOS — Phase 3 Sprint 5: Staff & HR Repository Implementations + Use Case Impls

> **Document ID:** ZYNTA-PLAN-PHASE3-SPRINT5-v1.0
> **Phase:** 3 — Enterprise (Months 13–18)
> **Sprint:** 5 of 24 | Week 5
> **Module(s):** `:shared:data`, `:shared:domain`
> **Author:** Senior KMP Architect & Lead Engineer
> **Reference:** ZYNTA-PLAN-PHASE3-SPRINT3-v1.0 | ZYNTA-PLAN-PHASE2-v1.0 §Sprint 6

---

## Goal

Implement all 5 Staff & HR repository classes in `:shared:data`, all 18 Staff & HR use case implementations in `:shared:domain`, and register all new Koin bindings in `DataModule`. Unit tests for critical payroll calculation must achieve 90% coverage.

---

## New Files to Create

### Repository Implementation Files

**Location:** `shared/data/src/commonMain/kotlin/com/zyntasolutions/zyntapos/data/repository/`

#### `EmployeeRepositoryImpl.kt`
```kotlin
class EmployeeRepositoryImpl(
    private val db: ZyntaPosDatabase
) : EmployeeRepository {

    override fun getByStore(storeId: String): Flow<List<Employee>> =
        db.employeesQueries.selectByStore(storeId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { it.map(EmployeeMapper::toDomain) }

    override suspend fun getById(id: String): Employee? =
        db.employeesQueries.selectById(id).executeAsOneOrNull()?.let(EmployeeMapper::toDomain)

    override suspend fun getByUserId(userId: String): Employee? =
        db.employeesQueries.selectByUserId(userId).executeAsOneOrNull()?.let(EmployeeMapper::toDomain)

    override suspend fun save(employee: Employee): Result<Employee> = runCatching {
        db.employeesQueries.upsert(EmployeeMapper.toDb(employee))
        employee
    }

    override suspend fun delete(id: String): Result<Unit> = runCatching {
        val now = Clock.System.now().toString()
        db.employeesQueries.softDelete(id = id, deletedAt = now, updatedAt = now)
    }
}
```

#### `AttendanceRepositoryImpl.kt`

**Critical business rule: Only one open record per employee at a time.**

```kotlin
class AttendanceRepositoryImpl(
    private val db: ZyntaPosDatabase
) : AttendanceRepository {

    override fun getByEmployee(employeeId: String): Flow<List<AttendanceRecord>> =
        db.attendanceRecordsQueries.selectByEmployee(employeeId)
            .asFlow().mapToList(Dispatchers.IO)
            .map { it.map(AttendanceMapper::toDomain) }

    override suspend fun getForPeriod(employeeId: String, from: String, to: String): List<AttendanceRecord> =
        db.attendanceRecordsQueries.selectByEmployeeForPeriod(employeeId, from, to)
            .executeAsList().map(AttendanceMapper::toDomain)

    override suspend fun getOpenRecord(employeeId: String): AttendanceRecord? =
        db.attendanceRecordsQueries.selectOpenRecord(employeeId)
            .executeAsOneOrNull()?.let(AttendanceMapper::toDomain)

    override fun getTodayForStore(storeId: String, datePrefix: String): Flow<List<AttendanceRecord>> {
        // Joins attendance_records with employees on store_id via a custom query
        return db.attendanceRecordsQueries.selectTodayForAllEmployees(datePrefix)
            .asFlow().mapToList(Dispatchers.IO)
            .map { it.map(AttendanceMapper::toDomain) }
    }

    override suspend fun save(record: AttendanceRecord): Result<AttendanceRecord> = runCatching {
        db.attendanceRecordsQueries.upsert(AttendanceMapper.toDb(record))
        record
    }

    override suspend fun clockOut(
        id: String, clockOut: String, totalHours: Double, overtimeHours: Double
    ): Result<Unit> = runCatching {
        db.attendanceRecordsQueries.clockOut(
            clockOut = clockOut,
            totalHours = totalHours,
            overtimeHours = overtimeHours,
            updatedAt = Clock.System.now().toString(),
            id = id
        )
    }
}
```

#### `LeaveRepositoryImpl.kt`

```kotlin
class LeaveRepositoryImpl(
    private val db: ZyntaPosDatabase
) : LeaveRepository {

    override fun getByEmployee(employeeId: String): Flow<List<LeaveRecord>> =
        db.leaveRecordsQueries.selectByEmployee(employeeId)
            .asFlow().mapToList(Dispatchers.IO)
            .map { it.map(LeaveMapper::toDomain) }

    override fun getPendingForStore(storeId: String): Flow<List<LeaveRecord>> =
        db.leaveRecordsQueries.selectPendingForStore(storeId)
            .asFlow().mapToList(Dispatchers.IO)
            .map { it.map(LeaveMapper::toDomain) }

    override suspend fun save(record: LeaveRecord): Result<LeaveRecord> = runCatching {
        db.leaveRecordsQueries.upsert(LeaveMapper.toDb(record))
        record
    }

    override suspend fun updateStatus(
        id: String,
        status: LeaveStatus,
        approvedBy: String?,
        rejectionReason: String?
    ): Result<Unit> = runCatching {
        val now = Clock.System.now().toString()
        db.leaveRecordsQueries.updateStatus(
            status = status.name,
            approvedBy = approvedBy,
            approvedAt = if (status == LeaveStatus.APPROVED || status == LeaveStatus.REJECTED) now else null,
            rejectionReason = rejectionReason,
            updatedAt = now,
            id = id
        )
    }
}
```

#### `PayrollRepositoryImpl.kt`

```kotlin
class PayrollRepositoryImpl(
    private val db: ZyntaPosDatabase
) : PayrollRepository {

    override fun getByEmployee(employeeId: String): Flow<List<PayrollRecord>> =
        db.payrollRecordsQueries.selectByEmployee(employeeId)
            .asFlow().mapToList(Dispatchers.IO)
            .map { it.map(PayrollMapper::toDomain) }

    override suspend fun getByPeriod(storeId: String, periodStart: String): List<PayrollRecord> =
        db.payrollRecordsQueries.selectByPeriod(periodStart, storeId)
            .executeAsList().map(PayrollMapper::toDomain)

    override suspend fun getByEmployeeAndPeriod(employeeId: String, periodStart: String): PayrollRecord? =
        db.payrollRecordsQueries.selectByEmployeeAndPeriod(employeeId, periodStart)
            .executeAsOneOrNull()?.let(PayrollMapper::toDomain)

    override suspend fun save(record: PayrollRecord): Result<PayrollRecord> = runCatching {
        // UNIQUE(employee_id, period_start) enforced at DB level
        db.payrollRecordsQueries.upsert(PayrollMapper.toDb(record))
        record
    }

    override suspend fun updateStatus(
        id: String, status: PayrollStatus, paidAt: String?, paymentRef: String?
    ): Result<Unit> = runCatching {
        db.payrollRecordsQueries.updateStatus(
            status = status.name,
            paidAt = paidAt,
            paymentRef = paymentRef,
            updatedAt = Clock.System.now().toString(),
            id = id
        )
    }
}
```

#### `ShiftRepositoryImpl.kt`

```kotlin
class ShiftRepositoryImpl(
    private val db: ZyntaPosDatabase
) : ShiftRepository {

    override fun getByStoreAndWeek(
        storeId: String, weekStart: String, weekEnd: String
    ): Flow<List<ShiftSchedule>> =
        db.shiftSchedulesQueries.selectByStoreAndWeek(storeId, weekStart, weekEnd)
            .asFlow().mapToList(Dispatchers.IO)
            .map { it.map(ShiftMapper::toDomain) }

    override suspend fun getByEmployee(employeeId: String, from: String, to: String): List<ShiftSchedule> =
        db.shiftSchedulesQueries.selectByEmployee(employeeId, from, to)
            .executeAsList().map(ShiftMapper::toDomain)

    override suspend fun save(shift: ShiftSchedule): Result<ShiftSchedule> = runCatching {
        // UNIQUE(employee_id, shift_date) — INSERT OR REPLACE acts as upsert
        db.shiftSchedulesQueries.upsert(ShiftMapper.toDb(shift))
        shift
    }

    override suspend fun delete(id: String): Result<Unit> = runCatching {
        db.shiftSchedulesQueries.deleteById(id)
    }
}
```

### Mapper Files

**Location:** `shared/data/src/commonMain/kotlin/com/zyntasolutions/zyntapos/data/local/mapper/`

Create: `EmployeeMapper.kt`, `AttendanceMapper.kt`, `LeaveMapper.kt`, `PayrollMapper.kt`, `ShiftMapper.kt`

Each mapper has two functions:
- `fun toDomain(db: DbType): DomainType`
- `fun toDb(domain: DomainType): DbType`

### Use Case Implementation Files

**Location:** `shared/domain/src/commonMain/kotlin/com/zyntasolutions/zyntapos/domain/usecase/staff/`

#### `ClockInUseCaseImpl.kt`
```kotlin
class ClockInUseCaseImpl(
    private val attendanceRepository: AttendanceRepository
) : ClockInUseCase {
    override suspend fun invoke(employeeId: String): Result<AttendanceRecord> {
        // Guard: reject if open record already exists
        val openRecord = attendanceRepository.getOpenRecord(employeeId)
        if (openRecord != null) {
            return Result.failure(IllegalStateException("Employee already clocked in at ${openRecord.clockIn}"))
        }
        val record = AttendanceRecord(
            id = generateUuid(),
            employeeId = employeeId,
            clockIn = Clock.System.now().toString(),
            clockOut = null,
            totalHours = null,
            overtimeHours = 0.0,
            notes = null,
            status = AttendanceStatus.PRESENT,
            createdAt = Clock.System.now().toString(),
            updatedAt = Clock.System.now().toString()
        )
        return attendanceRepository.save(record)
    }
}
```

#### `ClockOutUseCaseImpl.kt`
```kotlin
class ClockOutUseCaseImpl(
    private val attendanceRepository: AttendanceRepository
) : ClockOutUseCase {
    override suspend fun invoke(employeeId: String): Result<AttendanceRecord> {
        val openRecord = attendanceRepository.getOpenRecord(employeeId)
            ?: return Result.failure(IllegalStateException("No open clock-in record found"))

        val clockOut = Clock.System.now().toString()
        val totalHours = calculateHours(openRecord.clockIn, clockOut)
        val overtimeHours = maxOf(0.0, totalHours - 8.0)  // Standard 8h workday

        return attendanceRepository.clockOut(openRecord.id, clockOut, totalHours, overtimeHours)
            .map { openRecord.copy(clockOut = clockOut, totalHours = totalHours, overtimeHours = overtimeHours) }
    }

    private fun calculateHours(clockIn: String, clockOut: String): Double {
        // Parse ISO-8601 strings using kotlinx-datetime (pinned 0.6.1)
        val inInstant = Instant.parse(clockIn)
        val outInstant = Instant.parse(clockOut)
        val durationMs = (outInstant - inInstant).inWholeMilliseconds
        return durationMs / (1000.0 * 60 * 60)
    }
}
```

#### `GeneratePayrollUseCaseImpl.kt`

**Critical:** 90% unit test coverage required.

```kotlin
class GeneratePayrollUseCaseImpl(
    private val employeeRepository: EmployeeRepository,
    private val attendanceRepository: AttendanceRepository,
    private val payrollRepository: PayrollRepository,
    private val orderRepository: OrderRepository   // For commission calculation
) : GeneratePayrollUseCase {

    override suspend fun invoke(
        employeeId: String,
        periodStart: String,
        periodEnd: String
    ): Result<PayrollRecord> {
        // 1. Check no existing payroll for this period
        val existing = payrollRepository.getByEmployeeAndPeriod(employeeId, periodStart)
        if (existing != null) {
            return Result.failure(IllegalStateException("Payroll already generated for period $periodStart"))
        }

        // 2. Get employee
        val employee = employeeRepository.getById(employeeId)
            ?: return Result.failure(IllegalArgumentException("Employee not found: $employeeId"))

        // 3. Calculate base salary from attendance
        val attendance = attendanceRepository.getForPeriod(employeeId, periodStart, periodEnd)
        val baseSalary = calculateBaseSalary(employee, attendance)

        // 4. Overtime pay (overtime_hours × hourly_rate × 1.5)
        val totalOvertimeHours = attendance.sumOf { it.overtimeHours }
        val overtimePay = totalOvertimeHours * employee.hourlyRate * 1.5

        // 5. Commission (total sales × commission_rate)
        val commission = if (employee.commissionRate > 0 && employee.userId != null) {
            calculateCommission(employee.userId, periodStart, periodEnd, employee.commissionRate)
        } else 0.0

        // 6. Build payroll record
        val netPay = baseSalary + overtimePay + commission // deductions added by caller
        val record = PayrollRecord(
            id = generateUuid(),
            employeeId = employeeId,
            periodStart = periodStart,
            periodEnd = periodEnd,
            baseSalary = baseSalary,
            overtimePay = overtimePay,
            commission = commission,
            deductions = 0.0,       // Manual entry in UI
            netPay = netPay,
            status = PayrollStatus.PENDING,
            paidAt = null,
            paymentRef = null,
            notes = null,
            createdAt = Clock.System.now().toString(),
            updatedAt = Clock.System.now().toString()
        )
        return payrollRepository.save(record)
    }

    private fun calculateBaseSalary(employee: Employee, attendance: List<AttendanceRecord>): Double {
        val presentDays = attendance.count { it.status == AttendanceStatus.PRESENT || it.status == AttendanceStatus.LATE }
        val presentHours = attendance.sumOf { it.totalHours ?: 0.0 }

        return when (employee.salaryType) {
            SalaryType.MONTHLY -> employee.salary ?: 0.0       // Fixed monthly regardless of days
            SalaryType.DAILY   -> presentDays * (employee.salary ?: 0.0)
            SalaryType.HOURLY  -> presentHours * (employee.salary ?: 0.0)
            SalaryType.WEEKLY  -> {
                val weeks = presentHours / 40.0
                weeks * (employee.salary ?: 0.0)
            }
        }
    }

    private suspend fun calculateCommission(
        userId: String,
        fromDate: String,
        toDate: String,
        commissionRate: Double
    ): Double {
        // Sum of completed orders where cashier = this user in the period
        val totalSales = orderRepository.getTotalSalesForCashier(userId, fromDate, toDate)
        return totalSales * commissionRate
    }
}
```

### Updated Koin Bindings

**File:** `shared/data/src/commonMain/kotlin/com/zyntasolutions/zyntapos/data/di/DataModule.kt`

Add to the existing module:
```kotlin
// Staff & HR — Phase 3
single<EmployeeRepository>   { EmployeeRepositoryImpl(get()) }
single<AttendanceRepository> { AttendanceRepositoryImpl(get()) }
single<LeaveRepository>      { LeaveRepositoryImpl(get()) }
single<PayrollRepository>    { PayrollRepositoryImpl(get()) }
single<ShiftRepository>      { ShiftRepositoryImpl(get()) }
```

### Unit Test Files

**Location:** `shared/domain/src/commonTest/kotlin/com/zyntasolutions/zyntapos/domain/usecase/staff/`

Key test scenarios:
- `GeneratePayrollUseCaseTest` — HOURLY, DAILY, WEEKLY, MONTHLY salary types; zero commission; commission with orders
- `ClockInUseCaseTest` — successful clock-in; reject duplicate clock-in (open record guard)
- `ClockOutUseCaseTest` — successful clock-out with hours calculation; reject clock-out with no open record
- `ApproveLeaveUseCaseTest` — approval sets status APPROVED + approvedBy + approvedAt; rejection sets REJECTED + reason

---

## Tasks

- [ ] **5.1** Implement `EmployeeRepositoryImpl` with CRUD + soft delete
- [ ] **5.2** Implement `AttendanceRepositoryImpl` with open-record guard (duplicate clock-in protection)
- [ ] **5.3** Implement `LeaveRepositoryImpl` with approval workflow status update
- [ ] **5.4** Implement `PayrollRepositoryImpl` with UNIQUE constraint handling (INSERT OR REPLACE)
- [ ] **5.5** Implement `ShiftRepositoryImpl` with upsert (INSERT OR REPLACE on UNIQUE employee+date)
- [ ] **5.6** Create all 5 mapper files (`EmployeeMapper`, `AttendanceMapper`, `LeaveMapper`, `PayrollMapper`, `ShiftMapper`)
- [ ] **5.7** Implement `ClockInUseCaseImpl` — open-record guard, UUID generation, ISO-8601 timestamp
- [ ] **5.8** Implement `ClockOutUseCaseImpl` — hours calculation using kotlinx-datetime (pinned 0.6.1)
- [ ] **5.9** Implement `GeneratePayrollUseCaseImpl` — all 4 salary types, overtime, commission
- [ ] **5.10** Implement remaining 15 use case impls (CRUD delegates, status update wrappers)
- [ ] **5.11** Update `DataModule.kt` with 5 new Koin bindings
- [ ] **5.12** Write `GeneratePayrollUseCaseTest` covering all 4 salary types (target: 90% coverage)
- [ ] **5.13** Write `ClockInUseCaseTest` and `ClockOutUseCaseTest`
- [ ] **5.14** Write `ApproveLeaveUseCaseTest` and `RejectLeaveUseCaseTest`
- [ ] **5.15** Run `./gradlew :shared:domain:test` — all tests pass

---

## Verification

```bash
./gradlew :shared:data:assemble
./gradlew :shared:domain:test
./gradlew :shared:data:jvmTest  # Integration tests with in-memory SQLDelight
./gradlew :shared:data:detekt
./gradlew :shared:domain:detekt
```

---

## Critical Patterns

### kotlinx-datetime Usage (Pinned to 0.6.1)
Always use:
```kotlin
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

// Current timestamp
val now: String = Clock.System.now().toString()

// Parse for duration calculation
val start = Instant.parse(clockInString)
val end = Instant.parse(clockOutString)
val hours = (end - start).inWholeMilliseconds / (1000.0 * 60 * 60)
```
**Never** use `java.time.*` — breaks KMP compilation for iOS/JS targets.

### Result<T> Pattern
All use case impls wrap DB calls in `runCatching { }` for consistent error propagation. Domain errors use `IllegalStateException` / `IllegalArgumentException` with descriptive messages. The UI layer maps these to user-facing error strings.

---

## Definition of Done

- [ ] All 5 repository implementations created
- [ ] All 5 mapper files created
- [ ] All 18 use case implementations created
- [ ] `DataModule.kt` updated with 5 new bindings
- [ ] `GeneratePayrollUseCaseImpl` has 90%+ test coverage
- [ ] Clock-in/clock-out guard tests pass
- [ ] `./gradlew :shared:data:assemble` passes
- [ ] Commit: `feat(data): implement Staff & HR repositories and use cases`
