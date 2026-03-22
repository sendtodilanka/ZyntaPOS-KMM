package com.zyntasolutions.zyntapos.feature.inventory.pricing

import com.zyntasolutions.zyntapos.domain.model.PricingRule

/** All user actions for the pricing rules management screen (C2.1). */
sealed interface PricingRuleIntent {

    // ── List ────────────────────────────────────────────────────────────────────
    data object LoadRules                                            : PricingRuleIntent
    data class FilterByProduct(val productId: String?)               : PricingRuleIntent
    data class SetActiveOnlyFilter(val activeOnly: Boolean)          : PricingRuleIntent

    // ── Dialog ──────────────────────────────────────────────────────────────────
    data class OpenDialog(val rule: PricingRule?)                     : PricingRuleIntent
    data object DismissDialog                                        : PricingRuleIntent
    data class UpdateField(val field: PricingField, val value: String) : PricingRuleIntent
    data class SetActive(val active: Boolean)                        : PricingRuleIntent
    data object SaveRule                                             : PricingRuleIntent

    // ── Delete ──────────────────────────────────────────────────────────────────
    data class ConfirmDelete(val rule: PricingRule)                   : PricingRuleIntent
    data object DismissDelete                                        : PricingRuleIntent
    data object ExecuteDelete                                        : PricingRuleIntent

    // ── Common ──────────────────────────────────────────────────────────────────
    data object DismissError                                         : PricingRuleIntent
    data object DismissSuccess                                       : PricingRuleIntent
}

enum class PricingField {
    PRODUCT_ID, STORE_ID, PRICE, COST_PRICE, PRIORITY,
    VALID_FROM, VALID_TO, DESCRIPTION,
}
