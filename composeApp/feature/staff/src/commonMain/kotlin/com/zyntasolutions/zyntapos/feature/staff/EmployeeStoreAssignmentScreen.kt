package com.zyntasolutions.zyntapos.feature.staff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaEmptyState
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.EmployeeStoreAssignment

/**
 * Employee Store Assignments screen (C3.4 Employee Roaming).
 *
 * Lists all active store assignments for an employee and allows adding / revoking them.
 *
 * @param state        Current [EmployeeRoamingState].
 * @param onIntent     Dispatches intents to [EmployeeRoamingViewModel].
 * @param onNavigateUp Called when the user taps the back arrow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeStoreAssignmentScreen(
    state: EmployeeRoamingState,
    onIntent: (EmployeeRoamingIntent) -> Unit,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            onIntent(EmployeeRoamingIntent.DismissError)
        }
    }
    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            onIntent(EmployeeRoamingIntent.DismissSuccess)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.employeeName.isNotBlank()) "Stores: ${state.employeeName}" else "Store Assignments",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onIntent(EmployeeRoamingIntent.ShowAddDialog) }) {
                Icon(Icons.Default.Add, contentDescription = "Add store assignment")
            }
        },
    ) { innerPadding ->

        if (state.isLoading && state.assignments.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.assignments.isEmpty()) {
            ZyntaEmptyState(
                title = "No additional stores assigned",
                subtitle = "Tap + to add one.",
                icon = Icons.Default.Store,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = ZyntaSpacing.md),
                verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
            ) {
                items(state.assignments, key = { it.id }) { assignment ->
                    AssignmentCard(
                        assignment = assignment,
                        onRevoke = { onIntent(EmployeeRoamingIntent.RevokeAssignment(assignment.storeId)) },
                    )
                }
            }
        }

        // Add dialog
        if (state.showAddDialog) {
            AddAssignmentDialog(
                form = state.addForm,
                isLoading = state.isLoading,
                onUpdateField = { f, v -> onIntent(EmployeeRoamingIntent.UpdateField(f, v)) },
                onToggleTemporary = { onIntent(EmployeeRoamingIntent.ToggleTemporary) },
                onConfirm = { onIntent(EmployeeRoamingIntent.ConfirmAssignment) },
                onDismiss = { onIntent(EmployeeRoamingIntent.HideAddDialog) },
            )
        }
    }
}

// ─── Private composables ──────────────────────────────────────────────────────

@Composable
private fun AssignmentCard(
    assignment: EmployeeStoreAssignment,
    onRevoke: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(ZyntaSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Store, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(ZyntaSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(assignment.storeId, style = MaterialTheme.typography.bodyMedium)
                Text(
                    buildString {
                        append("From: ${assignment.startDate}")
                        assignment.endDate?.let { append("  To: $it") }
                        if (assignment.isTemporary) append("  (Temporary)")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRevoke) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Revoke",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun AddAssignmentDialog(
    form: AssignmentFormState,
    isLoading: Boolean,
    onUpdateField: (String, String) -> Unit,
    onToggleTemporary: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Store Assignment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                OutlinedTextField(
                    value = form.storeId,
                    onValueChange = { onUpdateField("storeId", it) },
                    label = { Text("Store ID") },
                    isError = form.validationErrors.containsKey("storeId"),
                    supportingText = form.validationErrors["storeId"]?.let {
                        { Text(it, color = MaterialTheme.colorScheme.error) }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.startDate,
                    onValueChange = { onUpdateField("startDate", it) },
                    label = { Text("Start Date (YYYY-MM-DD)") },
                    isError = form.validationErrors.containsKey("startDate"),
                    supportingText = form.validationErrors["startDate"]?.let {
                        { Text(it, color = MaterialTheme.colorScheme.error) }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.endDate,
                    onValueChange = { onUpdateField("endDate", it) },
                    label = { Text("End Date (optional)") },
                    placeholder = { Text("Leave blank for permanent") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Checkbox(checked = form.isTemporary, onCheckedChange = { onToggleTemporary() })
                    Spacer(Modifier.width(4.dp))
                    Text("Temporary assignment", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isLoading) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Add")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
