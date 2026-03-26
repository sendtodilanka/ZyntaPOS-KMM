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
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
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
    val s = LocalStrings.current
    val state by viewModel.state.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dispatch(PricingRuleIntent.DismissError)
        }
    }
    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dispatch(PricingRuleIntent.DismissSuccess)
        }
    }

    ZyntaPageScaffold(
        title = s[StringResource.INVENTORY_PRICING_RULES],
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.dispatch(PricingRuleIntent.OpenDialog(null)) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(s[StringResource.INVENTORY_NEW_RULE]) },
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
                title = s[StringResource.INVENTORY_NO_PRICING_RULES],
                icon = Icons.Default.AttachMoney,
                subtitle = s[StringResource.INVENTORY_NO_PRICING_RULES_SUBTITLE],
                ctaLabel = s[StringResource.INVENTORY_NEW_RULE],
                onCtaClick = { viewModel.dispatch(PricingRuleIntent.OpenDialog(null)) },
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
                        onEdit = { viewModel.dispatch(PricingRuleIntent.OpenDialog(rule)) },
                        onDelete = { viewModel.dispatch(PricingRuleIntent.ConfirmDelete(rule)) },
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
                viewModel.dispatch(PricingRuleIntent.UpdateField(field, value))
            },
            onSetActive = { viewModel.dispatch(PricingRuleIntent.SetActive(it)) },
            onSave = { viewModel.dispatch(PricingRuleIntent.SaveRule) },
            onDismiss = { viewModel.dispatch(PricingRuleIntent.DismissDialog) },
        )
    }

    // ── Delete Confirm Dialog ────────────────────────────────────────────
    state.deleteTarget?.let { rule ->
        AlertDialog(
            onDismissRequest = { viewModel.dispatch(PricingRuleIntent.DismissDelete) },
            title = { Text(s[StringResource.INVENTORY_DELETE_PRICING_RULE_TITLE]) },
            text = {
                Text("\"${rule.description.ifBlank { "Rule for ${rule.productId.take(8)}" }}\" will be permanently deleted.")
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.dispatch(PricingRuleIntent.ExecuteDelete) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(s[StringResource.COMMON_DELETE]) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dispatch(PricingRuleIntent.DismissDelete) }) { Text(s[StringResource.COMMON_CANCEL]) }
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
    val s = LocalStrings.current
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
                                if (rule.storeId != null) s[StringResource.INVENTORY_STORE] else s[StringResource.INVENTORY_GLOBAL],
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
                            label = { Text(s[StringResource.INVENTORY_INACTIVE], style = MaterialTheme.typography.labelSmall) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                        )
                    }
                }
            }

            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Edit, contentDescription = s[StringResource.COMMON_EDIT], modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, contentDescription = s[StringResource.COMMON_DELETE], modifier = Modifier.size(18.dp),
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
    val s = LocalStrings.current
    val isEditing = state.editingRule != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) s[StringResource.INVENTORY_EDIT_PRICING_RULE] else s[StringResource.INVENTORY_NEW_PRICING_RULE]) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
                modifier = Modifier.widthIn(min = 280.dp),
            ) {
                // Description
                OutlinedTextField(
                    value = state.formDescription,
                    onValueChange = { onUpdateField(PricingField.DESCRIPTION, it) },
                    label = { Text(s[StringResource.INVENTORY_DESCRIPTION]) },
                    placeholder = { Text("e.g. Summer Sale, Colombo Region") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Product ID
                OutlinedTextField(
                    value = state.formProductId,
                    onValueChange = { onUpdateField(PricingField.PRODUCT_ID, it) },
                    label = { Text(s[StringResource.INVENTORY_PRODUCT_ID_REQUIRED]) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Store ID
                OutlinedTextField(
                    value = state.formStoreId,
                    onValueChange = { onUpdateField(PricingField.STORE_ID, it) },
                    label = { Text(s[StringResource.INVENTORY_STORE_ID]) },
                    placeholder = { Text(s[StringResource.INVENTORY_GLOBAL_RULE_HINT]) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Price
                OutlinedTextField(
                    value = state.formPrice,
                    onValueChange = { onUpdateField(PricingField.PRICE, it) },
                    label = { Text(s[StringResource.INVENTORY_PRICE_REQUIRED]) },
                    leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Cost Price
                OutlinedTextField(
                    value = state.formCostPrice,
                    onValueChange = { onUpdateField(PricingField.COST_PRICE, it) },
                    label = { Text(s[StringResource.INVENTORY_COST_PRICE]) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Priority
                OutlinedTextField(
                    value = state.formPriority,
                    onValueChange = { onUpdateField(PricingField.PRIORITY, it) },
                    label = { Text(s[StringResource.INVENTORY_PRIORITY]) },
                    placeholder = { Text(s[StringResource.INVENTORY_PRIORITY_HINT]) },
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
