package com.zyntasolutions.zyntapos.feature.staff

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Root screen for the Staff & HR feature (Sprints 8–12).
 *
 * Hosts a [TabRow] with five sections:
 * 1. **Employees** — directory and CRUD
 * 2. **Attendance** — today's clock-in/out board
 * 3. **Leave** — pending requests and approval workflow
 * 4. **Shifts** — weekly shift scheduler
 * 5. **Payroll** — pay period overview and payment processing
 *
 * @param state     Current [StaffState] from [StaffViewModel].
 * @param onIntent  Dispatches intents to [StaffViewModel].
 * @param storeId   Active store ID (passed down to sub-screens).
 * @param modifier  Optional modifier.
 */
@Composable
fun StaffScreen(
    state: StaffState,
    onIntent: (StaffIntent) -> Unit,
    storeId: String,
    modifier: Modifier = Modifier,
) {
    // Compute today's date once; stable across recompositions
    val todayDate = remember {
        val ldt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        "%04d-%02d-%02d".format(ldt.year, ldt.monthNumber, ldt.dayOfMonth)
    }

    // Compute current week bounds (Mon–Sun) once
    val (weekStart, weekEnd) = remember(todayDate) { currentWeekBounds(todayDate) }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = state.activeTab.ordinal) {
            StaffTabItem.entries.forEach { item ->
                Tab(
                    selected = state.activeTab == item.tab,
                    onClick = { onIntent(StaffIntent.SwitchTab(item.tab)) },
                    text = { Text(item.label) },
                    icon = { Icon(item.icon, contentDescription = null) },
                )
            }
        }

        when (state.activeTab) {
            StaffTab.EMPLOYEES -> {
                if (state.selectedEmployee != null || state.employeeForm.isEditing) {
                    EmployeeDetailScreen(
                        state = state,
                        onIntent = onIntent,
                        onNavigateBack = { onIntent(StaffIntent.BackToEmployeeList) },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    EmployeeListScreen(
                        state = state,
                        onIntent = onIntent,
                        onNavigateToDetail = { empId -> onIntent(StaffIntent.SelectEmployee(empId)) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            StaffTab.ATTENDANCE -> {
                AttendanceScreen(
                    state = state,
                    onIntent = onIntent,
                    storeId = storeId,
                    today = todayDate,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            StaffTab.LEAVE -> {
                LeaveManagementScreen(
                    state = state,
                    onIntent = onIntent,
                    storeId = storeId,
                    currentUserId = "", // injected via ViewModel; screen uses onIntent dispatch
                    modifier = Modifier.fillMaxSize(),
                )
            }

            StaffTab.SHIFTS -> {
                ShiftSchedulerScreen(
                    state = state,
                    onIntent = onIntent,
                    storeId = storeId,
                    weekStart = weekStart,
                    weekEnd = weekEnd,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            StaffTab.PAYROLL -> {
                PayrollScreen(
                    state = state,
                    onIntent = onIntent,
                    storeId = storeId,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    // Global error snackbar (non-intrusive)
    state.error?.let { msg ->
        LaunchedEffect(msg) {
            // Snackbar would normally be shown here via a SnackbarHostState.
            // For simplicity, the error is cleared when the user dismisses.
        }
    }
}

// ─── Tab metadata ─────────────────────────────────────────────────────────────

private enum class StaffTabItem(
    val tab: StaffTab,
    val label: String,
    val icon: ImageVector,
) {
    EMPLOYEES(StaffTab.EMPLOYEES, "Staff", Icons.Default.Groups),
    ATTENDANCE(StaffTab.ATTENDANCE, "Attendance", Icons.Default.Today),
    LEAVE(StaffTab.LEAVE, "Leave", Icons.Default.CalendarMonth),
    SHIFTS(StaffTab.SHIFTS, "Shifts", Icons.Default.Schedule),
    PAYROLL(StaffTab.PAYROLL, "Payroll", Icons.Default.Payments),
}

/**
 * Returns (weekStart, weekEnd) as ISO dates (YYYY-MM-DD) for the ISO week
 * (Monday–Sunday) that contains [today].
 *
 * Uses simple day-of-week arithmetic; avoids `java.time` for KMP compat.
 */
private fun currentWeekBounds(today: String): Pair<String, String> {
    return try {
        val parts = today.split("-")
        val year = parts[0].toInt()
        val month = parts[1].toInt()
        val day = parts[2].toInt()

        // Zeller-like day-of-week: 0=Sun, 1=Mon ... 6=Sat
        val dayOfWeek = dayOfWeek(year, month, day) // 0=Sun
        val isoOffset = if (dayOfWeek == 0) 6 else dayOfWeek - 1 // 0=Mon

        val monDay = addDays(year, month, day, -isoOffset)
        val sunDay = addDays(monDay.first, monDay.second, monDay.third, 6)

        val start = "%04d-%02d-%02d".format(monDay.first, monDay.second, monDay.third)
        val end = "%04d-%02d-%02d".format(sunDay.first, sunDay.second, sunDay.third)
        start to end
    } catch (_: Exception) {
        today to today
    }
}

/** Tomohiko Sakamoto's algorithm — returns 0=Sun, 1=Mon … 6=Sat. */
private fun dayOfWeek(year: Int, month: Int, day: Int): Int {
    val t = intArrayOf(0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4)
    val y = if (month < 3) year - 1 else year
    return (y + y / 4 - y / 100 + y / 400 + t[month - 1] + day) % 7
}

/** Adds [days] calendar days to (year, month, day); handles month/year rollover. */
private fun addDays(year: Int, month: Int, day: Int, days: Int): Triple<Int, Int, Int> {
    var d = day + days
    var m = month
    var y = year
    while (d < 1) {
        m--
        if (m < 1) { m = 12; y-- }
        d += daysInMonth(y, m)
    }
    while (d > daysInMonth(y, m)) {
        d -= daysInMonth(y, m)
        m++
        if (m > 12) { m = 1; y++ }
    }
    return Triple(y, m, d)
}

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
    else -> 30
}
