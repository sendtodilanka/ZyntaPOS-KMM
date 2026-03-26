package com.zyntasolutions.zyntapos.feature.customers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButton
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaEmptyState
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaTextField
import com.zyntasolutions.zyntapos.domain.model.CustomerGroup

/**
 * Customer group management screen — list + create/edit bottom sheet.
 *
 * Stateless; all state from [CustomerState], all mutations via [onIntent].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerGroupScreen(
    state: CustomerState,
    onIntent: (CustomerIntent) -> Unit,
    onNavigateUp: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val s = LocalStrings.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pendingDeleteGroupId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s[StringResource.CUSTOMERS_GROUPS]) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = s[StringResource.COMMON_BACK])
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onIntent(CustomerIntent.SelectGroup(null)) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(s[StringResource.CUSTOMERS_NEW_GROUP]) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (state.customerGroups.isEmpty()) {
            ZyntaEmptyState(
                title = s[StringResource.CUSTOMERS_NO_GROUPS],
                subtitle = s[StringResource.CUSTOMERS_NO_GROUPS_HINT],
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            ) {
                items(state.customerGroups, key = { it.id }) { group ->
                    GroupListItem(
                        group = group,
                        onClick = { onIntent(CustomerIntent.SelectGroup(group.id)) },
                        onDelete = { pendingDeleteGroupId = group.id },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    // ── Group Detail Bottom Sheet ──────────────────────────────────────────────
    if (state.showGroupDetail) {
        ModalBottomSheet(
            onDismissRequest = { onIntent(CustomerIntent.DismissGroupDetail) },
            sheetState = sheetState,
        ) {
            GroupDetailSheet(
                state = state,
                onIntent = onIntent,
            )
        }
    }

    // ── Delete Confirmation ────────────────────────────────────────────────────
    pendingDeleteGroupId?.let { groupId ->
        AlertDialog(
            onDismissRequest = { pendingDeleteGroupId = null },
            title = { Text(s[StringResource.CUSTOMERS_DELETE_GROUP]) },
            text = { Text(s[StringResource.CUSTOMERS_DELETE_GROUP_MESSAGE]) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onIntent(CustomerIntent.DeleteGroup(groupId))
                        pendingDeleteGroupId = null
                    },
                ) {
                    Text(s[StringResource.COMMON_DELETE], color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteGroupId = null }) { Text(s[StringResource.COMMON_CANCEL]) }
            },
        )
    }
}

@Composable
private fun GroupListItem(
    group: CustomerGroup,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val s = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(group.name, style = MaterialTheme.typography.bodyMedium)
            if (!group.description.isNullOrBlank()) {
                Text(group.description!!, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            text = "${group.priceType.name} · ${if (group.discountValue > 0) "${group.discountValue}% ${s[StringResource.COUPONS_OFF]}" else s[StringResource.CUSTOMERS_NO_DISCOUNT]}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun GroupDetailSheet(
    state: CustomerState,
    onIntent: (CustomerIntent) -> Unit,
) {
    val s = LocalStrings.current
    val form = state.groupFormState
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = if (form.isEditing) s[StringResource.CUSTOMERS_EDIT_GROUP] else s[StringResource.CUSTOMERS_NEW_GROUP],
            style = MaterialTheme.typography.titleMedium,
        )

        ZyntaTextField(
            value = form.name,
            onValueChange = { onIntent(CustomerIntent.UpdateGroupField("name", it)) },
            label = s[StringResource.CUSTOMERS_GROUP_NAME_REQUIRED],
            error = form.validationErrors["name"],            modifier = Modifier.fillMaxWidth(),
        )

        ZyntaTextField(
            value = form.description,
            onValueChange = { onIntent(CustomerIntent.UpdateGroupField("description", it)) },
            label = s[StringResource.CUSTOMERS_DESCRIPTION],
            modifier = Modifier.fillMaxWidth(),
        )

        ZyntaTextField(
            value = form.discountValue,
            onValueChange = { onIntent(CustomerIntent.UpdateGroupField("discountValue", it)) },
            label = s[StringResource.CUSTOMERS_DISCOUNT_PERCENT],
            modifier = Modifier.fillMaxWidth(),
        )

        ZyntaButton(
            text = if (form.isEditing) s[StringResource.CUSTOMERS_UPDATE_GROUP] else s[StringResource.CUSTOMERS_CREATE_GROUP],
            onClick = { onIntent(CustomerIntent.SaveGroup) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
