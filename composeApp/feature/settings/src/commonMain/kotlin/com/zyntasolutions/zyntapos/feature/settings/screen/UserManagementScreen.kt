package com.zyntasolutions.zyntapos.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaBottomSheet
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButton
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaTable
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaTableColumn
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaTextField
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaPageScaffold
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
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
                when {
                    state.isLoading -> Text(
                        "Loading users…",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    state.users.isEmpty() -> Text(
                        "No users found. Tap + to create one.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    else -> ZyntaTable(
                        columns = USER_COLUMNS,
                        items = state.users,
                        rowKey = { it.id },
                        modifier = Modifier.fillMaxWidth(),
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
}

// ─── UserFormSheet ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserFormSheet(
    isCreating: Boolean,
    form: SettingsState.UserState.UserForm,
    saveError: String?,
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
            DropdownField(
                label = "Role",
                options = Role.entries.map { it.name },
                selectedIndex = Role.entries.indexOfFirst { it.name == form.roleKey }.coerceAtLeast(0),
                onSelect = { onIntent(SettingsIntent.UpdateUserFormRole(Role.entries[it])) },
            )
            ToggleRow(
                label = "Account Active",
                checked = form.isActive,
                onCheckedChange = { onIntent(SettingsIntent.UpdateUserFormActive(it)) },
            )
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
