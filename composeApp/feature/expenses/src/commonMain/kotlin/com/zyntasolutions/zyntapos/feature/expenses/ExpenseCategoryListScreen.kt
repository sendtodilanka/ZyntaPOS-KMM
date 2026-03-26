package com.zyntasolutions.zyntapos.feature.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import org.koin.compose.viewmodel.koinViewModel

/**
 * Manages expense categories — list, create, edit, and delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseCategoryListScreen(
    onNavigateUp: () -> Unit,
    viewModel: ExpenseViewModel = koinViewModel(),
) {
    val s = LocalStrings.current
    val state by viewModel.state.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s[StringResource.EXPENSES_CATEGORIES_TITLE]) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s[StringResource.COMMON_BACK])
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.dispatch(ExpenseIntent.SelectCategory(null)) }) {
                Icon(Icons.Default.Add, contentDescription = s[StringResource.EXPENSES_NEW_CATEGORY_TITLE])
            }
        },
    ) { padding ->
        if (state.categories.isEmpty()) {
            ZyntaEmptyState(
                title = s[StringResource.EXPENSES_CATEGORIES_EMPTY_TITLE],
                icon = Icons.Default.Add,
                subtitle = s[StringResource.EXPENSES_CATEGORIES_EMPTY_SUBTITLE],
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.categories, key = { it.id }) { category ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(category.name, style = MaterialTheme.typography.titleSmall)
                                category.description?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            IconButton(onClick = { viewModel.dispatch(ExpenseIntent.SelectCategory(category.id)) }) {
                                Icon(Icons.Default.Edit, contentDescription = s[StringResource.COMMON_EDIT])
                            }
                            IconButton(onClick = { pendingDeleteId = category.id }) {
                                Icon(Icons.Default.Delete, contentDescription = s[StringResource.COMMON_DELETE],
                                    tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Category Create / Edit Bottom Sheet ────────────────────────────────
    if (state.showCategoryDetail) {
        val form = state.categoryForm
        ModalBottomSheet(
            onDismissRequest = { viewModel.dispatch(ExpenseIntent.DismissCategoryDetail) },
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = if (form.isEditing) s[StringResource.EXPENSES_EDIT_CATEGORY_TITLE] else s[StringResource.EXPENSES_NEW_CATEGORY_TITLE],
                    style = MaterialTheme.typography.titleMedium,
                )

                OutlinedTextField(
                    value = form.name,
                    onValueChange = { viewModel.dispatch(ExpenseIntent.UpdateCategoryField("name", it)) },
                    label = { Text(s[StringResource.EXPENSES_CATEGORY_NAME_LABEL]) },
                    isError = form.validationErrors.containsKey("name"),
                    supportingText = form.validationErrors["name"]?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = form.description,
                    onValueChange = { viewModel.dispatch(ExpenseIntent.UpdateCategoryField("description", it)) },
                    label = { Text(s[StringResource.EXPENSES_CATEGORY_DESC_LABEL]) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { viewModel.dispatch(ExpenseIntent.DismissCategoryDetail) }) {
                        Text(s[StringResource.COMMON_CANCEL])
                    }
                    ZyntaButton(
                        text = if (form.isEditing) s[StringResource.COMMON_UPDATE] else s[StringResource.COMMON_CREATE],
                        onClick = { viewModel.dispatch(ExpenseIntent.SaveCategory) },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }

    // ── Delete Confirmation ────────────────────────────────────────────────
    pendingDeleteId?.let { id ->
        val categoryName = state.categories.find { it.id == id }?.name ?: id
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text(s[StringResource.EXPENSES_DELETE_CATEGORY_TITLE]) },
            text = { Text(s[StringResource.EXPENSES_DELETE_CATEGORY_BODY, categoryName]) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteId = null
                        viewModel.dispatch(ExpenseIntent.DeleteCategory(id))
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(s[StringResource.COMMON_DELETE]) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text(s[StringResource.COMMON_CANCEL]) }
            },
        )
    }
}
