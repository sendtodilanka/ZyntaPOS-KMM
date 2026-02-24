# ZyntaPOS — Phase 3 Sprint 3: Staff & HR Domain Models + Repository Interfaces

> **Document ID:** ZYNTA-PLAN-PHASE3-SPRINT3-v1.0
> **Phase:** 3 — Enterprise (Months 13–18)
> **Sprint:** 3 of 24 | Week 3
> **Module(s):** `:shared:domain`
> **Author:** Senior KMP Architect & Lead Engineer
> **Reference:** ZYNTA-MASTER-PLAN-v1.0 | ADR-001 | ADR-002 | ADR-C.2

---

## Goal

Create all Staff & HR domain models (ADR-002 compliant — plain names, no `*Entity` suffix), enum types, repository interfaces, and all 18 use case interfaces for the Staff & HR functional group. This sprint defines all contracts that the repository and feature layers will implement.

---

## New Files to Create

### Domain Model Files

**Location:** `shared/domain/src/commonMain/kotlin/com/zyntasolutions/zyntapos/domain/model/`

#### `Employee.kt`
```kotlin
package com.zyntasolutions.zyntapos.domain.model

data class Employee(
    val id: String,
    val userId: String?,                         // Optional: linked system user account
    val storeId: String,
    val firstName: String,
    val lastName: String,
    val email: String?,
    val phone: String?,
    val address: String?,
    val dateOfBirth: String?,                    // ISO date: YYYY-MM-DD
    val hireDate: String,                        // ISO date: YYYY-MM-DD
    val department: String?,
    val position: String,
    val salary: Double?,
    val salaryType: SalaryType,
    val commissionRate: Double,                  // 0.0–1.0 (percentage as decimal)
    val emergencyContact: String?,
    val documents: List<EmployeeDocument>,
    val isActive: Boolean,
    val createdAt: String,
    val updatedAt: String
) {
    val fullName: String get() = "$firstName $lastName"

    val hourlyRate: Double get() = when (salaryType) {
        SalaryType.HOURLY  -> salary ?: 0.0
        SalaryType.DAILY   -> (salary ?: 0.0) / 8.0
        SalaryType.WEEKLY  -> (salary ?: 0.0) / 40.0
        SalaryType.MONTHLY -> (salary ?: 0.0) / 160.0
    }
}

data class EmployeeDocument(
    val name: String,
    val url: String,
    val type: String    // 'CONTRACT', 'ID', 'CERTIFICATE', 'OTHER'
)
```

#### `AttendanceRecord.kt`
```kotlin
package com.zyntasolutions.zyntapos.domain.model

data class AttendanceRecord(
    val id: String,
    val employeeId: String,
    val clockIn: String,                         // ISO-8601 datetime
    val clockOut: String?,                       // Null if still clocked in
    val totalHours: Double?,                     // Computed on clock-out
    val overtimeHours: Double,                   // max(0, totalHours - 8)
    val notes: String?,
    val status: AttendanceStatus,
    val createdAt: String,
    val updatedAt: String
) {
    val isOpen: Boolean get() = clockOut == null
}
```

#### `LeaveRecord.kt`
```kotlin
package com.zyntasolutions.zyntapos.domain.model

data class LeaveRecord(
    val id: String,
    val employeeId: String,
    val leaveType: LeaveType,
    val startDate: String,                       // ISO date: YYYY-MM-DD
    val endDate: String,                         // ISO date: YYYY-MM-DD
    val reason: String?,
    val status: LeaveStatus,
    val approvedBy: String?,                     // User ID of approving manager
    val approvedAt: String?,
    val rejectionReason: String?,
    val createdAt: String,
    val updatedAt: String
)
```

#### `PayrollRecord.kt`
```kotlin
package com.zyntasolutions.zyntapos.domain.model

data class PayrollRecord(
    val id: String,
    val employeeId: String,
    val periodStart: String,                     // ISO date: YYYY-MM-DD
    val periodEnd: String,                       // ISO date: YYYY-MM-DD
    val baseSalary: Double,
    val overtimePay: Double,
    val commission: Double,
    val deductions: Double,
    val netPay: Double,
    val status: PayrollStatus,
    val paidAt: String?,
    val paymentRef: String?,
    val notes: String?,
    val createdAt: String,
    val updatedAt: String
) {
    val grossPay: Double get() = baseSalary + overtimePay + commission
}
```

#### `ShiftSchedule.kt`
```kotlin
package com.zyntasolutions.zyntapos.domain.model

data class ShiftSchedule(
    val id: String,
    val employeeId: String,
    val storeId: String,
    val shiftDate: String,                       // ISO date: YYYY-MM-DD
    val startTime: String,                       // HH:MM (24h)
    val endTime: String,                         // HH:MM (24h)
    val notes: String?,
    val createdAt: String,
    val updatedAt: String
) {
    /** Duration in hours (may cross midnight). */
    val durationHours: Double
        get() {
            val (sh, sm) = startTime.split(":").map { it.toInt() }
            val (eh, em) = endTime.split(":").map { it.toInt() }
            val startMinutes = sh * 60 + sm
            val endMinutes = eh * 60 + em
            val totalMinutes = if (endMinutes >= startMinutes) endMinutes - startMinutes
                               else (24 * 60 - startMinutes) + endMinutes
            return totalMinutes / 60.0
        }
}
```

