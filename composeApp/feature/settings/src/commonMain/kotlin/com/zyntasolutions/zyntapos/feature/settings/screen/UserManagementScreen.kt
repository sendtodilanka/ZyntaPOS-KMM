package com.zyntasolutions.zyntapos.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaBottomSheet
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaEmptyState
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButton
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaTable
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaTableColumn
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaTextField
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaPageScaffold
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.CustomRole
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.feature.settings.SettingsEffect
import com.zyntasolutions.zyntapos.feature.settings.SettingsIntent
import com.zyntasolutions.zyntapos.feature.settings.SettingsState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

// ─────────────────────────────────────────────────────────────────────────────
// UserManagementScreen — ZyntaTable of users, create/edit slide-over,
//                        gated by ADMIN role via RoleGuard.
// Sprint 23 — Step 13.1.6
// ─────────────────────────────────────────────────────────────────────────────

private val USER_COLUMNS = listOf(
    ZyntaTableColumn(key = "name",   header = "Name",   weight = 2f),
    ZyntaTableColumn(key = "email",  header = "Email",  weight = 2f),
    ZyntaTableColumn(key = "role",   header = "Role",   weight = 1f),
    ZyntaTableColumn(key = "status", header = "Status", weight = 1f),
    ZyntaTableColumn(key = "action", header = "",       weight = 1f, sortable = false),
)

