package com.zyntasolutions.zyntapos.feature.settings.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaBottomSheet
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButton
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButtonVariant
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaTextField
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaPageScaffold
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.CustomRole
import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.feature.settings.SettingsEffect
import com.zyntasolutions.zyntapos.feature.settings.SettingsIntent
import com.zyntasolutions.zyntapos.feature.settings.SettingsState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

// ─────────────────────────────────────────────────────────────────────────────
// RbacManagementScreen — Admin role and permission management.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * RBAC Management screen.
 *
 * Allows the ADMIN to:
 * - View and toggle permissions for built-in roles (STORE_MANAGER, CASHIER, ACCOUNTANT, STOCK_MANAGER).
 * - Reset built-in roles to their static defaults.
 * - Create, edit, and delete custom roles with an arbitrary permission set.
 *
 * @param state    RBAC state slice from [SettingsViewModel].
 * @param effects  One-shot effects from [SettingsViewModel].
 * @param onIntent Intent dispatcher to [SettingsViewModel].
 * @param onBack   Back navigation callback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RbacManagementScreen(
    state: SettingsState.RbacState,
    effects: Flow<SettingsEffect>,
    onIntent: (SettingsIntent) -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(Unit) { onIntent(SettingsIntent.LoadRbac) }

    // Consume effects (RoleSaved / RoleDeleted → dismiss sheet)
    LaunchedEffect(effects) {
        effects.onEach { effect ->
            when (effect) {
                is SettingsEffect.RoleSaved,
                is SettingsEffect.RoleDeleted -> onIntent(SettingsIntent.DismissCustomRoleForm)
                else -> Unit
            }
        }.launchIn(this)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ZyntaPageScaffold(
        title = "Roles & Permissions",
        onNavigateBack = onBack,
        floatingActionButton = {
            FloatingActionButton(onClick = { onIntent(SettingsIntent.OpenCreateCustomRole) }) {
                Icon(Icons.Default.Add, contentDescription = "Create custom role")
            }
        },
    ) { innerPadding ->
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = ZyntaSpacing.md,
                end = ZyntaSpacing.md,
                top = innerPadding.calculateTopPadding() + ZyntaSpacing.md,
                bottom = innerPadding.calculateBottomPadding() + ZyntaSpacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
        ) {
            // ── Built-in Roles ────────────────────────────────────────────────
            item {
                SectionLabel("Built-in Roles")
            }

            if (state.isLoading && state.builtInRoles.isEmpty()) {
                item {
                    Text(
                        "Loading…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(ZyntaSpacing.sm),
                    )
                }
            } else {
                items(state.builtInRoles, key = { it.first.name }) { (role, permissions) ->
                    BuiltInRoleCard(
                        role = role,
                        permissions = permissions,
                        onTogglePermission = { perm ->
                            onIntent(SettingsIntent.ToggleBuiltInRolePermission(role, perm))
                        },
                        onReset = { onIntent(SettingsIntent.ResetBuiltInRolePermissions(role)) },
                    )
                }
            }

            // ── Custom Roles ──────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(ZyntaSpacing.sm))
                SectionLabel("Custom Roles")
            }

            if (state.customRoles.isEmpty() && !state.isLoading) {
                item {
                    Text(
                        "No custom roles yet. Tap + to create one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(ZyntaSpacing.sm),
                    )
                }
            } else {
                items(state.customRoles, key = { it.id }) { role ->
                    CustomRoleCard(
                        role = role,
                        onEdit = { onIntent(SettingsIntent.OpenEditCustomRole(role)) },
                        onDelete = { onIntent(SettingsIntent.DeleteCustomRole(role.id)) },
                    )
                }
            }
        }
    }

    // ── Custom Role Form (bottom sheet) ───────────────────────────────────────
    val showSheet = state.isCreatingCustomRole || state.editingCustomRole != null
    if (showSheet) {
        ZyntaBottomSheet(
            sheetState = sheetState,
            onDismiss = { onIntent(SettingsIntent.DismissCustomRoleForm) },
        ) {
            CustomRoleFormContent(
                form = state.roleForm,
                isEditing = state.editingCustomRole != null,
                isLoading = state.isLoading,
                saveError = state.saveError,
                onNameChange = { onIntent(SettingsIntent.UpdateCustomRoleFormName(it)) },
                onDescChange = { onIntent(SettingsIntent.UpdateCustomRoleFormDescription(it)) },
                onTogglePermission = { onIntent(SettingsIntent.ToggleCustomRolePermission(it)) },
                onSave = { onIntent(SettingsIntent.SaveCustomRole) },
                onDismiss = { onIntent(SettingsIntent.DismissCustomRoleForm) },
            )
        }
    }
}

// ── BuiltInRoleCard ───────────────────────────────────────────────────────────