### Value Object Files

#### `AttendanceSummary.kt`
```kotlin
package com.zyntasolutions.zyntapos.domain.model

data class AttendanceSummary(
    val employeeId: String,
    val period: String,         // 'YYYY-MM'
    val presentCount: Int,
    val absentCount: Int,
    val lateCount: Int,
    val leaveCount: Int,
    val totalHours: Double,
    val overtimeHours: Double
) {
    val totalDays: Int get() = presentCount + absentCount + lateCount + leaveCount
}
```

#### `PayrollSummary.kt`
```kotlin
package com.zyntasolutions.zyntapos.domain.model

data class PayrollSummary(
    val period: String,              // 'YYYY-MM'
    val storeId: String,
    val totalEmployees: Int,
    val totalBaseSalary: Double,
    val totalOvertimePay: Double,
    val totalCommission: Double,
    val totalDeductions: Double,
    val totalNetPay: Double,
    val paidCount: Int,
    val pendingCount: Int
)
```

### Enum Type Files

**Location:** `shared/domain/src/commonMain/kotlin/com/zyntasolutions/zyntapos/domain/model/`

#### `SalaryType.kt`
```kotlin
enum class SalaryType { HOURLY, DAILY, WEEKLY, MONTHLY }
```

#### `LeaveType.kt`
```kotlin
enum class LeaveType { SICK, ANNUAL, PERSONAL, UNPAID }
```

#### `LeaveStatus.kt`
```kotlin
enum class LeaveStatus { PENDING, APPROVED, REJECTED }
```

#### `AttendanceStatus.kt`
```kotlin
enum class AttendanceStatus { PRESENT, ABSENT, LATE, LEAVE }
```

#### `PayrollStatus.kt`
```kotlin
enum class PayrollStatus { PENDING, PAID }
```

### Repository Interface Files

**Location:** `shared/domain/src/commonMain/kotlin/com/zyntasolutions/zyntapos/domain/repository/`

```kotlin
// EmployeeRepository.kt
interface EmployeeRepository {
    fun getByStore(storeId: String): Flow<List<Employee>>
    suspend fun getById(id: String): Employee?
    suspend fun getByUserId(userId: String): Employee?
    suspend fun save(employee: Employee): Result<Employee>
    suspend fun delete(id: String): Result<Unit>
}

// AttendanceRepository.kt
interface AttendanceRepository {
    fun getByEmployee(employeeId: String): Flow<List<AttendanceRecord>>
    suspend fun getForPeriod(employeeId: String, from: String, to: String): List<AttendanceRecord>
    suspend fun getOpenRecord(employeeId: String): AttendanceRecord?
    fun getTodayForStore(storeId: String, datePrefix: String): Flow<List<AttendanceRecord>>
    suspend fun save(record: AttendanceRecord): Result<AttendanceRecord>
    suspend fun clockOut(id: String, clockOut: String, totalHours: Double, overtimeHours: Double): Result<Unit>
}

// LeaveRepository.kt
interface LeaveRepository {
    fun getByEmployee(employeeId: String): Flow<List<LeaveRecord>>
    fun getPendingForStore(storeId: String): Flow<List<LeaveRecord>>
    suspend fun save(record: LeaveRecord): Result<LeaveRecord>
    suspend fun updateStatus(id: String, status: LeaveStatus, approvedBy: String?, rejectionReason: String?): Result<Unit>
}

// PayrollRepository.kt
interface PayrollRepository {
    fun getByEmployee(employeeId: String): Flow<List<PayrollRecord>>
    suspend fun getByPeriod(storeId: String, periodStart: String): List<PayrollRecord>
    suspend fun getByEmployeeAndPeriod(employeeId: String, periodStart: String): PayrollRecord?
    suspend fun save(record: PayrollRecord): Result<PayrollRecord>
    suspend fun updateStatus(id: String, status: PayrollStatus, paidAt: String?, paymentRef: String?): Result<Unit>
}

// ShiftRepository.kt
interface ShiftRepository {
    fun getByStoreAndWeek(storeId: String, weekStart: String, weekEnd: String): Flow<List<ShiftSchedule>>
    suspend fun getByEmployee(employeeId: String, from: String, to: String): List<ShiftSchedule>
    suspend fun save(shift: ShiftSchedule): Result<ShiftSchedule>
    suspend fun delete(id: String): Result<Unit>
}
```

### Use Case Interface Files

**Location:** `shared/domain/src/commonMain/kotlin/com/zyntasolutions/zyntapos/domain/usecase/staff/`

