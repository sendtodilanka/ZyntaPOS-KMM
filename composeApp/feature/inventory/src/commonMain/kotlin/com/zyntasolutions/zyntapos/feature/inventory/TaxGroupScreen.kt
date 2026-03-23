package com.zyntasolutions.zyntapos.feature.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
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
import com.zyntasolutions.zyntapos.domain.model.TaxGroup

/**
 * Tax Group management screen — Sprint 19, Step 10.1.12.
 *
 * Provides a full-screen list of [TaxGroup] items with inline FAB-driven
 * create and tap-to-edit functionality. Used from both the Inventory module
 * (product tax assignment) and the Settings module (tax configuration).
 *
 * ### Features
 * - Scrollable list of tax groups with name, rate %, inclusive badge
 * - Tap any row → opens [TaxGroupEditDialog] for editing
 * - FAB → [TaxGroupEditDialog] for creating a new tax group
 * - Active/inactive toggle per group
 * - Delete with confirmation dialog
 *
 * ### Business Rules (from domain [TaxGroup])
 * - Rate must be in 0.0–100.0 range.
 * - Inclusive: tax already included in product price.
 * - Exclusive: tax added on top of product price at checkout.
 *
 * @param taxGroups          Current list of tax groups.
 * @param isLoading          True while async load/save is in-flight.
 * @param onSaveTaxGroup     Called with [TaxGroup] on create or edit confirmation.
 *                           Caller decides insert vs update based on [TaxGroup.id].
 * @param onDeleteTaxGroup   Called with the group ID on delete confirmation.
 * @param modifier           Optional root modifier.
 */
@Composable
fun TaxGroupScreen(
    taxGroups: List<TaxGroup> = emptyList(),
    isLoading: Boolean = false,
    onSaveTaxGroup: (TaxGroup) -> Unit = {},
    onDeleteTaxGroup: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<TaxGroup?>(null) }

    ZyntaPageScaffold(
        title = "Tax Groups",
        modifier = modifier,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editingGroup = null; showEditDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Tax Group") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { innerPadding ->
        when {
            isLoading -> Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            taxGroups.isEmpty() -> ZyntaEmptyState(
                title = "No tax groups configured",
                icon = Icons.Default.Percent,
                subtitle = "Create your first tax group to assign to products",
                ctaLabel = "New Tax Group",
                onCtaClick = { editingGroup = null; showEditDialog = true },
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
                items(taxGroups, key = { it.id }) { group ->
                    TaxGroupCard(
                        group = group,
                        onEdit = { editingGroup = group; showEditDialog = true },
                        onDelete = { onDeleteTaxGroup(group.id) },
                    )
                }
                item { Spacer(Modifier.height(88.dp)) } // FAB clearance
            }
        }
    }

    // ── Edit / Create Dialog ─────────────────────────────────────────────
    if (showEditDialog) {
        TaxGroupEditDialog(
            existingGroup = editingGroup,
            onConfirm = { group ->
                onSaveTaxGroup(group)
                showEditDialog = false
                editingGroup = null
            },
            onDismiss = { showEditDialog = false; editingGroup = null },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Private: Tax Group Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TaxGroupCard(
    group: TaxGroup,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onEdit() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (group.isActive)
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
            // Tax icon
            Icon(
                Icons.Default.Percent,
                contentDescription = null,
                tint = if (group.isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.width(ZyntaSpacing.md))

            // Name + badges
            Column(Modifier.weight(1f)) {
                Text(
                    group.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (group.isActive) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Rate chip
                    AssistChip(
                        onClick = {},
                        label = { Text("${"%.2f".format(group.rate)}%", style = MaterialTheme.typography.labelSmall) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                    // Inclusive/Exclusive chip
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                if (group.isInclusive) "Inclusive" else "Exclusive",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (group.isInclusive)
                                MaterialTheme.colorScheme.tertiaryContainer
                            else
                                MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    )
                    // Inactive badge
                    if (!group.isActive) {
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

            // Actions
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Tax Group?") },
            text = {
                Text(
                    "\"${group.name}\" will be removed. Products using this tax group will retain their current rates for historical orders.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteDialog = false; onDelete() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Private: Edit Dialog
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Dialog for creating or editing a [TaxGroup].
 *
 * Validates:
 * - Name is not blank.
 * - Rate is a valid double in 0.0–100.0.
 *
 * @param existingGroup  The group to edit, or null to create a new one.
 * @param onConfirm      Called with the valid [TaxGroup] when confirmed.
 * @param onDismiss      Called when the user cancels.
 */
@Composable
private fun TaxGroupEditDialog(
    existingGroup: TaxGroup?,
    onConfirm: (TaxGroup) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(existingGroup) { mutableStateOf(existingGroup?.name ?: "") }
    var rate by remember(existingGroup) { mutableStateOf(existingGroup?.rate?.let { "%.2f".format(it) } ?: "0.00") }
    var isInclusive by remember(existingGroup) { mutableStateOf(existingGroup?.isInclusive ?: false) }
    var isActive by remember(existingGroup) { mutableStateOf(existingGroup?.isActive ?: true) }

    var nameError by remember { mutableStateOf<String?>(null) }
    var rateError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingGroup == null) "New Tax Group" else "Edit Tax Group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = if (it.isBlank()) "Required" else null },
                    label = { Text("Name *") },
                    placeholder = { Text("e.g. VAT 15%") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null) },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Rate
                OutlinedTextField(
                    value = rate,
                    onValueChange = {
                        rate = it
                        val d = it.toDoubleOrNull()
                        rateError = when {
                            d == null -> "Must be a valid number"
                            d < 0.0 || d > 100.0 -> "Rate must be 0–100"
                            else -> null
                        }
                    },
                    label = { Text("Rate (%)  *") },
                    leadingIcon = { Icon(Icons.Default.Percent, contentDescription = null) },
                    isError = rateError != null,
                    supportingText = { Text(rateError ?: "Enter percentage, e.g. 15.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Inclusive toggle
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = ZyntaSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Tax Inclusive", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (isInclusive) "Price already includes this tax"
                            else "Tax added on top of price",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = isInclusive, onCheckedChange = { isInclusive = it })
                }

                // Active toggle
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = ZyntaSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Active", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                var valid = true
                if (name.isBlank()) { nameError = "Required"; valid = false }
                val rateVal = rate.toDoubleOrNull()
                if (rateVal == null || rateVal < 0.0 || rateVal > 100.0) {
                    rateError = "Rate must be 0–100"; valid = false
                }
                if (valid) {
                    onConfirm(TaxGroup(
                        id = existingGroup?.id ?: com.zyntasolutions.zyntapos.core.utils.IdGenerator.newId(),
                        name = name.trim(),
                        rate = rateVal!!,
                        isInclusive = isInclusive,
                        isActive = isActive,
                    ))
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
