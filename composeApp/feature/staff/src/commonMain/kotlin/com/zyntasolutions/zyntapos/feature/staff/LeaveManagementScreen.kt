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
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.LeaveRecord
import com.zyntasolutions.zyntapos.domain.model.LeaveStatus
import com.zyntasolutions.zyntapos.domain.model.LeaveType
import kotlinx.datetime.Clock

/**
 * Leave management screen — shows pending leave requests for manager review.
 *
 * Managers can approve or reject requests. Employees (or managers on behalf)
 * can submit new leave requests via the FAB.
 *
 * @param state       Current [StaffState].
 * @param onIntent    Dispatches intents to [StaffViewModel].
 * @param storeId     Active store ID.
 * @param modifier    Optional modifier.
 */
@Composable
fun LeaveManagementScreen(
    state: StaffState,
    onIntent: (StaffIntent) -> Unit,
    storeId: String,
    currentUserId: String,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(storeId) { onIntent(StaffIntent.LoadPendingLeave(storeId)) }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onIntent(StaffIntent.ShowLeaveForm) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Request Leave") },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = ZyntaSpacing.md),
        ) {
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.pendingLeaveRequests.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.EventAvailable,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "No pending leave requests",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Text(
                    text = "${state.pendingLeaveRequests.size} pending request${if (state.pendingLeaveRequests.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = ZyntaSpacing.sm),
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.pendingLeaveRequests, key = { it.id }) { request ->
                        val employeeName = state.employees.find { it.id == request.employeeId }?.fullName
                            ?: request.employeeId
                        LeaveRequestCard(
                            request = request,
                            employeeName = employeeName,
                            onApprove = {
                                val now = Clock.System.now().toEpochMilliseconds()
                                onIntent(StaffIntent.ApproveLeave(request.id, currentUserId, now))
                            },
                            onReject = { reason ->
                                val now = Clock.System.now().toEpochMilliseconds()
                                onIntent(StaffIntent.RejectLeave(request.id, reason, now))
                            },
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }

        // Leave submission dialog
        if (state.showLeaveForm) {
            LeaveRequestDialog(
                form = state.leaveForm,
                employees = state.employees.filter { it.isActive },
                onIntent = onIntent,
            )
        }
    }
}

@Composable
private fun LeaveRequestCard(
    request: LeaveRecord,
    employeeName: String,
    onApprove: () -> Unit,
    onReject: (String) -> Unit,
) {
    var showRejectDialog by remember { mutableStateOf(false) }
    var rejectReason by remember { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(ZyntaSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (request.leaveType) {
                        LeaveType.SICK -> Icons.Default.HealthAndSafety
                        LeaveType.ANNUAL -> Icons.Default.BeachAccess
                        LeaveType.PERSONAL -> Icons.Default.Person
                        LeaveType.UNPAID -> Icons.Default.MoneyOff
                    },
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(employeeName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "${request.leaveType.name} · ${request.startDate} → ${request.endDate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LeaveStatusBadge(request.status)
            }

            request.reason?.let { reason ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (request.status == LeaveStatus.PENDING) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showRejectDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reject")
                    }
                    Button(
                        onClick = onApprove,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Approve")
                    }
                }
            }
        }
    }

    if (showRejectDialog) {
        AlertDialog(
            onDismissRequest = { showRejectDialog = false },
            title = { Text("Rejection Reason") },
            text = {
                OutlinedTextField(
                    value = rejectReason,
                    onValueChange = { rejectReason = it },
                    label = { Text("Reason *") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (rejectReason.isNotBlank()) {
                            onReject(rejectReason)
                            showRejectDialog = false
                        }
                    },
                    enabled = rejectReason.isNotBlank(),
                ) { Text("Reject") }
            },
            dismissButton = {
                TextButton(onClick = { showRejectDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun LeaveStatusBadge(status: LeaveStatus) {
    val (containerColor, contentColor) = when (status) {
        LeaveStatus.PENDING -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        LeaveStatus.APPROVED -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        LeaveStatus.REJECTED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = containerColor,
    ) {
        Text(
            text = status.name,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeaveRequestDialog(
    form: LeaveFormState,
    employees: List<com.zyntasolutions.zyntapos.domain.model.Employee>,
    onIntent: (StaffIntent) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { onIntent(StaffIntent.HideLeaveForm) },
        title = { Text("Request Leave") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                // Employee dropdown
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                ) {
                    OutlinedTextField(
                        value = employees.find { it.id == form.employeeId }?.fullName ?: "Select Employee",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Employee *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        employees.forEach { emp ->
                            DropdownMenuItem(
                                text = { Text(emp.fullName) },
                                onClick = {
                                    onIntent(StaffIntent.UpdateLeaveFormField("employeeId", emp.id))
                                    expanded = false
                                },
                            )
                        }
                    }
                }

                // Leave type chips
                Text("Type", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    LeaveType.entries.forEach { type ->
                        FilterChip(
                            selected = form.leaveType == type,
                            onClick = { onIntent(StaffIntent.UpdateLeaveType(type)) },
                            label = { Text(type.name) },
                        )
                    }
                }

                OutlinedTextField(
                    value = form.startDate,
                    onValueChange = { onIntent(StaffIntent.UpdateLeaveFormField("startDate", it)) },
                    label = { Text("Start Date (YYYY-MM-DD) *") },
                    isError = form.validationErrors.containsKey("startDate"),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = form.endDate,
                    onValueChange = { onIntent(StaffIntent.UpdateLeaveFormField("endDate", it)) },
                    label = { Text("End Date (YYYY-MM-DD) *") },
                    isError = form.validationErrors.containsKey("endDate"),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = form.reason,
                    onValueChange = { onIntent(StaffIntent.UpdateLeaveFormField("reason", it)) },
                    label = { Text("Reason") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onIntent(StaffIntent.SubmitLeaveRequest) }) { Text("Submit") }
        },
        dismissButton = {
            TextButton(onClick = { onIntent(StaffIntent.HideLeaveForm) }) { Text("Cancel") }
        },
    )
}
