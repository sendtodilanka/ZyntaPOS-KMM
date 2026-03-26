package com.zyntasolutions.zyntapos.feature.staff

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.SalaryType

/**
 * Employee create / edit screen.
 *
 * Renders form fields bound to [StaffState.employeeForm] and delegates all
 * mutations through [StaffIntent]. Navigation back to the list is triggered
 * via [StaffIntent.BackToEmployeeList].
 *
 * @param state       Current [StaffState].
 * @param onIntent    Dispatches intents to [StaffViewModel].
 * @param onNavigateBack Navigation callback to pop back to the list.
 * @param modifier    Optional root modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeDetailScreen(
    state: StaffState,
    onIntent: (StaffIntent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val form = state.employeeForm
    val isEditing = form.isEditing
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(if (isEditing) s[StringResource.STAFF_EDIT_EMPLOYEE] else s[StringResource.STAFF_NEW_EMPLOYEE])
                },
                navigationIcon = {
                    IconButton(onClick = {
                        onIntent(StaffIntent.BackToEmployeeList)
                        onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s[StringResource.COMMON_BACK])
                    }
                },
                actions = {
                    if (isEditing) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = s[StringResource.STAFF_DELETE_EMPLOYEE],
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = ZyntaSpacing.md)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
        ) {
            Spacer(Modifier.height(ZyntaSpacing.sm))

            // ── Personal Info ──────────────────────────────────────────────
            SectionHeader(s[StringResource.STAFF_PERSONAL_INFO])

            Row(horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                OutlinedTextField(
                    value = form.firstName,
                    onValueChange = { onIntent(StaffIntent.UpdateEmployeeField("firstName", it)) },
                    label = { Text(s[StringResource.STAFF_FIRST_NAME]) },
                    isError = form.validationErrors.containsKey("firstName"),
                    supportingText = form.validationErrors["firstName"]?.let { { Text(it) } },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = form.lastName,
                    onValueChange = { onIntent(StaffIntent.UpdateEmployeeField("lastName", it)) },
                    label = { Text(s[StringResource.STAFF_LAST_NAME]) },
                    isError = form.validationErrors.containsKey("lastName"),
                    supportingText = form.validationErrors["lastName"]?.let { { Text(it) } },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }

            OutlinedTextField(
                value = form.email,
                onValueChange = { onIntent(StaffIntent.UpdateEmployeeField("email", it)) },
                label = { Text(s[StringResource.CUSTOMERS_EMAIL]) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = form.phone,
                onValueChange = { onIntent(StaffIntent.UpdateEmployeeField("phone", it)) },
                label = { Text(s[StringResource.CUSTOMERS_PHONE]) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            // ── Employment Info ────────────────────────────────────────────
            SectionHeader(s[StringResource.STAFF_EMPLOYMENT])

            OutlinedTextField(
                value = form.position,
                onValueChange = { onIntent(StaffIntent.UpdateEmployeeField("position", it)) },
                label = { Text(s[StringResource.STAFF_POSITION_REQUIRED]) },
                isError = form.validationErrors.containsKey("position"),
                supportingText = form.validationErrors["position"]?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = form.department,
                onValueChange = { onIntent(StaffIntent.UpdateEmployeeField("department", it)) },
                label = { Text(s[StringResource.STAFF_DEPARTMENT]) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = form.hireDate,
                onValueChange = { onIntent(StaffIntent.UpdateEmployeeField("hireDate", it)) },
                label = { Text(s[StringResource.STAFF_HIRE_DATE]) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            // ── Compensation ───────────────────────────────────────────────
            SectionHeader(s[StringResource.STAFF_COMPENSATION])

            Row(horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                OutlinedTextField(
                    value = form.salary,
                    onValueChange = { onIntent(StaffIntent.UpdateEmployeeField("salary", it)) },
                    label = { Text(s[StringResource.STAFF_SALARY]) },
                    isError = form.validationErrors.containsKey("salary"),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = form.commissionRate,
                    onValueChange = { onIntent(StaffIntent.UpdateEmployeeField("commissionRate", it)) },
                    label = { Text(s[StringResource.STAFF_COMMISSION]) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }

            // Salary type selector
            Text(s[StringResource.STAFF_SALARY_TYPE], style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SalaryType.entries.forEach { type ->
                    FilterChip(
                        selected = form.salaryType == type,
                        onClick = { onIntent(StaffIntent.UpdateEmployeeSalaryType(type)) },
                        label = { Text(type.name) },
                    )
                }
            }

            // ── Status ────────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = s[StringResource.STAFF_ACTIVE],
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = form.isActive,
                    onCheckedChange = { onIntent(StaffIntent.ToggleEmployeeActive) },
                )
            }

            Spacer(Modifier.height(ZyntaSpacing.sm))

            // ── Store Assignments (C3.4 — only for existing employees) ─────
            if (isEditing) {
                OutlinedButton(
                    onClick = {
                        form.id?.let { onIntent(StaffIntent.NavigateToEmployeeStores(it)) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Store, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(s[StringResource.STAFF_MANAGE_STORE_ASSIGNMENTS])
                }
                Spacer(Modifier.height(ZyntaSpacing.sm))
            }

            // ── Save Button ────────────────────────────────────────────────
            Button(
                onClick = { onIntent(StaffIntent.SaveEmployee) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading,
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isEditing) s[StringResource.STAFF_UPDATE_EMPLOYEE] else s[StringResource.STAFF_CREATE_EMPLOYEE])
                }
            }

            Spacer(Modifier.height(ZyntaSpacing.lg))
        }

        // Delete confirmation dialog
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text(s[StringResource.STAFF_DEACTIVATE_EMPLOYEE_TITLE]) },
                text = { Text(s[StringResource.STAFF_DEACTIVATE_EMPLOYEE_MESSAGE]) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirm = false
                            form.id?.let { onIntent(StaffIntent.DeleteEmployee(it)) }
                            onNavigateBack()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text(s[StringResource.STAFF_DEACTIVATE])
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) { Text(s[StringResource.COMMON_CANCEL]) }
                },
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = ZyntaSpacing.sm),
    )
    HorizontalDivider()
}
