package com.zyntasolutions.zyntapos.feature.multistore

/**
 * One-shot side effects for the Multi-store / Warehouse feature.
 */
sealed interface WarehouseEffect {
    data class NavigateToDetail(val warehouseId: String?) : WarehouseEffect
    data object NavigateToList : WarehouseEffect
    data object NavigateToTransfers : WarehouseEffect
    data object TransferComplete : WarehouseEffect

    // IST multi-step workflow effects (C1.3)
    data class NavigateToTransferDetail(val transferId: String) : WarehouseEffect
    data object TransferApproved : WarehouseEffect
    data object TransferDispatched : WarehouseEffect
    data object TransferReceived : WarehouseEffect

    // Rack navigation
    data class NavigateToRackDetail(val rackId: String?, val warehouseId: String) : WarehouseEffect
    data object NavigateToRackList : WarehouseEffect

    data class ShowError(val message: String) : WarehouseEffect
    data class ShowSuccess(val message: String) : WarehouseEffect
}
