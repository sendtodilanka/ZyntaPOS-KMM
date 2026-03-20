package com.zyntasolutions.zyntapos.feature.inventory.replenishment

/** One-shot side effects for the replenishment management screen (C1.5). */
sealed interface ReplenishmentEffect {
    data class ShowError(val message: String)                : ReplenishmentEffect
    data class ShowSuccess(val message: String)              : ReplenishmentEffect
    data class NavigateToPurchaseOrder(val orderId: String)  : ReplenishmentEffect
    data object NavigateBack                                 : ReplenishmentEffect
}
