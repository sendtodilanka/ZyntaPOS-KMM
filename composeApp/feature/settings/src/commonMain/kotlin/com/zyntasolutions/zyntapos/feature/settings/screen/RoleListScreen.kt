package com.zyntasolutions.zyntapos.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.components.StringResolver
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaPageScaffold
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.CustomRole
import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.feature.settings.SettingsIntent
import com.zyntasolutions.zyntapos.feature.settings.SettingsState

/**
 * Read-only role catalog (Sprint 23 task 23.4).
 *
 * Renders two grouped sections:
 *  1. **System Roles** — every value of the [Role] enum (`ADMIN`,
 *     `STORE_MANAGER`, `CASHIER`, `ACCOUNTANT`, `STOCK_MANAGER`) shown as
 *     non-editable cards with their effective permission count.
 *     ADMIN's count is `Permission.entries.size` (full access); the other
 *     four show the override-or-default set already loaded into
 *     `SettingsState.RbacState.builtInRoles`.
 *  2. **Custom Roles** — the live `state.rbac.customRoles` list. Each card
 *     displays the role's name + permission count.
 *
 * Edit, delete, and "Create Custom Role" affordances land in Sprint 23
 * task 23.5 (`RoleEditorScreen`) — this screen is the read-only foundation
 * the editor will navigate from.
 *
 * State + load orchestration come from the existing
 * `SettingsViewModel.loadRbac()` path (intent: `SettingsIntent.LoadRbac`).
 *
 * @param state    Slice of [SettingsState.RbacState] holding built-in role
 *                 overrides + reactive custom roles.
 * @param onIntent Pipe back to `SettingsViewModel.dispatch` so the caller
 *                 can fire `LoadRbac` when the screen first mounts.
 * @param onBack   Back navigation handler.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun RoleListScreen(
    state: SettingsState.RbacState,
    onIntent: (SettingsIntent) -> Unit,
    onBack: () -> Unit,
    onCreateRole: () -> Unit = {},
    onEditRole: (roleId: String) -> Unit = {},
    onCloneRole: (CustomRole) -> Unit = {},
) {
    val s = LocalStrings.current
    LaunchedEffect(Unit) { onIntent(SettingsIntent.LoadRbac) }

    ZyntaPageScaffold(
        title = s[StringResource.SETTINGS_ROLES_PERMISSIONS],
        onNavigateBack = onBack,
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateRole) {
                Icon(Icons.Default.Add, contentDescription = s[StringResource.RBAC_CREATE_ROLE])
            }
        },
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = ZyntaSpacing.md,
                end = ZyntaSpacing.md,
                top = innerPadding.calculateTopPadding() + ZyntaSpacing.md,
                bottom = innerPadding.calculateBottomPadding() + ZyntaSpacing.md,
            ),
            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
        ) {
            item {
                SectionLabel(s[StringResource.RBAC_SYSTEM_ROLES])
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SystemRoleRow(
                        role = Role.ADMIN,
                        permissionCount = Permission.entries.size,
                        labelStrings = s,
                    )
                    state.builtInRoles.forEach { (role, permissions) ->
                        SystemRoleRow(role = role, permissionCount = permissions.size, labelStrings = s)
                    }
                }
            }

            item {
                SectionLabel(s[StringResource.RBAC_CUSTOM_ROLES])
            }
            if (state.customRoles.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    s[StringResource.COMMON_NO_DATA],
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                        )
                    }
                }
            } else {
                items(state.customRoles, key = CustomRole::id) { role ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        CustomRoleRow(
                            role = role,
                            labelStrings = s,
                            onEdit = { onEditRole(role.id) },
                            onDelete = { onIntent(SettingsIntent.DeleteCustomRole(role.id)) },
                            onClone = { onCloneRole(role) },
                        )
                    }
                }
            }

        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SystemRoleRow(
    role: Role,
    permissionCount: Int,
    labelStrings: StringResolver,
) {
    val icon: ImageVector = when (role) {
        Role.ADMIN -> Icons.Default.AdminPanelSettings
        else -> Icons.Default.Verified
    }
    ListItem(
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        headlineContent = {
            Text(role.name, style = MaterialTheme.typography.bodyLarge)
        },
        supportingContent = {
            Text(
                labelStrings[StringResource.RBAC_PERMISSION_COUNT, permissionCount],
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CustomRoleRow(
    role: CustomRole,
    labelStrings: StringResolver,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onClone: () -> Unit,
) {
    ListItem(
        leadingContent = {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        headlineContent = {
            Text(role.name, style = MaterialTheme.typography.bodyLarge)
        },
        supportingContent = {
            Column {
                Text(
                    labelStrings[StringResource.RBAC_PERMISSION_COUNT, role.permissions.size],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (role.description.isNotBlank()) {
                    Text(
                        role.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        trailingContent = {
            androidx.compose.foundation.layout.Row {
                IconButton(onClick = onClone) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = labelStrings[StringResource.RBAC_CLONE_ROLE],
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = labelStrings[StringResource.RBAC_EDIT_ROLE],
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = labelStrings[StringResource.RBAC_DELETE_ROLE],
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    )
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
