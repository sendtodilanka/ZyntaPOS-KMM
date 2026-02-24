# ZyntaPOS — Phase 3 Sprint 9: Staff Feature Part 2 — Attendance Clock-In/Out

> **Document ID:** ZYNTA-PLAN-PHASE3-SPRINT9-v1.0
> **Phase:** 3 — Enterprise (Months 13–18)
> **Sprint:** 9 of 24 | Week 9
> **Module(s):** `:composeApp:feature:staff`
> **Author:** Senior KMP Architect & Lead Engineer
> **Reference:** ZYNTA-MASTER-PLAN-v1.0 | ADR-001

---

## Goal

Implement attendance management screens for `:composeApp:feature:staff`: a daily store-wide attendance grid (`AttendanceScreen`) and an individual attendance history viewer (`AttendanceHistoryScreen`). Clock-in/out business logic enforces the open-record guard (cannot clock in if already clocked in) and auto-computes `totalHours` and `overtimeHours` on clock-out.

---

## New Screen Files

**Location:** `composeApp/feature/staff/src/commonMain/kotlin/com/zyntasolutions/zyntapos/feature/staff/screen/`

```
screen/
├── AttendanceScreen.kt           # Store-wide today's attendance grid
└── AttendanceHistoryScreen.kt    # Per-employee attendance history + monthly summary
```

---

## Attendance Screen

### `AttendanceScreen.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.staff.screen

/**
 * Store-wide attendance dashboard for today.
 *
 * Layout:
 * - Header: today's date + store name
 * - Summary row: Present / Absent / Late / On Leave counts (from AttendanceSummary)
 * - Employee attendance list:
 *   Each row: avatar, fullName, status badge (PRESENT/ABSENT/LATE/LEAVE), clock-in time,
 *             clock-out time (or "Active" if still clocked in), total hours
 *   Action: Clock In / Clock Out button per employee
 *
 * Business rules:
 * - Clock In button shown only if employee has NO open AttendanceRecord today (clockOut == null)
 * - Clock Out button shown only if employee HAS an open record
 * - Button disabled while isSaving = true (prevent double-tap)
 *
 * Data:
 * - viewModel.state.todayAttendance: List<AttendanceRecord> — reactive from DB via Flow
 * - viewModel.state.employees:       List<Employee>         — joined for display
 */
@Composable
fun AttendanceScreen(
    viewModel: StaffViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.handleIntent(StaffIntent.LoadTodayAttendance)
        viewModel.handleIntent(StaffIntent.LoadEmployees)
    }

    Column(modifier = modifier.fillMaxSize()) {
        AttendanceSummaryHeader(
            date = getCurrentDateString(),          // e.g. "Monday, 24 Feb 2026"
            presentCount = state.todayAttendance.count { it.status == AttendanceStatus.PRESENT },
            absentCount = state.employees.size - state.todayAttendance.size,
            lateCount = state.todayAttendance.count { it.status == AttendanceStatus.LATE },
            leaveCount = state.todayAttendance.count { it.status == AttendanceStatus.LEAVE }
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.activeEmployees, key = { it.id }) { employee ->
                val record = state.todayAttendance.find { it.employeeId == employee.id }
                AttendanceEmployeeRow(
                    employee = employee,
                    record = record,
                    onClockIn = { viewModel.handleIntent(StaffIntent.ClockIn(employee.id)) },
                    onClockOut = { viewModel.handleIntent(StaffIntent.ClockOut(employee.id)) },
                    isLoading = state.isSaving
                )
            }
        }
    }

    // Error / success snackbars via StaffEffect collection
}

@Composable
private fun AttendanceSummaryHeader(
    date: String,
    presentCount: Int,
    absentCount: Int,
    lateCount: Int,
    leaveCount: Int,
    modifier: Modifier = Modifier
) {
    // Card with horizontal row of ZyntaStatChip:
    // Present (green), Absent (red), Late (orange), Leave (blue)
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(text = date, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ZyntaStatChip(label = "Present", count = presentCount, color = Color.Green)
                ZyntaStatChip(label = "Absent", count = absentCount, color = Color.Red)
                ZyntaStatChip(label = "Late", count = lateCount, color = Color(0xFFF59E0B))
                ZyntaStatChip(label = "Leave", count = leaveCount, color = Color.Blue)
            }
        }
    }
}

@Composable
private fun AttendanceEmployeeRow(
    employee: com.zyntasolutions.zyntapos.domain.model.Employee,
    record: com.zyntasolutions.zyntapos.domain.model.AttendanceRecord?,
    onClockIn: () -> Unit,
    onClockOut: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    // ZyntaCard row:
    // Left: initials avatar + employee name + status badge
    // Middle: clockIn time, clockOut time or "Active" indicator
    // Right: Clock In or Clock Out ZyntaButton
    //
    // Status badge color:
    //   PRESENT -> green, LATE -> amber, LEAVE -> blue
    //   No record -> grey "Absent" badge
}
```

---

## Attendance History Screen

### `AttendanceHistoryScreen.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.staff.screen

/**
 * Per-employee attendance history screen.
 *
 * Layout:
 * - Month selector (previous/next month arrows + "YYYY-MM" label)
 * - Monthly summary card: present/absent/late/leave counts + totalHours + overtimeHours
 * - Attendance record list (sorted descending by clock_in date)
 *
 * Each record row shows:
 * - Date (day of week + date)
 * - Status badge
 * - Clock In → Clock Out times (or "Open" if clockOut is null)
 * - Total hours (formatted as "7h 30m")
 * - Overtime hours (if > 0: "OT: 1h 30m" in amber)
 *
 * Note: uses kotlinx.datetime.LocalDate for month navigation (never java.time.*)
 */
