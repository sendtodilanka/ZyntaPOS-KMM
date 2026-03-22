package com.zyntasolutions.zyntapos.feature.inventory.pricing

/** One-shot side effects for the pricing rules management screen (C2.1). */
sealed interface PricingRuleEffect {
    data class ShowError(val message: String)   : PricingRuleEffect
    data class ShowSuccess(val message: String) : PricingRuleEffect
}
