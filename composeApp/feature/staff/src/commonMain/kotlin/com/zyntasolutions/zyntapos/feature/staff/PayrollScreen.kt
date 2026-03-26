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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaEmptyState
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.PayrollRecord
import com.zyntasolutions.zyntapos.domain.model.PayrollStatus
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Payroll overview screen (Sprint 12).
 *
 * Displays payroll records for a given pay period grouped by status.
 * Managers can:
 * - Select a pay period (YYYY-MM format)
 * - Generate payroll for individual employees
 * - Mark records as PAID with an optional payment reference
 *
 * @param state       Current [StaffState].
 * @param onIntent    Dispatches intents to [StaffViewModel].
 * @param storeId     Active store ID.
 * @param modifier    Optional modifier.
 */
@Composable
fun PayrollScreen(
    state: StaffState,
    onIntent: (StaffIntent) -> Unit,
    storeId: String,
    modifier: Modifier = Modifier,
) {
    // Default period to current month (YYYY-MM) on first composition
    var period by remember {
        val ldt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        mutableStateOf("%04d-%02d".format(ldt.year, ldt.monthNumber))
    }

    LaunchedEffect(period) {
        onIntent(StaffIntent.LoadPayroll(storeId, period))
    }

    var showGenerateDialog by remember { mutableStateOf(false) }
    var showPayDialog by remember { mutableStateOf<PayrollRecord?>(null) }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showGenerateDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Generate Payroll") },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = ZyntaSpacing.md),
        ) {
            // Period selector row
            PeriodSelectorRow(
                period = period,
                onPeriodChange = { period = it },
            )

            // Summary card
            state.payrollSummary?.let { summary ->
                PayrollSummaryCard(summary = summary)
                Spacer(Modifier.height(ZyntaSpacing.sm))
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.payrollRecords.isEmpty()) {
                ZyntaEmptyState(
                    title = "No payroll records for $period",
                    icon = Icons.Default.Payments,
                    subtitle = "Tap + to generate payroll.",
                )
            } else {
                val pending = state.payrollRecords.filter { it.status == PayrollStatus.PENDING }
                val paid = state.payrollRecords.filter { it.status == PayrollStatus.PAID }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                    if (pending.isNotEmpty()) {
                        item(key = "header_pending") {
                            PayrollStatusHeader("Pending (${pending.size})", MaterialTheme.colorScheme.error)
                        }
                        items(pending, key = { it.id }) { record ->
                            PayrollRecordCard(
                                record = record,
                                employeeName = state.employees.find { it.id == record.employeeId }?.fullName
                                    ?: record.employeeId,
                                onMarkPaid = { showPayDialog = record },
                            )
                        }
                    }

                    if (paid.isNotEmpty()) {
                        item(key = "header_paid") {
                            PayrollStatusHeader("Paid (${paid.size})", MaterialTheme.colorScheme.primary)
                        }
                        items(paid, key = { it.id }) { record ->
                            PayrollRecordCard(
                                record = record,
                                employeeName = state.employees.find { it.id == record.employeeId }?.fullName
                                    ?: record.employeeId,
                                onMarkPaid = null,
                            )
                        }
                    }

                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }

        // Generate payroll dialog
        if (showGenerateDialog) {
            GeneratePayrollDialog(
                employees = state.employees.filter { it.isActive },
                currentPeriod = period,
                onGenerate = { employeeId, periodStart, periodEnd ->
                    onIntent(StaffIntent.GeneratePayroll(employeeId, periodStart, periodEnd))
                    showGenerateDialog = false
                },
                onDismiss = { showGenerateDialog = false },
            )
        }

        // Mark as paid dialog
        showPayDialog?.let { record ->
            MarkPaidDialog(
                record = record,
                employeeName = state.employees.find { it.id == record.employeeId }?.fullName
                    ?: record.employeeId,
                onConfirm = { paidAt, paymentRef ->
                    onIntent(StaffIntent.ProcessPayment(record.id, paidAt, paymentRef))
                    showPayDialog = null
                },
                onDismiss = { showPayDialog = null },
            )
        }
    }
}

// ─── Private composables ──────────────────────────────────────────────────────

