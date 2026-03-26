package com.zyntasolutions.zyntapos.domain.usecase.pos

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.CompoundTaxComponent
import com.zyntasolutions.zyntapos.domain.repository.CompoundTaxRepository

/**
 * Resolves compound tax components for a given tax group (C2.3).
 *
 * Returns empty list if the tax group has no compound components,
 * meaning it uses a single flat rate (backward compatible).
 */
class GetCompoundTaxComponentsUseCase(
    private val compoundTaxRepository: CompoundTaxRepository,
) {
    suspend operator fun invoke(taxGroupId: String): List<CompoundTaxComponent> {
        return when (val result = compoundTaxRepository.getComponentsForTaxGroup(taxGroupId)) {
            is Result.Success -> result.data
            else -> emptyList()
        }
    }
}
