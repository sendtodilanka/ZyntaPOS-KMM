package com.zyntasolutions.zyntapos.feature.staff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaEmptyState
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * C3.4: Cross-store attendance report screen.
 *
 * Shows aggregated attendance data per employee per store for the current month.
 * Uses the optimized SQL JOIN query via [GetCrossStoreAttendanceUseCase].
 */
@Composable
fun CrossStoreAttendanceScreen(
    state: StaffState,
    onIntent: (StaffIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Compute current month date range
    val (from, to) = remember {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val monthStart = "%04d-%02d-01T00:00:00".format(now.year, now.monthNumber)
        val monthEnd = "%04d-%02d-%02dT23:59:59".format(now.year, now.monthNumber, now.dayOfMonth)
        monthStart to monthEnd
    }

    LaunchedEffect(Unit) {
        onIntent(StaffIntent.LoadAvailableStores)
    }

    LaunchedEffect(from, to) {
        onIntent(StaffIntent.LoadCrossStoreAttendance(from, to))
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Header with period info
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(ZyntaSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Business,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "Cross-Store Attendance",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.weight(1f))
                AssistChip(
                    onClick = {},
                    label = { Text("This Month") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 2.dp),
                        )
                    },
                )
            }
        }

        if (state.isCrossStoreAttendanceLoading) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Text(
                    "Loading cross-store data...",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = ZyntaSpacing.sm),
                )
            }
            return
        }

        if (state.crossStoreAttendance.isEmpty()) {
            ZyntaEmptyState(
                title = "No cross-store attendance data",
                icon = Icons.Default.Business,
                subtitle = "Employee attendance across stores will appear here.",
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            return
        }

        // Group by employee for display
        val grouped = remember(state.crossStoreAttendance) {
            state.crossStoreAttendance.groupBy { it.employeeId }
        }

        // Summary row
        val totalDays = state.crossStoreAttendance.sumOf { it.totalDays }
        val totalHours = state.crossStoreAttendance.sumOf { it.totalHoursWorked }
        val storeCount = state.crossStoreAttendance.map { it.storeId }.distinct().size

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(ZyntaSpacing.md),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SummaryKpi("Employees", grouped.size.toString())
                SummaryKpi("Stores", storeCount.toString())
                SummaryKpi("Total Days", totalDays.toString())
                SummaryKpi("Total Hours", "%.1f".format(totalHours))
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(ZyntaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for ((employeeId, rows) in grouped) {
                val employeeName = rows.first().employeeName
                item(key = "header_$employeeId") {
                    Text(
                        text = employeeName,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                }
                items(rows, key = { "${it.employeeId}_${it.storeId}" }) { row ->
                    CrossStoreAttendanceCard(row = row, maxHours = totalHours)
                }
            }
        }
    }
}

@Composable
private fun CrossStoreAttendanceCard(
    row: CrossStoreAttendanceRow,
    maxHours: Double,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(ZyntaSpacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Business,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(row.storeName, style = MaterialTheme.typography.bodyLarge)
                }
                Text(
                    "${row.totalDays} days",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.lg),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "%.1f hrs".format(row.totalHoursWorked),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (row.lateArrivals > 0) {
                    Text(
                        "${row.lateArrivals} late",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (maxHours > 0) {
                LinearProgressIndicator(
                    progress = { (row.totalHoursWorked / maxHours).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun SummaryKpi(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
