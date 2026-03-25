package com.zyntasolutions.zyntapos.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Lightweight warehouse option for use in [ZyntaWarehouseDropdown].
 *
 * @param id      Warehouse UUID.
 * @param name    Display name (e.g. "Main Floor", "Back Storage").
 * @param address Optional address hint shown below the name.
 */
data class WarehouseOption(
    val id: String,
    val name: String,
    val address: String? = null,
)

/**
 * Warehouse selection dropdown for multi-warehouse inventory operations.
 *
 * Displays warehouse names (and optional address hint) in an
 * [ExposedDropdownMenuBox]. Provides error state support for form validation.
 *
 * Used in stock transfer screens and any context where the user must choose a
 * source or destination warehouse.
 *
 * @param label      Field label (e.g. "Source Warehouse", "Destination Warehouse").
 * @param warehouses The list of available [WarehouseOption]s to display.
 * @param selectedId The [WarehouseOption.id] of the currently selected warehouse.
 * @param onSelect   Callback with the [WarehouseOption.id] when the user picks an item.
 * @param modifier   Optional [Modifier].
 * @param isError    When `true` the field is rendered in error state.
 * @param errorText  Supporting error message shown below the field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZyntaWarehouseDropdown(
    label: String,
    warehouses: List<WarehouseOption>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorText: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = warehouses.find { it.id == selectedId }?.name ?: ""

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            isError = isError,
            supportingText = errorText?.let { msg -> { Text(msg) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            singleLine = true,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            warehouses.forEach { warehouse ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                warehouse.name,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            warehouse.address?.let { addr ->
                                Text(
                                    addr,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    onClick = {
                        onSelect(warehouse.id)
                        expanded = false
                    },
                )
            }
        }
    }
}
