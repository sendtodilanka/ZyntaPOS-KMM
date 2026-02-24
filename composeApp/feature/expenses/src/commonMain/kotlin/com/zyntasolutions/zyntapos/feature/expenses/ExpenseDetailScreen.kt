package com.zyntasolutions.zyntapos.feature.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButton
import org.koin.compose.viewmodel.koinViewModel

/**
 * Create or edit a single [Expense].
 *
 * @param expenseId null → create mode; non-null → edit mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDetailScreen(
    expenseId: String?,
    onNavigateUp: () -> Unit,
    viewModel: ExpenseViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val form = state.expenseForm
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(expenseId) {
        viewModel.handleIntent(ExpenseIntent.SelectExpense(expenseId))
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ExpenseEffect.NavigateToList -> onNavigateUp()
                else -> Unit
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (expenseId == null) "New Expense" else "Edit Expense") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (expenseId != null) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = form.description,
                onValueChange = { viewModel.handleIntent(ExpenseIntent.UpdateFormField("description", it)) },
                label = { Text("Description *") },
                isError = form.validationErrors.containsKey("description"),
                supportingText = form.validationErrors["description"]?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = form.amount,
                onValueChange = { viewModel.handleIntent(ExpenseIntent.UpdateFormField("amount", it)) },
                label = { Text("Amount (LKR) *") },
                isError = form.validationErrors.containsKey("amount"),
                supportingText = form.validationErrors["amount"]?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = form.expenseDate,
                onValueChange = { viewModel.handleIntent(ExpenseIntent.UpdateFormField("expenseDate", it)) },
                label = { Text("Expense Date (epoch ms) *") },
                isError = form.validationErrors.containsKey("expenseDate"),
                supportingText = form.validationErrors["expenseDate"]?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = form.categoryId,
                onValueChange = { viewModel.handleIntent(ExpenseIntent.UpdateFormField("categoryId", it)) },
                label = { Text("Category ID") },
                placeholder = { Text("Leave blank for uncategorized") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = form.receiptUrl,
                onValueChange = { viewModel.handleIntent(ExpenseIntent.UpdateFormField("receiptUrl", it)) },
                label = { Text("Receipt URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            // Show current approval status for existing expenses
            state.selectedExpense?.let { expense ->
                Text(
                    text = "Status: ${expense.status.name}",
                    style = MaterialTheme.typography.labelLarge,
                    color = when (expense.status) {
                        com.zyntasolutions.zyntapos.domain.model.Expense.Status.PENDING -> MaterialTheme.colorScheme.tertiary
                        com.zyntasolutions.zyntapos.domain.model.Expense.Status.APPROVED -> MaterialTheme.colorScheme.primary
                        com.zyntasolutions.zyntapos.domain.model.Expense.Status.REJECTED -> MaterialTheme.colorScheme.error
                    },
                )
            }

            Spacer(Modifier.height(8.dp))

            ZyntaButton(
                text = if (form.isEditing) "Update Expense" else "Log Expense",
                onClick = { viewModel.handleIntent(ExpenseIntent.SaveExpense) },
                modifier = Modifier.fillMaxWidth(),
                isLoading = state.isLoading,
            )
        }
    }

    if (showDeleteDialog && expenseId != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Expense") },
            text = { Text("Delete this expense record? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.handleIntent(ExpenseIntent.DeleteExpense(expenseId))
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }
}
