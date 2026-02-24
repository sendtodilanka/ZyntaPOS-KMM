package com.zyntasolutions.zyntapos.feature.multistore

import com.zyntasolutions.zyntapos.domain.model.StockTransfer
import com.zyntasolutions.zyntapos.domain.model.Warehouse

/**
 * Immutable UI state for the Multi-store / Warehouse screens.
 *
 * Consumed by [WarehouseListScreen], [WarehouseDetailScreen],
 * [StockTransferListScreen], and [NewStockTransferScreen].
 *
 * @property warehouses Warehouses belonging to the current store.
 * @property transfers Stock transfers for the selected warehouse.
 * @property pendingTransfers All PENDING transfers across all warehouses.
 * @property selectedWarehouse Warehouse loaded for detail/edit.
 * @property warehouseForm Draft for create/edit warehouse.
 * @property transferForm Draft for new stock transfer.
 * @property isLoading True while an async operation is in flight.
 * @property error Transient error message; null = no error.
 * @property successMessage Transient success message; null = no message.
 */
data class WarehouseState(
    // ── List ──────────────────────────────────────────────────────────────
    val warehouses: List<Warehouse> = emptyList(),
    val transfers: List<StockTransfer> = emptyList(),
    val pendingTransfers: List<StockTransfer> = emptyList(),

    // ── Detail / Edit ─────────────────────────────────────────────────────
    val selectedWarehouse: Warehouse? = null,
    val warehouseForm: WarehouseFormState = WarehouseFormState(),

    // ── New Transfer ──────────────────────────────────────────────────────
    val transferForm: TransferFormState = TransferFormState(),

    // ── Global ────────────────────────────────────────────────────────────
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
)

/** Mutable form fields for warehouse create/edit. */
data class WarehouseFormState(
    val id: String? = null,
    val name: String = "",
    val address: String = "",
    val isDefault: Boolean = false,
    val isEditing: Boolean = false,
    val validationErrors: Map<String, String> = emptyMap(),
)

/** Mutable form fields for creating a new stock transfer. */
data class TransferFormState(
    val sourceWarehouseId: String = "",
    val destWarehouseId: String = "",
    val productId: String = "",
    val productName: String = "",
    val quantity: String = "",
    val notes: String = "",
    val validationErrors: Map<String, String> = emptyMap(),
)
