package com.zyntasolutions.zyntapos.feature.multistore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing

/**
 * Form screen for setting per-warehouse stock levels (C1.2).
 *
 * Used to set or update the [quantity] and [minQuantity] for a (warehouse, product) pair.
 * Triggered from [WarehouseStockScreen] by tapping an item row or pressing the FAB.
 *
 * @param state       Current [WarehouseState].
 * @param onIntent    Dispatches intents to [WarehouseViewModel].
 * @param modifier    Optional modifier.
 */
@Composable
fun WarehouseStockEntryScreen(
    state: WarehouseState,
    onIntent: (WarehouseIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val form = state.stockEntryForm

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ZyntaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
    ) {
        Text(
            text = if (form.isEditing) s[StringResource.MULTISTORE_UPDATE_STOCK_LEVEL] else s[StringResource.MULTISTORE_SET_STOCK_LEVEL],
            style = MaterialTheme.typography.headlineSmall,
        )

        if (form.productName.isNotBlank()) {
            Text(
                text = form.productName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(ZyntaSpacing.sm))

        // Quantity field
        OutlinedTextField(
            value = form.quantity,
            onValueChange = { onIntent(WarehouseIntent.UpdateStockField("quantity", it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(s[StringResource.MULTISTORE_ON_HAND_QTY]) },
            placeholder = { Text(s[StringResource.MULTISTORE_QTY_HINT]) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = form.validationErrors.containsKey("quantity"),
            supportingText = form.validationErrors["quantity"]?.let { { Text(it) } },
            singleLine = true,
        )

        // Min quantity field
        OutlinedTextField(
            value = form.minQuantity,
            onValueChange = { onIntent(WarehouseIntent.UpdateStockField("minQuantity", it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(s[StringResource.MULTISTORE_REORDER_POINT]) },
            placeholder = { Text(s[StringResource.MULTISTORE_REORDER_POINT_HINT]) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = form.validationErrors.containsKey("minQuantity"),
            supportingText = form.validationErrors["minQuantity"]?.let { { Text(it) } },
            singleLine = true,
        )

        Spacer(Modifier.height(ZyntaSpacing.md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
        ) {
            OutlinedButton(
                onClick = { onIntent(WarehouseIntent.CancelStockEntry) },
                modifier = Modifier.weight(1f),
            ) {
                Text(s[StringResource.COMMON_CANCEL])
            }
            Button(
                onClick = { onIntent(WarehouseIntent.SaveStockEntry) },
                modifier = Modifier.weight(1f),
                enabled = !state.isLoading,
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(horizontal = ZyntaSpacing.sm),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(if (form.isEditing) s[StringResource.COMMON_UPDATE] else s[StringResource.MULTISTORE_SET_STOCK])
                }
            }
        }
    }
}
