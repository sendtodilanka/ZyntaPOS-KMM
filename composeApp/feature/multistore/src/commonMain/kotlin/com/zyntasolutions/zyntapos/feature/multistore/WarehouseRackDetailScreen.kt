package com.zyntasolutions.zyntapos.feature.multistore

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing

/**
 * Warehouse rack create/edit form screen (Sprint 18).
 *
 * Allows a user to create a new rack or edit an existing one within a warehouse.
 * Fields: name (required), optional description, optional capacity (units).
 *
 * MS-4: Includes TopAppBar with back navigation icon.
 *
 * @param state      Current [WarehouseState].
 * @param onIntent   Dispatches intents to [WarehouseViewModel].
 * @param onBack     Navigation callback to return to the rack list.
 * @param modifier   Optional modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarehouseRackDetailScreen(
    state: WarehouseState,
    onIntent: (WarehouseIntent) -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val form = state.rackForm

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (form.isEditing) "Edit Rack" else "New Rack") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to rack list",
                        )
                    }
                },
            )
        },
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(ZyntaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
        ) {
            // Name field
            OutlinedTextField(
                value = form.name,
                onValueChange = { onIntent(WarehouseIntent.UpdateRackField("name", it)) },
                label = { Text("Rack Name *") },
                placeholder = { Text("e.g. A1, Cold-Storage-01") },
                isError = form.validationErrors.containsKey("name"),
                supportingText = form.validationErrors["name"]?.let {
                    { Text(it, color = MaterialTheme.colorScheme.error) }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Description field
            OutlinedTextField(
                value = form.description,
                onValueChange = { onIntent(WarehouseIntent.UpdateRackField("description", it)) },
                label = { Text("Description") },
                placeholder = { Text("Optional — e.g. Refrigerated shelf for perishables") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            // Capacity field
            OutlinedTextField(
                value = form.capacity,
                onValueChange = { onIntent(WarehouseIntent.UpdateRackField("capacity", it)) },
                label = { Text("Capacity (units)") },
                placeholder = { Text("Leave blank for unlimited") },
                isError = form.validationErrors.containsKey("capacity"),
                supportingText = form.validationErrors["capacity"]?.let {
                    { Text(it, color = MaterialTheme.colorScheme.error) }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(ZyntaSpacing.md))

            // Save button
            Button(
                onClick = { onIntent(WarehouseIntent.SaveRack) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading,
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                        strokeWidth = ButtonDefaults.IconSize / 5,
                    )
                } else {
                    Text(if (form.isEditing) "Update Rack" else "Create Rack")
                }
            }
        }
    }
}
