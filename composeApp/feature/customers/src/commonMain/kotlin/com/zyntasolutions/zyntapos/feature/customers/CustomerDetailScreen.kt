package com.zyntasolutions.zyntapos.feature.customers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButton
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaLoadingOverlay
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaTextField

/**
 * Customer create/edit screen.
 *
 * Stateless — renders [state.editFormState] and dispatches [CustomerIntent]s.
 * Navigation is handled by the parent via [CustomerEffect] callbacks.
 *
 * @param customerId Null = create mode; non-null = edit mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    customerId: String?,
    state: CustomerState,
    onIntent: (CustomerIntent) -> Unit,
    onNavigateUp: () -> Unit,
    onNavigateToWallet: (String) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    LaunchedEffect(customerId) {
        onIntent(CustomerIntent.SelectCustomer(customerId))
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    val form = state.editFormState
    val isEditMode = form.isEditing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) "Edit Customer" else "New Customer") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isEditMode && state.selectedCustomer != null) {
                        IconButton(onClick = {
                            onNavigateToWallet(state.selectedCustomer.id)
                        }) {
                            Icon(Icons.Filled.AccountBalanceWallet, contentDescription = "Wallet")
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        ZyntaLoadingOverlay(isLoading = state.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ── Core Fields ──────────────────────────────────────────────
                ZyntaTextField(
                    value = form.name,
                    onValueChange = { onIntent(CustomerIntent.UpdateFormField("name", it)) },
                    label = "Full Name *",
                    isError = "name" in form.validationErrors,
                    errorMessage = form.validationErrors["name"],
                    modifier = Modifier.fillMaxWidth(),
                )

                ZyntaTextField(
                    value = form.phone,
                    onValueChange = { onIntent(CustomerIntent.UpdateFormField("phone", it)) },
                    label = "Phone *",
                    isError = "phone" in form.validationErrors,
                    errorMessage = form.validationErrors["phone"],
                    modifier = Modifier.fillMaxWidth(),
                )

                ZyntaTextField(
                    value = form.email,
                    onValueChange = { onIntent(CustomerIntent.UpdateFormField("email", it)) },
                    label = "Email",
                    modifier = Modifier.fillMaxWidth(),
                )

                ZyntaTextField(
                    value = form.address,
                    onValueChange = { onIntent(CustomerIntent.UpdateFormField("address", it)) },
                    label = "Address",
                    modifier = Modifier.fillMaxWidth(),
                )

                // ── Demographics ─────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ZyntaTextField(
                        value = form.gender,
                        onValueChange = { onIntent(CustomerIntent.UpdateFormField("gender", it)) },
                        label = "Gender",
                        modifier = Modifier.weight(1f),
                    )
                    ZyntaTextField(
                        value = form.birthday,
                        onValueChange = { onIntent(CustomerIntent.UpdateFormField("birthday", it)) },
                        label = "Birthday (YYYY-MM-DD)",
                        modifier = Modifier.weight(1f),
                    )
                }

                ZyntaTextField(
                    value = form.notes,
                    onValueChange = { onIntent(CustomerIntent.UpdateFormField("notes", it)) },
                    label = "Notes",
                    modifier = Modifier.fillMaxWidth(),
                )

                // ── Credit Settings ──────────────────────────────────────────
                Spacer(Modifier.height(8.dp))
                Text("Credit Settings", style = MaterialTheme.typography.titleSmall)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Credit Enabled", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(
                        checked = form.creditEnabled,
                        onCheckedChange = { onIntent(CustomerIntent.UpdateCreditEnabled(it)) },
                    )
                }

                if (form.creditEnabled) {
                    ZyntaTextField(
                        value = form.creditLimit,
                        onValueChange = { onIntent(CustomerIntent.UpdateFormField("creditLimit", it)) },
                        label = "Credit Limit",
                        isError = "creditLimit" in form.validationErrors,
                        errorMessage = form.validationErrors["creditLimit"],
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Walk-in Customer", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(
                        checked = form.isWalkIn,
                        onCheckedChange = { onIntent(CustomerIntent.UpdateIsWalkIn(it)) },
                    )
                }

                Spacer(Modifier.height(16.dp))

                // ── Save Button ──────────────────────────────────────────────
                ZyntaButton(
                    text = if (isEditMode) "Update Customer" else "Create Customer",
                    onClick = { onIntent(CustomerIntent.SaveCustomer) },
                    isLoading = state.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    // ── Delete Confirmation Dialog ─────────────────────────────────────────────
    if (showDeleteDialog && state.selectedCustomer != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Customer") },
            text = { Text("Delete ${state.selectedCustomer.name}? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onIntent(CustomerIntent.DeleteCustomer(state.selectedCustomer.id))
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}
