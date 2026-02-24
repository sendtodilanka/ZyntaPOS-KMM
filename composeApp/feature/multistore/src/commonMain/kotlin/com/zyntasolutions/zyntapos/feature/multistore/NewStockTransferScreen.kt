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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButton
import org.koin.compose.viewmodel.koinViewModel

/**
 * Screen for creating a new [StockTransfer].
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
    val state by viewModel.state.collectAsState()
    val form = state.transferForm

    LaunchedEffect(sourceWarehouseId) {
        viewModel.handleIntent(WarehouseIntent.InitTransferForm(sourceWarehouseId))
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
                title = { Text("New Stock Transfer") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                    }
                },
                actions = {
                    TextButton(onClick = onCancel) { Text("Cancel") }
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
                value = form.sourceWarehouseId,
                onValueChange = { viewModel.handleIntent(WarehouseIntent.UpdateTransferField("sourceWarehouseId", it)) },
                label = { Text("Source Warehouse ID *") },
                isError = form.validationErrors.containsKey("sourceWarehouseId"),
                supportingText = form.validationErrors["sourceWarehouseId"]?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = form.destWarehouseId,
                onValueChange = { viewModel.handleIntent(WarehouseIntent.UpdateTransferField("destWarehouseId", it)) },
                label = { Text("Destination Warehouse ID *") },
                isError = form.validationErrors.containsKey("destWarehouseId"),
                supportingText = form.validationErrors["destWarehouseId"]?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = form.productId,
                onValueChange = { viewModel.handleIntent(WarehouseIntent.UpdateTransferField("productId", it)) },
                label = { Text("Product ID *") },
                isError = form.validationErrors.containsKey("productId"),
                supportingText = form.validationErrors["productId"]?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = form.quantity,
                onValueChange = { viewModel.handleIntent(WarehouseIntent.UpdateTransferField("quantity", it)) },
                label = { Text("Quantity *") },
                isError = form.validationErrors.containsKey("quantity"),
                supportingText = form.validationErrors["quantity"]?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = form.notes,
                onValueChange = { viewModel.handleIntent(WarehouseIntent.UpdateTransferField("notes", it)) },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
            )

            Spacer(Modifier.height(8.dp))

            ZyntaButton(
                text = "Create Transfer",
                onClick = { viewModel.handleIntent(WarehouseIntent.SubmitTransfer) },
                modifier = Modifier.fillMaxWidth(),
                isLoading = state.isLoading,
            )
        }
    }
}
