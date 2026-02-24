package com.zyntasolutions.zyntapos.feature.multistore

/**
 * One-shot side effects for the Multi-store / Warehouse feature.
 */
sealed interface WarehouseEffect {
    data class NavigateToDetail(val warehouseId: String?) : WarehouseEffect
    data object NavigateToList : WarehouseEffect
    data object NavigateToTransfers : WarehouseEffect
    data object TransferComplete : WarehouseEffect
    data class ShowError(val message: String) : WarehouseEffect
    data class ShowSuccess(val message: String) : WarehouseEffect
}
