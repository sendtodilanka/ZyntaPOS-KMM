package com.zyntasolutions.zyntapos.domain.usecase.pos

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.TaxGroup
import com.zyntasolutions.zyntapos.domain.repository.RegionalTaxOverrideRepository

/**
 * Resolves the effective tax rate for a [TaxGroup] at a specific store.
 *
 * Resolution order:
 * 1. Store-specific override (matching taxGroupId + storeId, time-valid) → override rate
 * 2. No override found → global [TaxGroup.rate]
 *
 * Per ADR-009: tax rate configuration is a store-level business operation.
 */
class GetEffectiveTaxRateUseCase(
    private val regionalTaxOverrideRepository: RegionalTaxOverrideRepository,
) {
    /**
     * Returns the effective tax rate for a tax group at a specific store.
     *
     * @param taxGroup The tax group to resolve the rate for.
     * @param storeId The store ID for regional override lookup.
     * @param nowEpochMs Current time in epoch ms for time-bounded overrides.
     * @return The effective tax rate (0.0–100.0).
     */
    suspend operator fun invoke(
        taxGroup: TaxGroup,
        storeId: String,
        nowEpochMs: Long,
    ): Double {
        val overrideResult = regionalTaxOverrideRepository.getEffectiveOverride(
            taxGroupId = taxGroup.id,
            storeId = storeId,
            nowEpochMs = nowEpochMs,
        )
        return when (overrideResult) {
            is Result.Success -> overrideResult.data?.effectiveRate ?: taxGroup.rate
            is Result.Error -> taxGroup.rate
            is Result.Loading -> taxGroup.rate
        }
    }
}
