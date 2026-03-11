package com.zyntasolutions.zyntapos.feature.admin

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.AuditEntry
import com.zyntasolutions.zyntapos.domain.model.AuditEventType
import com.zyntasolutions.zyntapos.domain.model.IntegrityReport
import com.zyntasolutions.zyntapos.domain.model.Role
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/**
 * Security audit log viewer screen (Sprint 15).
 *
 * Displays all audit entries in reverse chronological order.
 * Supports filtering by user ID. Each entry shows:
 * - Event type with an icon
 * - Actor user ID and device ID
 * - Success / failure badge
 * - ISO timestamp
 * - Optional JSON payload (expandable)
 *
 * @param state     Current [AdminState].
 * @param onIntent  Dispatches intents to [AdminViewModel].
 * @param modifier  Optional modifier.
 */
@Composable
fun AuditLogScreen(
    state: AdminState,
    onIntent: (AdminIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Apply all client-side filters (user, event type, success/fail, date range)
    val filtered = remember(
        state.auditEntries, state.auditUserFilter, state.auditEventTypeFilter,
        state.auditRoleFilter, state.auditSuccessFilter, state.auditDateFrom, state.auditDateTo,
    ) {
        state.auditEntries.filter { e ->
            (state.auditUserFilter.isBlank() || e.userId.contains(state.auditUserFilter, ignoreCase = true)) &&
            (state.auditEventTypeFilter == null || e.eventType == state.auditEventTypeFilter) &&
            (state.auditRoleFilter == null || e.userRole == state.auditRoleFilter) &&
            (state.auditSuccessFilter == null || e.success == state.auditSuccessFilter) &&
            (state.auditDateFrom == null || e.createdAt >= state.auditDateFrom) &&
            (state.auditDateTo == null || e.createdAt <= state.auditDateTo)
        }
    }

    // Client-side pagination
    val pageSize = 50
    val totalFilteredPages = remember(filtered.size) {
        ((filtered.size + pageSize - 1) / pageSize).coerceAtLeast(1)
    }
    val currentPage = state.auditPage.coerceIn(0, (totalFilteredPages - 1).coerceAtLeast(0))
    val pagedEntries = remember(filtered, currentPage) {
        filtered.drop(currentPage * pageSize).take(pageSize)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = ZyntaSpacing.md),
    ) {
        // ── Integrity badge ───────────────────────────────────────────────
        IntegrityBadge(
            report = state.integrityReport,
            isVerifying = state.isVerifyingIntegrity,
            onRefresh = { onIntent(AdminIntent.VerifyIntegrity) },
        )

        // ── Filter bar: user ID ───────────────────────────────────────────
        OutlinedTextField(
            value = state.auditUserFilter,
            onValueChange = { onIntent(AdminIntent.FilterAuditByUser(it)) },
            label = { Text("Filter by user ID") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (state.auditUserFilter.isNotBlank()) {
                    IconButton(onClick = { onIntent(AdminIntent.FilterAuditByUser("")) }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear filter")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = ZyntaSpacing.sm),
        )

        // ── Filter bar: event type dropdown ───────────────────────────────
        EventTypeDropdown(
            selected = state.auditEventTypeFilter,
            onSelected = { onIntent(AdminIntent.FilterAuditByEventType(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = ZyntaSpacing.sm),
        )

        // ── Filter bar: role dropdown ───────────────────────────────────────
        RoleFilterDropdown(
            selected = state.auditRoleFilter,
            onSelected = { onIntent(AdminIntent.FilterAuditByRole(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = ZyntaSpacing.sm),
        )

        // ── Filter bar: date range ──────────────────────────────────────────
        DateRangeFilter(
            dateFrom = state.auditDateFrom,
            dateTo = state.auditDateTo,
            onDateRangeChanged = { from, to ->
                onIntent(AdminIntent.FilterAuditByDateRange(from, to))
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = ZyntaSpacing.sm),
        )

        // ── Filter bar: success/fail + CSV export ─────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = state.auditSuccessFilter == null,
                onClick = { onIntent(AdminIntent.FilterAuditBySuccess(null)) },
                label = { Text("All", style = MaterialTheme.typography.labelSmall) },
            )
            FilterChip(
                selected = state.auditSuccessFilter == true,
                onClick = { onIntent(AdminIntent.FilterAuditBySuccess(true)) },
                label = { Text("OK", style = MaterialTheme.typography.labelSmall) },
            )
            FilterChip(
                selected = state.auditSuccessFilter == false,
                onClick = { onIntent(AdminIntent.FilterAuditBySuccess(false)) },
                label = { Text("FAIL", style = MaterialTheme.typography.labelSmall) },
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { onIntent(AdminIntent.ExportAuditLogCsv) }) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = "Export CSV",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Text(
            "${filtered.size} event(s)${if (totalFilteredPages > 1) " · Page ${currentPage + 1} of $totalFilteredPages" else ""}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = ZyntaSpacing.sm),
        )

        // ── Entry list ────────────────────────────────────────────────────
        if (filtered.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.EventNote,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when {
                            state.auditUserFilter.isBlank() &&
                            state.auditEventTypeFilter == null &&
                            state.auditRoleFilter == null &&
                            state.auditSuccessFilter == null &&
                            state.auditDateFrom == null &&
                            state.auditDateTo == null -> "No audit events recorded."
                            else -> "No events match the current filters."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
            ) {
                items(pagedEntries, key = { it.id }) { entry ->
                    AuditEntryCard(entry)
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }

        // ── Pagination controls ───────────────────────────────────────────
        if (totalFilteredPages > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = ZyntaSpacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    onClick = { onIntent(AdminIntent.PrevAuditPage) },
                    enabled = currentPage > 0,
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous page", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Prev", style = MaterialTheme.typography.labelMedium)
                }
                Text(
                    "${currentPage + 1} / $totalFilteredPages",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(
                    onClick = { onIntent(AdminIntent.NextAuditPage) },
                    enabled = currentPage < totalFilteredPages - 1,
                ) {
                    Text("Next", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next page", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ─── Private composables ──────────────────────────────────────────────────────

/**
 * Material 3 exposed dropdown for filtering audit entries by [AuditEventType].
 * Selecting "All types" sets [selected] to null (clears filter).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventTypeDropdown(
    selected: AuditEventType?,
    onSelected: (AuditEventType?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected?.name?.replace('_', ' ') ?: "All event types",
            onValueChange = {},
            readOnly = true,
            label = { Text("Event type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            singleLine = true,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 300.dp),
        ) {
            DropdownMenuItem(
                text = { Text("All event types", style = MaterialTheme.typography.bodyMedium) },
                onClick = { onSelected(null); expanded = false },
            )
            AuditEventType.entries.forEach { type ->
                DropdownMenuItem(
                    text = {
                        Text(
                            type.name.replace('_', ' '),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    onClick = { onSelected(type); expanded = false },
                )
            }
        }
    }
}

/**
 * Material 3 exposed dropdown for filtering audit entries by [Role].
 * Selecting "All roles" sets [selected] to null (clears filter).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoleFilterDropdown(
    selected: Role?,
    onSelected: (Role?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected?.name?.replace('_', ' ') ?: "All roles",
            onValueChange = {},
            readOnly = true,
            label = { Text("User role") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            singleLine = true,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("All roles", style = MaterialTheme.typography.bodyMedium) },
                onClick = { onSelected(null); expanded = false },
            )
            Role.entries.forEach { role ->
                DropdownMenuItem(
                    text = {
                        Text(
                            role.name.replace('_', ' '),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    onClick = { onSelected(role); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun IntegrityBadge(
    report: IntegrityReport?,
    isVerifying: Boolean,
    onRefresh: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = ZyntaSpacing.xs),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isVerifying || report == null -> MaterialTheme.colorScheme.surfaceVariant
                report.isIntact -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.errorContainer
            },
        ),
        shape = MaterialTheme.shapes.small,
    ) {
        if (isVerifying) {
            Column(modifier = Modifier.fillMaxWidth().padding(ZyntaSpacing.sm)) {
                Text(
                    "Verifying audit chain…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = ZyntaSpacing.md, vertical = ZyntaSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
                ) {
                    Icon(
                        imageVector = if (report?.isIntact != false) Icons.Default.VerifiedUser else Icons.Default.GppBad,
                        contentDescription = null,
                        tint = when {
                            report == null -> MaterialTheme.colorScheme.onSurfaceVariant
                            report.isIntact -> MaterialTheme.colorScheme.onTertiaryContainer
                            else -> MaterialTheme.colorScheme.onErrorContainer
                        },
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = when {
                            report == null -> "Integrity not verified"
                            report.isIntact -> "Chain intact · ${report.totalEntries} entries"
                            else -> "⚠ ${report.violations} violation(s) · ${report.totalEntries} entries"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            report == null -> MaterialTheme.colorScheme.onSurfaceVariant
                            report.isIntact -> MaterialTheme.colorScheme.onTertiaryContainer
                            else -> MaterialTheme.colorScheme.onErrorContainer
                        },
                    )
                }
                IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Re-verify",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AuditEntryCard(entry: AuditEntry) {
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(ZyntaSpacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = iconForEventType(entry.eventType),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (entry.success) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(ZyntaSpacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        entry.eventType.name.replace('_', ' '),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "User: ${entry.userId}  ·  Device: ${entry.deviceId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    SuccessBadge(entry.success)
                    Text(
                        formatInstant(entry),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (entry.payload.isNotBlank() && entry.payload != "{}") {
                TextButton(
                    onClick = { expanded = !expanded },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(
                        if (expanded) "Hide payload" else "Show payload",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (expanded) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            entry.payload,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(ZyntaSpacing.sm),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuccessBadge(success: Boolean) {
    Surface(
        color = if (success) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text = if (success) "OK" else "FAIL",
            style = MaterialTheme.typography.labelSmall,
            color = if (success) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private fun formatInstant(entry: AuditEntry): String {
    return try {
        val ldt = entry.createdAt.toLocalDateTime(TimeZone.currentSystemDefault())
        "%02d:%02d:%02d".format(ldt.hour, ldt.minute, ldt.second)
    } catch (_: Exception) {
        "?"
    }
}

/**
 * Compact date range filter with "From" / "To" chips.
 * Uses Material 3 DatePickerDialog for selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeFilter(
    dateFrom: Instant?,
    dateTo: Instant?,
    onDateRangeChanged: (Instant?, Instant?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tz = TimeZone.currentSystemDefault()
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.DateRange,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // "From" chip
        FilterChip(
            selected = dateFrom != null,
            onClick = { showFromPicker = true },
            label = {
                Text(
                    text = dateFrom?.let {
                        val ld = it.toLocalDateTime(tz).date
                        "${ld.year}-%02d-%02d".format(ld.monthNumber, ld.dayOfMonth)
                    } ?: "From",
                    style = MaterialTheme.typography.labelSmall,
                )
            },
            trailingIcon = if (dateFrom != null) {
                {
                    IconButton(
                        onClick = { onDateRangeChanged(null, dateTo) },
                        modifier = Modifier.size(16.dp),
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear from date", modifier = Modifier.size(12.dp))
                    }
                }
            } else null,
        )

        // "To" chip
        FilterChip(
            selected = dateTo != null,
            onClick = { showToPicker = true },
            label = {
                Text(
                    text = dateTo?.let {
                        val ld = it.toLocalDateTime(tz).date
                        "${ld.year}-%02d-%02d".format(ld.monthNumber, ld.dayOfMonth)
                    } ?: "To",
                    style = MaterialTheme.typography.labelSmall,
                )
            },
            trailingIcon = if (dateTo != null) {
                {
                    IconButton(
                        onClick = { onDateRangeChanged(dateFrom, null) },
                        modifier = Modifier.size(16.dp),
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear to date", modifier = Modifier.size(12.dp))
                    }
                }
            } else null,
        )
    }

    // From date picker dialog
    if (showFromPicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showFromPicker = false
                    datePickerState.selectedDateMillis?.let { millis ->
                        onDateRangeChanged(Instant.fromEpochMilliseconds(millis), dateTo)
                    }
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showFromPicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // To date picker dialog
    if (showToPicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showToPicker = false
                    datePickerState.selectedDateMillis?.let { millis ->
                        // End of selected day (23:59:59.999)
                        val endOfDay = Instant.fromEpochMilliseconds(millis + 86_400_000L - 1L)
                        onDateRangeChanged(dateFrom, endOfDay)
                    }
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showToPicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun iconForEventType(type: AuditEventType) = when (type) {
    // Authentication
    AuditEventType.LOGIN_ATTEMPT      -> Icons.Default.Login
    AuditEventType.LOGOUT             -> Icons.Default.Logout
    AuditEventType.SESSION_TIMEOUT    -> Icons.Default.Timer
    AuditEventType.PIN_CHANGE         -> Icons.Default.Pin
    AuditEventType.PASSWORD_CHANGE    -> Icons.Default.Password
    // Authorization
    AuditEventType.PERMISSION_DENIED  -> Icons.Default.Block
    AuditEventType.ROLE_CHANGED       -> Icons.Default.ManageAccounts
    // POS Operations
    AuditEventType.ORDER_CREATED      -> Icons.Default.ShoppingCart
    AuditEventType.ORDER_VOIDED       -> Icons.Default.Cancel
    AuditEventType.ORDER_REFUNDED     -> Icons.Default.MoneyOff
    AuditEventType.DISCOUNT_APPLIED   -> Icons.Default.LocalOffer
    AuditEventType.PAYMENT_PROCESSED  -> Icons.Default.Payment
    AuditEventType.ORDER_HELD         -> Icons.Default.Pause
    AuditEventType.ORDER_RESUMED      -> Icons.Default.PlayArrow
    AuditEventType.PRICE_OVERRIDE     -> Icons.Default.Edit
    // Inventory
    AuditEventType.STOCK_ADJUSTED     -> Icons.Default.Inventory
    AuditEventType.PRODUCT_CREATED    -> Icons.Default.AddBox
    AuditEventType.PRODUCT_MODIFIED   -> Icons.Default.Edit
    AuditEventType.PRODUCT_DELETED    -> Icons.Default.Delete
    AuditEventType.STOCKTAKE_COMPLETED -> Icons.Default.FactCheck
    // Register
    AuditEventType.REGISTER_OPENED    -> Icons.Default.LockOpen
    AuditEventType.REGISTER_CLOSED    -> Icons.Default.Lock
    AuditEventType.CASH_IN            -> Icons.Default.Add
    AuditEventType.CASH_OUT           -> Icons.Default.Remove
    // User Management
    AuditEventType.USER_CREATED       -> Icons.Default.PersonAdd
    AuditEventType.USER_DEACTIVATED   -> Icons.Default.PersonOff
    AuditEventType.USER_REACTIVATED   -> Icons.Default.PersonAdd
    AuditEventType.CUSTOM_ROLE_MODIFIED -> Icons.Default.AdminPanelSettings
    // Financial
    AuditEventType.EXPENSE_APPROVED   -> Icons.Default.Receipt
    AuditEventType.JOURNAL_POSTED     -> Icons.Default.AccountBalance
    AuditEventType.TAX_CONFIG_CHANGED -> Icons.Default.Percent
    // System
    AuditEventType.SETTINGS_CHANGED   -> Icons.Default.Settings
    AuditEventType.BACKUP_CREATED     -> Icons.Default.Save
    AuditEventType.BACKUP_RESTORED    -> Icons.Default.Restore
    AuditEventType.DATA_EXPORTED      -> Icons.Default.FileDownload
    AuditEventType.DIAGNOSTIC_SESSION                 -> Icons.Default.BugReport
    AuditEventType.DIAGNOSTIC_SESSION_CONSENT_GRANTED -> Icons.Default.CheckCircle
    AuditEventType.DIAGNOSTIC_SESSION_REVOKED         -> Icons.Default.Cancel
    AuditEventType.DIAGNOSTIC_SESSION_EXPIRED         -> Icons.Default.Timer
    // Data
    AuditEventType.SYNC_COMPLETED     -> Icons.Default.Sync
    AuditEventType.SYNC_FAILED        -> Icons.Default.SyncProblem
    AuditEventType.DATA_PURGED        -> Icons.Default.DeleteSweep
}
