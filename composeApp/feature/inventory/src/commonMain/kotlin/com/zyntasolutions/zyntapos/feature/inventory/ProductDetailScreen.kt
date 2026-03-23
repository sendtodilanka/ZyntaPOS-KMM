package com.zyntasolutions.zyntapos.feature.inventory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButton
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaTextField
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaPageScaffold
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.designsystem.util.FilePickerMode
import com.zyntasolutions.zyntapos.designsystem.util.PlatformFilePicker
import com.zyntasolutions.zyntapos.designsystem.util.WindowSize
import com.zyntasolutions.zyntapos.designsystem.util.currentWindowSize
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.zyntasolutions.zyntapos.domain.model.Category
import com.zyntasolutions.zyntapos.domain.model.ProductVariant
import com.zyntasolutions.zyntapos.domain.model.TaxGroup
import com.zyntasolutions.zyntapos.domain.model.UnitOfMeasure

/**
 * Product create/edit form screen (Sprint 18, task 10.1.2).
 *
 * ### Tab-based layout (NexoPOS-style)
 * Uses a [TabRow] to organize product fields into logical tabs:
 * - **Identification**: Name, barcode, SKU, category, unit, description, active toggle
 * - **Pricing**: Price, cost price, tax group
 * - **Stock**: Qty, min stock threshold
 * - **Variants**: Product variation management (add/remove)
 * - **Images**: Image URL / file picker
 *
 * ### Responsive
 * - **Expanded:** Two-column layout within each tab (fields + helpers side-by-side)
 * - **Medium/Compact:** Single-column scrollable form with tabs
 *
 * @param state    Current [InventoryState] snapshot.
 * @param onIntent Dispatches [InventoryIntent] to the ViewModel.
 * @param onBack   Navigation callback to return to the list.
 * @param modifier Optional root [Modifier].
 */
