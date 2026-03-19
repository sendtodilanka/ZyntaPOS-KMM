package com.zyntasolutions.zyntapos.feature.multistore

import com.zyntasolutions.zyntapos.domain.model.Product
import com.zyntasolutions.zyntapos.domain.model.StockTransfer
import com.zyntasolutions.zyntapos.domain.model.Warehouse
import com.zyntasolutions.zyntapos.domain.model.WarehouseRack

/**
 * Immutable UI state for the Multi-store / Warehouse screens.
 *
 * Consumed by [WarehouseListScreen], [WarehouseDetailScreen],
 * [StockTransferListScreen], [NewStockTransferScreen],
 * [WarehouseRackListScreen], and [WarehouseRackDetailScreen].
 */
data class WarehouseState(
    // ── Warehouse List ────────────────────────────────────────────────────
    val warehouses: List<Warehouse> = emptyList(),
    val transfers: List<StockTransfer> = emptyList(),
    val pendingTransfers: List<StockTransfer> = emptyList(),

    // ── IST Multi-step workflow state (C1.3) ──────────────────────────────
    val approvedTransfers: List<StockTransfer> = emptyList(),
    val inTransitTransfers: List<StockTransfer> = emptyList(),
    val selectedTransfer: StockTransfer? = null,

    // ── Warehouse Detail / Edit ───────────────────────────────────────────
    val selectedWarehouse: Warehouse? = null,
    val warehouseForm: WarehouseFormState = WarehouseFormState(),

    // ── New Transfer ──────────────────────────────────────────────────────
    val transferForm: TransferFormState = TransferFormState(),
    val productSearchQuery: String = "",
    val productSearchResults: List<Product> = emptyList(),

    // ── Rack Management ───────────────────────────────────────────────────
    val racks: List<WarehouseRack> = emptyList(),
    val selectedRack: WarehouseRack? = null,
    val rackForm: RackFormState = RackFormState(),
    val showDeleteRackConfirm: WarehouseRack? = null,

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

/** Mutable form fields for creating/editing a warehouse rack. */
data class RackFormState(
    val id: String? = null,
    val warehouseId: String = "",
    val name: String = "",
    val description: String = "",
    val capacity: String = "",
    val isEditing: Boolean = false,
    val validationErrors: Map<String, String> = emptyMap(),
)
