# ZyntaPOS — Phase 3 Sprint 12: Staff Feature Part 5 — Payroll Generation & Payslip

> **Document ID:** ZYNTA-PLAN-PHASE3-SPRINT12-v1.0
> **Phase:** 3 — Enterprise (Months 13–18)
> **Sprint:** 12 of 24 | Week 12
> **Module(s):** `:composeApp:feature:staff`
> **Author:** Senior KMP Architect & Lead Engineer
> **Reference:** ZYNTA-MASTER-PLAN-v1.0 | ADR-001

---

## Goal

Implement payroll generation, payslip view, and payroll history screens for `:composeApp:feature:staff`. Integrates `GeneratePayrollUseCaseImpl` (Sprint 5) with a generation form, a payslip detail screen, and a payroll list per employee. Payslip export is triggered via `ReceiptPrinterPort` (ESC/POS format) or an OS-level PDF/print intent.

---

## New Screen Files

**Location:** `composeApp/feature/staff/src/commonMain/kotlin/com/zyntasolutions/zyntapos/feature/staff/screen/`

```
screen/
├── PayrollListScreen.kt          # List of payroll records for an employee
├── PayrollDetailScreen.kt        # Payslip view + payment action + export button
└── PayrollGenerationScreen.kt    # Form to trigger payroll generation for a period
```

---

## Payroll List Screen

### `PayrollListScreen.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.staff.screen

/**
 * Per-employee payroll history screen.
 *
 * Layout:
 * - Header: employee name + total paid to date
 * - LazyColumn of PayrollPeriodCard (sorted descending by periodStart)
 *
 * Each card shows:
 * - Period (e.g. "February 2026")
 * - Status badge: PENDING (amber) / PAID (green)
 * - Net pay amount (large, prominent)
 * - Summary: base + overtime + commission − deductions
 * - If PAID: payment date + reference
 *
 * Action buttons:
 * - "View Payslip" → PayrollDetailScreen
 * - "Generate New" FAB → PayrollGenerationScreen
 *
 * RBAC: requires Permission.MANAGE_PAYROLL to generate/pay.
 *       Viewing history available to MANAGER+ with VIEW_STAFF.
 */
