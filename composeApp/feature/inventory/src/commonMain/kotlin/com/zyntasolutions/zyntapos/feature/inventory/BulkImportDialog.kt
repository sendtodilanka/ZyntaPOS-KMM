package com.zyntasolutions.zyntapos.feature.inventory

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
/**
 * Bulk product import dialog via CSV files (Sprint 18, task 10.1.5).
 *
 * ### Features
 * 1. **File Picker:** Platform-specific file chooser to select a CSV file.
 *    File parsing happens in the ViewModel via [InventoryIntent.SetImportFile].
 * 2. **Column Mapping:** Maps CSV column headers to product fields using
 *    dropdown selectors. Required fields: name, price, categoryId, unitId.
 * 3. **Preview Table:** Shows first 10 parsed rows with mapped field values
 *    for visual confirmation before import.
 * 4. **Import Execution:** Confirm triggers batch [CreateProductUseCase] calls
 *    with progress bar and error summary.
 *
 * ### Architecture
 * The dialog is **stateless** — all state lives in [BulkImportState] within
 * [InventoryState]. File selection triggers [InventoryIntent.SetImportFile],
 * column mapping triggers [InventoryIntent.SetColumnMapping], and import
 * confirmation triggers [InventoryIntent.ConfirmBulkImport].
 *
 * Platform-specific CSV file reading is delegated to a `CsvFileReader` expect/actual
 * interface (Phase 2). For Phase 1, the file picker callback passes parsed data directly.
 *
 * @param state     Current [BulkImportState] snapshot.
 * @param onIntent  Dispatches [InventoryIntent] to the ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkImportDialog(
    state: BulkImportState,
    onIntent: (InventoryIntent) -> Unit,
) {
    if (!state.isVisible) return
    AlertDialog(
        onDismissRequest = { onIntent(InventoryIntent.DismissBulkImport) },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null)
                Text("Bulk Import Products", style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
                modifier = Modifier
                    .widthIn(min = 400.dp, max = 640.dp)
                    .heightIn(max = 480.dp),
            ) {
                when {
                    // ── Step 1: File Selection ───────────────────────────
                    state.fileName == null -> FileSelectionStep(onIntent)

                    // ── Step 2: Column Mapping ───────────────────────────
                    !state.isImporting && state.importErrors.isEmpty() ->
                        ColumnMappingStep(state, onIntent)

                    // ── Step 3: Import Progress / Results ────────────────
                    state.isImporting -> ImportProgressStep(state)

                    // ── Step 4: Import Errors ────────────────────────────
                    state.importErrors.isNotEmpty() -> ImportErrorsStep(state)
                }
            }
        },        confirmButton = {
            if (state.fileName != null && !state.isImporting && state.importErrors.isEmpty()) {
                val requiredFields = listOf("name", "price", "categoryId", "unitId")
                val hasAllRequired = requiredFields.all { field ->
                    state.columnMapping.values.contains(field)
                }
                FilledTonalButton(
                    onClick = { onIntent(InventoryIntent.ConfirmBulkImport) },
                    enabled = hasAllRequired && state.parsedRows.isNotEmpty(),
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(ZyntaSpacing.xs))
                    Text("Import ${state.parsedRows.size} Products")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { onIntent(InventoryIntent.DismissBulkImport) }) {
                Text(if (state.isImporting) "Cancel" else "Close")
            }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 1: File Selection
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FileSelectionStep(onIntent: (InventoryIntent) -> Unit) {    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
    ) {
        // Drop zone / file picker area
        Card(
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(ZyntaSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
            ) {
                Icon(
                    Icons.Default.CloudUpload,
                    contentDescription = "Upload CSV",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "Select a CSV file to import products",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(                    "Supported format: .csv with header row",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(ZyntaSpacing.sm))
                OutlinedButton(onClick = {
                    // Platform file picker will be wired via expect/actual in Phase 2.
                    // For now, this triggers a demo/sample import for development.
                    onIntent(
                        InventoryIntent.SetImportFile(
                            fileName = "sample_products.csv",
                            columns = listOf("Product Name", "Barcode", "SKU", "Category", "Unit", "Price", "Cost", "Stock"),
                            rows = listOf(
                                mapOf("Product Name" to "Sample Product 1", "Price" to "100.00", "Category" to "General", "Unit" to "pcs"),
                            ),
                        )
                    )
                }) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(ZyntaSpacing.xs))
                    Text("Choose File")
                }
            }
        }

        // ── Expected CSV format hint ─────────────────────────────────
        Text(
            "Required columns: Name, Price, Category, Unit",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
// ─────────────────────────────────────────────────────────────────────────────
// Step 2: Column Mapping + Preview
// ─────────────────────────────────────────────────────────────────────────────

/** Target product fields for CSV column mapping. */
private val productFieldOptions = listOf(
    "" to "— Skip —",
    "name" to "Product Name",
    "barcode" to "Barcode",
    "sku" to "SKU",
    "categoryId" to "Category",
    "unitId" to "Unit",
    "price" to "Price",
    "costPrice" to "Cost Price",
    "stockQty" to "Stock Qty",
    "minStockQty" to "Min Stock",
    "description" to "Description",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnMappingStep(
    state: BulkImportState,
    onIntent: (InventoryIntent) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
        modifier = Modifier.fillMaxWidth(),
    ) {        // ── File info header ──────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
        ) {
            Icon(Icons.Default.Description, contentDescription = null,
                modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Text(state.fileName ?: "", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            Text(
                "${state.parsedRows.size} rows",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HorizontalDivider()

        // ── Column mapping section ───────────────────────────────────
        Text("Map CSV Columns to Product Fields", style = MaterialTheme.typography.labelLarge)

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.xs),
            modifier = Modifier.heightIn(max = 200.dp),
        ) {
            itemsIndexed(state.availableColumns) { _, csvColumn ->
                ColumnMappingRow(
                    csvColumn = csvColumn,
                    currentMapping = state.columnMapping[csvColumn] ?: "",
                    onMappingChanged = { field ->
                        onIntent(InventoryIntent.SetColumnMapping(csvColumn, field))
                    },
                )
            }
        }

        // ── Preview table (first 10 rows) ────────────────────────────
        if (state.parsedRows.isNotEmpty()) {
            HorizontalDivider()
            Text("Preview (first ${state.parsedRows.size.coerceAtMost(10)} rows)", style = MaterialTheme.typography.labelLarge)

            val mappedFields = state.columnMapping.entries
                .filter { it.value.isNotBlank() }
                .sortedBy { it.value }

            if (mappedFields.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                ) {
                    Column {
                        // ── Header row ───────────────────────────────
                        Row(
                            modifier = Modifier.border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                            ),
                        ) {
                            mappedFields.forEach { (csvCol, field) ->
                                Text(
                                    text = productFieldOptions.firstOrNull { it.first == field }?.second ?: field,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.width(120.dp).padding(ZyntaSpacing.xs),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        // ── Data rows (max 10 preview) ───────────────
                        state.parsedRows.take(10).forEachIndexed { _, row ->
                            Row(
                                modifier = Modifier.border(
                                    width = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                ),
                            ) {
                                mappedFields.forEach { (csvCol, _) ->
                                    Text(
                                        text = row[csvCol] ?: "—",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.width(120.dp).padding(ZyntaSpacing.xs),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Column Mapping Row
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A single row mapping one CSV column header to a product field via dropdown.
 *
 * @param csvColumn      The CSV header name (e.g., "Product Name").
 * @param currentMapping The currently mapped product field key (e.g., "name") or empty.
 * @param onMappingChanged Callback when a new mapping is selected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnMappingRow(
    csvColumn: String,
    currentMapping: String,
    onMappingChanged: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = productFieldOptions.firstOrNull { it.first == currentMapping }?.second ?: "— Skip —"

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // CSV column label
        Text(
            text = csvColumn,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Icon(
            Icons.Default.ArrowForward,
            contentDescription = "maps to",
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Dropdown for target field
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.weight(1.5f),
        ) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.menuAnchor(),
                textStyle = MaterialTheme.typography.bodySmall,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                singleLine = true,
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                productFieldOptions.forEach { (fieldKey, fieldLabel) ->
                    DropdownMenuItem(
                        text = { Text(fieldLabel, style = MaterialTheme.typography.bodySmall) },
                        onClick = {
                            onMappingChanged(fieldKey)
                            expanded = false
                        },
                        leadingIcon = if (fieldKey == currentMapping) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 3: Import Progress
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Shows a progress indicator while the batch import is running.
 */
@Composable
private fun ImportProgressStep(state: BulkImportState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
    ) {
        CircularProgressIndicator()

        Text(
            text = "Importing products…",
            style = MaterialTheme.typography.bodyMedium,
        )

        LinearProgressIndicator(
            progress = { state.importProgress },
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = "${(state.importProgress * state.parsedRows.size).toInt()} / ${state.parsedRows.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 4: Import Errors Summary
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Displays a list of import errors after the batch process completes.
 *
 * Each error shows the row number and the error message from [CreateProductUseCase].
 */
@Composable
private fun ImportErrorsStep(state: BulkImportState) {
    Column(
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = "${state.importErrors.size} error(s) during import",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        HorizontalDivider()

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.xs),
            modifier = Modifier.heightIn(max = 240.dp),
        ) {
            itemsIndexed(state.importErrors) { idx, error ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(ZyntaSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${idx + 1}.",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.width(32.dp),
                        )
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }

        // Informational hint
        Text(
            text = "Successfully imported rows are already saved. Fix the errors above and re-import the failed rows.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}