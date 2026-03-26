package com.zyntasolutions.zyntapos.feature.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButton
import com.zyntasolutions.zyntapos.feature.media.rememberNativeFilePicker
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
    val s = LocalStrings.current
    val state by viewModel.state.collectAsState()
    val form = state.expenseForm
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Native file picker (G13) — must be called unconditionally at the screen level
    val pickReceipt = rememberNativeFilePicker { path ->
        if (path != null) viewModel.dispatch(ExpenseIntent.UpdateFormField("receiptUrl", path))
    }

    LaunchedEffect(expenseId) {
        viewModel.dispatch(ExpenseIntent.SelectExpense(expenseId))
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
                title = { Text(if (expenseId == null) s[StringResource.EXPENSES_NEW_EXPENSE_TITLE] else s[StringResource.EXPENSES_EDIT_EXPENSE_TITLE]) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s[StringResource.COMMON_BACK])
                    }
                },
                actions = {
                    if (expenseId != null) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = s[StringResource.COMMON_DELETE])
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
                onValueChange = { viewModel.dispatch(ExpenseIntent.UpdateFormField("description", it)) },
                label = { Text(s[StringResource.EXPENSES_DESC_LABEL]) },
                isError = form.validationErrors.containsKey("description"),
                supportingText = form.validationErrors["description"]?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = form.amount,
                onValueChange = { viewModel.dispatch(ExpenseIntent.UpdateFormField("amount", it)) },
                label = { Text(s[StringResource.EXPENSES_AMOUNT_LKR_LABEL]) },
                isError = form.validationErrors.containsKey("amount"),
                supportingText = form.validationErrors["amount"]?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = form.expenseDate,
                onValueChange = { viewModel.dispatch(ExpenseIntent.UpdateFormField("expenseDate", it)) },
                label = { Text(s[StringResource.EXPENSES_DATE_LABEL]) },
                isError = form.validationErrors.containsKey("expenseDate"),
                supportingText = form.validationErrors["expenseDate"]?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = form.categoryId,
                onValueChange = { viewModel.dispatch(ExpenseIntent.UpdateFormField("categoryId", it)) },
                label = { Text(s[StringResource.EXPENSES_CATEGORY_ID_LABEL]) },
                placeholder = { Text(s[StringResource.EXPENSES_CATEGORY_ID_PLACEHOLDER]) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            // Receipt attachment: URL text field + native file picker browse button (G13)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = form.receiptUrl,
                    onValueChange = { viewModel.dispatch(ExpenseIntent.UpdateFormField("receiptUrl", it)) },
                    label = { Text(s[StringResource.EXPENSES_RECEIPT_LABEL]) },
                    placeholder = { Text(s[StringResource.EXPENSES_RECEIPT_PLACEHOLDER]) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                IconButton(onClick = pickReceipt) {
                    Icon(Icons.Default.AttachFile, contentDescription = s[StringResource.EXPENSES_RECEIPT_LABEL])
                }
            }

            // Receipt image preview — shown when a path/URL is provided (G13)
            if (form.receiptUrl.isNotBlank()) {
                AsyncImage(
                    model = form.receiptUrl,
                    contentDescription = s[StringResource.EXPENSES_RECEIPT_LABEL],
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .size(200.dp),
                )
            }

            // Show current approval status for existing expenses
            state.selectedExpense?.let { expense ->
                Text(
                    text = "${s[StringResource.COMMON_STATUS]}: ${expense.status.name}",
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
                text = if (form.isEditing) s[StringResource.EXPENSES_UPDATE_BUTTON] else s[StringResource.EXPENSES_LOG_BUTTON],
                onClick = { viewModel.dispatch(ExpenseIntent.SaveExpense) },
                modifier = Modifier.fillMaxWidth(),
                isLoading = state.isLoading,
            )
        }
    }

    if (showDeleteDialog && expenseId != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(s[StringResource.EXPENSES_DELETE_EXPENSE_TITLE]) },
            text = { Text(s[StringResource.EXPENSES_DELETE_EXPENSE_BODY]) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.dispatch(ExpenseIntent.DeleteExpense(expenseId))
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(s[StringResource.COMMON_DELETE]) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(s[StringResource.COMMON_CANCEL]) }
            },
        )
    }
}
