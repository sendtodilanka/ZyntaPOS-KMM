package com.zyntasolutions.zyntapos.feature.inventory.pricing

import androidx.compose.runtime.Immutable
import com.zyntasolutions.zyntapos.domain.model.PricingRule
import com.zyntasolutions.zyntapos.domain.model.Product

/**
 * Immutable UI state for the pricing rules management screen (C2.1).
 */
@Immutable
data class PricingRuleState(

    // ── Rule list ───────────────────────────────────────────────────────────────
    val rules: List<PricingRule> = emptyList(),
    val isLoadingRules: Boolean = false,

    // ── Filter ──────────────────────────────────────────────────────────────────
    val filterProductId: String? = null,
    val filterActiveOnly: Boolean = true,

    // ── Dialog ──────────────────────────────────────────────────────────────────
    val showDialog: Boolean = false,
    val editingRule: PricingRule? = null,
    val formProductId: String = "",
    val formStoreId: String = "",
    val formPrice: String = "",
    val formCostPrice: String = "",
    val formPriority: String = "0",
    val formValidFrom: String = "",
    val formValidTo: String = "",
    val formDescription: String = "",
    val formIsActive: Boolean = true,
    val isSaving: Boolean = false,

    // ── Delete confirm ──────────────────────────────────────────────────────────
    val deleteTarget: PricingRule? = null,

    // ── Reference data ──────────────────────────────────────────────────────────
    val products: List<Product> = emptyList(),

    // ── Common ──────────────────────────────────────────────────────────────────
    val error: String? = null,
    val successMessage: String? = null,
)
