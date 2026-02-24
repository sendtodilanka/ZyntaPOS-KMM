# ZyntaPOS — Phase 3 Sprint 11: Staff Feature Part 4 — Shift Scheduling

> **Document ID:** ZYNTA-PLAN-PHASE3-SPRINT11-v1.0
> **Phase:** 3 — Enterprise (Months 13–18)
> **Sprint:** 11 of 24 | Week 11
> **Module(s):** `:composeApp:feature:staff`, `:composeApp:designsystem`
> **Author:** Senior KMP Architect & Lead Engineer
> **Reference:** ZYNTA-MASTER-PLAN-v1.0 | ADR-001

---

## Goal

Implement shift scheduling screens for `:composeApp:feature:staff`: a weekly calendar grid showing all employees' shifts, a bottom-sheet form for adding/editing individual shifts, and a new `ZyntaWeekCalendar` design-system component. Uses `kotlinx.datetime.LocalDate` (pinned 0.6.1) for all date arithmetic.

---

## New Screen Files

**Location:** `composeApp/feature/staff/src/commonMain/kotlin/com/zyntasolutions/zyntapos/feature/staff/screen/`

```
screen/
├── ShiftScheduleScreen.kt      # Weekly grid calendar for all employees
└── ShiftFormSheet.kt           # Bottom sheet for add/edit shift
```

---

## Design System Addition

**Location:** `composeApp/designsystem/src/commonMain/kotlin/com/zyntasolutions/zyntapos/designsystem/component/`

```
component/
└── ZyntaWeekCalendar.kt        # Reusable 7-column week grid composable
```

---

## `ZyntaWeekCalendar.kt`

```kotlin
package com.zyntasolutions.zyntapos.designsystem.component

import kotlinx.datetime.LocalDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.datetime.minus

/**
 * Generic reusable week calendar grid.
 *
 * Layout:
 * - Header row: week navigation (← Previous Week | "Feb 24 – Mar 2, 2026" | Next Week →)
 * - Column headers: Mon Tue Wed Thu Fri Sat Sun (short day names + date number)
 * - Rows: one row per item in [rowKeys]; each cell shows slot(s) for that day+item
 *
 * @param weekStart       Monday of the displayed week (LocalDate)
 * @param rowKeys         Ordered list of row identifiers (e.g., employee IDs)
 * @param rowLabel        Composable label for each row (e.g., employee name)
 * @param cellContent     Composable for each (rowKey, date) cell
 * @param onPreviousWeek  Navigate to previous week
 * @param onNextWeek      Navigate to next week
 */
@Composable
fun ZyntaWeekCalendar(
    weekStart: LocalDate,
    rowKeys: List<String>,
    rowLabel: @Composable (rowKey: String) -> Unit,
    cellContent: @Composable (rowKey: String, date: LocalDate) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    modifier: Modifier = Modifier
) {
    val weekDays = (0..6).map { weekStart.plus(it, DateTimeUnit.DAY) }

    Column(modifier = modifier) {
        // Week navigation header
        WeekNavigationHeader(
            weekStart = weekStart,
            weekEnd = weekStart.plus(6, DateTimeUnit.DAY),
            onPrevious = onPreviousWeek,
            onNext = onNextWeek
        )

        // Day column headers (Mon 24, Tue 25, ...)
        HorizontalDivider()
        Row {
            Spacer(Modifier.width(120.dp))   // row label column width
            weekDays.forEach { day ->
                DayHeader(day = day, modifier = Modifier.weight(1f))
            }
        }
        HorizontalDivider()

        // Rows
        LazyColumn {
            items(rowKeys, key = { it }) { rowKey ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(IntrinsicSize.Min)
                ) {
                    // Row label (employee name)
                    Box(modifier = Modifier.width(120.dp).fillMaxHeight()) {
                        rowLabel(rowKey)
                    }
                    // Cells for each day of the week
                    weekDays.forEach { day ->
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            cellContent(rowKey, day)
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun WeekNavigationHeader(
    weekStart: LocalDate,
    weekEnd: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        IconButton(onClick = onPrevious) { /* chevron left */ }
        Text(
            text = "${formatShortDate(weekStart)} – ${formatShortDate(weekEnd)}",
            style = MaterialTheme.typography.titleMedium
        )
        IconButton(onClick = onNext) { /* chevron right */ }
    }
}

@Composable
private fun DayHeader(day: LocalDate, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(vertical = 4.dp)
    ) {
        Text(
            text = day.dayOfWeek.name.take(3),          // "MON", "TUE", ...
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            text = day.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

/** Returns the Monday of the week containing [date]. */
fun getWeekStart(date: LocalDate): LocalDate {
    val dayOfWeek = date.dayOfWeek.ordinal    // 0=Monday in kotlinx.datetime
    return date.minus(dayOfWeek, DateTimeUnit.DAY)
}
```

