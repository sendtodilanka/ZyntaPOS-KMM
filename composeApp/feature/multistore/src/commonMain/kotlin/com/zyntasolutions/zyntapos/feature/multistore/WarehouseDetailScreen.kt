package com.zyntasolutions.zyntapos.feature.multistore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButton
import org.koin.compose.viewmodel.koinViewModel

/**
 * Create or edit a [Warehouse].
 *
 * @param warehouseId null → create mode; non-null → edit mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarehouseDetailScreen(
    warehouseId: String?,
    onNavigateUp: () -> Unit,
    viewModel: WarehouseViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val form = state.warehouseForm

    LaunchedEffect(warehouseId) {
        viewModel.handleIntent(WarehouseIntent.SelectWarehouse(warehouseId))
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is WarehouseEffect.NavigateToList -> onNavigateUp()
                else -> Unit
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (warehouseId == null) "New Warehouse" else "Edit Warehouse") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                value = form.name,
                onValueChange = { viewModel.handleIntent(WarehouseIntent.UpdateWarehouseField("name", it)) },
                label = { Text("Warehouse Name *") },
                isError = form.validationErrors.containsKey("name"),
                supportingText = form.validationErrors["name"]?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = form.address,
                onValueChange = { viewModel.handleIntent(WarehouseIntent.UpdateWarehouseField("address", it)) },
                label = { Text("Address") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Set as Default", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Pre-selected for stock operations",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = form.isDefault,
                    onCheckedChange = { viewModel.handleIntent(WarehouseIntent.UpdateIsDefault(it)) },
                )
            }

            Spacer(Modifier.height(8.dp))

            ZyntaButton(
                text = if (form.isEditing) "Update Warehouse" else "Create Warehouse",
                onClick = { viewModel.handleIntent(WarehouseIntent.SaveWarehouse) },
                modifier = Modifier.fillMaxWidth(),
                isLoading = state.isLoading,
            )
        }
    }
}