@Composable
fun ProductDetailScreen(
    state: InventoryState,
    onIntent: (InventoryIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val form = state.editFormState
    val isNew = form.id == null
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Identification", "Pricing", "Stock", "Variants", "Images")

    // INV-9: Track initial form state to detect unsaved changes
    val initialForm = remember(state.selectedProduct) { form }
    val isDirty = form != initialForm
    var showDiscardDialog by remember { mutableStateOf(false) }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard changes?") },
            text = { Text("You have unsaved changes. Are you sure you want to go back?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Keep editing") }
            },
        )
    }

    // INV-10: TaxGroupScreen as full-screen dialog
    if (state.showTaxGroupManagement) {
        AlertDialog(
            onDismissRequest = { onIntent(InventoryIntent.CloseTaxGroupManagement) },
            confirmButton = {},
            title = null,
            text = {
                TaxGroupScreen(
                    taxGroups = state.allTaxGroups,
                    isLoading = state.isLoading,
                    onSaveTaxGroup = { onIntent(InventoryIntent.SaveTaxGroup(it)) },
                    onDeleteTaxGroup = { onIntent(InventoryIntent.DeleteTaxGroup(it)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )
    }

    ZyntaPageScaffold(
        title = if (isNew) "New Product" else "Edit Product",
        modifier = modifier,
        onNavigateBack = {
            if (isDirty) showDiscardDialog = true else onBack()
        },
        actions = {
            FilledTonalButton(
                onClick = { onIntent(InventoryIntent.SaveProduct) },
                enabled = !state.isLoading,
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(ZyntaSpacing.xs))
                Text(if (isNew) "Create" else "Save")
            }
        },
    ) { innerPadding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@ZyntaPageScaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // ── Tab Row ─────────────────────────────────────────────────
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = ZyntaSpacing.md,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = { HorizontalDivider() },
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                        icon = {
                            Icon(
                                imageVector = when (index) {
                                    0 -> Icons.Default.Badge
                                    1 -> Icons.Default.AttachMoney
                                    2 -> Icons.Default.Inventory
                                    3 -> Icons.Default.Layers
                                    else -> Icons.Default.Image
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }

            // ── Tab Content ─────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = ZyntaSpacing.md)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
            ) {
                Spacer(Modifier.height(ZyntaSpacing.sm))
                when (selectedTab) {
                    0 -> {
                        CoreFieldsSection(form, state, onIntent)
                        ActiveToggleSection(form, onIntent)
                    }
                    1 -> PricingSection(form, state, onIntent)
                    2 -> StockSection(form, isNew, onIntent)
                    3 -> VariantSection(state.productVariants, onIntent)
                    4 -> ImageSection(form, onIntent)
                }
                Spacer(Modifier.height(ZyntaSpacing.xxl))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Form Sections
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Core product fields: name, barcode, SKU, category selector, unit selector.
 */
@Composable
private fun CoreFieldsSection(
    form: ProductFormState,
    state: InventoryState,
    onIntent: (InventoryIntent) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(ZyntaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
        ) {
            Text("Product Details", style = MaterialTheme.typography.titleMedium)

            // Name (required)
            ZyntaTextField(
                value = form.name,
                onValueChange = { onIntent(InventoryIntent.UpdateFormField("name", it)) },
                label = "Product Name *",
                error = form.validationErrors["name"],
                modifier = Modifier.fillMaxWidth(),
            )

            // Barcode
            Row(horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                ZyntaTextField(
                    value = form.barcode,
                    onValueChange = { onIntent(InventoryIntent.UpdateFormField("barcode", it)) },
                    label = "Barcode (EAN-13 / Code128)",
                    error = form.validationErrors["barcode"],
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        if (state.isScannerActive) {
                            onIntent(InventoryIntent.StopBarcodeScanner)
                        } else {
                            onIntent(InventoryIntent.StartBarcodeScanner)
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterVertically),
                ) {
                    Icon(
                        Icons.Default.QrCodeScanner,
                        contentDescription = if (state.isScannerActive) "Stop scanner" else "Scan barcode",
                        tint = if (state.isScannerActive) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                }
            }

            // SKU
            ZyntaTextField(
                value = form.sku,
                onValueChange = { onIntent(InventoryIntent.UpdateFormField("sku", it)) },
                label = "SKU",
                error = form.validationErrors["sku"],
                modifier = Modifier.fillMaxWidth(),
            )

            // Category selector
            CategoryDropdown(
                categories = state.categories,
                selectedId = form.categoryId,
                onSelect = { onIntent(InventoryIntent.UpdateFormField("categoryId", it)) },
                isError = form.validationErrors.containsKey("categoryId"),
                errorText = form.validationErrors["categoryId"],
            )

            // Unit selector
            UnitDropdown(
                units = state.allUnits,
                selectedId = form.unitId,
                onSelect = { onIntent(InventoryIntent.UpdateFormField("unitId", it)) },
                isError = form.validationErrors.containsKey("unitId"),
                errorText = form.validationErrors["unitId"],
            )

            // Description
            ZyntaTextField(
                value = form.description,
                onValueChange = { onIntent(InventoryIntent.UpdateFormField("description", it)) },
                label = "Description",
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                singleLine = false,
                maxLines = 4,
            )
        }
    }
}

/**
 * Pricing fields: price, cost price, tax group selector.
 */
@Composable
private fun PricingSection(
    form: ProductFormState,
    state: InventoryState,
    onIntent: (InventoryIntent) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(ZyntaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
        ) {
            Text("Pricing", style = MaterialTheme.typography.titleMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                ZyntaTextField(
                    value = form.price,
                    onValueChange = { onIntent(InventoryIntent.UpdateFormField("price", it)) },
                    label = "Selling Price *",
                    error = form.validationErrors["price"],
                    modifier = Modifier.weight(1f),
                )
                ZyntaTextField(
                    value = form.costPrice,
                    onValueChange = { onIntent(InventoryIntent.UpdateFormField("costPrice", it)) },
                    label = "Cost Price",
                    error = form.validationErrors["costPrice"],
                    modifier = Modifier.weight(1f),
                )
            }

            // Tax group selector + manage link
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    TaxGroupDropdown(
                        taxGroups = state.allTaxGroups,
                        selectedId = form.taxGroupId,
                        onSelect = { onIntent(InventoryIntent.UpdateFormField("taxGroupId", it ?: "")) },
                    )
                }
                TextButton(onClick = { onIntent(InventoryIntent.OpenTaxGroupManagement) }) {
                    Text("Manage", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/**
 * Stock fields: quantity, minimum stock threshold.
 */
@Composable
private fun StockSection(
    form: ProductFormState,
    isNew: Boolean,
    onIntent: (InventoryIntent) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(ZyntaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
        ) {
            Text("Stock", style = MaterialTheme.typography.titleMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                ZyntaTextField(
                    value = form.stockQty,
                    onValueChange = {
                        if (isNew) onIntent(InventoryIntent.UpdateFormField("stockQty", it))
                    },
                    label = if (isNew) "Initial Stock Qty" else "Stock Qty (read-only)",
                    enabled = isNew,
                    modifier = Modifier.weight(1f),
                )
                ZyntaTextField(
                    value = form.minStockQty,
                    onValueChange = { onIntent(InventoryIntent.UpdateFormField("minStockQty", it)) },
                    label = "Low Stock Alert Threshold",
                    modifier = Modifier.weight(1f),
                )
            }

            if (!isNew) {
                Text(
                    "Stock adjustments are made via the Stock Adjustment dialog.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Image picker section — URL input + platform-native file picker + Coil preview (INV-4).
 * Users can either enter a URL directly or browse for a local image file.
 */
@Composable
private fun ImageSection(
    form: ProductFormState,
    onIntent: (InventoryIntent) -> Unit,
) {
    var showImagePicker by remember { mutableStateOf(false) }

    PlatformFilePicker(
        show = showImagePicker,
        mode = FilePickerMode.IMAGE,
        onResult = { pickedFile ->
            showImagePicker = false
            if (pickedFile != null) {
                onIntent(InventoryIntent.UpdateFormField("imageUrl", pickedFile.path))
            }
        },
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(ZyntaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
        ) {
            Text("Image", style = MaterialTheme.typography.titleMedium)

            ZyntaTextField(
                value = form.imageUrl ?: "",
                onValueChange = { onIntent(InventoryIntent.UpdateFormField("imageUrl", it)) },
                label = "Image URL or file path",
                modifier = Modifier.fillMaxWidth(),
            )

            // INV-4: Coil AsyncImage preview
            if (!form.imageUrl.isNullOrBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = form.imageUrl,
                            contentDescription = "Product image preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }

            ZyntaButton(
                text = "Browse Image",
                onClick = { showImagePicker = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Variation management section: add/remove [ProductVariant] rows.
 */
@Composable
private fun VariantSection(
    variants: List<ProductVariant>,
    onIntent: (InventoryIntent) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(ZyntaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Variations", style = MaterialTheme.typography.titleMedium)
                FilledTonalButton(onClick = { onIntent(InventoryIntent.AddVariant) }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(ZyntaSpacing.xs))
                    Text("Add Variant")
                }
            }

            if (variants.isEmpty()) {
                Text(
                    "No variations. Add variants for products with different sizes, colors, etc.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            variants.forEachIndexed { index, variant ->
                VariantRow(
                    variant = variant,
                    index = index,
                    onUpdate = { field, value -> onIntent(InventoryIntent.UpdateVariant(index, field, value)) },
                    onRemove = { onIntent(InventoryIntent.RemoveVariant(index)) },
                )
            }
        }
    }
}

/**
 * Single variant row with name, price override, barcode, stock, and remove button.
 */
@Composable
private fun VariantRow(
    variant: ProductVariant,
    index: Int,
    onUpdate: (String, String) -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(ZyntaSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Variant ${index + 1}", style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove variant",
                        tint = MaterialTheme.colorScheme.error)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                ZyntaTextField(
                    value = variant.name,
                    onValueChange = { onUpdate("name", it) },
                    label = "Variant Name",
                    modifier = Modifier.weight(1f),
                )
                ZyntaTextField(
                    value = variant.price?.toString() ?: "",
                    onValueChange = { onUpdate("price", it) },
                    label = "Price Override",
                    modifier = Modifier.weight(1f),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                ZyntaTextField(
                    value = variant.barcode ?: "",
                    onValueChange = { onUpdate("barcode", it) },
                    label = "Barcode",
                    modifier = Modifier.weight(1f),
                )
                ZyntaTextField(
                    value = variant.stock.toString(),
                    onValueChange = { onUpdate("stock", it) },
                    label = "Stock",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Active/inactive toggle with label.
 */
@Composable
private fun ActiveToggleSection(
    form: ProductFormState,
    onIntent: (InventoryIntent) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ZyntaSpacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Active", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (form.isActive) "Product visible in POS and search"
                    else "Product hidden from POS and search",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = form.isActive,
                onCheckedChange = { onIntent(InventoryIntent.ToggleFormActive) },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Dropdown Selectors
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Category dropdown selector using [ExposedDropdownMenuBox].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    categories: List<Category>,
    selectedId: String,
    onSelect: (String) -> Unit,
    isError: Boolean = false,
    errorText: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = categories.find { it.id == selectedId }?.name ?: ""

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Category *") },
            isError = isError,
            supportingText = errorText?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            categories.forEach { cat ->
                DropdownMenuItem(
                    text = { Text(cat.name) },
                    onClick = {
                        onSelect(cat.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * Unit of measure dropdown selector.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitDropdown(
    units: List<UnitOfMeasure>,
    selectedId: String,
    onSelect: (String) -> Unit,
    isError: Boolean = false,
    errorText: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = units.find { it.id == selectedId }?.let { "${it.name} (${it.abbreviation})" } ?: ""

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Unit *") },
            isError = isError,
            supportingText = errorText?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            units.forEach { unit ->
                DropdownMenuItem(
                    text = { Text("${unit.name} (${unit.abbreviation})") },
                    onClick = {
                        onSelect(unit.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * Tax group dropdown selector with "No Tax" option.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaxGroupDropdown(
    taxGroups: List<TaxGroup>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = taxGroups.find { it.id == selectedId }?.let { "${it.name} (${it.rate}%)" } ?: "No Tax"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Tax Group") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("No Tax") },
                onClick = { onSelect(null); expanded = false },
            )
            taxGroups.filter { it.isActive }.forEach { tg ->
                DropdownMenuItem(
                    text = { Text("${tg.name} (${tg.rate}%)") },
                    onClick = { onSelect(tg.id); expanded = false },
                )
            }
        }
    }
}