---

## Shift Schedule Screen

### `ShiftScheduleScreen.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.staff.screen

import com.zyntasolutions.zyntapos.designsystem.component.ZyntaWeekCalendar
import com.zyntasolutions.zyntapos.designsystem.component.getWeekStart
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.datetime.minus

/**
 * Weekly shift scheduling screen.
 *
 * Features:
 * - ZyntaWeekCalendar with employees as rows, week days as columns
 * - Each cell shows the shift time (e.g. "08:00–17:00") if a shift exists
 * - Tap a cell → opens ShiftFormSheet pre-filled with employee + date
 * - Long-press an existing shift → delete confirmation dialog
 * - FAB: "Current Week" button to jump back to this week
 *
 * Data flow:
 * - viewModel.state.shiftSchedule: List<ShiftSchedule> for current week
 * - weekStart changes → StaffIntent.LoadShiftSchedule(storeId, weekStart, weekEnd)
 */
@Composable
fun ShiftScheduleScreen(
    storeId: String,
    viewModel: StaffViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    var weekStart by remember {
        mutableStateOf(
            getWeekStart(
                Clock.System.now()
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                    .date
            )
        )
    }
    var editTarget by remember { mutableStateOf<ShiftFormTarget?>(null) }   // null = sheet closed
    var deleteTarget by remember { mutableStateOf<String?>(null) }           // shift ID to delete

    val weekEnd = weekStart.plus(6, DateTimeUnit.DAY)

    LaunchedEffect(storeId, weekStart) {
        viewModel.handleIntent(
            StaffIntent.LoadShiftSchedule(
                storeId = storeId,
                weekStart = weekStart.toString(),
                weekEnd = weekEnd.toString()
            )
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Shift Schedule") }) },
        floatingActionButton = {
            ZyntaFab(
                text = "Today",
                onClick = {
                    weekStart = getWeekStart(
                        Clock.System.now()
                            .toLocalDateTime(TimeZone.currentSystemDefault())
                            .date
                    )
                }
            )
        }
    ) { padding ->
        ZyntaWeekCalendar(
            weekStart = weekStart,
            rowKeys = state.activeEmployees.map { it.id },
            rowLabel = { employeeId ->
                val emp = state.employees.find { it.id == employeeId }
                Text(
                    text = emp?.fullName ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            },
            cellContent = { employeeId, date ->
                val shift = state.shiftSchedule.find {
                    it.employeeId == employeeId && it.shiftDate == date.toString()
                }
                ShiftCell(
                    shift = shift,
                    onClick = {
                        editTarget = ShiftFormTarget(
                            employeeId = employeeId,
                            date = date,
                            existingShift = shift
                        )
                    },
                    onLongClick = shift?.let { { deleteTarget = it.id } }
                )
            },
            onPreviousWeek = { weekStart = weekStart.minus(7, DateTimeUnit.DAY) },
            onNextWeek = { weekStart = weekStart.plus(7, DateTimeUnit.DAY) },
            modifier = modifier.padding(padding)
        )
    }

    // Bottom sheet for add/edit shift
    editTarget?.let { target ->
        ShiftFormSheet(
            target = target,
            onSave = { shift ->
                viewModel.handleIntent(StaffIntent.SaveShift(shift))
                editTarget = null
            },
            onDismiss = { editTarget = null }
        )
    }

    // Delete confirmation dialog
    deleteTarget?.let { shiftId ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Shift") },
            text = { Text("Remove this shift assignment?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.handleIntent(StaffIntent.DeleteShift(shiftId))
                    deleteTarget = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ShiftCell(
    shift: com.zyntasolutions.zyntapos.domain.model.ShiftSchedule?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val bgColor = if (shift != null)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surface

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(2.dp)
            .background(bgColor, RoundedCornerShape(4.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick ?: {})
            .padding(4.dp)
    ) {
        if (shift != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${shift.startTime}",
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = "${shift.endTime}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        } else {
            Icon(
                painter = painterResource("ic_add"),
                contentDescription = "Add shift",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

data class ShiftFormTarget(
    val employeeId: String,
    val date: LocalDate,
    val existingShift: com.zyntasolutions.zyntapos.domain.model.ShiftSchedule?
)
```

---

## Shift Form Sheet

### `ShiftFormSheet.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.staff.screen

/**
 * Bottom sheet for adding or editing a shift.
 *
 * Fields:
 * - Employee name (read-only — pre-filled from target)
 * - Date (read-only — pre-filled from target)
 * - Start Time: time picker (HH:MM 24h)
 * - End Time:   time picker (HH:MM 24h)
 *   Duration preview auto-computed: "8h 0m" (using ShiftSchedule.durationHours)
 * - Notes: optional text field
 *
 * Validation:
 * - startTime and endTime required (non-blank)
 * - Warns if duration > 12 hours (unusual shift)
 *
 * On save: creates ShiftSchedule with:
 *   id          = existingShift?.id ?: UUID
 *   employeeId  = target.employeeId
 *   storeId     = session.storeId
 *   shiftDate   = target.date.toString()
 *   startTime   = startTime
 *   endTime     = endTime
 *   notes       = notes.takeIf { it.isNotBlank() }
 *
 * Uses kotlinx.datetime for duration computation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftFormSheet(
    target: ShiftFormTarget,
    onSave: (com.zyntasolutions.zyntapos.domain.model.ShiftSchedule) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var startTime by remember { mutableStateOf(target.existingShift?.startTime ?: "09:00") }
    var endTime by remember { mutableStateOf(target.existingShift?.endTime ?: "17:00") }
    var notes by remember { mutableStateOf(target.existingShift?.notes ?: "") }

    // Preview duration using domain model logic
    val previewShift = target.existingShift?.copy(startTime = startTime, endTime = endTime)
        ?: com.zyntasolutions.zyntapos.domain.model.ShiftSchedule(
            id = "", employeeId = target.employeeId, storeId = "",
            shiftDate = target.date.toString(), startTime = startTime, endTime = endTime,
            notes = null, createdAt = "", updatedAt = ""
        )
    val durationHours = previewShift.durationHours
    val durationText = "${durationHours.toInt()}h ${((durationHours % 1) * 60).toInt()}m"

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = modifier
                .padding(16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (target.existingShift != null) "Edit Shift" else "Add Shift",
                style = MaterialTheme.typography.titleLarge
            )

            // Employee + Date info
            Text(
                text = "${target.date} — Employee ${target.employeeId}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Time pickers
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ZyntaTimeField(
                    label = "Start Time",
                    value = startTime,
                    onValueChange = { startTime = it },
                    modifier = Modifier.weight(1f)
                )
                ZyntaTimeField(
                    label = "End Time",
                    value = endTime,
                    onValueChange = { endTime = it },
                    modifier = Modifier.weight(1f)
                )
            }

            // Duration preview
            Text(
                text = "Duration: $durationText${if (durationHours > 12) " ⚠ Unusually long shift" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = if (durationHours > 12) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Notes
            ZyntaTextField(
                label = "Notes (optional)",
                value = notes,
                onValueChange = { notes = it },
                singleLine = false
            )

            // Save button
            ZyntaButton(
                text = "Save Shift",
                onClick = {
                    val shift = com.zyntasolutions.zyntapos.domain.model.ShiftSchedule(
                        id = target.existingShift?.id ?: generateUuid(),
                        employeeId = target.employeeId,
                        storeId = "",          // injected from Koin session in ViewModel
                        shiftDate = target.date.toString(),
                        startTime = startTime,
                        endTime = endTime,
                        notes = notes.takeIf { it.isNotBlank() },
                        createdAt = target.existingShift?.createdAt ?: nowIsoDateTime(),
                        updatedAt = nowIsoDateTime()
                    )
                    onSave(shift)
                },
                enabled = startTime.isNotBlank() && endTime.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
```

---

## Design System Additions

### `ZyntaTimeField.kt`

```kotlin
package com.zyntasolutions.zyntapos.designsystem.component

/**
 * Time input field for HH:MM (24h) format.
 * Shows time picker dialog on click.
 * Value format: "HH:MM" string (matches ShiftSchedule.startTime / endTime fields).
 */
@Composable
fun ZyntaTimeField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
)
```

---

## Integration with `EmployeeDetailScreen` Shift Tab (Sprint 8 placeholder)

```kotlin
// Tab note: ShiftSchedule tab is not on EmployeeDetailScreen.
// Instead, shift scheduling is accessed from the main Staff nav item → ShiftScheduleScreen.
// The EmployeeDetailScreen exposes a "View Schedule" action button that navigates to
// ShiftScheduleScreen pre-filtered to the employee's week.
```

---

## Navigation Addition

```kotlin
// In MainNavGraph.kt — staff sub-graph
composable<ZyntaRoute.StaffSchedule> {
    val vm = koinViewModel<StaffViewModel>()
    ShiftScheduleScreen(
        storeId = /* from session */ "",
        viewModel = vm
    )
}
```

---

## Tasks

- [ ] **11.1** Implement `ZyntaWeekCalendar.kt` in `:composeApp:designsystem` with `rowKeys`, `rowLabel`, `cellContent`, week navigation
- [ ] **11.2** Implement `getWeekStart(date: LocalDate): LocalDate` utility using `kotlinx.datetime` (never java.time)
- [ ] **11.3** Implement `ShiftScheduleScreen.kt` wiring `ZyntaWeekCalendar` with employee rows and shift cells
- [ ] **11.4** Implement `ShiftCell` composable — shows shift times or add-icon, tap → ShiftFormSheet
- [ ] **11.5** Implement `ShiftFormSheet.kt` (ModalBottomSheet) with time pickers and duration preview
- [ ] **11.6** Create `ZyntaTimeField.kt` in `:composeApp:designsystem`
- [ ] **11.7** Add `StaffSchedule` route to navigation wiring in `MainNavGraph.kt`
- [ ] **11.8** Write `ShiftScheduleViewModelTest` — test save shift, delete shift, week navigation
- [ ] **11.9** Write `ZyntaWeekCalendarTest` — test `getWeekStart` returns correct Monday for various dates
- [ ] **11.10** Verify: `./gradlew :composeApp:feature:staff:assemble && ./gradlew :composeApp:designsystem:assemble`

---

## Verification

```bash
./gradlew :composeApp:feature:staff:assemble
./gradlew :composeApp:designsystem:assemble
./gradlew :composeApp:feature:staff:test
./gradlew :composeApp:designsystem:test
```

---

## Definition of Done

- [ ] `ZyntaWeekCalendar` renders correctly with 7 columns and configurable rows
- [ ] `ShiftScheduleScreen` shows all employee shifts for current week
- [ ] Tapping a cell opens `ShiftFormSheet` pre-filled with employee + date
- [ ] Long-press on existing shift shows delete confirmation
- [ ] Duration preview updates live as start/end times change
- [ ] `getWeekStart` correctly returns Monday using `kotlinx.datetime.LocalDate`
- [ ] Unit tests for `getWeekStart` and shift save/delete pass
- [ ] Commit: `feat(staff): add shift scheduling with ZyntaWeekCalendar composable`
