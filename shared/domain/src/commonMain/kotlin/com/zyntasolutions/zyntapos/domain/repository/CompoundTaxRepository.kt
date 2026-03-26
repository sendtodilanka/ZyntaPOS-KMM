package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.CompoundTaxComponent

/**
 * Contract for compound tax component CRUD operations (C2.3).
 *
 * Compound taxes allow stacking multiple tax rates on a single product
 * (e.g., VAT + Service Charge + Local Surcharge).
 */
interface CompoundTaxRepository {

    /**
     * Returns all compound tax components for a given parent tax group,
     * ordered by [CompoundTaxComponent.applicationOrder].
     *
     * Empty list = no compound components (single-rate tax mode).
     */
    suspend fun getComponentsForTaxGroup(parentTaxGroupId: String): Result<List<CompoundTaxComponent>>

    /** Returns all parent tax group IDs that have compound components. */
    suspend fun getAllCompoundTaxGroupIds(): Result<List<String>>

    /** Inserts or replaces a compound tax component. */
    suspend fun insertComponent(component: CompoundTaxComponent): Result<Unit>

    /** Deletes a single compound tax component by ID. */
    suspend fun deleteComponent(componentId: String): Result<Unit>

    /** Deletes all compound tax components for a parent tax group. */
    suspend fun deleteAllForTaxGroup(parentTaxGroupId: String): Result<Unit>
}
