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
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
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
    val s = LocalStrings.current
    var showEditDialog by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<TaxGroup?>(null) }

    ZyntaPageScaffold(
        title = s[StringResource.INVENTORY_TAX_GROUPS],
        modifier = modifier,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editingGroup = null; showEditDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(s[StringResource.INVENTORY_NEW_TAX_GROUP]) },
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
                title = s[StringResource.INVENTORY_NO_TAX_GROUPS],
                icon = Icons.Default.Percent,
                subtitle = s[StringResource.INVENTORY_NO_TAX_GROUPS_SUBTITLE],
                ctaLabel = s[StringResource.INVENTORY_NEW_TAX_GROUP],
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
    val s = LocalStrings.current
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
                                if (group.isInclusive) s[StringResource.INVENTORY_INCLUSIVE] else s[StringResource.INVENTORY_EXCLUSIVE],
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
                            label = { Text(s[StringResource.INVENTORY_INACTIVE], style = MaterialTheme.typography.labelSmall) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                        )
                    }
                }
            }

            // Actions
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Edit, contentDescription = s[StringResource.COMMON_EDIT], modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, contentDescription = s[StringResource.COMMON_DELETE], modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(s[StringResource.INVENTORY_DELETE_TAX_GROUP_TITLE]) },
            text = {
                Text(s[StringResource.INVENTORY_TAX_GROUP_DEACTIVATE_MSG, group.name])
            },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteDialog = false; onDelete() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(s[StringResource.COMMON_DELETE]) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text(s[StringResource.COMMON_CANCEL]) } },
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
    val s = LocalStrings.current
    var name by remember(existingGroup) { mutableStateOf(existingGroup?.name ?: "") }
    var rate by remember(existingGroup) { mutableStateOf(existingGroup?.rate?.let { "%.2f".format(it) } ?: "0.00") }
    var isInclusive by remember(existingGroup) { mutableStateOf(existingGroup?.isInclusive ?: false) }
    var isActive by remember(existingGroup) { mutableStateOf(existingGroup?.isActive ?: true) }

    var nameError by remember { mutableStateOf<String?>(null) }
    var rateError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingGroup == null) s[StringResource.INVENTORY_NEW_TAX_GROUP] else s[StringResource.INVENTORY_EDIT_TAX_GROUP]) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = if (it.isBlank()) s[StringResource.COMMON_REQUIRED] else null },
                    label = { Text(s[StringResource.INVENTORY_TAX_NAME_REQUIRED]) },
                    placeholder = { Text(s[StringResource.INVENTORY_TAX_GROUP_NAME_PLACEHOLDER]) },
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
                            d == null -> s[StringResource.COMMON_REQUIRED]
                            d < 0.0 || d > 100.0 -> s[StringResource.INVENTORY_TAX_RATE_REQUIRED]
                            else -> null
                        }
                    },
                    label = { Text(s[StringResource.INVENTORY_TAX_RATE_REQUIRED]) },
                    leadingIcon = { Icon(Icons.Default.Percent, contentDescription = null) },
                    isError = rateError != null,
                    supportingText = { Text(rateError ?: s[StringResource.INVENTORY_TAX_RATE_HINT]) },
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
                        Text(s[StringResource.INVENTORY_TAX_INCLUSIVE], style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (isInclusive) s[StringResource.INVENTORY_TAX_INCLUSIVE_HINT]
                            else s[StringResource.INVENTORY_TAX_EXCLUSIVE_HINT],
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
                    Text(s[StringResource.INVENTORY_ACTIVE], style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                var valid = true
                if (name.isBlank()) { nameError = s[StringResource.COMMON_REQUIRED]; valid = false }
                val rateVal = rate.toDoubleOrNull()
                if (rateVal == null || rateVal < 0.0 || rateVal > 100.0) {
                    rateError = s[StringResource.INVENTORY_TAX_RATE_REQUIRED]; valid = false
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
            }) { Text(s[StringResource.COMMON_SAVE]) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s[StringResource.COMMON_CANCEL]) } },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