@Composable
private fun PeriodSelectorRow(
    period: String,
    onPeriodChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = ZyntaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
    ) {
        Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        OutlinedTextField(
            value = period,
            onValueChange = onPeriodChange,
            label = { Text("Period (YYYY-MM)") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PayrollSummaryCard(summary: com.zyntasolutions.zyntapos.domain.model.PayrollSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(ZyntaSpacing.md)) {
            Text(
                text = "Period Summary — ${summary.period}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(ZyntaSpacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SummaryMetric("Employees", summary.totalEmployees.toString())
                SummaryMetric("Gross", "%.2f".format(summary.totalGrossPay))
                SummaryMetric("Deductions", "%.2f".format(summary.totalDeductions))
                SummaryMetric("Net", "%.2f".format(summary.totalNetPay))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${summary.paidCount} paid · ${summary.pendingCount} pending",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PayrollStatusHeader(label: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = color,
        modifier = Modifier.padding(top = ZyntaSpacing.sm, bottom = 4.dp),
    )
}

@Composable
private fun PayrollRecordCard(
    record: PayrollRecord,
    employeeName: String,
    onMarkPaid: (() -> Unit)?,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ZyntaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (record.status == PayrollStatus.PAID) Icons.Default.CheckCircle else Icons.Default.Schedule,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (record.status == PayrollStatus.PAID)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.width(ZyntaSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(employeeName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    text = "${record.periodStart} → ${record.periodEnd}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                    PayLineItem("Base", record.baseSalary)
                    if (record.overtimePay > 0) PayLineItem("OT", record.overtimePay)
                    if (record.commission > 0) PayLineItem("Comm", record.commission)
                    if (record.deductions > 0) PayLineItem("Ded", -record.deductions)
                }
                Text(
                    text = "Net: %.2f".format(record.netPay),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                record.paymentRef?.let {
                    Text(
                        text = "Ref: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (onMarkPaid != null) {
                TextButton(onClick = onMarkPaid) {
                    Text("Mark Paid")
                }
            }
        }
    }
}

@Composable
private fun PayLineItem(label: String, amount: Double) {
    Text(
        text = "$label: %.2f".format(amount),
        style = MaterialTheme.typography.labelSmall,
        color = if (amount < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeneratePayrollDialog(
    employees: List<com.zyntasolutions.zyntapos.domain.model.Employee>,
    currentPeriod: String,
    onGenerate: (employeeId: String, periodStart: String, periodEnd: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedEmployeeId by remember { mutableStateOf("") }
    var periodStart by remember { mutableStateOf("$currentPeriod-01") }
    var periodEnd by remember {
        // Default end: last day of the current month (approximate)
        val parts = currentPeriod.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull() ?: 2026
        val month = parts.getOrNull(1)?.toIntOrNull() ?: 1
        val lastDay = when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
            else -> 30
        }
        mutableStateOf("%04d-%02d-%02d".format(year, month, lastDay))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generate Payroll") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                ) {
                    OutlinedTextField(
                        value = employees.find { it.id == selectedEmployeeId }?.fullName ?: "Select Employee",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Employee *") },
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
                                    selectedEmployeeId = emp.id
                                    expanded = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = periodStart,
                    onValueChange = { periodStart = it },
                    label = { Text("Period Start (YYYY-MM-DD) *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = periodEnd,
                    onValueChange = { periodEnd = it },
                    label = { Text("Period End (YYYY-MM-DD) *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (selectedEmployeeId.isNotBlank() && periodStart.isNotBlank() && periodEnd.isNotBlank()) {
                        onGenerate(selectedEmployeeId, periodStart, periodEnd)
                    }
                },
            ) { Text("Generate") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun MarkPaidDialog(
    record: PayrollRecord,
    employeeName: String,
    onConfirm: (paidAt: Long, paymentRef: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var paymentRef by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mark as Paid") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                Text(
                    text = "Confirm payment of %.2f to $employeeName?".format(record.netPay),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = paymentRef,
                    onValueChange = { paymentRef = it },
                    label = { Text("Payment Reference (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Bank transfer ID, cheque no., etc.") },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val now = Clock.System.now().toEpochMilliseconds()
                    onConfirm(now, paymentRef.ifBlank { null })
                },
            ) { Text("Confirm Payment") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
