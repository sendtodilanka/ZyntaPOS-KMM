package com.zyntasolutions.zyntapos.domain.usecase.settings

import com.zyntasolutions.zyntapos.domain.model.RegionalTaxOverride
import com.zyntasolutions.zyntapos.domain.repository.RegionalTaxOverrideRepository
import kotlinx.coroutines.flow.Flow

/**
 * Returns a reactive stream of all active tax rate overrides for a given store.
 *
 * Used by the Settings → Tax screen to populate the per-store override list.
 *
 * @param repository Source of [RegionalTaxOverride] records.
 */
class GetTaxOverridesUseCase(
    private val repository: RegionalTaxOverrideRepository,
) {
    operator fun invoke(storeId: String): Flow<List<RegionalTaxOverride>> =
        repository.getOverridesForStore(storeId)
}
