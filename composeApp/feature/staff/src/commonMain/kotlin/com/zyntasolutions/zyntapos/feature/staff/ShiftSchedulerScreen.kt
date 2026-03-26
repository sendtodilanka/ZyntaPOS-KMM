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
import com.zyntasolutions.zyntapos.domain.model.ShiftSchedule

/**
 * Weekly shift scheduler screen.
 *
 * Displays all shifts for the current week grouped by date. Managers can
 * add shifts via the FAB and delete existing shifts by tapping the delete icon.
 *
 * @param state       Current [StaffState].
 * @param onIntent    Dispatches intents to [StaffViewModel].
 * @param storeId     Active store ID.
 * @param weekStart   ISO date: Monday of the week (YYYY-MM-DD).
 * @param weekEnd     ISO date: Sunday of the week (YYYY-MM-DD).
 * @param modifier    Optional modifier.
 */
@Composable
fun ShiftSchedulerScreen(
    state: StaffState,
    onIntent: (StaffIntent) -> Unit,
    storeId: String,
    weekStart: String,
    weekEnd: String,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    LaunchedEffect(weekStart, weekEnd) {
        if (weekStart.isNotBlank() && weekEnd.isNotBlank()) {
            onIntent(StaffIntent.LoadWeeklyShifts(storeId, weekStart, weekEnd))
        }
    }

    // Group shifts by date
    val shiftsByDate = remember(state.weeklyShifts) {
        state.weeklyShifts.groupBy { it.shiftDate }.toSortedMap()
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onIntent(StaffIntent.ShowShiftForm) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(s[StringResource.STAFF_ADD_SHIFT]) },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = ZyntaSpacing.md),
        ) {
            // Week header
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = ZyntaSpacing.sm),
            ) {
                Text(
                    text = "Week: $weekStart → $weekEnd",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(ZyntaSpacing.md),
                )
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.weeklyShifts.isEmpty()) {
                ZyntaEmptyState(
                    title = s[StringResource.STAFF_NO_SHIFTS],
                    icon = Icons.Default.CalendarMonth,
                    subtitle = s[StringResource.STAFF_TAP_ADD_SHIFT],
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                    shiftsByDate.forEach { (date, shifts) ->
                        item(key = "header_$date") {
                            DaySectionHeader(date)
                        }
                        items(shifts, key = { it.id }) { shift ->
                            val employeeName = state.employees.find { it.id == shift.employeeId }?.fullName
                                ?: shift.employeeId
                            ShiftRow(
                                shift = shift,
                                employeeName = employeeName,
                                onDelete = { onIntent(StaffIntent.DeleteShift(shift.id)) },
                                onEdit = {
                                    updateState(shift, onIntent)
                                    onIntent(StaffIntent.ShowShiftForm)
                                },
                            )
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }

        // Shift form dialog
        if (state.showShiftForm) {
            ShiftFormDialog(
                form = state.shiftForm,
                employees = state.employees.filter { it.isActive },
                onIntent = onIntent,
            )
        }
    }
}

private fun updateState(shift: ShiftSchedule, onIntent: (StaffIntent) -> Unit) {
    onIntent(StaffIntent.UpdateShiftField("employeeId", shift.employeeId))
    onIntent(StaffIntent.UpdateShiftField("shiftDate", shift.shiftDate))
    onIntent(StaffIntent.UpdateShiftField("startTime", shift.startTime))
    onIntent(StaffIntent.UpdateShiftField("endTime", shift.endTime))
    onIntent(StaffIntent.UpdateShiftField("notes", shift.notes ?: ""))
}

@Composable
private fun DaySectionHeader(date: String) {
    Text(
        text = date,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = ZyntaSpacing.sm, bottom = 4.dp),
    )
}

@Composable
private fun ShiftRow(
    shift: ShiftSchedule,
    employeeName: String,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ZyntaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(ZyntaSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(employeeName, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "${shift.startTime} – ${shift.endTime}" +
                        (shift.durationHours?.let { " (%.1fh)".format(it) } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                shift.notes?.let { notes ->
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = s[StringResource.STAFF_EDIT_SHIFT])
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = s[StringResource.STAFF_DELETE_SHIFT],
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShiftFormDialog(
    form: ShiftFormState,
    employees: List<com.zyntasolutions.zyntapos.domain.model.Employee>,
    onIntent: (StaffIntent) -> Unit,
) {
    val s = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { onIntent(StaffIntent.HideShiftForm) },
        title = { Text(if (form.id == null) s[StringResource.STAFF_ADD_SHIFT] else s[StringResource.STAFF_EDIT_SHIFT]) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                ) {
                    OutlinedTextField(
                        value = employees.find { it.id == form.employeeId }?.fullName ?: s[StringResource.STAFF_SELECT_EMPLOYEE],
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(s[StringResource.STAFF_EMPLOYEE_REQUIRED]) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        employees.forEach { emp ->
                            DropdownMenuItem(
                                text = { Text(emp.fullName) },
                                onClick = {
                                    onIntent(StaffIntent.UpdateShiftField("employeeId", emp.id))
                                    expanded = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = form.shiftDate,
                    onValueChange = { onIntent(StaffIntent.UpdateShiftField("shiftDate", it)) },
                    label = { Text(s[StringResource.STAFF_DATE_REQUIRED]) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                    OutlinedTextField(
                        value = form.startTime,
                        onValueChange = { onIntent(StaffIntent.UpdateShiftField("startTime", it)) },
                        label = { Text(s[StringResource.STAFF_START_TIME_REQUIRED]) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = form.endTime,
                        onValueChange = { onIntent(StaffIntent.UpdateShiftField("endTime", it)) },
                        label = { Text(s[StringResource.STAFF_END_TIME_REQUIRED]) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
                OutlinedTextField(
                    value = form.notes,
                    onValueChange = { onIntent(StaffIntent.UpdateShiftField("notes", it)) },
                    label = { Text(s[StringResource.STAFF_NOTES]) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onIntent(StaffIntent.SaveShift) }) { Text(s[StringResource.COMMON_SAVE]) }
        },
        dismissButton = {
            TextButton(onClick = { onIntent(StaffIntent.HideShiftForm) }) { Text(s[StringResource.COMMON_CANCEL]) }
        },
    )
}
