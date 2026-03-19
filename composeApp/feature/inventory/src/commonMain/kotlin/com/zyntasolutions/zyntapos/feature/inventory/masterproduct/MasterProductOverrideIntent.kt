package com.zyntasolutions.zyntapos.feature.inventory.masterproduct

/**
 * User actions on the master product override screen.
 */
sealed class MasterProductOverrideIntent {
    data class Load(val masterProductId: String) : MasterProductOverrideIntent()
    data class UpdateLocalPrice(val price: String) : MasterProductOverrideIntent()
    data class UpdateLocalStock(val qty: String) : MasterProductOverrideIntent()
    data object Save : MasterProductOverrideIntent()
    data object NavigateBack : MasterProductOverrideIntent()
}