@Composable
fun PayrollListScreen(
    employeeId: String,
    viewModel: StaffViewModel,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToGenerate: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(employeeId) {
        viewModel.handleIntent(StaffIntent.LoadPayrollHistory(employeeId))
    }

    val employee = state.employees.find { it.id == employeeId }
    val totalPaid = state.payrollRecords
        .filter { it.status == PayrollStatus.PAID }
        .sumOf { it.netPay }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Payroll — ${employee?.fullName ?: ""}") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { /* back */ } }
            )
        },
        floatingActionButton = {
            ZyntaFab(text = "Generate", onClick = onNavigateToGenerate)
        }
    ) { padding ->
        Column(modifier = modifier.padding(padding)) {
            // Summary header card
            PayrollSummaryHeader(
                totalPaid = totalPaid,
                pendingCount = state.payrollRecords.count { it.status == PayrollStatus.PENDING }
            )

            if (state.payrollRecords.isEmpty()) {
                EmptyStateView(message = "No payroll records for this employee")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        state.payrollRecords.sortedByDescending { it.periodStart },
                        key = { it.id }
                    ) { record ->
                        PayrollPeriodCard(
                            record = record,
                            onClick = { onNavigateToDetail(record.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PayrollSummaryHeader(
    totalPaid: Double,
    pendingCount: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Total Paid to Date", style = MaterialTheme.typography.bodySmall)
                Text(
                    text = CurrencyUtils.format(totalPaid),
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            if (pendingCount > 0) {
                ZyntaStatusBadge(
                    label = "$pendingCount Pending",
                    color = Color(0xFFF59E0B)
                )
            }
        }
    }
}

@Composable
private fun PayrollPeriodCard(
    record: com.zyntasolutions.zyntapos.domain.model.PayrollRecord,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ZyntaCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = formatPeriod(record.periodStart),   // "February 2026"
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Base ${CurrencyUtils.format(record.baseSalary)} + OT ${CurrencyUtils.format(record.overtimePay)} + Comm ${CurrencyUtils.format(record.commission)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                record.paidAt?.let {
                    Text(
                        text = "Paid on ${formatDate(it)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                PayrollStatusBadge(record.status)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = CurrencyUtils.format(record.netPay),
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    }
}

@Composable
private fun PayrollStatusBadge(status: PayrollStatus, modifier: Modifier = Modifier) {
    val (label, color) = when (status) {
        PayrollStatus.PENDING -> "Pending" to Color(0xFFF59E0B)
        PayrollStatus.PAID    -> "Paid" to Color(0xFF16A34A)
    }
    ZyntaStatusBadge(label = label, color = color, modifier = modifier)
}
```

---

## Payroll Detail Screen (Payslip View)

### `PayrollDetailScreen.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.staff.screen

/**
 * Payslip detail screen.
 *
 * Layout (mimics a paper payslip):
 * ┌─────────────────────────────────────┐
 * │  [Store Logo]  PAYSLIP              │
 * │  Store Name                         │
 * │  Employee: John Silva               │
 * │  Period: Feb 1 – Feb 28, 2026       │
 * │  Status: PAID / PENDING             │
 * ├─────────────────────────────────────┤
 * │  EARNINGS                           │
 * │  Base Salary         LKR  80,000.00 │
 * │  Overtime Pay        LKR   6,000.00 │
 * │  Commission          LKR   4,500.00 │
 * │  ─────────────────────────────────  │
 * │  GROSS PAY           LKR  90,500.00 │
 * ├─────────────────────────────────────┤
 * │  DEDUCTIONS                         │
 * │  Deductions          LKR   5,000.00 │
 * │  ─────────────────────────────────  │
 * │  NET PAY             LKR  85,500.00 │
 * └─────────────────────────────────────┘
 *
 * If status == PENDING: "Mark as Paid" button (requires MANAGE_PAYROLL)
 * "Export Payslip" button: triggers ReceiptPrinterPort or OS PDF intent
 */
@Composable
fun PayrollDetailScreen(
    payrollId: String,
    viewModel: StaffViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    var showPaymentDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is StaffEffect.ShowSuccess -> { /* handled by snackbar */ }
                is StaffEffect.ExportPayslip -> { /* trigger platform export */ }
                else -> {}
            }
        }
    }

    val record = state.payrollRecords.find { it.id == payrollId }
        ?: state.selectedPayroll
        ?: return

    val employee = state.employees.find { it.id == record.employeeId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Payslip") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { /* back */ } },
                actions = {
                    IconButton(onClick = {
                        viewModel.handleIntent(StaffIntent.ExportPayslip(payrollId))
                    }) { /* share/export icon */ }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PayslipHeader(employee = employee, record = record)
            PayslipEarningsSection(record = record)
            PayslipDeductionsSection(record = record)
            PayslipNetPaySection(record = record)

            if (record.status == PayrollStatus.PENDING) {
                ZyntaButton(
                    text = "Mark as Paid",
                    onClick = { showPaymentDialog = true },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            ZyntaOutlinedButton(
                text = "Export Payslip",
                onClick = { sendEffect(StaffEffect.ExportPayslip(payrollId)) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showPaymentDialog) {
        PaymentReferenceDialog(
            onConfirm = { ref ->
                viewModel.handleIntent(StaffIntent.ProcessPayrollPayment(payrollId, ref))
                showPaymentDialog = false
            },
            onDismiss = { showPaymentDialog = false }
        )
    }
}

@Composable
private fun PayslipHeader(
    employee: com.zyntasolutions.zyntapos.domain.model.Employee?,
    record: com.zyntasolutions.zyntapos.domain.model.PayrollRecord,
    modifier: Modifier = Modifier
) {
    ZyntaCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("PAYSLIP", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text("Employee: ${employee?.fullName ?: record.employeeId}")
            Text("Position: ${employee?.position ?: ""}")
            Text("Period: ${formatDate(record.periodStart)} – ${formatDate(record.periodEnd)}")
            Text("Status: ${record.status.name}")
            record.paidAt?.let { Text("Payment Date: ${formatDate(it)}") }
            record.paymentRef?.let { Text("Reference: $it") }
        }
    }
}

@Composable
private fun PayslipEarningsSection(record: com.zyntasolutions.zyntapos.domain.model.PayrollRecord, modifier: Modifier = Modifier) {
    ZyntaCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("EARNINGS", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            PayslipRow("Base Salary", record.baseSalary)
            PayslipRow("Overtime Pay", record.overtimePay)
            PayslipRow("Commission", record.commission)
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            PayslipRow("GROSS PAY", record.grossPay, isBold = true)
        }
    }
}

@Composable
private fun PayslipDeductionsSection(record: com.zyntasolutions.zyntapos.domain.model.PayrollRecord, modifier: Modifier = Modifier) {
    ZyntaCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("DEDUCTIONS", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            PayslipRow("Total Deductions", record.deductions)
        }
    }
}

@Composable
private fun PayslipNetPaySection(record: com.zyntasolutions.zyntapos.domain.model.PayrollRecord, modifier: Modifier = Modifier) {
    ZyntaCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("NET PAY", style = MaterialTheme.typography.titleMedium)
            Text(
                text = CurrencyUtils.format(record.netPay),
                style = MaterialTheme.typography.headlineSmall
            )
        }
    }
}

@Composable
private fun PayslipRow(label: String, amount: Double, isBold: Boolean = false) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Text(
            label,
            style = if (isBold) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    else MaterialTheme.typography.bodyMedium
        )
        Text(
            CurrencyUtils.format(amount),
            style = if (isBold) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    else MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun PaymentReferenceDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var ref by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Payment Reference") },
        text = {
            ZyntaTextField(
                label = "Reference / Transaction ID",
                value = ref,
                onValueChange = { ref = it }
            )
        },
        confirmButton = {
            TextButton(onClick = { if (ref.isNotBlank()) onConfirm(ref) }, enabled = ref.isNotBlank()) {
                Text("Confirm")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
```

---

## Payroll Generation Screen

### `PayrollGenerationScreen.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.staff.screen

/**
 * Payroll generation form.
 *
 * Fields:
 * - Employee selector (dropdown from state.activeEmployees) OR pre-set if launched from EmployeeDetail
 * - Period Start:  month-year picker (first day of month, ISO "YYYY-MM-01")
 * - Period End:    auto-set to last day of selected month
 *
 * Preview section (read-only, computed after selecting employee + period):
 * - Estimated base salary (from employee.salary + salary type + attendance data)
 * - Note: actual computation runs in GeneratePayrollUseCase on server-side data
 *
 * On "Generate": fires StaffIntent.GeneratePayroll(employeeId, periodStart, periodEnd)
 *
 * Shows loading indicator while isLoading = true.
 * On success: navigates to PayrollDetailScreen with generated record ID.
 *
 * Validation:
 * - Employee required
 * - Period start required
 * - No duplicate payroll (backend check: GeneratePayrollUseCaseImpl returns failure if record exists)
 */
@Composable
fun PayrollGenerationScreen(
    preselectedEmployeeId: String?,
    viewModel: StaffViewModel,
    onNavigateToPayslip: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    var selectedEmployeeId by remember { mutableStateOf(preselectedEmployeeId) }
    var periodStart by remember { mutableStateOf("") }   // "YYYY-MM-01"
    var periodEnd by remember { mutableStateOf("") }     // "YYYY-MM-DD" (last day)

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is StaffEffect.ShowSuccess -> {
                    // After payroll generated, selectedPayroll is set in state
                    state.selectedPayroll?.let { onNavigateToPayslip(it.id) }
                }
                else -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Generate Payroll") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { /* back */ } }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Employee selector (if not pre-set)
            if (preselectedEmployeeId == null) {
                ZyntaDropdownField(
                    label = "Employee *",
                    options = state.activeEmployees.map { it.fullName },
                    selected = state.activeEmployees.find { it.id == selectedEmployeeId }?.fullName,
                    onSelect = { name ->
                        selectedEmployeeId = state.activeEmployees.find { it.fullName == name }?.id
                    }
                )
            } else {
                Text(
                    text = "Employee: ${state.employees.find { it.id == preselectedEmployeeId }?.fullName}",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            // Month-year picker (simplified: dropdown of last 12 months)
            ZyntaDropdownField(
                label = "Payroll Period *",
                options = generateLast12Months(),   // ["2026-02", "2026-01", "2025-12", ...]
                selected = periodStart.take(7),
                onSelect = { yearMonth ->
                    periodStart = "${yearMonth}-01"
                    periodEnd = lastDayOfMonth(yearMonth)
                }
            )

            if (periodStart.isNotBlank() && periodEnd.isNotBlank()) {
                Text(
                    text = "Period: ${formatDate(periodStart)} to ${formatDate(periodEnd)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Warning for existing payroll
            if (state.error?.contains("already exists") == true) {
                Text(
                    text = "⚠ Payroll for this period already exists.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            ZyntaButton(
                text = "Generate Payroll",
                enabled = selectedEmployeeId != null && periodStart.isNotBlank() && !state.isLoading,
                isLoading = state.isLoading,
                onClick = {
                    viewModel.handleIntent(
                        StaffIntent.GeneratePayroll(
                            employeeId = selectedEmployeeId!!,
                            periodStart = periodStart,
                            periodEnd = periodEnd
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Returns ISO date strings for the last day of each of the last 12 months. */
private fun generateLast12Months(): List<String> {
    // Uses kotlinx.datetime.Clock.System.now() + LocalDate arithmetic
    // Returns list like ["2026-02", "2026-01", "2025-12", ...]
    return emptyList()  // Implementation uses kotlinx-datetime 0.6.1
}

/** Returns ISO date for the last day of a given "YYYY-MM" string. */
private fun lastDayOfMonth(yearMonth: String): String {
    // Uses kotlinx.datetime.LocalDate(year, month+1, 1).minus(1, DateTimeUnit.DAY)
    return ""
}
```

---

## Integration with `EmployeeDetailScreen` Payroll Tab

```kotlin
// Tab 3: Payroll (in EmployeeDetailScreen)
@Composable
private fun EmployeePayrollTab(
    state: StaffState,
    viewModel: StaffViewModel,
    onNavigateToPayrollList: () -> Unit,
    onNavigateToGenerate: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        // Recent 2 payroll records
        state.payrollRecords.take(2).forEach { record ->
            PayrollPeriodCard(record = record, onClick = { /* navigate to detail */ })
            Spacer(Modifier.height(4.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ZyntaOutlinedButton(
                text = "View All",
                onClick = onNavigateToPayrollList,
                modifier = Modifier.weight(1f)
            )
            ZyntaButton(
                text = "Generate",
                onClick = onNavigateToGenerate,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
```

---

## Payslip Export

The export action calls `ReceiptPrinterPort.printReceipt(...)` for ESC/POS thermal printers, or triggers an OS-level PDF share intent. The exact implementation is platform-specific:

```kotlin
// In StaffEffect handler (platform-specific in androidMain/jvmMain):
// Android: startActivity(Intent.ACTION_SEND with PDF URI from FileProvider)
// Desktop: java.awt.Desktop.getDesktop().print(file)
//
// For now, StaffEffect.ExportPayslip(payrollId) is sent and handled
// by the platform host composable (androidApp / desktopApp).
```

---

## Tasks

- [ ] **12.1** Implement `PayrollListScreen.kt` with summary header and period cards
- [ ] **12.2** Implement `PayrollDetailScreen.kt` (payslip layout) with earnings/deductions/net sections
- [ ] **12.3** Implement `PayrollGenerationScreen.kt` with month-period picker and employee selector
- [ ] **12.4** Implement `PaymentReferenceDialog` composable for marking payroll as paid
- [ ] **12.5** Fill in `EmployeePayrollTab` in `EmployeeDetailScreen.kt` (Sprint 8 placeholder)
- [ ] **12.6** Implement `generateLast12Months()` and `lastDayOfMonth()` using `kotlinx.datetime` 0.6.1
- [ ] **12.7** Add payroll navigation routes: `StaffPayroll(employeeId)`, `PayrollDetail(payrollId)`, `PayrollGenerate(employeeId?)` in `MainNavGraph.kt`
- [ ] **12.8** Update `StaffEffect` to add `ExportPayslip(payrollId)` effect (missing from Sprint 8 — add now)
- [ ] **12.9** Write `PayrollGenerationTest` — test period calculation, duplicate payroll rejection, net pay derivation
- [ ] **12.10** Write `PayrollViewModelTest` — test mark-as-paid flow, generateLast12Months output
- [ ] **12.11** Verify: `./gradlew :composeApp:feature:staff:assemble && ./gradlew :composeApp:feature:staff:test`

---

## Verification

```bash
./gradlew :composeApp:feature:staff:assemble
./gradlew :composeApp:feature:staff:test
./gradlew :composeApp:feature:staff:detekt
```

---

## Definition of Done

- [ ] `PayrollListScreen` shows all payroll records per employee sorted by period
- [ ] `PayrollDetailScreen` renders a correctly formatted payslip with earnings / deductions / net pay
- [ ] `PayrollGenerationScreen` correctly sets periodStart/End and calls `GeneratePayrollUseCase`
- [ ] Marking as paid updates status and stores payment reference
- [ ] `ExportPayslip` effect dispatched correctly
- [ ] `lastDayOfMonth` and `generateLast12Months` use `kotlinx.datetime` only
- [ ] Payroll generation and mark-as-paid tests pass
- [ ] Commit: `feat(staff): add payroll generation, payslip view, and payment processing`
