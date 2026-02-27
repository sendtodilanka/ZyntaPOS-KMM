package com.zyntasolutions.zyntapos.feature.inventory.label

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.domain.model.LabelTemplate
import com.zyntasolutions.zyntapos.domain.model.Product
import com.zyntasolutions.zyntapos.domain.repository.ProductRepository
import com.zyntasolutions.zyntapos.domain.usecase.inventory.DeleteLabelTemplateUseCase
import com.zyntasolutions.zyntapos.domain.usecase.inventory.GetLabelTemplatesUseCase
import com.zyntasolutions.zyntapos.domain.usecase.inventory.SaveLabelTemplateUseCase
import com.zyntasolutions.zyntapos.domain.usecase.inventory.SeedDefaultLabelTemplatesUseCase
import androidx.lifecycle.viewModelScope
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Clock

@OptIn(FlowPreview::class)
class BarcodeLabelPrintViewModel(
    private val productRepository: ProductRepository,
    private val getLabelTemplatesUseCase: GetLabelTemplatesUseCase,
    private val saveLabelTemplateUseCase: SaveLabelTemplateUseCase,
    private val deleteLabelTemplateUseCase: DeleteLabelTemplateUseCase,
    private val seedDefaultLabelTemplatesUseCase: SeedDefaultLabelTemplatesUseCase,
    private val labelPdfRenderer: LabelPdfRenderer,
) : BaseViewModel<BarcodeLabelPrintState, BarcodeLabelPrintIntent, BarcodeLabelPrintEffect>(
    initialState = BarcodeLabelPrintState(),
) {

    private val _searchQuery = MutableStateFlow("")

    init {
        observeTemplates()
        observeProductSearch()
    }

    override suspend fun handleIntent(intent: BarcodeLabelPrintIntent) {
        when (intent) {
            is BarcodeLabelPrintIntent.Initialize             -> initialize(intent.productId)
            is BarcodeLabelPrintIntent.SearchProducts         -> _searchQuery.value = intent.query
            is BarcodeLabelPrintIntent.AddToQueue             -> addToQueue(intent.product)
            is BarcodeLabelPrintIntent.RemoveFromQueue        -> removeFromQueue(intent.itemId)
            is BarcodeLabelPrintIntent.SetQuantity            -> setQuantity(intent.itemId, intent.qty)
            is BarcodeLabelPrintIntent.ClearQueue             -> updateState { copy(queue = emptyList()) }
            is BarcodeLabelPrintIntent.SelectTemplate         -> updateState { copy(selectedTemplate = intent.template) }
            is BarcodeLabelPrintIntent.OpenNewTemplateEditor  -> updateState { copy(isTemplateEditorOpen = true, editingTemplate = null) }
            is BarcodeLabelPrintIntent.OpenEditTemplateEditor -> updateState { copy(isTemplateEditorOpen = true, editingTemplate = intent.template) }
            is BarcodeLabelPrintIntent.DismissTemplateEditor  -> updateState { copy(isTemplateEditorOpen = false, editingTemplate = null) }
            is BarcodeLabelPrintIntent.SaveTemplate           -> saveTemplate(intent.template)
            is BarcodeLabelPrintIntent.DeleteTemplate         -> deleteTemplate(intent.templateId)
            is BarcodeLabelPrintIntent.RefreshPreview         -> refreshPreview()
            is BarcodeLabelPrintIntent.PrintLabels            -> printLabels()
            is BarcodeLabelPrintIntent.DismissError           -> updateState { copy(error = null) }
            is BarcodeLabelPrintIntent.DismissSuccess         -> updateState { copy(successMessage = null) }
        }
    }

    // ── Initialisation ─────────────────────────────────────────────────────────

    private fun observeTemplates() {
        viewModelScope.launch {
            // Seed defaults if repository is empty before subscribing
            seedDefaultLabelTemplatesUseCase.execute()
        }
        getLabelTemplatesUseCase.execute()
            .onEach { templates ->
                updateState {
                    val newSelected = selectedTemplate
                        ?.let { prev -> templates.find { it.id == prev.id } }
                        ?: templates.firstOrNull { it.isDefault }
                        ?: templates.firstOrNull()
                    copy(templates = templates, selectedTemplate = newSelected)
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeProductSearch() {
        _searchQuery
            .debounce(300L)
            .distinctUntilChanged()
            .flatMapLatest { query ->
                if (query.isBlank()) productRepository.getAll()
                else productRepository.search(query, null)
            }
            .onEach { products ->
                updateState { copy(products = products, isSearching = false) }
            }
            .launchIn(viewModelScope)
    }

    private suspend fun initialize(productId: String?) {
        if (productId != null) {
            val result = productRepository.getById(productId)
            if (result is Result.Success) {
                addToQueue(result.data)
            }
        }
        _searchQuery.value = ""
    }

    // ── Queue ──────────────────────────────────────────────────────────────────

    private fun addToQueue(product: Product) {
        val barcode = product.barcode?.takeIf { it.isNotBlank() }
            ?: product.sku?.takeIf { it.isNotBlank() }
            ?: product.id.take(13)
        updateState {
            val existing = queue.indexOfFirst { it.productId == product.id }
            if (existing >= 0) {
                val updated = queue[existing].copy(quantity = queue[existing].quantity + 1)
                copy(queue = queue.toMutableList().also { it[existing] = updated })
            } else {
                val item = PrintQueueItem(
                    id          = IdGenerator.newId(),
                    productId   = product.id,
                    productName = product.name,
                    barcode     = barcode,
                    sku         = product.sku,
                    price       = product.price,
                    quantity    = 1,
                    addedAt     = Clock.System.now().toEpochMilliseconds(),
                )
                copy(queue = queue + item)
            }
        }
    }

    private fun removeFromQueue(itemId: String) {
        updateState { copy(queue = queue.filter { it.id != itemId }) }
    }

    private fun setQuantity(itemId: String, qty: Int) {
        if (qty <= 0) {
            removeFromQueue(itemId)
            return
        }
        updateState {
            copy(queue = queue.map { if (it.id == itemId) it.copy(quantity = qty) else it })
        }
    }

    // ── Template CRUD ──────────────────────────────────────────────────────────

    private suspend fun saveTemplate(template: LabelTemplate) {
        val result = saveLabelTemplateUseCase.execute(template)
        updateState { copy(isTemplateEditorOpen = false, editingTemplate = null) }
        if (result is Result.Error) {
            sendEffect(BarcodeLabelPrintEffect.ShowError(result.exception.message))
        }
    }

    private suspend fun deleteTemplate(templateId: String) {
        val result = deleteLabelTemplateUseCase.execute(templateId)
        if (result is Result.Error) {
            sendEffect(BarcodeLabelPrintEffect.ShowError(result.exception.message))
        }
    }

    // ── PDF rendering ──────────────────────────────────────────────────────────

    private suspend fun refreshPreview() {
        val template = currentState.selectedTemplate ?: return
        val items    = expandQueue(currentState.queue)
        if (items.isEmpty()) return
        updateState { copy(isGeneratingPreview = true) }
        try {
            val bytes = labelPdfRenderer.render(items, template)
            updateState { copy(pdfPreviewBytes = bytes, isGeneratingPreview = false) }
        } catch (e: Exception) {
            updateState { copy(isGeneratingPreview = false) }
            sendEffect(BarcodeLabelPrintEffect.ShowError("Preview failed: ${e.message}"))
        }
    }

    private suspend fun printLabels() {
        val template = currentState.selectedTemplate
        if (template == null) {
            sendEffect(BarcodeLabelPrintEffect.ShowError("Please select a label template first"))
            return
        }
        val items = expandQueue(currentState.queue)
        if (items.isEmpty()) {
            sendEffect(BarcodeLabelPrintEffect.ShowError("Print queue is empty"))
            return
        }
        updateState { copy(isPrinting = true) }
        try {
            val bytes    = labelPdfRenderer.render(items, template)
            val fileName = "labels_${Clock.System.now().toEpochMilliseconds()}.pdf"
            updateState { copy(isPrinting = false) }
            sendEffect(BarcodeLabelPrintEffect.OpenPrintDialog(bytes, fileName))
        } catch (e: Exception) {
            updateState { copy(isPrinting = false) }
            sendEffect(BarcodeLabelPrintEffect.ShowError("Print failed: ${e.message}"))
        }
    }

    /** Flattens queue items into a list where each product repeats [PrintQueueItem.quantity] times. */
    private fun expandQueue(queue: List<PrintQueueItem>): List<PrintQueueItem> =
        queue.flatMap { item -> List(item.quantity) { item.copy(quantity = 1) } }
}
