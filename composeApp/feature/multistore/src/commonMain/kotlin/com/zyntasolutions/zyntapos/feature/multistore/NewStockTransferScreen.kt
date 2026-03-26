package com.zyntasolutions.zyntapos.feature.multistore

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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.components.WarehouseOption
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaWarehouseDropdown
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButton
import org.koin.compose.viewmodel.koinViewModel

/**
 * Screen for creating a new [StockTransfer].
 *
 * MS-1: Product selector uses search-as-you-type dropdown instead of raw ID input.
 * Warehouse source/dest use dropdown selectors showing warehouse names.
 *
 * @param sourceWarehouseId Pre-fills the source warehouse field when navigated
 *   from a specific warehouse context.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewStockTransferScreen(
    sourceWarehouseId: String?,
    onComplete: () -> Unit,
    onCancel: () -> Unit,
    viewModel: WarehouseViewModel = koinViewModel(),
) {
    val s = LocalStrings.current
    val state by viewModel.state.collectAsState()
    val form = state.transferForm

    LaunchedEffect(sourceWarehouseId) {
        viewModel.dispatch(WarehouseIntent.InitTransferForm(sourceWarehouseId))
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is WarehouseEffect.TransferComplete -> onComplete()
                else -> Unit
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s[StringResource.MULTISTORE_NEW_STOCK_TRANSFER]) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s[StringResource.COMMON_CANCEL])
                    }
                },
                actions = {
                    TextButton(onClick = onCancel) { Text(s[StringResource.COMMON_CANCEL]) }
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
            // ── Source Warehouse Dropdown ─────────────────────────────────
            val warehouseOptions = state.warehouses.map { WarehouseOption(it.id, it.name, it.address) }
            ZyntaWarehouseDropdown(
                label = s[StringResource.MULTISTORE_SOURCE_WAREHOUSE],
                warehouses = warehouseOptions,
                selectedId = form.sourceWarehouseId,
                onSelect = { viewModel.dispatch(WarehouseIntent.UpdateTransferField("sourceWarehouseId", it)) },
                modifier = Modifier.fillMaxWidth(),
                isError = form.validationErrors.containsKey("sourceWarehouseId"),
                errorText = form.validationErrors["sourceWarehouseId"],
            )

            // ── Destination Warehouse Dropdown ───────────────────────────
            ZyntaWarehouseDropdown(
                label = s[StringResource.MULTISTORE_DEST_WAREHOUSE],
                warehouses = warehouseOptions,
                selectedId = form.destWarehouseId,
                onSelect = { viewModel.dispatch(WarehouseIntent.UpdateTransferField("destWarehouseId", it)) },
                modifier = Modifier.fillMaxWidth(),
                isError = form.validationErrors.containsKey("destWarehouseId"),
                errorText = form.validationErrors["destWarehouseId"],
            )

            // ── Product Search Dropdown (MS-1) ──────────────────────────
            ProductSearchDropdown(
                query = state.productSearchQuery,
                selectedProductName = form.productName,
                searchResults = state.productSearchResults,
                onQueryChange = { viewModel.dispatch(WarehouseIntent.SearchProducts(it)) },
                onSelect = { viewModel.dispatch(WarehouseIntent.SelectTransferProduct(it)) },
                isError = form.validationErrors.containsKey("productId"),
                errorText = form.validationErrors["productId"],
            )

            OutlinedTextField(
                value = form.quantity,
                onValueChange = { viewModel.dispatch(WarehouseIntent.UpdateTransferField("quantity", it)) },
                label = { Text(s[StringResource.MULTISTORE_QUANTITY]) },
                isError = form.validationErrors.containsKey("quantity"),
                supportingText = form.validationErrors["quantity"]?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = form.notes,
                onValueChange = { viewModel.dispatch(WarehouseIntent.UpdateTransferField("notes", it)) },
                label = { Text(s[StringResource.MULTISTORE_NOTES]) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
            )

            Spacer(Modifier.height(8.dp))

            ZyntaButton(
                text = s[StringResource.MULTISTORE_CREATE_TRANSFER],
                onClick = { viewModel.dispatch(WarehouseIntent.SubmitTransfer) },
                modifier = Modifier.fillMaxWidth(),
                isLoading = state.isLoading,
            )
        }
    }
}

/**
 * Product search-as-you-type dropdown (MS-1).
 * Displays product name + SKU + stock info in results.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductSearchDropdown(
    query: String,
    selectedProductName: String,
    searchResults: List<com.zyntasolutions.zyntapos.domain.model.Product>,
    onQueryChange: (String) -> Unit,
    onSelect: (com.zyntasolutions.zyntapos.domain.model.Product) -> Unit,
    isError: Boolean = false,
    errorText: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    // Show selected product name or search query
    val displayText = if (selectedProductName.isNotBlank()) selectedProductName else query

    ExposedDropdownMenuBox(
        expanded = expanded && searchResults.isNotEmpty(),
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = { newValue ->
                onQueryChange(newValue)
                expanded = true
            },
            label = { val s = LocalStrings.current; Text(s[StringResource.MULTISTORE_PRODUCT]) },
            placeholder = { val s = LocalStrings.current; Text(s[StringResource.MULTISTORE_SEARCH_PRODUCT]) },
            isError = isError,
            supportingText = errorText?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
            singleLine = true,
        )
        ExposedDropdownMenu(
            expanded = expanded && searchResults.isNotEmpty(),
            onDismissRequest = { expanded = false },
        ) {
            searchResults.forEach { product ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(product.name, style = MaterialTheme.typography.bodyMedium)
                            val s = LocalStrings.current
                            Text(
                                buildString {
                                    product.sku?.let { append(s[StringResource.MULTISTORE_SKU_FORMAT, it]) }
                                    append(" | ${s[StringResource.MULTISTORE_STOCK_SHORT_FORMAT, product.stockQty.toInt()]}")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        onSelect(product)
                        expanded = false
                    },
                )
            }
        }
    }
}
