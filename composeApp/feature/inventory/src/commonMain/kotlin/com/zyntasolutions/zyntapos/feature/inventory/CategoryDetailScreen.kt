package com.zyntasolutions.zyntapos.feature.inventory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaPageScaffold
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.domain.model.Category

/**
 * Category create/edit screen — Sprint 19, Step 10.1.8.
 *
 * Renders a vertical form that allows the user to:
 * - Enter the category **name** (required)
 * - Select an optional **parent category** from a dropdown of root categories
 * - Specify a **display order** integer
 * - Toggle **active/inactive** status
 * - Provide an optional **image URL** (Coil-backed preview)
 *
 * Submitting the form calls [onConfirm] which triggers the domain use-case
 * (insert or update) in the caller's ViewModel.
 *
 * @param existingCategory  The category being edited, or null when creating a new one.
 * @param allCategories     Full category list used to populate the parent dropdown.
 *                          Parent selector excludes the category itself and its children.
 * @param isLoading         True while a save operation is in-flight.
 * @param errorMessage      Validation/server error to display at the top.
 * @param onConfirm         Called with the updated [Category] when the user confirms.
 * @param onNavigateBack    Called when the user taps Back / Cancel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailScreen(
    existingCategory: Category? = null,
    allCategories: List<Category> = emptyList(),
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onConfirm: (Category) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val isEditing = existingCategory != null

    // ── Form State ───────────────────────────────────────────────────────────
    var name by remember(existingCategory) { mutableStateOf(existingCategory?.name ?: "") }
    var selectedParentId by remember(existingCategory) { mutableStateOf(existingCategory?.parentId) }
    var displayOrder by remember(existingCategory) { mutableStateOf((existingCategory?.displayOrder ?: 0).toString()) }
    var imageUrl by remember(existingCategory) { mutableStateOf(existingCategory?.imageUrl ?: "") }
    var isActive by remember(existingCategory) { mutableStateOf(existingCategory?.isActive ?: true) }

    // Local validation
    var nameError by remember { mutableStateOf<String?>(null) }
    var displayOrderError by remember { mutableStateOf<String?>(null) }

    // Parent candidates: all root categories, excluding self
    val parentCandidates = remember(allCategories, existingCategory) {
        allCategories.filter { cat ->
            cat.parentId == null &&                    // only roots as parents
            cat.id != existingCategory?.id             // exclude self
        }.sortedBy { it.name }
    }

    var parentDropdownExpanded by remember { mutableStateOf(false) }
    val selectedParentName = parentCandidates.find { it.id == selectedParentId }?.name ?: s[StringResource.INVENTORY_NONE_ROOT]

    ZyntaPageScaffold(
        title = if (isEditing) s[StringResource.INVENTORY_EDIT_CATEGORY] else s[StringResource.INVENTORY_NEW_CATEGORY],
        modifier = modifier,
        onNavigateBack = onNavigateBack,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ZyntaSpacing.md, vertical = ZyntaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
        ) {
            // ── Error banner ─────────────────────────────────────────────
            if (errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Row(
                        Modifier.padding(ZyntaSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(ZyntaSpacing.sm))
                        Text(errorMessage, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            // ── Name Field ───────────────────────────────────────────────
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    nameError = if (it.isBlank()) "Name is required" else null
                },
                label = { Text(s[StringResource.INVENTORY_CATEGORY_NAME_REQUIRED]) },
                placeholder = { Text("e.g. Beverages") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null) },
                isError = nameError != null,
                supportingText = nameError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // ── Parent Category Dropdown ──────────────────────────────────
            ExposedDropdownMenuBox(
                expanded = parentDropdownExpanded,
                onExpandedChange = { parentDropdownExpanded = it },
            ) {
                OutlinedTextField(
                    value = selectedParentName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(s[StringResource.INVENTORY_PARENT_CATEGORY]) },
                    leadingIcon = { Icon(Icons.Default.AccountTree, contentDescription = null) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = parentDropdownExpanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = parentDropdownExpanded,
                    onDismissRequest = { parentDropdownExpanded = false },
                ) {
                    // Option: No parent (root)
                    DropdownMenuItem(
                        text = { Text(s[StringResource.INVENTORY_NONE_ROOT]) },
                        onClick = {
                            selectedParentId = null
                            parentDropdownExpanded = false
                        },
                        leadingIcon = {
                            if (selectedParentId == null)
                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                        },
                    )
                    HorizontalDivider()
                    parentCandidates.forEach { parent ->
                        DropdownMenuItem(
                            text = { Text(parent.name) },
                            onClick = {
                                selectedParentId = parent.id
                                parentDropdownExpanded = false
                            },
                            leadingIcon = {
                                if (selectedParentId == parent.id)
                                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                            },
                        )
                    }
                }
            }

            // ── Display Order Field ──────────────────────────────────────
            OutlinedTextField(
                value = displayOrder,
                onValueChange = { v ->
                    displayOrder = v.filter { it.isDigit() }
                    displayOrderError = if (displayOrder.isBlank()) "Required" else null
                },
                label = { Text(s[StringResource.INVENTORY_DISPLAY_ORDER]) },
                placeholder = { Text("0") },
                leadingIcon = { Icon(Icons.Default.FormatListNumbered, contentDescription = null) },
                isError = displayOrderError != null,
                supportingText = {
                    Text(displayOrderError ?: s[StringResource.INVENTORY_DISPLAY_ORDER_HINT])
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // ── Image URL Field ──────────────────────────────────────────
            OutlinedTextField(
                value = imageUrl,
                onValueChange = { imageUrl = it },
                label = { Text(s[StringResource.INVENTORY_IMAGE_URL_OPTIONAL]) },
                placeholder = { Text("https://…") },
                leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // ── Active Toggle ─────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = ZyntaSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(s[StringResource.INVENTORY_ACTIVE], style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (isActive) s[StringResource.INVENTORY_CATEGORY_VISIBLE]
                        else s[StringResource.INVENTORY_CATEGORY_HIDDEN],
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = isActive,
                    onCheckedChange = { isActive = it },
                )
            }

            Spacer(Modifier.height(ZyntaSpacing.sm))

            // ── Action Buttons ────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
            ) {
                OutlinedButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(s[StringResource.COMMON_CANCEL])
                }
                Button(
                    onClick = {
                        // Validate before submitting
                        var valid = true
                        if (name.isBlank()) {
                            nameError = "Name is required"
                            valid = false
                        }
                        val orderInt = displayOrder.toIntOrNull() ?: run {
                            displayOrderError = "Must be a valid number"
                            valid = false
                            0
                        }
                        if (valid) {
                            val category = Category(
                                id = existingCategory?.id ?: "",
                                name = name.trim(),
                                parentId = selectedParentId,
                                imageUrl = imageUrl.trim().ifBlank { null },
                                displayOrder = orderInt,
                                isActive = isActive,
                            )
                            onConfirm(category)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading,
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(ZyntaSpacing.sm))
                    }
                    Text(if (isEditing) s[StringResource.INVENTORY_UPDATE] else s[StringResource.INVENTORY_CREATE])
                }
            }
        }
    }
}
