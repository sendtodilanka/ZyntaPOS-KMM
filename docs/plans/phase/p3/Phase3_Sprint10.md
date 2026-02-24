# ZyntaPOS — Phase 3 Sprint 10: Staff Feature Part 3 — Leave Management

> **Document ID:** ZYNTA-PLAN-PHASE3-SPRINT10-v1.0
> **Phase:** 3 — Enterprise (Months 13–18)
> **Sprint:** 10 of 24 | Week 10
> **Module(s):** `:composeApp:feature:staff`
> **Author:** Senior KMP Architect & Lead Engineer
> **Reference:** ZYNTA-MASTER-PLAN-v1.0 | ADR-001

---

## Goal

Implement leave management screens for `:composeApp:feature:staff`: leave request submission, manager approval/rejection workflow, and leave history view. RBAC enforces `Permission.APPROVE_LEAVE` for approval actions — employees without this permission see their own history only.

---

## New Screen Files

**Location:** `composeApp/feature/staff/src/commonMain/kotlin/com/zyntasolutions/zyntapos/feature/staff/screen/`

```
screen/
├── LeaveRequestFormScreen.kt    # Employee submits new leave request
├── LeaveApprovalScreen.kt       # Manager sees all pending leave requests
└── LeaveHistoryScreen.kt        # Per-employee leave history
```

---

## Leave Request Form Screen

### `LeaveRequestFormScreen.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.staff.screen

/**
 * Leave request submission form.
 *
 * Fields:
 * - leaveType: dropdown (SICK, ANNUAL, PERSONAL, UNPAID)
 * - startDate: date picker (must be today or future)
 * - endDate:   date picker (must be >= startDate)
 * - reason:    optional multi-line text field
 *
 * On submit:
 * - Creates LeaveRecord with:
 *     id = UUID,
 *     employeeId = session.employeeId,
 *     status = LeaveStatus.PENDING,
 *     approvedBy = null,
 *     createdAt/updatedAt = now()
 * - Fires StaffIntent.SubmitLeaveRequest(leave)
 *
 * Validation:
 * - startDate and endDate required
 * - endDate must be >= startDate
 * - leaveType required (no null)
 *
 * Uses kotlinx.datetime.LocalDate for date handling (never java.time.*)
 */
