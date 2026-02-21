package com.zyntasolutions.zyntapos.feature.inventory

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
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.UnitOfMeasure

/**
 * Unit of Measure management screen — Sprint 19, Step 10.1.11.
 *
 * Manages groups of [UnitOfMeasure] items. Within each group:
 * - One unit must be flagged as **base unit** (conversionRate = 1.0).
 * - Other units have a **conversion rate** relative to the base.
 *
 * ### Layout
 * A [LazyColumn] where each group is a [Card] containing:
 * - Group name header + "Add Unit" button
 * - Per-unit rows with: name, abbreviation, conversion rate field, base unit toggle
 * - Inline edit is triggered by tapping a row → the row expands to show editable fields
 *
 * ### Data model used (Phase 1 simplification)
 * Groups are represented via [UnitGroup] data class (UI-layer only).
 * Persistence maps to `unit_groups` + `units` tables via use-cases (Phase 2).
 *
 * @param unitGroups         All unit groups with their units.
 * @param isLoading          True while async load is in-flight.
 * @param onSaveUnit         Called when user confirms a unit edit/add.
 * @param onDeleteUnit       Called when user deletes a unit.
 * @param onSaveGroup        Called when user adds or renames a group.
 * @param modifier           Optional root modifier.
 */
@Composable
fun UnitManagementScreen(
    unitGroups: List<UnitGroup> = emptyList(),
    isLoading: Boolean = false,
    onSaveUnit: (groupId: String, unit: UnitOfMeasure) -> Unit = { _, _ -> },
    onDeleteUnit: (unitId: String) -> Unit = {},
    onSaveGroup: (group: UnitGroup) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showAddGroupDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Units of Measure") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddGroupDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Group") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { innerPadding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (unitGroups.isEmpty()) {
            UnitEmptyState(onAdd = { showAddGroupDialog = true }, modifier = Modifier.padding(innerPadding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(
                    horizontal = ZyntaSpacing.md,
                    vertical = ZyntaSpacing.sm,
                ),
                verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
            ) {
                items(unitGroups, key = { it.id }) { group ->
                    UnitGroupCard(
                        group = group,
                        onSaveUnit = { unit -> onSaveUnit(group.id, unit) },
                        onDeleteUnit = onDeleteUnit,
                        onRenameGroup = { newName -> onSaveGroup(group.copy(name = newName)) },
                    )
                }
                item { Spacer(Modifier.height(88.dp)) } // FAB clearance
            }
        }
    }

    // ── Add Group Dialog ─────────────────────────────────────────────────
    if (showAddGroupDialog) {
        AddGroupDialog(
            onConfirm = { name ->
                onSaveGroup(UnitGroup(id = "", name = name, units = emptyList()))
                showAddGroupDialog = false
            },
            onDismiss = { showAddGroupDialog = false },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// UnitGroup UI model (screen-layer only)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * UI-level grouping of [UnitOfMeasure] items for display/management.
 *
 * Maps to the `unit_groups` table in the DB schema. Phase 1 uses this as a
 * local aggregation; the data layer returns a flat list of units that are
 * regrouped in the ViewModel.
 */
data class UnitGroup(
    val id: String,
    val name: String,
    val units: List<UnitOfMeasure>,
)

// ─────────────────────────────────────────────────────────────────────────────
// Private: UnitGroupCard
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun UnitGroupCard(
    group: UnitGroup,
    onSaveUnit: (UnitOfMeasure) -> Unit,
    onDeleteUnit: (String) -> Unit,
    onRenameGroup: (String) -> Unit,
) {
    var showAddUnitDialog by remember { mutableStateOf(false) }
    var editingUnit by remember { mutableStateOf<UnitOfMeasure?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth()) {
            // Group header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ZyntaSpacing.md, vertical = ZyntaSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.ScaleOutlined, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(ZyntaSpacing.sm))
                Text(group.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { showAddUnitDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Unit", style = MaterialTheme.typography.labelMedium)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (group.units.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(ZyntaSpacing.md), contentAlignment = Alignment.Center) {
                    Text("No units defined. Add the first unit above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                // Column headers
                Row(
                    Modifier.fillMaxWidth()
                        .padding(horizontal = ZyntaSpacing.md, vertical = ZyntaSpacing.xs),
                ) {
                    Text("Unit Name", style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(2f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Abbrev.", style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Rate", style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Base", style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(0.8f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(72.dp)) // actions
                }

                group.units.forEach { unit ->
                    UnitRow(
                        unit = unit,
                        hasOtherBase = group.units.any { it.isBaseUnit && it.id != unit.id },
                        onEdit = { editingUnit = unit },
                        onDelete = { onDeleteUnit(unit.id) },
                        onToggleBase = {
                            onSaveUnit(unit.copy(isBaseUnit = !unit.isBaseUnit,
                                conversionRate = if (!unit.isBaseUnit) 1.0 else unit.conversionRate))
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    // ── Add unit dialog ──────────────────────────────────────────────────
    if (showAddUnitDialog) {
        UnitEditDialog(
            existingUnit = null,
            onConfirm = { u -> onSaveUnit(u); showAddUnitDialog = false },
            onDismiss = { showAddUnitDialog = false },
        )
    }

    // ── Edit unit dialog ─────────────────────────────────────────────────
    editingUnit?.let { u ->
        UnitEditDialog(
            existingUnit = u,
            onConfirm = { updated -> onSaveUnit(updated); editingUnit = null },
            onDismiss = { editingUnit = null },
        )
    }
}

@Composable
private fun UnitRow(
    unit: UnitOfMeasure,
    hasOtherBase: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleBase: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = ZyntaSpacing.md, vertical = ZyntaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(unit.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(2f))
        Text(unit.abbreviation, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(
            if (unit.isBaseUnit) "—" else unit.conversionRate.toString(),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(Modifier.weight(0.8f)) {
            Switch(
                checked = unit.isBaseUnit,
                onCheckedChange = { onToggleBase() },
                enabled = !hasOtherBase || unit.isBaseUnit,
                modifier = Modifier.size(height = 24.dp, width = 40.dp),
            )
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Edit, contentDescription = "Edit unit", modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Delete unit", modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.error)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Private: Dialogs
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun UnitEditDialog(
    existingUnit: UnitOfMeasure?,
    onConfirm: (UnitOfMeasure) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(existingUnit?.name ?: "") }
    var abbreviation by remember { mutableStateOf(existingUnit?.abbreviation ?: "") }
    var conversionRate by remember { mutableStateOf(existingUnit?.conversionRate?.toString() ?: "1.0") }
    var isBaseUnit by remember { mutableStateOf(existingUnit?.isBaseUnit ?: false) }
    var nameError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingUnit == null) "Add Unit" else "Edit Unit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                OutlinedTextField(value = name, onValueChange = { name = it; nameError = null },
                    label = { Text("Unit Name *") }, isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = abbreviation, onValueChange = { abbreviation = it },
                    label = { Text("Abbreviation") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = conversionRate,
                    onValueChange = { conversionRate = it },
                    label = { Text("Conversion Rate") },
                    enabled = !isBaseUnit,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = { Text("Multiplier relative to base unit") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = isBaseUnit, onCheckedChange = { isBaseUnit = it })
                    Spacer(Modifier.width(ZyntaSpacing.sm))
                    Text("Base Unit", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) { nameError = "Required"; return@TextButton }
                val rate = conversionRate.toDoubleOrNull() ?: 1.0
                onConfirm(UnitOfMeasure(
                    id = existingUnit?.id ?: "",
                    name = name.trim(),
                    abbreviation = abbreviation.trim(),
                    isBaseUnit = isBaseUnit,
                    conversionRate = if (isBaseUnit) 1.0 else rate,
                ))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AddGroupDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Unit Group") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it },
                label = { Text("Group Name *") },
                placeholder = { Text("e.g. Weight, Volume, Count") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun UnitEmptyState(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Scale, contentDescription = null, modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(ZyntaSpacing.md))
            Text("No unit groups defined", style = MaterialTheme.typography.titleMedium)
            Text("Create a group (e.g. Weight) then add units",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(ZyntaSpacing.lg))
            Button(onClick = onAdd) { Icon(Icons.Default.Add, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("New Group") }
        }
    }
}
