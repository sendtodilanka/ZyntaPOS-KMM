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
import com.zyntasolutions.zyntapos.designsystem.components.StringResolver
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
    val s = LocalStrings.current
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
        title = s[StringResource.SETTINGS_RBAC_ROLES_PERMISSIONS],
        onNavigateBack = onBack,
        floatingActionButton = {
            FloatingActionButton(onClick = { onIntent(SettingsIntent.OpenCreateCustomRole) }) {
                Icon(Icons.Default.Add, contentDescription = s[StringResource.SETTINGS_RBAC_CREATE_CUSTOM_ROLE])
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
                SectionLabel(s[StringResource.SETTINGS_RBAC_BUILTIN_ROLES])
            }

            if (state.isLoading && state.builtInRoles.isEmpty()) {
                item {
                    Text(
                        s[StringResource.COMMON_LOADING],
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
                SectionLabel(s[StringResource.SETTINGS_RBAC_CUSTOM_ROLES])
            }

            if (state.customRoles.isEmpty() && !state.isLoading) {
                item {
                    Text(
                        s[StringResource.SETTINGS_RBAC_NO_CUSTOM_ROLES],
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
    val s = LocalStrings.current
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
                Text(roleName(role, s), style = MaterialTheme.typography.bodyLarge)
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
                    contentDescription = if (expanded) s[StringResource.COMMON_COLLAPSE] else s[StringResource.COMMON_EXPAND],
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
                        Text(permissionLabel(perm, s), style = MaterialTheme.typography.bodyMedium)
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
                    Text(s[StringResource.SETTINGS_RBAC_RESET_DEFAULTS])
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
    val s = LocalStrings.current
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
                            contentDescription = s[StringResource.COMMON_EDIT],
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = s[StringResource.COMMON_DELETE],
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
    val s = LocalStrings.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ZyntaSpacing.md, vertical = ZyntaSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
    ) {
        Text(
            text = if (isEditing) s[StringResource.SETTINGS_RBAC_EDIT_CUSTOM_ROLE] else s[StringResource.SETTINGS_RBAC_CREATE_CUSTOM_ROLE],
            style = MaterialTheme.typography.titleLarge,
        )

        ZyntaTextField(
            value = form.name,
            onValueChange = onNameChange,
            label = s[StringResource.SETTINGS_RBAC_ROLE_NAME],
            modifier = Modifier.fillMaxWidth(),
        )

        ZyntaTextField(
            value = form.description,
            onValueChange = onDescChange,
            label = s[StringResource.SETTINGS_RBAC_DESCRIPTION_OPTIONAL],
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = s[StringResource.SETTINGS_RBAC_PERMISSIONS],
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
                    Text(permissionLabel(perm, s), style = MaterialTheme.typography.bodyMedium)
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
                text = s[StringResource.COMMON_CANCEL],
                onClick = onDismiss,
                variant = ZyntaButtonVariant.Ghost,
            )
            ZyntaButton(
                text = if (isEditing) s[StringResource.COMMON_UPDATE] else s[StringResource.COMMON_CREATE],
                onClick = onSave,
                isLoading = isLoading,
            )
        }

        Spacer(Modifier.height(ZyntaSpacing.lg))
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun roleName(role: Role, s: StringResolver): String = when (role) {
    Role.ADMIN         -> s[StringResource.SETTINGS_RBAC_ROLE_ADMIN]
    Role.STORE_MANAGER -> s[StringResource.SETTINGS_RBAC_ROLE_STORE_MANAGER]
    Role.CASHIER       -> s[StringResource.SETTINGS_RBAC_ROLE_CASHIER]
    Role.ACCOUNTANT    -> s[StringResource.SETTINGS_RBAC_ROLE_ACCOUNTANT]
    Role.STOCK_MANAGER -> s[StringResource.SETTINGS_RBAC_ROLE_STOCK_MANAGER]
}

private fun permissionLabel(permission: Permission, s: StringResolver): String = when (permission) {
    Permission.VIEW_REPORTS         -> s[StringResource.SETTINGS_RBAC_PERM_VIEW_REPORTS]
    Permission.EXPORT_REPORTS       -> s[StringResource.SETTINGS_RBAC_PERM_EXPORT_REPORTS]
    Permission.PROCESS_SALE         -> s[StringResource.SETTINGS_RBAC_PERM_PROCESS_SALE]
    Permission.VOID_ORDER           -> s[StringResource.SETTINGS_RBAC_PERM_VOID_ORDER]
    Permission.APPLY_DISCOUNT       -> s[StringResource.SETTINGS_RBAC_PERM_APPLY_DISCOUNT]
    Permission.HOLD_ORDER           -> s[StringResource.SETTINGS_RBAC_PERM_HOLD_ORDER]
    Permission.PROCESS_REFUND       -> s[StringResource.SETTINGS_RBAC_PERM_PROCESS_REFUND]
    Permission.OPEN_REGISTER        -> s[StringResource.SETTINGS_RBAC_PERM_OPEN_REGISTER]
    Permission.CLOSE_REGISTER       -> s[StringResource.SETTINGS_RBAC_PERM_CLOSE_REGISTER]
    Permission.RECORD_CASH_MOVEMENT -> s[StringResource.SETTINGS_RBAC_PERM_RECORD_CASH_MOVEMENT]
    Permission.MANAGE_PRODUCTS      -> s[StringResource.SETTINGS_RBAC_PERM_MANAGE_PRODUCTS]
    Permission.MANAGE_CATEGORIES    -> s[StringResource.SETTINGS_RBAC_PERM_MANAGE_CATEGORIES]
    Permission.ADJUST_STOCK         -> s[StringResource.SETTINGS_RBAC_PERM_ADJUST_STOCK]
    Permission.MANAGE_SUPPLIERS     -> s[StringResource.SETTINGS_RBAC_PERM_MANAGE_SUPPLIERS]
    Permission.MANAGE_USERS         -> s[StringResource.SETTINGS_RBAC_PERM_MANAGE_USERS]
    Permission.MANAGE_SETTINGS      -> s[StringResource.SETTINGS_RBAC_PERM_MANAGE_SETTINGS]
    Permission.MANAGE_TAX           -> s[StringResource.SETTINGS_RBAC_PERM_MANAGE_TAX]
    Permission.MANAGE_HARDWARE      -> s[StringResource.SETTINGS_RBAC_PERM_MANAGE_HARDWARE]
    Permission.MANAGE_CUSTOMERS     -> s[StringResource.SETTINGS_RBAC_PERM_MANAGE_CUSTOMERS]
    Permission.VIEW_AUDIT_LOG       -> s[StringResource.SETTINGS_RBAC_PERM_VIEW_AUDIT_LOG]
    Permission.MANAGE_BACKUP        -> s[StringResource.SETTINGS_RBAC_PERM_MANAGE_BACKUP]
    Permission.MANAGE_CUSTOMER_GROUPS -> s[StringResource.SETTINGS_RBAC_PERM_MANAGE_CUSTOMER_GROUPS]
    Permission.MANAGE_WALLETS       -> s[StringResource.SETTINGS_RBAC_PERM_MANAGE_WALLETS]
    Permission.MANAGE_LOYALTY       -> s[StringResource.SETTINGS_RBAC_PERM_MANAGE_LOYALTY]
    Permission.MANAGE_COUPONS       -> s[StringResource.SETTINGS_RBAC_PERM_MANAGE_COUPONS]
    Permission.MANAGE_EXPENSES      -> s[StringResource.SETTINGS_RBAC_PERM_MANAGE_EXPENSES]
    Permission.APPROVE_EXPENSES     -> s[StringResource.SETTINGS_RBAC_PERM_APPROVE_EXPENSES]
    Permission.MANAGE_WAREHOUSES    -> s[StringResource.SETTINGS_RBAC_PERM_MANAGE_WAREHOUSES]
    Permission.MANAGE_STOCK_TRANSFERS -> s[StringResource.SETTINGS_RBAC_PERM_MANAGE_STOCK_TRANSFERS]
    Permission.MANAGE_STAFF         -> s[StringResource.SETTINGS_RBAC_PERM_MANAGE_STAFF]
    Permission.ADMIN_ACCESS         -> s[StringResource.SETTINGS_RBAC_PERM_ADMIN_ACCESS]
    Permission.MANAGE_ACCOUNTING    -> s[StringResource.SETTINGS_RBAC_PERM_MANAGE_ACCOUNTING]
    Permission.PRINT_INVOICE        -> s[StringResource.SETTINGS_RBAC_PERM_PRINT_INVOICE]
    Permission.MANAGE_STOCKTAKE     -> s[StringResource.SETTINGS_RBAC_PERM_MANAGE_STOCKTAKE]
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
