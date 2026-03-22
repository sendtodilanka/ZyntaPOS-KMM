package com.zyntasolutions.zyntapos.domain.usecase.fakes

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.RegionalTaxOverride
import com.zyntasolutions.zyntapos.domain.repository.RegionalTaxOverrideRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeRegionalTaxOverrideRepository : RegionalTaxOverrideRepository {

    private val overrides = MutableStateFlow<List<RegionalTaxOverride>>(emptyList())

    fun addOverride(override: RegionalTaxOverride) {
        overrides.value = overrides.value + override
    }

    override fun getOverridesForStore(storeId: String): Flow<List<RegionalTaxOverride>> =
        overrides.map { list -> list.filter { it.storeId == storeId && it.isActive } }

    override suspend fun getEffectiveOverride(
        taxGroupId: String,
        storeId: String,
        nowEpochMs: Long,
    ): Result<RegionalTaxOverride?> {
        val match = overrides.value.firstOrNull { o ->
            o.taxGroupId == taxGroupId &&
                o.storeId == storeId &&
                o.isActive &&
                (o.validFrom == null || o.validFrom <= nowEpochMs) &&
                (o.validTo == null || o.validTo >= nowEpochMs)
        }
        return Result.Success(match)
    }

    override fun getOverridesForTaxGroup(taxGroupId: String): Flow<List<RegionalTaxOverride>> =
        overrides.map { list -> list.filter { it.taxGroupId == taxGroupId && it.isActive } }

    override suspend fun upsert(override: RegionalTaxOverride): Result<Unit> {
        overrides.value = overrides.value.filter { it.id != override.id } + override
        return Result.Success(Unit)
    }

    override suspend fun delete(id: String): Result<Unit> {
        overrides.value = overrides.value.filter { it.id != id }
        return Result.Success(Unit)
    }
}
