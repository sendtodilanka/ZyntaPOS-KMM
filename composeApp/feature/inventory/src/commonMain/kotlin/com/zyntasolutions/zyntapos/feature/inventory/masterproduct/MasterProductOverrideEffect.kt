package com.zyntasolutions.zyntapos.feature.inventory.masterproduct

/**
 * One-shot side effects from the master product override screen.
 */
sealed class MasterProductOverrideEffect {
    data class ShowError(val message: String) : MasterProductOverrideEffect()
    data object SaveSuccess : MasterProductOverrideEffect()
    data object NavigateBack : MasterProductOverrideEffect()
}
