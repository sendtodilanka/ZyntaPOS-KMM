package com.zyntasolutions.zyntapos.feature.settings.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaPageScaffold
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.PermissionGroup
import com.zyntasolutions.zyntapos.domain.model.PermissionItem
import com.zyntasolutions.zyntapos.feature.settings.RoleEditorEffect
import com.zyntasolutions.zyntapos.feature.settings.RoleEditorIntent
import com.zyntasolutions.zyntapos.feature.settings.RoleEditorState
import kotlinx.coroutines.flow.Flow

/**
 * Custom-role editor screen (Sprint 23 task 23.5).
 *
 * Layout:
 *   * Outlined name field (with validation error chip).
 *   * Module-grouped permission tree using [TriStateCheckbox] headers and
 *     [Checkbox] rows. Tapping a header toggles every permission in the
 *     group; tapping a row toggles a single permission. Indeterminate
 *     state is computed from `selected` against the group's permission set.
 *   * Save action in the top bar; disabled while [RoleEditorState.isSaving].
 *
 * Effects ([RoleEditorEffect]) drive navigation and snackbars:
 *   * [RoleEditorEffect.Saved]      → invokes [onSaved] (typically `navigateUp`).
 *   * [RoleEditorEffect.ShowError]  → routed through [onError] to the host's
 *     snackbar host.
 *
 * @param roleId   Optional id of a [com.zyntasolutions.zyntapos.domain.model.CustomRole]
 *                 to load for editing. `null` opens an empty form for create.
 * @param state    MVI state slice from [com.zyntasolutions.zyntapos.feature.settings.RoleEditorViewModel].
 * @param effects  One-shot effects from the same view model.
 * @param onIntent Pipe back to `viewModel.dispatch`.
 * @param onSaved  Invoked once the save effect arrives.
 * @param onError  Invoked with the user-facing error string.
 * @param onBack   Back navigation from the top bar.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun RoleEditorScreen(
    roleId: String?,
    state: RoleEditorState,
    effects: Flow<RoleEditorEffect>,
    onIntent: (RoleEditorIntent) -> Unit,
    onSaved: () -> Unit,
    onBack: () -> Unit,
) {
    val s = LocalStrings.current
    LaunchedEffect(roleId) { onIntent(RoleEditorIntent.Load(roleId)) }
    LaunchedEffect(Unit) {
        effects.collect { effect ->
            when (effect) {
                RoleEditorEffect.Saved -> onSaved()
            }
        }
    }
    val title = if (state.roleId == null) s[StringResource.RBAC_CREATE_ROLE] else s[StringResource.RBAC_EDIT_ROLE]
    ZyntaPageScaffold(
        title = title,
        onNavigateBack = onBack,
        actions = {
            TextButton(
                onClick = { onIntent(RoleEditorIntent.Save) },
                enabled = !state.isSaving && !state.isLoading,
            ) {
                Text(s[StringResource.COMMON_SAVE])
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
            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
        ) {
            item {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { onIntent(RoleEditorIntent.UpdateName(it)) },
                    label = { Text(s[StringResource.RBAC_ROLE_NAME]) },
                    isError = state.nameError != null,
                    supportingText = state.nameError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(ZyntaSpacing.sm))
            }
            state.saveError?.let { err ->
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(ZyntaSpacing.sm),
                        )
                    }
                }
            }

            state.tree.forEach { group ->
                val groupPermissions = group.permissions.map { it.permission }.toSet()
                val allSelected = groupPermissions.isNotEmpty() && groupPermissions.all { it in state.selected }
                val anySelected = groupPermissions.any { it in state.selected }
                val triState: ToggleableState = when {
                    allSelected -> ToggleableState.On
                    anySelected -> ToggleableState.Indeterminate
                    else -> ToggleableState.Off
                }
                item(key = "group_${group.module}") {
                    PermissionGroupHeader(
                        group = group,
                        triState = triState,
                        permissionCountLabel = s[StringResource.RBAC_PERMISSION_COUNT, group.permissions.size],
                        onToggle = { onIntent(RoleEditorIntent.ToggleGroup(group)) },
                    )
                }
                items(group.permissions) { item ->
                    PermissionRow(
                        item = item,
                        checked = item.permission in state.selected,
                        onToggle = { onIntent(RoleEditorIntent.TogglePermission(item.permission)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionGroupHeader(
    group: PermissionGroup,
    triState: ToggleableState,
    permissionCountLabel: String,
    onToggle: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = ZyntaSpacing.sm, vertical = ZyntaSpacing.xs),
        ) {
            TriStateCheckbox(
                state = triState,
                onClick = onToggle,
            )
            Spacer(Modifier.width(ZyntaSpacing.sm))
            Column(Modifier.weight(1f)) {
                Text(group.displayName, style = MaterialTheme.typography.titleSmall)
                Text(
                    permissionCountLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    item: PermissionItem,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = ZyntaSpacing.lg, end = ZyntaSpacing.sm, top = ZyntaSpacing.xs, bottom = ZyntaSpacing.xs),
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Spacer(Modifier.width(ZyntaSpacing.sm))
        Column {
            Text(item.displayName, style = MaterialTheme.typography.bodyMedium)
            Text(
                item.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

