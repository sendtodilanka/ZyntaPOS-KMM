package com.zyntasolutions.zyntapos.feature.inventory.masterproduct

import com.zyntasolutions.zyntapos.domain.model.MasterProduct
import com.zyntasolutions.zyntapos.domain.model.StoreProductOverride

/**
 * Immutable state for the master product override screen.
 * Shows read-only master product details with editable local overrides.
 */
data class MasterProductOverrideState(
    val masterProduct: MasterProduct? = null,
    val currentOverride: StoreProductOverride? = null,
    val effectivePrice: Double = 0.0,
    val localPriceInput: String = "",
    val localStockInput: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
)
