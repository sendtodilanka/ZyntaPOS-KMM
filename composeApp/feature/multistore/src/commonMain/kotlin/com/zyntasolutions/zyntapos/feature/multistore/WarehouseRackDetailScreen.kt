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
    val s = LocalStrings.current
    val form = state.rackForm

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (form.isEditing) s[StringResource.MULTISTORE_EDIT_RACK] else s[StringResource.MULTISTORE_NEW_RACK]) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = s[StringResource.COMMON_BACK],
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
                label = { Text(s[StringResource.MULTISTORE_RACK_NAME]) },
                placeholder = { Text(s[StringResource.MULTISTORE_RACK_NAME_HINT]) },
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
                label = { Text(s[StringResource.ACCOUNTING_DESCRIPTION]) },
                placeholder = { Text(s[StringResource.MULTISTORE_RACK_DESC_HINT]) },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            // Capacity field
            OutlinedTextField(
                value = form.capacity,
                onValueChange = { onIntent(WarehouseIntent.UpdateRackField("capacity", it)) },
                label = { Text(s[StringResource.MULTISTORE_CAPACITY]) },
                placeholder = { Text(s[StringResource.MULTISTORE_CAPACITY_HINT]) },
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
                    Text(if (form.isEditing) s[StringResource.MULTISTORE_UPDATE_RACK] else s[StringResource.MULTISTORE_CREATE_RACK])
                }
            }
        }
    }
}
