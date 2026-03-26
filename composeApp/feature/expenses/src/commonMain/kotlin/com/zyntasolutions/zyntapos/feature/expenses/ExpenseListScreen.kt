package com.zyntasolutions.zyntapos.feature.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaEmptyState
import com.zyntasolutions.zyntapos.domain.model.Expense
import org.koin.compose.viewmodel.koinViewModel

/**
 * Lists expense records with status filter chips and quick approve/reject
 * actions for PENDING entries.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseListScreen(
    onNavigateToDetail: (expenseId: String?) -> Unit,
    onNavigateToCategories: () -> Unit,
    viewModel: ExpenseViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.dispatch(ExpenseIntent.LoadExpenses)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Expenses") },
                actions = {
                    IconButton(onClick = onNavigateToCategories) {
                        Icon(Icons.Outlined.Category, contentDescription = "Categories")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigateToDetail(null) }) {
                Icon(Icons.Default.Add, contentDescription = "New Expense")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            // Status filter chips
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.statusFilter == null,
                        onClick = { viewModel.dispatch(ExpenseIntent.FilterByStatus(null)) },
                        label = { Text("All") },
                    )
                }
                items(Expense.Status.entries) { status ->
                    FilterChip(
                        selected = state.statusFilter == status,
                        onClick = { viewModel.dispatch(ExpenseIntent.FilterByStatus(status)) },
                        label = { Text(status.name) },
                    )
                }
            }

            if (state.expenses.isEmpty() && !state.isLoading) {
                ZyntaEmptyState(
                    title = "No expenses found",
                    icon = Icons.Default.Receipt,
                    subtitle = "Tap + to record an expense.",
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.expenses, key = { it.id }) { expense ->
                        ExpenseListItem(
                            expense = expense,
                            categoryName = state.categories.find { it.id == expense.categoryId }?.name,
                            onClick = { onNavigateToDetail(expense.id) },
                            onApprove = { viewModel.dispatch(ExpenseIntent.ApproveExpense(expense.id)) },
                            onReject = { viewModel.dispatch(ExpenseIntent.RejectExpense(expense.id, null)) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseListItem(
    expense: Expense,
    categoryName: String?,
    onClick: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(expense.description, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = expense.status.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = when (expense.status) {
                        Expense.Status.PENDING -> MaterialTheme.colorScheme.tertiary
                        Expense.Status.APPROVED -> MaterialTheme.colorScheme.primary
                        Expense.Status.REJECTED -> MaterialTheme.colorScheme.error
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = categoryName ?: "Uncategorized",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "LKR ${expense.amount}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (expense.status == Expense.Status.PENDING) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = onReject,
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) { Text("Reject") }
                    TextButton(onClick = onApprove) { Text("Approve") }
                }
            }
        }
    }
}
