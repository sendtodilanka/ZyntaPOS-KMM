package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.RegionalTaxOverride
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for per-store tax rate overrides.
 *
 * Enables stores in different jurisdictions to override global tax group rates.
 * Write operations enqueue sync operations for cloud synchronization.
 */
interface RegionalTaxOverrideRepository {

    /** All active overrides for a specific store (reactive). */
    fun getOverridesForStore(storeId: String): Flow<List<RegionalTaxOverride>>

    /** Get the effective override for a tax group at a specific store, or null if none. */
    suspend fun getEffectiveOverride(
        taxGroupId: String,
        storeId: String,
        nowEpochMs: Long,
    ): Result<RegionalTaxOverride?>

    /** All overrides for a tax group across all stores. */
    fun getOverridesForTaxGroup(taxGroupId: String): Flow<List<RegionalTaxOverride>>

    /** Create or update an override. */
    suspend fun upsert(override: RegionalTaxOverride): Result<Unit>

    /** Delete an override by ID. */
    suspend fun delete(id: String): Result<Unit>
}
