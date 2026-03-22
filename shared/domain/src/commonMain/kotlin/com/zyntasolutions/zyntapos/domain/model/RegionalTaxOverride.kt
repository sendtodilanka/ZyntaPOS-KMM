package com.zyntasolutions.zyntapos.domain.model

/**
 * Per-store tax rate override for a specific tax group.
 *
 * Enables multi-region tax configurations where different stores in different
 * jurisdictions apply different tax rates for the same tax group.
 *
 * Resolution: At checkout, the system first checks for a [RegionalTaxOverride]
 * matching (taxGroupId, storeId). If found, [effectiveRate] is used instead of
 * [TaxGroup.rate]. If not found, the global [TaxGroup.rate] applies.
 *
 * Per ADR-009: regional tax configuration is a store-level business operation,
 * managed via the KMM app with POS JWT auth (`/v1/taxes/*` endpoints).
 */
data class RegionalTaxOverride(
    val id: String,
    val taxGroupId: String,
    val storeId: String,
    val effectiveRate: Double,
    val jurisdictionCode: String = "",
    val taxRegistrationNumber: String = "",
    val validFrom: Long? = null,
    val validTo: Long? = null,
    val isActive: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    init {
        require(effectiveRate in 0.0..100.0) { "Tax rate must be between 0.0 and 100.0, got $effectiveRate" }
    }
}
