package com.zyntasolutions.zyntapos.feature.inventory.label

import com.zyntasolutions.zyntapos.domain.model.LabelTemplate
import com.zyntasolutions.zyntapos.domain.model.Product

/**
 * Transient in-session print queue item. Not persisted to database.
 *
 * @property id          Local UUID (not a DB key).
 * @property productId   Source product ID for deep-linking.
 * @property productName Label line 1.
 * @property barcode     Barcode value to encode.
 * @property sku         Label line 3 (SKU preferred; falls back to barcode digits).
 * @property price       Selling price for the label.
 * @property quantity    Number of identical label copies in this print job.
 * @property addedAt     Epoch ms timestamp; queue preserves insertion order.
 */
data class PrintQueueItem(
    val id: String,
    val productId: String,
    val productName: String,
    val barcode: String,
    val sku: String?,
    val price: Double,
    val quantity: Int,
    val addedAt: Long,
)

/**
 * Immutable UI state for [BarcodeLabelPrintViewModel].
 */
data class BarcodeLabelPrintState(

    // ── Product search ─────────────────────────────────────────────────────
    val products: List<Product> = emptyList(),
    val searchQuery: String = "",
    val isSearching: Boolean = false,

    // ── Print queue ────────────────────────────────────────────────────────
    val queue: List<PrintQueueItem> = emptyList(),

    // ── Template management ────────────────────────────────────────────────
    val templates: List<LabelTemplate> = emptyList(),
    val selectedTemplate: LabelTemplate? = null,
    val isTemplateEditorOpen: Boolean = false,
    val editingTemplate: LabelTemplate? = null,   // null = create new

    // ── PDF preview (Desktop only) ─────────────────────────────────────────
    val pdfPreviewBytes: ByteArray? = null,
    val isGeneratingPreview: Boolean = false,

    // ── Global ─────────────────────────────────────────────────────────────
    val isLoading: Boolean = false,
    val isPrinting: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
) {
    val totalLabelCount: Int get() = queue.sumOf { it.quantity }
    val canPrint: Boolean get() = queue.isNotEmpty() && selectedTemplate != null
}