@Composable
fun AttendanceHistoryScreen(
    employeeId: String,
    viewModel: StaffViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    var selectedMonth by remember { mutableStateOf(currentYearMonth()) }  // 'YYYY-MM'

    LaunchedEffect(employeeId, selectedMonth) {
        viewModel.handleIntent(StaffIntent.LoadAttendanceHistory(employeeId))
        viewModel.handleIntent(StaffIntent.LoadAttendanceSummary(employeeId, selectedMonth))
    }

    val monthRecords = state.attendanceHistory.filter { it.clockIn.startsWith(selectedMonth) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Attendance History") }, navigationIcon = { /* back */ }) }
    ) { padding ->
        Column(modifier = modifier.padding(padding)) {
            MonthSelector(
                current = selectedMonth,
                onPrevious = { selectedMonth = previousMonth(selectedMonth) },
                onNext = { selectedMonth = nextMonth(selectedMonth) }
            )

            state.attendanceSummary?.let { summary ->
                AttendanceSummaryCard(summary)
            }

            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(monthRecords.sortedByDescending { it.clockIn }, key = { it.id }) { record ->
                    AttendanceRecordRow(record)
                }
            }
        }
    }
}

@Composable
private fun AttendanceSummaryCard(
    summary: com.zyntasolutions.zyntapos.domain.model.AttendanceSummary,
    modifier: Modifier = Modifier
) {
    // ZyntaCard showing:
    // Period label, totalDays worked, totalHours, overtimeHours
    // Mini stat row: Present / Absent / Late / Leave counts
}

@Composable
private fun AttendanceRecordRow(
    record: com.zyntasolutions.zyntapos.domain.model.AttendanceRecord,
    modifier: Modifier = Modifier
) {
    // Row: date label + status badge + clock in/out times + hours badge
    // If record.isOpen → show pulsing "Active" indicator instead of clock-out
}
```

---

## Design System Additions

New components added to `:composeApp:designsystem` this sprint:

### `ZyntaStatChip.kt`

```kotlin
package com.zyntasolutions.zyntapos.designsystem.component

@Composable
fun ZyntaStatChip(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    // Rounded chip: colored dot + label + count badge
    // Example: ● Present  12
}
```

### `ZyntaStatusBadge.kt`

```kotlin
package com.zyntasolutions.zyntapos.designsystem.component

@Composable
fun ZyntaStatusBadge(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    // Small pill-shaped badge with colored background and white text
}
```

---

## Clock-In/Out Business Logic

The open-record guard is implemented in `ClockInUseCaseImpl` (Sprint 5):

```kotlin
// Verification in AttendanceRepositoryImpl:
// SELECT * FROM attendance_records
//   WHERE employee_id = ? AND clock_out IS NULL
//   LIMIT 1
//
// ClockInUseCaseImpl rejects if open record exists:
//   return Result.failure(IllegalStateException("Employee already clocked in"))
//
// ClockOutUseCaseImpl computes hours using kotlinx-datetime:
//   val clockInInstant = Instant.parse(openRecord.clockIn)
//   val clockOutInstant = Clock.System.now()
//   val totalSeconds = (clockOutInstant - clockInInstant).inWholeSeconds
//   val totalHours = totalSeconds / 3600.0
//   val overtimeHours = maxOf(0.0, totalHours - 8.0)
```

---

## Integration with `EmployeeDetailScreen` Attendance Tab

The Attendance tab in `EmployeeDetailScreen` (Sprint 8) reuses these composables:

```kotlin
// Tab 1: Attendance
@Composable
private fun EmployeeAttendanceTab(
    state: StaffState,
    viewModel: StaffViewModel
) {
    // Show AttendanceSummaryCard for current month
    // Show last 5 AttendanceRecordRow entries
    // "View Full History" button → navigates to AttendanceHistoryScreen
    // Clock In / Clock Out button for this specific employee
}
```

---

## Tasks

- [ ] **9.1** Implement `AttendanceScreen.kt` — store-wide today's attendance grid with summary header
- [ ] **9.2** Implement `AttendanceSummaryHeader` composable with colored stat chips
- [ ] **9.3** Implement `AttendanceEmployeeRow` composable with Clock In/Out buttons and status badges
- [ ] **9.4** Implement `AttendanceHistoryScreen.kt` with month navigation and summary card
- [ ] **9.5** Implement `AttendanceRecordRow` composable with open-record "Active" indicator
- [ ] **9.6** Create `ZyntaStatChip.kt` and `ZyntaStatusBadge.kt` in `:composeApp:designsystem`
- [ ] **9.7** Fill in `EmployeeAttendanceTab` in `EmployeeDetailScreen.kt` using new composables
- [ ] **9.8** Add `StaffAttendance` route to navigation wiring in `MainNavGraph.kt`
- [ ] **9.9** Write `ClockInOutViewModelTest` — test clock-in blocked if open record, clock-out calculates hours
- [ ] **9.10** Verify: `./gradlew :composeApp:feature:staff:assemble && ./gradlew :composeApp:feature:staff:test`

---

## Verification

```bash
./gradlew :composeApp:feature:staff:assemble
./gradlew :composeApp:feature:staff:test
./gradlew :composeApp:designsystem:assemble
```

---

## Definition of Done

- [ ] `AttendanceScreen` renders today's attendance for all active employees
- [ ] Clock-in/out buttons correctly enabled/disabled based on open-record state
- [ ] `AttendanceHistoryScreen` shows monthly summary + records list with month navigation
- [ ] `ZyntaStatChip` and `ZyntaStatusBadge` added to design system
- [ ] `EmployeeAttendanceTab` integrated in `EmployeeDetailScreen`
- [ ] Clock-in guard and hours calculation tests pass
- [ ] Commit: `feat(staff): implement attendance clock-in/out screens with open-record guard`