/**
 * User management screen — list, create, edit and deactivate staff accounts.
 *
 * Rendering is gated by [RoleGuard]; only ADMIN-role users can reach this screen.
 *
 * @param state     Current [SettingsState.UserState] slice.
 * @param effects   Shared [SettingsEffect] flow.
 * @param onIntent  Dispatch callback.
 * @param onBack    Back navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementScreen(
    state: SettingsState.UserState,
    effects: Flow<SettingsEffect>,
    onIntent: (SettingsIntent) -> Unit,
    onBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { onIntent(SettingsIntent.LoadUsers) }

    LaunchedEffect(effects) {
        effects.collectLatest { effect ->
            when (effect) {
                SettingsEffect.UserSaved -> snackbarHostState.showSnackbar("User saved.")
                is SettingsEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                else -> Unit
            }
        }
    }

    // ── User form slide-over ──────────────────────────────────────────────────
    val showForm = state.isCreating || state.editingUser != null
    if (showForm) {
        UserFormSheet(
            isCreating = state.isCreating,
            form = state.form,
            saveError = state.saveError,
            availableCustomRoles = state.availableCustomRoles,
            onIntent = onIntent,
            onDismiss = { onIntent(SettingsIntent.DismissUserForm) },
        )
    }

    ZyntaPageScaffold(
        title = "User Management",
        onNavigateBack = onBack,
        snackbarHostState = snackbarHostState,
        floatingActionButton = {
            FloatingActionButton(onClick = { onIntent(SettingsIntent.OpenCreateUser) }) {
                Icon(Icons.Filled.Add, contentDescription = "Add User")
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = ZyntaSpacing.md,
                    end = ZyntaSpacing.md,
                    top = innerPadding.calculateTopPadding() + ZyntaSpacing.md,
                    bottom = innerPadding.calculateBottomPadding() + ZyntaSpacing.md,
                ),
        ) {
            when {
                state.isLoading -> Text(
                    "Loading users…",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyMedium,
                )
                state.users.isEmpty() -> ZyntaEmptyState(
                    title = "No users found",
                    icon = Icons.Filled.Add,
                    subtitle = "Tap + to create one.",
                )
                else -> ZyntaTable(
                    columns = USER_COLUMNS,
                    items = state.users,
                    rowKey = { it.id },
                    modifier = Modifier.fillMaxSize(),
                    rowContent = { u ->
                        Text(u.name,  modifier = Modifier.weight(2f),
                            style = MaterialTheme.typography.bodyMedium)
                        Text(u.email, modifier = Modifier.weight(2f),
                            style = MaterialTheme.typography.bodyMedium)
                        Text(u.role.name, modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium)
                        Text(if (u.isActive) "Active" else "Inactive",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium)
                        ZyntaButton(
                            text = "Edit",
                            onClick = { onIntent(SettingsIntent.OpenEditUser(u)) },
                            modifier = Modifier.weight(1f),
                        )
                    },
                )
            }
        }
    }
}

// ─── UserFormSheet ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserFormSheet(
    isCreating: Boolean,
    form: SettingsState.UserState.UserForm,
    saveError: String?,
    availableCustomRoles: List<CustomRole>,
    onIntent: (SettingsIntent) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ZyntaBottomSheet(
        sheetState = sheetState,
        onDismiss = onDismiss,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ZyntaSpacing.md)
                .padding(bottom = ZyntaSpacing.lg),
        ) {
            Text(
                text = if (isCreating) "Create User" else "Edit User",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = ZyntaSpacing.sm),
            )
            ZyntaTextField(
                value = form.name,
                onValueChange = { onIntent(SettingsIntent.UpdateUserFormName(it)) },
                label = "Full Name",
                modifier = Modifier.fillMaxWidth(),
            )
            ZyntaTextField(
                value = form.email,
                onValueChange = { onIntent(SettingsIntent.UpdateUserFormEmail(it)) },
                label = "Email Address",
                modifier = Modifier.fillMaxWidth(),
                enabled = isCreating, // email is immutable once created
            )
            if (isCreating) {
                ZyntaTextField(
                    value = form.password,
                    onValueChange = { onIntent(SettingsIntent.UpdateUserFormPassword(it)) },
                    label = "Password",
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
            // ── Role dropdown — built-in roles followed by custom roles ──────
            var roleDropdownExpanded by remember { mutableStateOf(false) }
            val selectedRoleLabel = availableCustomRoles
                .firstOrNull { it.id == form.roleKey }?.name
                ?: Role.entries.firstOrNull { it.name == form.roleKey }?.name
                ?: form.roleKey
            ExposedDropdownMenuBox(
                expanded = roleDropdownExpanded,
                onExpandedChange = { roleDropdownExpanded = !roleDropdownExpanded },
                modifier = Modifier.fillMaxWidth(),
            ) {
                ZyntaTextField(
                    value = selectedRoleLabel,
                    onValueChange = {},
                    label = "Role",
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleDropdownExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = roleDropdownExpanded,
                    onDismissRequest = { roleDropdownExpanded = false },
                ) {
                    Role.entries.filter { it != Role.ADMIN }.forEach { role ->
                        DropdownMenuItem(
                            text = { Text(role.name) },
                            onClick = {
                                onIntent(SettingsIntent.UpdateUserFormRole(role))
                                roleDropdownExpanded = false
                            },
                        )
                    }
                    availableCustomRoles.forEach { customRole ->
                        DropdownMenuItem(
                            text = { Text(customRole.name) },
                            onClick = {
                                onIntent(SettingsIntent.UpdateUserFormRoleKey(customRole.id))
                                roleDropdownExpanded = false
                            },
                        )
                    }
                }
            }
            ToggleRow(
                label = "Account Active",
                checked = form.isActive,
                onCheckedChange = { onIntent(SettingsIntent.UpdateUserFormActive(it)) },
            )
            // ── Quick-Switch PIN section ──────────────────────────────────────
            Text(
                text = "Quick-Switch PIN",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = ZyntaSpacing.md),
            )
            Text(
                text = "Leave blank to keep existing PIN",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ZyntaTextField(
                value = form.newPin,
                onValueChange = { onIntent(SettingsIntent.UpdateUserFormPin(it)) },
                label = "New PIN (4–6 digits)",
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )
            ZyntaTextField(
                value = form.confirmPin,
                onValueChange = { onIntent(SettingsIntent.UpdateUserFormConfirmPin(it)) },
                label = "Confirm PIN",
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )
            form.pinError?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            saveError?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
            ZyntaButton(
                text = "Save User",
                onClick = { onIntent(SettingsIntent.SaveUser) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@androidx.compose.ui.tooling.preview.Preview
@androidx.compose.runtime.Composable
private fun UserManagementScreenEmptyPreview() {
    com.zyntasolutions.zyntapos.designsystem.theme.ZyntaTheme {
        UserManagementScreen(
            state = SettingsState.UserState(),
            effects = kotlinx.coroutines.flow.emptyFlow(),
            onIntent = {},
            onBack = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview
@androidx.compose.runtime.Composable
private fun UserManagementScreenLoadedPreview() {
    com.zyntasolutions.zyntapos.designsystem.theme.ZyntaTheme {
        UserManagementScreen(
            state = SettingsState.UserState(
                users = listOf(
                    com.zyntasolutions.zyntapos.domain.model.User(
                        id = "u1", name = "Alice Admin", email = "alice@zyntapos.com",
                        role = com.zyntasolutions.zyntapos.domain.model.Role.ADMIN,
                        storeId = "s1", isActive = true,
                        createdAt = kotlinx.datetime.Instant.fromEpochMilliseconds(0L),
                        updatedAt = kotlinx.datetime.Instant.fromEpochMilliseconds(0L),
                    ),
                    com.zyntasolutions.zyntapos.domain.model.User(
                        id = "u2", name = "Bob Cashier", email = "bob@zyntapos.com",
                        role = com.zyntasolutions.zyntapos.domain.model.Role.CASHIER,
                        storeId = "s1", isActive = true,
                        createdAt = kotlinx.datetime.Instant.fromEpochMilliseconds(0L),
                        updatedAt = kotlinx.datetime.Instant.fromEpochMilliseconds(0L),
                    ),
                ),
            ),
            effects = kotlinx.coroutines.flow.emptyFlow(),
            onIntent = {},
            onBack = {},
        )
    }
}
