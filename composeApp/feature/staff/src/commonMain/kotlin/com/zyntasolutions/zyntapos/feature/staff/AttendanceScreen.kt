package com.zyntasolutions.zyntapos.feature.staff

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaEmptyState
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.AttendanceRecord
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Attendance board screen — shows today's clock-in/out status for all employees.
 *
 * Managers can clock employees in or out from this screen. Each card shows:
 * - Employee name and clock-in time
 * - Clock-out time (or "Clocked In" if still working)
 * - Hours worked (once clocked out)
 *
 * @param state       Current [StaffState].
 * @param onIntent    Dispatches intents to [StaffViewModel].
 * @param storeId     Active store ID used for scoping today's attendance.
 * @param today       ISO date string YYYY-MM-DD for today.
 * @param modifier    Optional modifier.
 */
@Composable
fun AttendanceScreen(
    state: StaffState,
    onIntent: (StaffIntent) -> Unit,
    storeId: String,
    today: String,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    LaunchedEffect(today) {
        onIntent(StaffIntent.LoadTodayAttendance(storeId, today))
    }

    // Map employeeId → open attendance record (clocked in, not out)
    val openRecordsByEmployee = remember(state.todayAttendance) {
        state.todayAttendance.filter { it.isOpen }.associateBy { it.employeeId }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Summary header
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(ZyntaSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SummaryChip(
                    label = s[StringResource.COMMON_TOTAL],
                    count = state.employees.size,
                    icon = Icons.Default.Group,
                )
                SummaryChip(
                    label = s[StringResource.STAFF_PRESENT],
                    count = state.todayAttendance.size,
                    icon = Icons.Default.CheckCircle,
                )
                SummaryChip(
                    label = s[StringResource.STAFF_CLOCKED_IN],
                    count = openRecordsByEmployee.size,
                    icon = Icons.Default.Login,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onIntent(StaffIntent.ExportAttendanceCsv) }) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = s[StringResource.STAFF_EXPORT_ATTENDANCE],
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        if (state.isLoading) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        if (state.employees.isEmpty()) {
            ZyntaEmptyState(
                title = s[StringResource.STAFF_NO_EMPLOYEES],
                icon = Icons.Default.Group,
                subtitle = s[StringResource.STAFF_ADD_EMPLOYEES_HINT],
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(ZyntaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.employees.filter { it.isActive }, key = { it.id }) { employee ->
                val openRecord = openRecordsByEmployee[employee.id]
                val todayRecord = state.todayAttendance.find { it.employeeId == employee.id }

                AttendanceCard(
                    employeeName = employee.fullName,
                    openRecord = openRecord,
                    todayRecord = todayRecord,
                    onClockIn = {
                        val ldt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                        val nowStr = "%04d-%02d-%02dT%02d:%02d:%02d".format(
                            ldt.year, ldt.monthNumber, ldt.dayOfMonth,
                            ldt.hour, ldt.minute, ldt.second,
                        )
                        onIntent(StaffIntent.ClockIn(employee.id, storeId, nowStr))
                    },
                    onClockOut = {
                        if (openRecord != null) {
                            val ldt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                            val nowStr = "%04d-%02d-%02dT%02d:%02d:%02d".format(
                                ldt.year, ldt.monthNumber, ldt.dayOfMonth,
                                ldt.hour, ldt.minute, ldt.second,
                            )
                            onIntent(StaffIntent.ClockOut(openRecord.id, employee.id, nowStr))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun AttendanceCard(
    employeeName: String,
    openRecord: AttendanceRecord?,
    todayRecord: AttendanceRecord?,
    onClockIn: () -> Unit,
    onClockOut: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ZyntaSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (openRecord != null) Icons.Default.Login else Icons.Default.Logout,
                contentDescription = null,
                tint = if (openRecord != null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(end = ZyntaSpacing.sm),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(employeeName, style = MaterialTheme.typography.titleSmall)
                when {
                    openRecord != null -> {
                        Text(
                            text = "In: ${openRecord.clockIn.substringAfter("T")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    todayRecord != null -> {
                        Text(
                            text = buildString {
                                append("In: ${todayRecord.clockIn.substringAfter("T")}")
                                todayRecord.clockOut?.let { append(" · Out: ${it.substringAfter("T")}") }
                                todayRecord.totalHours?.let { append(" · %.1fh".format(it)) }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> {
                        Text(
                            text = s[StringResource.STAFF_NOT_CLOCKED_IN],
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            when {
                openRecord != null -> {
                    OutlinedButton(onClick = onClockOut) {
                        Text(s[StringResource.STAFF_CLOCK_OUT])
                    }
                }
                todayRecord == null -> {
                    Button(onClick = onClockIn) {
                        Text(s[StringResource.STAFF_CLOCK_IN])
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryChip(label: String, count: Int, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "$count $label",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}
