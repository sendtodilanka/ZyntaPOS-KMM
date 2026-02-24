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
    data object SubmitTransfer : WarehouseIntent

    // ── Transfer Actions ───────────────────────────────────────────────────
    data class CommitTransfer(val transferId: String) : WarehouseIntent
    data class CancelTransfer(val transferId: String) : WarehouseIntent

    // ── Global ─────────────────────────────────────────────────────────────
    data object DismissMessage : WarehouseIntent
}
