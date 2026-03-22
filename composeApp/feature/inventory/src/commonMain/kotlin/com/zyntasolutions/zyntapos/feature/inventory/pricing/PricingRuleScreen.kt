package com.zyntasolutions.zyntapos.feature.inventory.pricing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaEmptyState
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaPageScaffold
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.PricingRule
import org.koin.compose.viewmodel.koinViewModel

/**
 * Pricing rule management screen (C2.1 Region-Based Pricing).
 *
 * Displays all pricing rules with CRUD operations. Supports:
 * - Store-specific and global pricing rules
 * - Time-bounded validity windows (seasonal/promotional)
 * - Priority-based conflict resolution
 * - Active/inactive toggle
 */
@Composable
fun PricingRuleScreen(
    modifier: Modifier = Modifier,
    viewModel: PricingRuleViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onIntent(PricingRuleIntent.DismissError)
        }
    }
    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onIntent(PricingRuleIntent.DismissSuccess)
        }
    }

    ZyntaPageScaffold(
        title = "Pricing Rules",
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.onIntent(PricingRuleIntent.OpenDialog(null)) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Rule") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { innerPadding ->
        val displayedRules = state.rules.let { rules ->
            var filtered = rules
            if (state.filterActiveOnly) {
                filtered = filtered.filter { it.isActive }
            }
            state.filterProductId?.let { pid ->
                filtered = filtered.filter { it.productId == pid }
            }
            filtered
        }

        when {
            state.isLoadingRules -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            displayedRules.isEmpty() -> ZyntaEmptyState(
                title = "No pricing rules",
                icon = Icons.Default.AttachMoney,
                subtitle = "Create pricing rules to set store-specific or time-bounded prices",
                ctaLabel = "New Rule",
                onCtaClick = { viewModel.onIntent(PricingRuleIntent.OpenDialog(null)) },
                modifier = Modifier.padding(innerPadding),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(
                    horizontal = ZyntaSpacing.md,
                    vertical = ZyntaSpacing.sm,
                ),
                verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
            ) {
                items(displayedRules, key = { it.id }) { rule ->
                    PricingRuleCard(
                        rule = rule,
                        productName = state.products.find { it.id == rule.productId }?.name,
                        onEdit = { viewModel.onIntent(PricingRuleIntent.OpenDialog(rule)) },
                        onDelete = { viewModel.onIntent(PricingRuleIntent.ConfirmDelete(rule)) },
                    )
                }
                item { Spacer(Modifier.height(88.dp)) } // FAB clearance
            }
        }
    }

    // ── Edit / Create Dialog ─────────────────────────────────────────────
    if (state.showDialog) {
        PricingRuleEditDialog(
            state = state,
            onUpdateField = { field, value ->
                viewModel.onIntent(PricingRuleIntent.UpdateField(field, value))
            },
            onSetActive = { viewModel.onIntent(PricingRuleIntent.SetActive(it)) },
            onSave = { viewModel.onIntent(PricingRuleIntent.SaveRule) },
            onDismiss = { viewModel.onIntent(PricingRuleIntent.DismissDialog) },
        )
    }

    // ── Delete Confirm Dialog ────────────────────────────────────────────
    state.deleteTarget?.let { rule ->
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(PricingRuleIntent.DismissDelete) },
            title = { Text("Delete Pricing Rule?") },
            text = {
                Text("\"${rule.description.ifBlank { "Rule for ${rule.productId.take(8)}" }}\" will be permanently deleted.")
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.onIntent(PricingRuleIntent.ExecuteDelete) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onIntent(PricingRuleIntent.DismissDelete) }) { Text("Cancel") }
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Private: Pricing Rule Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PricingRuleCard(
    rule: PricingRule,
    productName: String?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onEdit() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (rule.isActive)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ZyntaSpacing.md, vertical = ZyntaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.AttachMoney,
                contentDescription = null,
                tint = if (rule.isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.width(ZyntaSpacing.md))

            Column(Modifier.weight(1f)) {
                Text(
                    rule.description.ifBlank { productName ?: "Product ${rule.productId.take(8)}" },
                    style = MaterialTheme.typography.titleSmall,
                    color = if (rule.isActive) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Price chip
                    AssistChip(
                        onClick = {},
                        label = { Text("${"%.2f".format(rule.price)}", style = MaterialTheme.typography.labelSmall) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                    // Scope chip
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                if (rule.storeId != null) "Store" else "Global",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (rule.storeId != null)
                                MaterialTheme.colorScheme.tertiaryContainer
                            else
                                MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    )
                    // Priority chip
                    if (rule.priority > 0) {
                        AssistChip(
                            onClick = {},
                            label = { Text("P${rule.priority}", style = MaterialTheme.typography.labelSmall) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            ),
                        )
                    }
                    // Inactive badge
                    if (!rule.isActive) {
                        AssistChip(
                            onClick = {},
                            label = { Text("Inactive", style = MaterialTheme.typography.labelSmall) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                        )
                    }
                }
            }

            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Private: Edit Dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PricingRuleEditDialog(
    state: PricingRuleState,
    onUpdateField: (PricingField, String) -> Unit,
    onSetActive: (Boolean) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isEditing = state.editingRule != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit Pricing Rule" else "New Pricing Rule") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
                modifier = Modifier.widthIn(min = 280.dp),
            ) {
                // Description
                OutlinedTextField(
                    value = state.formDescription,
                    onValueChange = { onUpdateField(PricingField.DESCRIPTION, it) },
                    label = { Text("Description") },
                    placeholder = { Text("e.g. Summer Sale, Colombo Region") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Product ID
                OutlinedTextField(
                    value = state.formProductId,
                    onValueChange = { onUpdateField(PricingField.PRODUCT_ID, it) },
                    label = { Text("Product ID *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Store ID
                OutlinedTextField(
                    value = state.formStoreId,
                    onValueChange = { onUpdateField(PricingField.STORE_ID, it) },
                    label = { Text("Store ID") },
                    placeholder = { Text("Leave blank for global rule") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Price
                OutlinedTextField(
                    value = state.formPrice,
                    onValueChange = { onUpdateField(PricingField.PRICE, it) },
                    label = { Text("Price *") },
                    leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Cost Price
                OutlinedTextField(
                    value = state.formCostPrice,
                    onValueChange = { onUpdateField(PricingField.COST_PRICE, it) },
                    label = { Text("Cost Price") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Priority
                OutlinedTextField(
                    value = state.formPriority,
                    onValueChange = { onUpdateField(PricingField.PRIORITY, it) },
                    label = { Text("Priority") },
                    placeholder = { Text("Higher = precedence") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Active toggle
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = ZyntaSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Active", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = state.formIsActive, onCheckedChange = { onSetActive(it) })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                enabled = !state.isSaving,
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Save")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
