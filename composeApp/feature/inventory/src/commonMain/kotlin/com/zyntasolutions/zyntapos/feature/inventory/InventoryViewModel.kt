package com.zyntasolutions.zyntapos.feature.inventory

import androidx.lifecycle.viewModelScope
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.domain.model.Category
import com.zyntasolutions.zyntapos.domain.model.Product
import com.zyntasolutions.zyntapos.domain.model.ProductVariant
import com.zyntasolutions.zyntapos.domain.model.StockAdjustment
import com.zyntasolutions.zyntapos.domain.model.Supplier
import com.zyntasolutions.zyntapos.domain.model.TaxGroup
import com.zyntasolutions.zyntapos.domain.model.UnitOfMeasure
import com.zyntasolutions.zyntapos.hal.scanner.BarcodeScanner
import com.zyntasolutions.zyntapos.hal.scanner.ScanResult
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.repository.CategoryRepository
import com.zyntasolutions.zyntapos.domain.repository.ProductRepository
import com.zyntasolutions.zyntapos.domain.repository.ProductVariantRepository
import com.zyntasolutions.zyntapos.domain.repository.SupplierRepository
import com.zyntasolutions.zyntapos.domain.repository.TaxGroupRepository
import com.zyntasolutions.zyntapos.domain.repository.UnitGroupRepository
import com.zyntasolutions.zyntapos.domain.validation.ProductValidationParams
import com.zyntasolutions.zyntapos.domain.validation.ProductValidator
import com.zyntasolutions.zyntapos.domain.usecase.inventory.AdjustStockUseCase
import com.zyntasolutions.zyntapos.domain.usecase.inventory.CreateProductUseCase
import com.zyntasolutions.zyntapos.domain.usecase.inventory.SearchProductsUseCase
import com.zyntasolutions.zyntapos.domain.usecase.inventory.UpdateProductUseCase
import com.zyntasolutions.zyntapos.core.analytics.AnalyticsEvents
import com.zyntasolutions.zyntapos.core.analytics.AnalyticsParams
import com.zyntasolutions.zyntapos.core.analytics.AnalyticsTracker
import com.zyntasolutions.zyntapos.security.audit.SecurityAuditLogger
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * Root ViewModel for the Inventory management screens (Sprint 18, task 10.1).
 *
 * ### Responsibilities
 * - Subscribes to [ProductRepository] and [CategoryRepository] as reactive Flows,
 *   pushing filtered snapshots into [InventoryState.products] and [InventoryState.categories].
 * - Maintains a debounced search pipeline (300 ms) via [SearchProductsUseCase].
 * - Supports list/grid toggle, category + stock status filtering, and column sorting.
 * - Manages the product create/edit form lifecycle with validation.
 * - Delegates stock adjustments to [AdjustStockUseCase] with audit trail.
 * - Handles bulk CSV import via batch [CreateProductUseCase] calls.
 *
 * @param productRepository      Product catalogue source.
 * @param categoryRepository     Category list source.
 * @param taxGroupRepository     Tax group list source; populates [InventoryState.allTaxGroups].
 * @param unitGroupRepository    Unit-of-measure list source; populates [InventoryState.allUnits].
 * @param searchProductsUseCase  FTS5 product search (debounced).
 * @param createProductUseCase   Product creation with validation.
 * @param updateProductUseCase   Product update with validation.
 * @param adjustStockUseCase     Stock adjustment with audit trail.
 * @param authRepository         Provides the active auth session for resolving currentUserId.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class InventoryViewModel(
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository,
    private val supplierRepository: SupplierRepository,
    private val taxGroupRepository: TaxGroupRepository,
    private val unitGroupRepository: UnitGroupRepository,
    private val variantRepository: ProductVariantRepository,
    private val barcodeScanner: BarcodeScanner,
    private val _searchProductsUseCase: SearchProductsUseCase,
    private val createProductUseCase: CreateProductUseCase,
    private val updateProductUseCase: UpdateProductUseCase,
    private val adjustStockUseCase: AdjustStockUseCase,
    private val authRepository: AuthRepository,
    private val auditLogger: SecurityAuditLogger,
    private val analytics: AnalyticsTracker,
) : BaseViewModel<InventoryState, InventoryIntent, InventoryEffect>(InventoryState()) {

    private var currentUserId: String = "unknown"

    // ── Reactive filter state flows ───────────────────────────────────────

    private val _searchQuery = MutableStateFlow("")
    private val _selectedCategoryId = MutableStateFlow<String?>(null)

    init {
        analytics.logScreenView("Inventory", "InventoryViewModel")
        viewModelScope.launch {
            currentUserId = authRepository.getSession().first()?.id ?: "unknown"
        }
        observeCategories()
        observeProducts()
        observeTaxGroups()
        observeUnits()
    }

    private fun observeCategories() {
        categoryRepository.getAll()
            .onEach { cats -> updateState { copy(categories = cats) } }
            .launchIn(viewModelScope)
    }

    private fun observeTaxGroups() {
        taxGroupRepository.getAll()
            .onEach { groups -> updateState { copy(allTaxGroups = groups) } }
            .launchIn(viewModelScope)
    }

    private fun observeUnits() {
        unitGroupRepository.getAll()
            .onEach { units -> updateState { copy(allUnits = units) } }
            .launchIn(viewModelScope)
    }

    private fun observeProducts() {
        combine(_searchQuery.debounce(300L), _selectedCategoryId) { q, cat -> q to cat }
            .distinctUntilChanged()
            .flatMapLatest { (q, cat) ->
                if (q.isBlank() && cat == null) {
                    productRepository.getAll()
                } else {
                    productRepository.search(q, cat)
                }
            }
            .onEach { products ->
                val filtered = applyLocalFilters(products)
                val sorted = applySort(filtered)
                updateState { copy(products = sorted, isLoading = false) }
            }
            .launchIn(viewModelScope)
    }

    // ── Intent handler ────────────────────────────────────────────────────

    override suspend fun handleIntent(intent: InventoryIntent) {
        when (intent) {
            is InventoryIntent.LoadProducts -> onLoadProducts()
            is InventoryIntent.SearchQueryChanged -> onSearchQueryChanged(intent.query)
            is InventoryIntent.SelectCategory -> onSelectCategory(intent.categoryId)
            is InventoryIntent.SetStockFilter -> onSetStockFilter(intent.filter)
            is InventoryIntent.ToggleViewMode -> onToggleViewMode()
            is InventoryIntent.SortByColumn -> onSortByColumn(intent.columnKey)
            is InventoryIntent.SelectProduct -> onSelectProduct(intent.productId)
            is InventoryIntent.BackToList -> onBackToList()
            is InventoryIntent.UpdateFormField -> onUpdateFormField(intent.field, intent.value)
            is InventoryIntent.ToggleFormActive -> onToggleFormActive()
            is InventoryIntent.ClearForm -> onClearForm()
            is InventoryIntent.SaveProduct -> onSaveProduct()
            is InventoryIntent.DeleteProduct -> onDeleteProduct(intent.productId)
            is InventoryIntent.AddVariant -> onAddVariant()
            is InventoryIntent.RemoveVariant -> onRemoveVariant(intent.index)
            is InventoryIntent.UpdateVariant -> onUpdateVariant(intent.index, intent.field, intent.value)
            is InventoryIntent.OpenStockAdjustment -> updateState { copy(stockAdjustmentTarget = intent.product) }
            is InventoryIntent.SubmitStockAdjustment -> onSubmitStockAdjustment(intent.type, intent.quantity, intent.reason)
            is InventoryIntent.DismissStockAdjustment -> updateState { copy(stockAdjustmentTarget = null) }
            is InventoryIntent.OpenBarcodeGenerator -> updateState { copy(barcodeGeneratorTarget = intent.product) }
            is InventoryIntent.DismissBarcodeGenerator -> updateState { copy(barcodeGeneratorTarget = null) }
            is InventoryIntent.OpenBulkImport -> updateState { copy(bulkImportState = BulkImportState(isVisible = true)) }
            is InventoryIntent.SetImportFile -> onSetImportFile(intent.fileName, intent.columns, intent.rows)
            is InventoryIntent.SetColumnMapping -> onSetColumnMapping(intent.csvColumn, intent.productField)
            is InventoryIntent.ConfirmBulkImport -> onConfirmBulkImport()
            is InventoryIntent.DismissBulkImport -> updateState { copy(bulkImportState = BulkImportState()) }
            is InventoryIntent.StartBarcodeScanner -> onStartBarcodeScanner()
            is InventoryIntent.StopBarcodeScanner -> onStopBarcodeScanner()
            is InventoryIntent.BarcodeScanResult -> onBarcodeScanResult(intent.barcode)
            is InventoryIntent.DismissError -> updateState { copy(error = null) }
            is InventoryIntent.DismissSuccess -> updateState { copy(successMessage = null) }
            // ── Category Management ─────────────────────────────────────────
            is InventoryIntent.LoadCategories -> onLoadCategories()
            is InventoryIntent.OpenCategoryDetail -> onOpenCategoryDetail(intent.categoryId)
            is InventoryIntent.SaveCategory -> onSaveCategory(intent.category)
            is InventoryIntent.DeleteCategory -> onDeleteCategory(intent.categoryId)
            is InventoryIntent.CloseCategoryDetail -> updateState { copy(showCategoryDetail = false, selectedCategory = null) }
            // ── Supplier Management ─────────────────────────────────────────
            is InventoryIntent.LoadSuppliers -> onLoadSuppliers()
            is InventoryIntent.OpenSupplierDetail -> onOpenSupplierDetail(intent.supplierId)
            is InventoryIntent.SaveSupplier -> onSaveSupplier(intent.supplier)
            is InventoryIntent.DeleteSupplier -> onDeleteSupplier(intent.supplierId)
            is InventoryIntent.CloseSupplierDetail -> updateState { copy(showSupplierDetail = false, selectedSupplier = null) }
            // ── Tax Group Management ────────────────────────────────────────
            is InventoryIntent.OpenTaxGroupManagement -> updateState { copy(showTaxGroupManagement = true) }
            is InventoryIntent.CloseTaxGroupManagement -> updateState { copy(showTaxGroupManagement = false) }
            is InventoryIntent.SaveTaxGroup -> onSaveTaxGroup(intent.taxGroup)
            is InventoryIntent.DeleteTaxGroup -> onDeleteTaxGroup(intent.taxGroupId)
            // ── Unit Management ─────────────────────────────────────────────
            is InventoryIntent.OpenUnitManagement -> updateState { copy(showUnitManagement = true) }
            is InventoryIntent.CloseUnitManagement -> updateState { copy(showUnitManagement = false) }
            is InventoryIntent.SaveUnit -> onSaveUnit(intent.groupId, intent.unit)
            is InventoryIntent.DeleteUnit -> onDeleteUnit(intent.unitId)
            is InventoryIntent.SaveUnitGroup -> onSaveUnitGroup(intent.group)
        }
    }

    // ── Intent handler implementations ────────────────────────────────────

    private fun onLoadProducts() {
        updateState { copy(isLoading = true, error = null) }
        _searchQuery.value = currentState.searchQuery
        _selectedCategoryId.value = currentState.selectedCategoryId
    }

    private fun onSearchQueryChanged(query: String) {
        updateState { copy(searchQuery = query) }
        _searchQuery.value = query
        if (query.isNotBlank()) {
            analytics.logEvent(AnalyticsEvents.PRODUCT_SEARCHED, mapOf(
                AnalyticsParams.SEARCH_TERM to query,
            ))
        }
    }

    private fun onSelectCategory(categoryId: String?) {
        updateState { copy(selectedCategoryId = categoryId) }
        _selectedCategoryId.value = categoryId
    }

    private fun onSetStockFilter(filter: StockFilter) {
        updateState { copy(stockFilter = filter) }
        // Re-apply local filters on current products
        val filtered = applyLocalFilters(currentState.products)
        val sorted = applySort(filtered)
        updateState { copy(products = sorted) }
    }

    private fun onToggleViewMode() {
        val newMode = if (currentState.viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
        updateState { copy(viewMode = newMode) }
    }

    private fun onSortByColumn(columnKey: String) {
        val newDir = if (currentState.sortColumn == columnKey) {
            if (currentState.sortDirection == SortDir.ASC) SortDir.DESC else SortDir.ASC
        } else SortDir.ASC
        updateState { copy(sortColumn = columnKey, sortDirection = newDir) }
        val sorted = applySort(currentState.products)
        updateState { copy(products = sorted) }
    }

    private suspend fun onSelectProduct(productId: String?) {
        if (productId == null) {
            // New product — clear form
            updateState {
                copy(
                    selectedProduct = null,
                    editFormState = ProductFormState(isEditing = false),
                    productVariants = emptyList(),
                )
            }
            sendEffect(InventoryEffect.NavigateToDetail(null))
            return
        }
        updateState { copy(isLoading = true) }
        when (val result = productRepository.getById(productId)) {
            is Result.Success -> {
                val product = result.data
                updateState {
                    copy(
                        selectedProduct = product,
                        editFormState = product.toFormState(),
                        productVariants = emptyList(),
                        isLoading = false,
                    )
                }
                // Load variants for the selected product
                variantRepository.getByProductId(productId)
                    .onEach { variants -> updateState { copy(productVariants = variants) } }
                    .launchIn(viewModelScope)
                sendEffect(InventoryEffect.NavigateToDetail(productId))
            }
            is Result.Error -> {
                updateState { copy(isLoading = false) }
                sendEffect(InventoryEffect.ShowError(result.exception.message ?: "Product not found"))
            }
            is Result.Loading -> Unit
        }
    }

    private fun onBackToList() {
        updateState {
            copy(
                selectedProduct = null,
                editFormState = ProductFormState(),
                productVariants = emptyList(),
            )
        }
        sendEffect(InventoryEffect.NavigateToList)
    }

    private fun onUpdateFormField(field: String, value: String) {
        val form = currentState.editFormState
        val updated = when (field) {
            "name" -> form.copy(name = value)
            "barcode" -> form.copy(barcode = value)
            "sku" -> form.copy(sku = value)
            "categoryId" -> form.copy(categoryId = value)
            "unitId" -> form.copy(unitId = value)
            "price" -> form.copy(price = value)
            "costPrice" -> form.copy(costPrice = value)
            "taxGroupId" -> form.copy(taxGroupId = value.ifBlank { null })
            "stockQty" -> form.copy(stockQty = value)
            "minStockQty" -> form.copy(minStockQty = value)
            "description" -> form.copy(description = value)
            "imageUrl" -> form.copy(imageUrl = value.ifBlank { null })
            else -> form
        }
        // Clear validation error for updated field
        val errors = updated.validationErrors.toMutableMap().apply { remove(field) }
        updateState { copy(editFormState = updated.copy(validationErrors = errors)) }
    }

    private fun onToggleFormActive() {
        updateState {
            copy(editFormState = editFormState.copy(isActive = !editFormState.isActive))
        }
    }

    private fun onClearForm() {
        updateState { copy(editFormState = ProductFormState()) }
    }

    private suspend fun onSaveProduct() {
        val form = currentState.editFormState
        val errors = ProductValidator.validate(form.toValidationParams())
        if (errors.isNotEmpty()) {
            updateState { copy(editFormState = form.copy(validationErrors = errors)) }
            return
        }

        updateState { copy(isLoading = true) }
        val now = Clock.System.now()
        val product = Product(
            id = form.id ?: IdGenerator.newId(),
            name = form.name.trim(),
            barcode = form.barcode.ifBlank { null },
            sku = form.sku.ifBlank { null },
            categoryId = form.categoryId,
            unitId = form.unitId,
            price = form.price.toDoubleOrNull() ?: 0.0,
            costPrice = form.costPrice.toDoubleOrNull() ?: 0.0,
            taxGroupId = form.taxGroupId,
            stockQty = form.stockQty.toDoubleOrNull() ?: 0.0,
            minStockQty = form.minStockQty.toDoubleOrNull() ?: 0.0,
            imageUrl = form.imageUrl,
            description = form.description.ifBlank { null },
            isActive = form.isActive,
            createdAt = currentState.selectedProduct?.createdAt ?: now,
            updatedAt = now,
        )

        // Ensure all variants have the correct productId
        val variants = currentState.productVariants.map { it.copy(productId = product.id) }
        val result = if (form.id != null) {
            updateProductUseCase(product, variants)
        } else {
            createProductUseCase(product, variants)
        }

        updateState { copy(isLoading = false) }
        when (result) {
            is Result.Success -> {
                if (form.id != null) {
                    auditLogger.logProductModified(currentUserId, product.id)
                } else {
                    auditLogger.logProductCreated(currentUserId, product.id, product.name)
                }
                val action = if (form.id != null) "updated" else "created"
                sendEffect(InventoryEffect.ShowSuccess("Product '${ product.name }' $action."))
                onBackToList()
            }
            is Result.Error -> {
                sendEffect(InventoryEffect.ShowError(result.exception.message ?: "Save failed"))
            }
            is Result.Loading -> Unit
        }
    }

    private suspend fun onDeleteProduct(productId: String) {
        updateState { copy(isLoading = true) }
        when (val result = productRepository.delete(productId)) {
            is Result.Success -> {
                val deletedName = currentState.products.find { it.id == productId }?.name ?: ""
                auditLogger.logProductDeleted(currentUserId, productId, deletedName)
                updateState { copy(isLoading = false) }
                sendEffect(InventoryEffect.ShowSuccess("Product deactivated."))
                if (currentState.selectedProduct?.id == productId) onBackToList()
            }
            is Result.Error -> {
                updateState { copy(isLoading = false) }
                sendEffect(InventoryEffect.ShowError(result.exception.message ?: "Delete failed"))
            }
            is Result.Loading -> Unit
        }
    }

    // ── Variant management ────────────────────────────────────────────────

    private fun onAddVariant() {
        val variants = currentState.productVariants.toMutableList()
        variants.add(
            ProductVariant(
                id = IdGenerator.newId(),
                productId = currentState.editFormState.id ?: "",
                name = "",
            )
        )
        updateState { copy(productVariants = variants) }
    }

    private fun onRemoveVariant(index: Int) {
        val variants = currentState.productVariants.toMutableList()
        if (index in variants.indices) variants.removeAt(index)
        updateState { copy(productVariants = variants) }
    }

    private fun onUpdateVariant(index: Int, field: String, value: String) {
        val variants = currentState.productVariants.toMutableList()
        if (index !in variants.indices) return
        val v = variants[index]
        variants[index] = when (field) {
            "name" -> v.copy(name = value)
            "price" -> v.copy(price = value.toDoubleOrNull())
            "barcode" -> v.copy(barcode = value.ifBlank { null })
            "stock" -> v.copy(stock = value.toDoubleOrNull() ?: 0.0)
            else -> v
        }
        updateState { copy(productVariants = variants) }
    }

    // ── Barcode Scanner ─────────────────────────────────────────────────

    private var scannerJob: kotlinx.coroutines.Job? = null

    private fun onStartBarcodeScanner() {
        if (currentState.isScannerActive) return
        viewModelScope.launch {
            val result = barcodeScanner.startListening()
            if (result.isSuccess) {
                updateState { copy(isScannerActive = true) }
                scannerJob = barcodeScanner.scanEvents
                    .onEach { scanResult ->
                        when (scanResult) {
                            is ScanResult.Barcode -> dispatch(InventoryIntent.BarcodeScanResult(scanResult.value))
                            is ScanResult.Error -> sendEffect(InventoryEffect.ShowError("Scan error: ${scanResult.message}"))
                        }
                    }
                    .launchIn(viewModelScope)
            } else {
                sendEffect(InventoryEffect.ShowError("Failed to start scanner"))
            }
        }
    }

    private fun onStopBarcodeScanner() {
        scannerJob?.cancel()
        scannerJob = null
        viewModelScope.launch { barcodeScanner.stopListening() }
        updateState { copy(isScannerActive = false) }
    }

    private fun onBarcodeScanResult(barcode: String) {
        // Fill the barcode form field on the product detail screen
        val form = currentState.editFormState
        val updated = form.copy(barcode = barcode)
        val errors = updated.validationErrors.toMutableMap().apply { remove("barcode") }
        updateState { copy(editFormState = updated.copy(validationErrors = errors)) }
        // Stop scanner after successful scan
        onStopBarcodeScanner()
    }

    // ── Stock Adjustment ──────────────────────────────────────────────────

    private suspend fun onSubmitStockAdjustment(
        type: StockAdjustment.Type,
        quantity: Double,
        reason: String,
    ) {
        val target = currentState.stockAdjustmentTarget ?: return
        updateState { copy(isLoading = true) }
        val result = adjustStockUseCase(
            productId = target.id,
            type = type,
            quantity = quantity,
            reason = reason,
            adjustedBy = currentUserId,
            currentStock = target.stockQty,
            minStockQty = target.minStockQty,
        )
        updateState { copy(isLoading = false, stockAdjustmentTarget = null) }
        when (result) {
            is Result.Success -> {
                auditLogger.logStockAdjusted(currentUserId, target.id, quantity, reason)
                analytics.logEvent(AnalyticsEvents.STOCK_ADJUSTED, mapOf(
                    AnalyticsParams.ITEM_COUNT to quantity.toString(),
                ))
                sendEffect(InventoryEffect.ShowSuccess("Stock adjusted for '${target.name}'."))
            }
            is Result.Error -> sendEffect(InventoryEffect.ShowError(result.exception.message ?: "Adjustment failed"))
            is Result.Loading -> Unit
        }
    }

    // ── Bulk Import ───────────────────────────────────────────────────────

    private fun onSetImportFile(fileName: String, columns: List<String>, rows: List<Map<String, String>>) {
        updateState {
            copy(
                bulkImportState = bulkImportState.copy(
                    fileName = fileName,
                    availableColumns = columns,
                    parsedRows = rows,
                    columnMapping = emptyMap(),
                )
            )
        }
    }

    private fun onSetColumnMapping(csvColumn: String, productField: String) {
        val mapping = currentState.bulkImportState.columnMapping.toMutableMap()
        mapping[csvColumn] = productField
        updateState { copy(bulkImportState = bulkImportState.copy(columnMapping = mapping)) }
    }

    private suspend fun onConfirmBulkImport() {
        val state = currentState.bulkImportState
        if (state.parsedRows.isEmpty() || state.columnMapping.isEmpty()) return

        updateState { copy(bulkImportState = state.copy(isImporting = true, importErrors = emptyList())) }
        val mapping = state.columnMapping
        var imported = 0
        val errors = mutableListOf<String>()
        val now = Clock.System.now()

        state.parsedRows.forEachIndexed { idx, row ->
            val product = Product(
                id = IdGenerator.newId(),
                name = row[mapping.entries.find { it.value == "name" }?.key] ?: "",
                barcode = row[mapping.entries.find { it.value == "barcode" }?.key],
                sku = row[mapping.entries.find { it.value == "sku" }?.key],
                categoryId = row[mapping.entries.find { it.value == "categoryId" }?.key] ?: "",
                unitId = row[mapping.entries.find { it.value == "unitId" }?.key] ?: "",
                price = row[mapping.entries.find { it.value == "price" }?.key]?.toDoubleOrNull() ?: 0.0,
                costPrice = row[mapping.entries.find { it.value == "costPrice" }?.key]?.toDoubleOrNull() ?: 0.0,
                stockQty = row[mapping.entries.find { it.value == "stockQty" }?.key]?.toDoubleOrNull() ?: 0.0,
                createdAt = now,
                updatedAt = now,
            )
            when (val result = createProductUseCase(product)) {
                is Result.Success -> imported++
                is Result.Error -> errors.add("Row ${idx + 1}: ${result.exception.message}")
                is Result.Loading -> Unit
            }
            val progress = (idx + 1).toFloat() / state.parsedRows.size
            updateState { copy(bulkImportState = bulkImportState.copy(importProgress = progress)) }
        }

        updateState {
            copy(bulkImportState = bulkImportState.copy(isImporting = false, importErrors = errors))
        }
        sendEffect(InventoryEffect.BulkImportComplete(imported, errors.size))
        if (errors.isEmpty()) {
            updateState { copy(bulkImportState = BulkImportState()) }
        }
    }

    // ── Category Management ────────────────────────────────────────────

    private fun onLoadCategories() {
        categoryRepository.getTree()
            .onEach { cats -> updateState { copy(allCategoriesFlat = cats) } }
            .launchIn(viewModelScope)
    }

    private suspend fun onOpenCategoryDetail(categoryId: String?) {
        if (categoryId == null) {
            updateState { copy(selectedCategory = null, showCategoryDetail = true) }
            return
        }
        when (val result = categoryRepository.getById(categoryId)) {
            is Result.Success -> updateState { copy(selectedCategory = result.data, showCategoryDetail = true) }
            is Result.Error -> sendEffect(InventoryEffect.ShowError(result.exception.message ?: "Category not found"))
            is Result.Loading -> Unit
        }
    }

    private suspend fun onSaveCategory(category: Category) {
        updateState { copy(isLoading = true) }
        val isNew = currentState.selectedCategory == null
        val result = if (isNew) categoryRepository.insert(category) else categoryRepository.update(category)
        updateState { copy(isLoading = false) }
        when (result) {
            is Result.Success -> {
                updateState { copy(showCategoryDetail = false, selectedCategory = null) }
                sendEffect(InventoryEffect.ShowSuccess("Category '${category.name}' ${if (isNew) "created" else "updated"}."))
            }
            is Result.Error -> sendEffect(InventoryEffect.ShowError(result.exception.message ?: "Save failed"))
            is Result.Loading -> Unit
        }
    }

    private suspend fun onDeleteCategory(categoryId: String) {
        updateState { copy(isLoading = true) }
        when (val result = categoryRepository.delete(categoryId)) {
            is Result.Success -> {
                updateState { copy(isLoading = false, showCategoryDetail = false, selectedCategory = null) }
                sendEffect(InventoryEffect.ShowSuccess("Category deleted."))
            }
            is Result.Error -> {
                updateState { copy(isLoading = false) }
                sendEffect(InventoryEffect.ShowError(result.exception.message ?: "Delete failed"))
            }
            is Result.Loading -> Unit
        }
    }

    // ── Supplier Management ────────────────────────────────────────────

    private fun onLoadSuppliers() {
        supplierRepository.getAll()
            .onEach { suppliers -> updateState { copy(suppliers = suppliers) } }
            .launchIn(viewModelScope)
    }

    private suspend fun onOpenSupplierDetail(supplierId: String?) {
        if (supplierId == null) {
            updateState { copy(selectedSupplier = null, showSupplierDetail = true, supplierPurchaseHistory = emptyList()) }
            return
        }
        when (val result = supplierRepository.getById(supplierId)) {
            is Result.Success -> updateState { copy(selectedSupplier = result.data, showSupplierDetail = true) }
            is Result.Error -> sendEffect(InventoryEffect.ShowError(result.exception.message ?: "Supplier not found"))
            is Result.Loading -> Unit
        }
    }

    private suspend fun onSaveSupplier(supplier: Supplier) {
        updateState { copy(isLoading = true) }
        val isNew = currentState.selectedSupplier == null
        val result = if (isNew) supplierRepository.insert(supplier) else supplierRepository.update(supplier)
        updateState { copy(isLoading = false) }
        when (result) {
            is Result.Success -> {
                updateState { copy(showSupplierDetail = false, selectedSupplier = null) }
                sendEffect(InventoryEffect.ShowSuccess("Supplier '${supplier.name}' ${if (isNew) "created" else "updated"}."))
            }
            is Result.Error -> sendEffect(InventoryEffect.ShowError(result.exception.message ?: "Save failed"))
            is Result.Loading -> Unit
        }
    }

    private suspend fun onDeleteSupplier(supplierId: String) {
        updateState { copy(isLoading = true) }
        when (val result = supplierRepository.delete(supplierId)) {
            is Result.Success -> {
                updateState { copy(isLoading = false, showSupplierDetail = false, selectedSupplier = null) }
                sendEffect(InventoryEffect.ShowSuccess("Supplier deleted."))
            }
            is Result.Error -> {
                updateState { copy(isLoading = false) }
                sendEffect(InventoryEffect.ShowError(result.exception.message ?: "Delete failed"))
            }
            is Result.Loading -> Unit
        }
    }

    // ── Tax Group Management ───────────────────────────────────────────

    private suspend fun onSaveTaxGroup(taxGroup: TaxGroup) {
        updateState { copy(isLoading = true) }
        val isNew = currentState.allTaxGroups.none { it.id == taxGroup.id }
        val result = if (isNew) taxGroupRepository.insert(taxGroup) else taxGroupRepository.update(taxGroup)
        updateState { copy(isLoading = false) }
        when (result) {
            is Result.Success -> sendEffect(InventoryEffect.ShowSuccess("Tax group '${taxGroup.name}' saved."))
            is Result.Error -> sendEffect(InventoryEffect.ShowError(result.exception.message ?: "Save failed"))
            is Result.Loading -> Unit
        }
    }

    private suspend fun onDeleteTaxGroup(taxGroupId: String) {
        updateState { copy(isLoading = true) }
        when (val result = taxGroupRepository.delete(taxGroupId)) {
            is Result.Success -> {
                updateState { copy(isLoading = false) }
                sendEffect(InventoryEffect.ShowSuccess("Tax group deleted."))
            }
            is Result.Error -> {
                updateState { copy(isLoading = false) }
                sendEffect(InventoryEffect.ShowError(result.exception.message ?: "Delete failed"))
            }
            is Result.Loading -> Unit
        }
    }

    // ── Unit Management ────────────────────────────────────────────────

    private suspend fun onSaveUnit(groupId: String, unit: UnitOfMeasure) {
        updateState { copy(isLoading = true) }
        val isNew = currentState.allUnits.none { it.id == unit.id }
        val result = if (isNew) unitGroupRepository.insert(unit) else unitGroupRepository.update(unit)
        updateState { copy(isLoading = false) }
        when (result) {
            is Result.Success -> sendEffect(InventoryEffect.ShowSuccess("Unit '${unit.name}' saved."))
            is Result.Error -> sendEffect(InventoryEffect.ShowError(result.exception.message ?: "Save failed"))
            is Result.Loading -> Unit
        }
    }

    private suspend fun onDeleteUnit(unitId: String) {
        updateState { copy(isLoading = true) }
        when (val result = unitGroupRepository.delete(unitId)) {
            is Result.Success -> {
                updateState { copy(isLoading = false) }
                sendEffect(InventoryEffect.ShowSuccess("Unit deleted."))
            }
            is Result.Error -> {
                updateState { copy(isLoading = false) }
                sendEffect(InventoryEffect.ShowError(result.exception.message ?: "Delete failed"))
            }
            is Result.Loading -> Unit
        }
    }

    private fun onSaveUnitGroup(group: UnitGroup) {
        // UnitGroup is a UI-level grouping concept. No direct repository operation needed.
        // Units are saved individually via SaveUnit intent.
        updateState {
            copy(unitGroups = unitGroups.map { if (it.id == group.id) group else it })
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────

    /** Applies [StockFilter] locally after the DB query. */
    private fun applyLocalFilters(products: List<Product>): List<Product> {
        return when (currentState.stockFilter) {
            StockFilter.ALL -> products
            StockFilter.IN_STOCK -> products.filter { it.stockQty > 0.0 }
            StockFilter.LOW_STOCK -> products.filter {
                it.stockQty > 0.0 && it.stockQty <= it.minStockQty.coerceAtLeast(1.0)
            }
            StockFilter.OUT_OF_STOCK -> products.filter { it.stockQty <= 0.0 }
        }
    }

    /** Applies column sort based on current [InventoryState.sortColumn] / [InventoryState.sortDirection]. */
    private fun applySort(products: List<Product>): List<Product> {
        val comparator: Comparator<Product> = when (currentState.sortColumn) {
            "name" -> compareBy { it.name.lowercase() }
            "sku" -> compareBy { it.sku?.lowercase() ?: "" }
            "price" -> compareBy { it.price }
            "stockQty" -> compareBy { it.stockQty }
            "category" -> compareBy { it.categoryId }
            else -> compareBy { it.name.lowercase() }
        }
        return if (currentState.sortDirection == SortDir.DESC) {
            products.sortedWith(comparator.reversed())
        } else {
            products.sortedWith(comparator)
        }
    }

    /** Converts a [Product] domain model to [ProductFormState] for the edit form. */
    private fun Product.toFormState(): ProductFormState = ProductFormState(
        id = id,
        name = name,
        barcode = barcode ?: "",
        sku = sku ?: "",
        categoryId = categoryId,
        unitId = unitId,
        price = price.toString(),
        costPrice = costPrice.toString(),
        taxGroupId = taxGroupId,
        stockQty = stockQty.toString(),
        minStockQty = minStockQty.toString(),
        description = description ?: "",
        imageUrl = imageUrl,
        isActive = isActive,
        isEditing = true,
    )

    /**
     * Maps [ProductFormState] (feature-layer UI model) to [ProductValidationParams]
     * (domain-layer value object) so that [ProductValidator] has no dependency on any
     * presentation type.
     */
    private fun ProductFormState.toValidationParams() = ProductValidationParams(
        name = name,
        barcode = barcode,
        sku = sku,
        categoryId = categoryId,
        unitId = unitId,
        price = price,
        costPrice = costPrice,
        stockQty = stockQty,
        minStockQty = minStockQty,
    )
}