@Composable
private fun BuiltInRoleCard(
    role: Role,
    permissions: Set<Permission>,
    onTogglePermission: (Permission) -> Unit,
    onReset: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Header row
        ListItem(
            headlineContent = {
                Text(roleName(role), style = MaterialTheme.typography.bodyLarge)
            },
            supportingContent = {
                Text(
                    "${permissions.size} permissions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingContent = {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
        )

        if (expanded) {
            HorizontalDivider()
            // Permission toggles
            Permission.entries.forEach { perm ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTogglePermission(perm) }
                        .padding(horizontal = ZyntaSpacing.md, vertical = ZyntaSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
                ) {
                    Checkbox(
                        checked = perm in permissions,
                        onCheckedChange = { onTogglePermission(perm) },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(permissionLabel(perm), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            HorizontalDivider()
            // Reset row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ZyntaSpacing.md, vertical = ZyntaSpacing.xs),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onReset) {
                    Text("Reset to defaults")
                }
            }
        }
    }
}

// ── CustomRoleCard ────────────────────────────────────────────────────────────

@Composable
private fun CustomRoleCard(
    role: CustomRole,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ListItem(
            headlineContent = {
                Text(role.name, style = MaterialTheme.typography.bodyLarge)
            },
            supportingContent = {
                val desc = role.description.ifBlank { null }
                val permText = "${role.permissions.size} permissions"
                Text(
                    if (desc != null) "$desc · $permText" else permText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingContent = {
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit ${role.name}",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete ${role.name}",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        )
    }
}

// ── CustomRoleFormContent ─────────────────────────────────────────────────────

@Composable
private fun CustomRoleFormContent(
    form: SettingsState.RbacState.CustomRoleForm,
    isEditing: Boolean,
    isLoading: Boolean,
    saveError: String?,
    onNameChange: (String) -> Unit,
    onDescChange: (String) -> Unit,
    onTogglePermission: (Permission) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ZyntaSpacing.md, vertical = ZyntaSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
    ) {
        Text(
            text = if (isEditing) "Edit Custom Role" else "Create Custom Role",
            style = MaterialTheme.typography.titleLarge,
        )

        ZyntaTextField(
            value = form.name,
            onValueChange = onNameChange,
            label = "Role Name",
            modifier = Modifier.fillMaxWidth(),
        )

        ZyntaTextField(
            value = form.description,
            onValueChange = onDescChange,
            label = "Description (optional)",
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = "Permissions",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )

        // Scrollable permission list inside the bottom sheet content
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Permission.entries.forEach { perm ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTogglePermission(perm) }
                        .padding(vertical = ZyntaSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
                ) {
                    Checkbox(
                        checked = perm in form.selectedPermissions,
                        onCheckedChange = { onTogglePermission(perm) },
                    )
                    Text(permissionLabel(perm), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (saveError != null) {
            Text(
                text = saveError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm, Alignment.End),
        ) {
            ZyntaButton(
                text = "Cancel",
                onClick = onDismiss,
                variant = ZyntaButtonVariant.Ghost,
            )
            ZyntaButton(
                text = if (isEditing) "Update" else "Create",
                onClick = onSave,
                isLoading = isLoading,
            )
        }

        Spacer(Modifier.height(ZyntaSpacing.lg))
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun roleName(role: Role): String = when (role) {
    Role.ADMIN         -> "Admin"
    Role.STORE_MANAGER -> "Store Manager"
    Role.CASHIER       -> "Cashier"
    Role.ACCOUNTANT    -> "Accountant"
    Role.STOCK_MANAGER -> "Stock Manager"
}

private fun permissionLabel(permission: Permission): String = when (permission) {
    Permission.VIEW_REPORTS         -> "View Reports"
    Permission.EXPORT_REPORTS       -> "Export Reports"
    Permission.PROCESS_SALE         -> "Process Sale"
    Permission.VOID_ORDER           -> "Void Order"
    Permission.APPLY_DISCOUNT       -> "Apply Discount"
    Permission.HOLD_ORDER           -> "Hold Order"
    Permission.PROCESS_REFUND       -> "Process Refund"
    Permission.OPEN_REGISTER        -> "Open Register"
    Permission.CLOSE_REGISTER       -> "Close Register"
    Permission.RECORD_CASH_MOVEMENT -> "Record Cash Movement"
    Permission.MANAGE_PRODUCTS      -> "Manage Products"
    Permission.MANAGE_CATEGORIES    -> "Manage Categories"
    Permission.ADJUST_STOCK         -> "Adjust Stock"
    Permission.MANAGE_SUPPLIERS     -> "Manage Suppliers"
    Permission.MANAGE_USERS         -> "Manage Users"
    Permission.MANAGE_SETTINGS      -> "Manage Settings"
    Permission.MANAGE_TAX           -> "Manage Tax"
    Permission.MANAGE_HARDWARE      -> "Manage Hardware"
    Permission.MANAGE_CUSTOMERS     -> "Manage Customers"
    Permission.VIEW_AUDIT_LOG       -> "View Audit Log"
    Permission.MANAGE_BACKUP        -> "Manage Backup"
    Permission.MANAGE_CUSTOMER_GROUPS -> "Manage Customer Groups"
    Permission.MANAGE_WALLETS       -> "Manage Wallets"
    Permission.MANAGE_LOYALTY       -> "Manage Loyalty"
    Permission.MANAGE_COUPONS       -> "Manage Coupons"
    Permission.MANAGE_EXPENSES      -> "Manage Expenses"
    Permission.APPROVE_EXPENSES     -> "Approve Expenses"
    Permission.MANAGE_WAREHOUSES    -> "Manage Warehouses"
    Permission.MANAGE_STOCK_TRANSFERS -> "Manage Stock Transfers"
    Permission.MANAGE_STAFF         -> "Manage Staff"
    Permission.ADMIN_ACCESS         -> "Admin Access"
    Permission.MANAGE_ACCOUNTING    -> "Manage Accounting"
    Permission.PRINT_INVOICE        -> "Print Invoice"
    Permission.MANAGE_STOCKTAKE     -> "Manage Stocktake"
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = ZyntaSpacing.xs, bottom = ZyntaSpacing.xs),
    )
}