@Composable
fun LeaveRequestFormScreen(
    employeeId: String,
    viewModel: StaffViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var leaveType by remember { mutableStateOf<LeaveType?>(null) }
    var startDate by remember { mutableStateOf<String?>(null) }
    var endDate by remember { mutableStateOf<String?>(null) }
    var reason by remember { mutableStateOf("") }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is StaffEffect.ShowSuccess -> onNavigateBack()
                else -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Request Leave") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { /* back icon */ } }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Leave type dropdown
            ZyntaDropdownField(
                label = "Leave Type *",
                options = LeaveType.entries.map { it.name },
                selected = leaveType?.name,
                onSelect = { leaveType = LeaveType.valueOf(it) }
            )

            // Start date
            ZyntaDateField(
                label = "Start Date *",
                value = startDate,
                onClick = { showStartDatePicker = true }
            )

            // End date
            ZyntaDateField(
                label = "End Date *",
                value = endDate,
                onClick = { showEndDatePicker = true },
                isError = endDate != null && startDate != null && endDate!! < startDate!!,
                errorMessage = "End date must be on or after start date"
            )

            // Reason
            ZyntaTextField(
                label = "Reason (optional)",
                value = reason,
                onValueChange = { reason = it },
                singleLine = false,
                minLines = 3,
                maxLines = 6
            )

            // Submit button
            ZyntaButton(
                text = "Submit Request",
                enabled = leaveType != null && startDate != null && endDate != null
                       && (endDate ?: "") >= (startDate ?: "") && !state.isSaving,
                isLoading = state.isSaving,
                onClick = {
                    val leave = buildLeaveRecord(employeeId, leaveType!!, startDate!!, endDate!!, reason)
                    viewModel.handleIntent(StaffIntent.SubmitLeaveRequest(leave))
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // Date pickers (Material3 DatePickerDialog)
    if (showStartDatePicker) {
        ZyntaDatePickerDialog(
            onDateSelected = { date ->
                startDate = date
                showStartDatePicker = false
                // Auto-set endDate to startDate if not set yet
                if (endDate == null) endDate = date
            },
            onDismiss = { showStartDatePicker = false },
            minDate = todayIsoDate()    // cannot request past leave from this form
        )
    }

    if (showEndDatePicker) {
        ZyntaDatePickerDialog(
            onDateSelected = { date ->
                endDate = date
                showEndDatePicker = false
            },
            onDismiss = { showEndDatePicker = false },
            minDate = startDate ?: todayIsoDate()
        )
    }
}

private fun buildLeaveRecord(
    employeeId: String,
    leaveType: LeaveType,
    startDate: String,
    endDate: String,
    reason: String
): LeaveRecord = LeaveRecord(
    id = generateUuid(),
    employeeId = employeeId,
    leaveType = leaveType,
    startDate = startDate,
    endDate = endDate,
    reason = reason.takeIf { it.isNotBlank() },
    status = LeaveStatus.PENDING,
    approvedBy = null,
    approvedAt = null,
    rejectionReason = null,
    createdAt = nowIsoDateTime(),
    updatedAt = nowIsoDateTime()
)
```

---

## Leave Approval Screen

### `LeaveApprovalScreen.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.staff.screen

/**
 * Manager-only leave approval screen.
 *
 * RBAC: This screen is only accessible to users with Permission.APPROVE_LEAVE.
 * The RbacNavFilter in :composeApp:navigation ensures this at the route level.
 *
 * Layout:
 * - Pending leave request list (sorted by createdAt ascending — oldest first)
 * - Each card shows:
 *     - Employee name + avatar
 *     - Leave type badge (SICK/ANNUAL/PERSONAL/UNPAID)
 *     - Date range ("Feb 24 – Feb 28, 2026") + total days count
 *     - Reason (if provided)
 *     - Approve (green) / Reject (red) action buttons
 *
 * On "Approve": fires StaffIntent.ApproveLeave(leaveId)
 * On "Reject":  shows RejectionReasonDialog → fires StaffIntent.RejectLeave(leaveId, reason)
 *
 * Empty state: illustration + "No pending leave requests" message
 */
@Composable
fun LeaveApprovalScreen(
    viewModel: StaffViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    var rejectTarget by remember { mutableStateOf<String?>(null) }   // leaveId being rejected

    LaunchedEffect(Unit) {
        viewModel.handleIntent(StaffIntent.LoadPendingLeaves)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Pending Leave Requests") }) }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.pendingLeaves.isEmpty()) {
            EmptyStateView(
                message = "No pending leave requests",
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = modifier.padding(padding)
            ) {
                items(state.pendingLeaves, key = { it.id }) { leave ->
                    val employee = state.employees.find { it.id == leave.employeeId }
                    PendingLeaveCard(
                        leave = leave,
                        employeeName = employee?.fullName ?: "Unknown Employee",
                        onApprove = { viewModel.handleIntent(StaffIntent.ApproveLeave(leave.id)) },
                        onReject = { rejectTarget = leave.id }
                    )
                }
            }
        }
    }

    // Rejection reason dialog
    rejectTarget?.let { leaveId ->
        RejectionReasonDialog(
            onConfirm = { reason ->
                viewModel.handleIntent(StaffIntent.RejectLeave(leaveId, reason))
                rejectTarget = null
            },
            onDismiss = { rejectTarget = null }
        )
    }
}

@Composable
private fun PendingLeaveCard(
    leave: com.zyntasolutions.zyntapos.domain.model.LeaveRecord,
    employeeName: String,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier
) {
    ZyntaCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(employeeName, style = MaterialTheme.typography.titleMedium)
                LeaveTypeBadge(leaveType = leave.leaveType)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${formatDate(leave.startDate)} – ${formatDate(leave.endDate)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${calculateDays(leave.startDate, leave.endDate)} day(s)",
                style = MaterialTheme.typography.bodySmall
            )
            leave.reason?.let { reason ->
                Spacer(Modifier.height(8.dp))
                Text(text = "Reason: $reason", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ZyntaOutlinedButton(
                    text = "Reject",
                    onClick = onReject,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                ZyntaButton(
                    text = "Approve",
                    onClick = onApprove,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun RejectionReasonDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reject Leave Request") },
        text = {
            Column {
                Text("Please provide a reason for rejection:")
                Spacer(Modifier.height(8.dp))
                ZyntaTextField(
                    label = "Reason",
                    value = reason,
                    onValueChange = { reason = it },
                    singleLine = false,
                    minLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (reason.isNotBlank()) onConfirm(reason) },
                enabled = reason.isNotBlank()
            ) { Text("Reject") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun LeaveTypeBadge(leaveType: LeaveType, modifier: Modifier = Modifier) {
    val (label, color) = when (leaveType) {
        LeaveType.SICK     -> "Sick" to Color(0xFFEF4444)
        LeaveType.ANNUAL   -> "Annual" to Color(0xFF3B82F6)
        LeaveType.PERSONAL -> "Personal" to Color(0xFF8B5CF6)
        LeaveType.UNPAID   -> "Unpaid" to Color(0xFF6B7280)
    }
    ZyntaStatusBadge(label = label, color = color, modifier = modifier)
}
```

---

## Leave History Screen

### `LeaveHistoryScreen.kt`

```kotlin
package com.zyntasolutions.zyntapos.feature.staff.screen

/**
 * Per-employee leave history screen.
 *
 * Layout:
 * - Filter row: leave type chips (All / Sick / Annual / Personal / Unpaid)
 *               + status chips (All / Pending / Approved / Rejected)
 * - Leave record list (sorted descending by startDate)
 *
 * Each record shows:
 * - Leave type badge + status badge (PENDING=amber, APPROVED=green, REJECTED=red)
 * - Date range + day count
 * - Reason (if provided)
 * - If APPROVED: "Approved by {manager name}"
 * - If REJECTED: "Rejected: {rejectionReason}"
 *
 * "Request Leave" FAB → LeaveRequestFormScreen
 */
@Composable
fun LeaveHistoryScreen(
    employeeId: String,
    viewModel: StaffViewModel,
    onNavigateToRequest: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    var typeFilter by remember { mutableStateOf<LeaveType?>(null) }
    var statusFilter by remember { mutableStateOf<LeaveStatus?>(null) }

    LaunchedEffect(employeeId) {
        viewModel.handleIntent(StaffIntent.LoadLeaveHistory(employeeId))
    }

    val filteredLeaves = state.leaveHistory
        .filter { typeFilter == null || it.leaveType == typeFilter }
        .filter { statusFilter == null || it.status == statusFilter }
        .sortedByDescending { it.startDate }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Leave History") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { /* back */ } }
            )
        },
        floatingActionButton = {
            ZyntaFab(text = "Request Leave", onClick = onNavigateToRequest)
        }
    ) { padding ->
        Column(modifier = modifier.padding(padding)) {
            // Filter chips
            LeaveFilterRow(
                selectedType = typeFilter,
                selectedStatus = statusFilter,
                onTypeSelected = { typeFilter = it },
                onStatusSelected = { statusFilter = it }
            )

            if (filteredLeaves.isEmpty()) {
                EmptyStateView(message = "No leave records found")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredLeaves, key = { it.id }) { leave ->
                        LeaveHistoryCard(leave = leave, employees = state.employees)
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaveHistoryCard(
    leave: com.zyntasolutions.zyntapos.domain.model.LeaveRecord,
    employees: List<com.zyntasolutions.zyntapos.domain.model.Employee>,
    modifier: Modifier = Modifier
) {
    ZyntaCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                LeaveTypeBadge(leave.leaveType)
                LeaveStatusBadge(leave.status)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${formatDate(leave.startDate)} – ${formatDate(leave.endDate)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "${calculateDays(leave.startDate, leave.endDate)} day(s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            leave.reason?.let {
                Spacer(Modifier.height(4.dp))
                Text(text = it, style = MaterialTheme.typography.bodySmall)
            }
            when (leave.status) {
                LeaveStatus.APPROVED -> {
                    val approver = employees.find { e -> e.id == leave.approvedBy }
                    Text(
                        text = "Approved by ${approver?.fullName ?: "Manager"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF16A34A)
                    )
                }
                LeaveStatus.REJECTED -> {
                    Text(
                        text = "Rejected: ${leave.rejectionReason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                LeaveStatus.PENDING -> {
                    Text(
                        text = "Awaiting approval",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFF59E0B)
                    )
                }
            }
        }
    }
}

@Composable
private fun LeaveStatusBadge(status: LeaveStatus, modifier: Modifier = Modifier) {
    val (label, color) = when (status) {
        LeaveStatus.PENDING  -> "Pending" to Color(0xFFF59E0B)
        LeaveStatus.APPROVED -> "Approved" to Color(0xFF16A34A)
        LeaveStatus.REJECTED -> "Rejected" to Color(0xFFEF4444)
    }
    ZyntaStatusBadge(label = label, color = color, modifier = modifier)
}
```

---

## Integration with `EmployeeDetailScreen` Leave Tab

```kotlin
// Tab 2: Leave
@Composable
private fun EmployeeLeaveTab(
    state: StaffState,
    viewModel: StaffViewModel,
    onNavigateToLeaveRequest: () -> Unit,
    onNavigateToLeaveApproval: () -> Unit,
    hasApprovePermission: Boolean
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (hasApprovePermission) {
            // Show pending leaves badge + "Review Requests" button
            if (state.pendingLeaves.isNotEmpty()) {
                ZyntaCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${state.pendingLeaves.size} pending request(s)")
                        ZyntaButton(text = "Review", onClick = onNavigateToLeaveApproval)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        // Recent leave history (last 3)
        state.leaveHistory.take(3).forEach { leave ->
            LeaveHistoryCard(leave, state.employees)
            Spacer(Modifier.height(4.dp))
        }

        ZyntaOutlinedButton(
            text = "Request Leave",
            onClick = onNavigateToLeaveRequest,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
```

---

## New Design System Additions

### `ZyntaDateField.kt`

```kotlin
package com.zyntasolutions.zyntapos.designsystem.component

/**
 * Read-only tap-to-select date field.
 * Displays formatted date or placeholder when null.
 * Passes onClick to parent to show a DatePickerDialog.
 */
@Composable
fun ZyntaDateField(
    label: String,
    value: String?,           // ISO date "YYYY-MM-DD"
    onClick: () -> Unit,
    isError: Boolean = false,
    errorMessage: String? = null,
    modifier: Modifier = Modifier
)
```

### `ZyntaDatePickerDialog.kt`

```kotlin
package com.zyntasolutions.zyntapos.designsystem.component

/**
 * Material 3 DatePickerDialog wrapper.
 * Returns selected date as ISO string "YYYY-MM-DD".
 * Uses kotlinx.datetime.LocalDate for internal state (never java.time.*).
 */
@Composable
fun ZyntaDatePickerDialog(
    onDateSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    minDate: String? = null,    // ISO date — disables dates before this
    maxDate: String? = null
)
```

---

## Tasks

- [ ] **10.1** Implement `LeaveRequestFormScreen.kt` with date pickers and validation
- [ ] **10.2** Implement `LeaveApprovalScreen.kt` with pending leave cards and rejection dialog
- [ ] **10.3** Implement `LeaveHistoryScreen.kt` with type/status filter chips
- [ ] **10.4** Create `LeaveHistoryCard`, `LeaveTypeBadge`, `LeaveStatusBadge` composables
- [ ] **10.5** Fill in `EmployeeLeaveTab` in `EmployeeDetailScreen.kt`
- [ ] **10.6** Create `ZyntaDateField.kt` and `ZyntaDatePickerDialog.kt` in `:composeApp:designsystem`
- [ ] **10.7** Add `LeaveApproval` route to navigation — gated by `Permission.APPROVE_LEAVE`
- [ ] **10.8** Wire navigation: `StaffGraph` → `LeaveRequestForm`, `LeaveApproval`, `LeaveHistory`
- [ ] **10.9** Write `LeaveViewModelTest` — test submit → pending, approve → approved, reject → rejected with reason
- [ ] **10.10** Verify: `./gradlew :composeApp:feature:staff:assemble && ./gradlew :composeApp:feature:staff:test`

---

## Verification

```bash
./gradlew :composeApp:feature:staff:assemble
./gradlew :composeApp:feature:staff:test
./gradlew :composeApp:designsystem:assemble
```

---

## Definition of Done

- [ ] `LeaveRequestFormScreen` validates dates, prevents past start dates, submits correctly
- [ ] `LeaveApprovalScreen` shows pending requests with approve/reject actions; requires `APPROVE_LEAVE` permission
- [ ] `LeaveHistoryScreen` shows filtered leave history with correct status badges
- [ ] Date picker components added to design system
- [ ] `EmployeeLeaveTab` integrated in detail screen
- [ ] Leave workflow tests (submit → approve / reject) pass
- [ ] Commit: `feat(staff): implement leave request submission and manager approval workflow`
