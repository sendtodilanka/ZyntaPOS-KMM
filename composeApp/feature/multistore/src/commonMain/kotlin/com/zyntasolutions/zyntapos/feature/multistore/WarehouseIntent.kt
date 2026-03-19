package com.zyntasolutions.zyntapos.feature.multistore

/**
 * All user actions for the Multi-store / Warehouse feature.
 */
sealed interface WarehouseIntent {

    // ── Warehouse List ─────────────────────────────────────────────────────
    data object LoadWarehouses : WarehouseIntent

    // ── Warehouse Detail ───────────────────────────────────────────────────
    /** Navigate to create/edit. [warehouseId] null → create new. */
    data class SelectWarehouse(val warehouseId: String?) : WarehouseIntent
    data class UpdateWarehouseField(val field: String, val value: String) : WarehouseIntent
    data class UpdateIsDefault(val isDefault: Boolean) : WarehouseIntent
    data object SaveWarehouse : WarehouseIntent

    // ── Transfer List ──────────────────────────────────────────────────────
    data class LoadTransfers(val warehouseId: String) : WarehouseIntent

    // ── New Transfer ───────────────────────────────────────────────────────
    data class InitTransferForm(val sourceWarehouseId: String?) : WarehouseIntent
    data class UpdateTransferField(val field: String, val value: String) : WarehouseIntent
    /** Search products by name/SKU for the transfer product selector. */
    data class SearchProducts(val query: String) : WarehouseIntent
    /** Select a product from search results for the transfer. */
    data class SelectTransferProduct(val product: com.zyntasolutions.zyntapos.domain.model.Product) : WarehouseIntent
    data object SubmitTransfer : WarehouseIntent

    // ── Transfer Actions ───────────────────────────────────────────────────
    data class CommitTransfer(val transferId: String) : WarehouseIntent
    data class CancelTransfer(val transferId: String) : WarehouseIntent

    // ── Rack Management ────────────────────────────────────────────────────
    /** Start observing racks for [warehouseId]. */
    data class LoadRacks(val warehouseId: String) : WarehouseIntent
    /** Navigate to rack create/edit. [rackId] null → create new. */
    data class SelectRack(val rackId: String?, val warehouseId: String) : WarehouseIntent
    data class UpdateRackField(val field: String, val value: String) : WarehouseIntent
    data object SaveRack : WarehouseIntent
    data class RequestDeleteRack(val rack: com.zyntasolutions.zyntapos.domain.model.WarehouseRack) : WarehouseIntent
    data object ConfirmDeleteRack : WarehouseIntent
    data object CancelDeleteRack : WarehouseIntent

    // ── Rack Products / C1.2 ──────────────────────────────────────────────
    /** Load all products in [rackId]. */
    data class LoadRackProducts(val rackId: String) : WarehouseIntent
    /** Open rack-product form. [productId] null → pick product first. */
    data class OpenRackProductEntry(val rackId: String, val productId: String?) : WarehouseIntent
    data class UpdateRackProductField(val field: String, val value: String) : WarehouseIntent
    data object SaveRackProduct : WarehouseIntent
    data object CancelRackProductEntry : WarehouseIntent
    data class RequestDeleteRackProduct(val entry: com.zyntasolutions.zyntapos.domain.model.RackProduct) : WarehouseIntent
    data object ConfirmDeleteRackProduct : WarehouseIntent
    data object CancelDeleteRackProduct : WarehouseIntent

    // ── Warehouse Stock / C1.2 ─────────────────────────────────────────────
    /** Load per-warehouse stock list and low-stock items for [warehouseId]. */
    data class LoadWarehouseStock(val warehouseId: String) : WarehouseIntent
    /** Filter the stock list by product name/SKU. */
    data class SearchStock(val query: String) : WarehouseIntent
    /** Open set-stock form. [productId] null → pick product first. */
    data class OpenStockEntry(val warehouseId: String, val productId: String?) : WarehouseIntent
    data class UpdateStockField(val field: String, val value: String) : WarehouseIntent
    data object SaveStockEntry : WarehouseIntent
    data object CancelStockEntry : WarehouseIntent

    // ── Global ─────────────────────────────────────────────────────────────
    data object DismissMessage : WarehouseIntent
}
