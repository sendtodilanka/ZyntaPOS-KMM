package com.zyntasolutions.zyntapos.feature.inventory.label

import com.zyntasolutions.zyntapos.domain.model.LabelTemplate
import com.zyntasolutions.zyntapos.domain.model.Product

/** All user actions that can mutate [BarcodeLabelPrintState]. */
sealed interface BarcodeLabelPrintIntent {

    // ── Initialisation ─────────────────────────────────────────────────────
    /** Load templates; optionally pre-seed queue with the product matching [productId]. */
    data class Initialize(val productId: String?) : BarcodeLabelPrintIntent

    // ── Product search ─────────────────────────────────────────────────────
    data class SearchProducts(val query: String) : BarcodeLabelPrintIntent

    // ── Queue management ───────────────────────────────────────────────────
    /** Add product to queue. If already present, increments quantity by 1. */
    data class AddToQueue(val product: Product) : BarcodeLabelPrintIntent
    /** Remove item entirely from queue. */
    data class RemoveFromQueue(val itemId: String) : BarcodeLabelPrintIntent
    /** Update copy count. If [qty] <= 0 the item is removed. */
    data class SetQuantity(val itemId: String, val qty: Int) : BarcodeLabelPrintIntent
    data object ClearQueue : BarcodeLabelPrintIntent

    // ── Template management ────────────────────────────────────────────────
    data class SelectTemplate(val template: LabelTemplate) : BarcodeLabelPrintIntent
    data object OpenNewTemplateEditor : BarcodeLabelPrintIntent
    data class OpenEditTemplateEditor(val template: LabelTemplate) : BarcodeLabelPrintIntent
    data object DismissTemplateEditor : BarcodeLabelPrintIntent
    data class SaveTemplate(val template: LabelTemplate) : BarcodeLabelPrintIntent
    data class DeleteTemplate(val templateId: String) : BarcodeLabelPrintIntent

    // ── PDF actions ────────────────────────────────────────────────────────
    /** Render PDF for the Desktop live-preview panel. */
    data object RefreshPreview : BarcodeLabelPrintIntent
    /** Render final PDF and emit [BarcodeLabelPrintEffect.OpenPrintDialog]. */
    data object PrintLabels : BarcodeLabelPrintIntent

    // ── UI feedback ────────────────────────────────────────────────────────
    data object DismissError : BarcodeLabelPrintIntent
    data object DismissSuccess : BarcodeLabelPrintIntent
}