```kotlin
// Employee use cases
fun interface GetEmployeesUseCase {
    operator fun invoke(storeId: String): Flow<List<Employee>>
}

fun interface GetEmployeeByIdUseCase {
    suspend operator fun invoke(id: String): Employee?
}

fun interface SaveEmployeeUseCase {
    suspend operator fun invoke(employee: Employee): Result<Employee>
}

fun interface DeleteEmployeeUseCase {
    suspend operator fun invoke(id: String): Result<Unit>
}

// Attendance use cases
fun interface ClockInUseCase {
    suspend operator fun invoke(employeeId: String): Result<AttendanceRecord>
}

fun interface ClockOutUseCase {
    suspend operator fun invoke(employeeId: String): Result<AttendanceRecord>
}

fun interface GetAttendanceHistoryUseCase {
    operator fun invoke(employeeId: String): Flow<List<AttendanceRecord>>
}

fun interface GetTodayAttendanceUseCase {
    operator fun invoke(storeId: String): Flow<List<AttendanceRecord>>
}

fun interface GetAttendanceSummaryUseCase {
    suspend operator fun invoke(employeeId: String, period: String): AttendanceSummary
}

// Leave use cases
fun interface SubmitLeaveRequestUseCase {
    suspend operator fun invoke(leave: LeaveRecord): Result<LeaveRecord>
}

fun interface ApproveLeaveUseCase {
    suspend operator fun invoke(leaveId: String, approvedBy: String): Result<Unit>
}

fun interface RejectLeaveUseCase {
    suspend operator fun invoke(leaveId: String, approvedBy: String, reason: String): Result<Unit>
}

fun interface GetLeaveHistoryUseCase {
    operator fun invoke(employeeId: String): Flow<List<LeaveRecord>>
}

fun interface GetPendingLeaveRequestsUseCase {
    operator fun invoke(storeId: String): Flow<List<LeaveRecord>>
}

// Shift use cases
fun interface GetShiftScheduleUseCase {
    operator fun invoke(storeId: String, weekStart: String, weekEnd: String): Flow<List<ShiftSchedule>>
}

fun interface SaveShiftScheduleUseCase {
    suspend operator fun invoke(shift: ShiftSchedule): Result<ShiftSchedule>
}

fun interface DeleteShiftScheduleUseCase {
    suspend operator fun invoke(id: String): Result<Unit>
}

// Payroll use cases
fun interface GeneratePayrollUseCase {
    suspend operator fun invoke(employeeId: String, periodStart: String, periodEnd: String): Result<PayrollRecord>
}

fun interface ProcessPayrollPaymentUseCase {
    suspend operator fun invoke(payrollId: String, paymentRef: String): Result<Unit>
}

fun interface GetPayrollHistoryUseCase {
    operator fun invoke(employeeId: String): Flow<List<PayrollRecord>>
}
```

---

## Tasks

- [ ] **3.1** Create `Employee.kt` with `EmployeeDocument` value object and computed `fullName` / `hourlyRate` properties
- [ ] **3.2** Create `AttendanceRecord.kt` with `isOpen` computed property
- [ ] **3.3** Create `LeaveRecord.kt`
- [ ] **3.4** Create `PayrollRecord.kt` with `grossPay` computed property
- [ ] **3.5** Create `ShiftSchedule.kt` with `durationHours` computed property
- [ ] **3.6** Create `AttendanceSummary.kt` and `PayrollSummary.kt` value objects
- [ ] **3.7** Create all 5 enum files: `SalaryType`, `LeaveType`, `LeaveStatus`, `AttendanceStatus`, `PayrollStatus`
- [ ] **3.8** Create all 5 repository interfaces (`Employee`, `Attendance`, `Leave`, `Payroll`, `Shift`)
- [ ] **3.9** Create all 18 use case interfaces using `fun interface` (ADR C.2 — no defaults on abstract methods)
- [ ] **3.10** Verify compilation: `./gradlew :shared:domain:assemble`
- [ ] **3.11** Write unit tests for computed properties (hourlyRate, grossPay, durationHours) in `shared/domain/src/commonTest/`
- [ ] **3.12** Run `./gradlew :shared:domain:test`

---

## Verification

```bash
# Compile domain module
./gradlew :shared:domain:assemble

# Run domain tests
./gradlew :shared:domain:test

# Static analysis
./gradlew :shared:domain:detekt
```

---

## Architectural Constraints

1. **ADR-002:** All model names are plain (`Employee`, not `EmployeeEntity`). The `*Entity` suffix is reserved for SQLDelight mapper types in `:shared:data`.
2. **ADR-C.2:** All `fun interface` use cases have **no default values** on the abstract `invoke` method.
3. **Dependency rule:** These domain files import only from `:shared:core` (for `Result<T>`, logging). Never import from `:shared:data`, `:shared:security`, or `:shared:hal`.
4. **`Flow<T>` for lists:** Repository methods returning observable lists use `Flow<List<T>>` to enable reactive UI updates. Suspend functions are used for one-shot operations.

---

## Definition of Done

- [ ] All 5 domain model files + 2 value objects created
- [ ] All 5 enum types created
- [ ] All 5 repository interfaces created
- [ ] All 18 use case interfaces created (`fun interface`, no defaults)
- [ ] Unit tests for computed properties pass
- [ ] `./gradlew :shared:domain:assemble` and `test` pass
- [ ] Commit: `feat(domain): add Staff & HR domain models, repository interfaces, and use case contracts`
