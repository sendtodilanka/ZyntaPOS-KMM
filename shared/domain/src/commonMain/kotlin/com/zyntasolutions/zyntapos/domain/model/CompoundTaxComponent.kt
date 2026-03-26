package com.zyntasolutions.zyntapos.domain.model

/**
 * A single component in a compound tax chain.
 *
 * Compound taxes allow stacking multiple tax rates on a single product.
 * For example: VAT 15% + Service Charge 10% + Local Surcharge 2%.
 *
 * Components are applied in [applicationOrder] sequence. When [isCompounding]
 * is true, each subsequent tax is calculated on the already-taxed amount
 * (tax-on-tax). When false, all components are calculated on the original
 * base amount (parallel/additive).
 *
 * @property id Unique identifier (UUID v4).
 * @property parentTaxGroupId The primary tax group that this component belongs to.
 * @property componentTaxGroupId FK to the tax group providing the rate for this component.
 * @property componentName Display name of the component tax group (denormalized for display).
 * @property componentRate Tax rate percentage (0.0–100.0) from the component tax group.
 * @property componentIsInclusive Whether this component's rate is inclusive in the product price.
 * @property applicationOrder Order in which this component is applied (0 = first).
 * @property isCompounding If true, this tax is calculated on the running total (tax-on-tax).
 *                         If false, calculated on the original base amount (additive/parallel).
 */
data class CompoundTaxComponent(
    val id: String,
    val parentTaxGroupId: String,
    val componentTaxGroupId: String,
    val componentName: String = "",
    val componentRate: Double = 0.0,
    val componentIsInclusive: Boolean = false,
    val applicationOrder: Int = 0,
    val isCompounding: Boolean = false,
) {
    init {
        require(componentRate in 0.0..100.0) {
            "Component tax rate must be between 0.0 and 100.0, got $componentRate"
        }
    }
}
