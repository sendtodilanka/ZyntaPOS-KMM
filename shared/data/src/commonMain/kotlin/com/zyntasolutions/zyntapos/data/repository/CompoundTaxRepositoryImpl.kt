package com.zyntasolutions.zyntapos.data.repository

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.CompoundTaxComponent
import com.zyntasolutions.zyntapos.domain.repository.CompoundTaxRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * SQLDelight implementation of [CompoundTaxRepository] (C2.3).
 */
class CompoundTaxRepositoryImpl(
    private val database: ZyntaDatabase,
) : CompoundTaxRepository {

    private val queries get() = database.compound_tax_componentsQueries

    override suspend fun getComponentsForTaxGroup(
        parentTaxGroupId: String,
    ): Result<List<CompoundTaxComponent>> = withContext(Dispatchers.IO) {
        try {
            val components = queries.getComponentsForTaxGroup(parentTaxGroupId)
                .executeAsList()
                .map { row ->
                    CompoundTaxComponent(
                        id = row.id,
                        parentTaxGroupId = row.parent_tax_group_id,
                        componentTaxGroupId = row.component_tax_group_id,
                        componentName = row.component_name,
                        componentRate = row.component_rate,
                        componentIsInclusive = row.component_is_inclusive != 0L,
                        applicationOrder = row.application_order.toInt(),
                        isCompounding = row.is_compounding != 0L,
                    )
                }
            Result.Success(components)
        } catch (e: Exception) {
            Result.Error(DatabaseException(e.message ?: "Failed to get compound tax components", operation = "getComponentsForTaxGroup", cause = e))
        }
    }

    override suspend fun getAllCompoundTaxGroupIds(): Result<List<String>> =
        withContext(Dispatchers.IO) {
            try {
                val ids = queries.getAllCompoundTaxGroups().executeAsList()
                Result.Success(ids)
            } catch (e: Exception) {
                Result.Error(DatabaseException(e.message ?: "Failed to get compound tax group IDs", operation = "getAllCompoundTaxGroups", cause = e))
            }
        }

    override suspend fun insertComponent(component: CompoundTaxComponent): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val now = Clock.System.now().toEpochMilliseconds()
                queries.insertComponent(
                    id = component.id,
                    parent_tax_group_id = component.parentTaxGroupId,
                    component_tax_group_id = component.componentTaxGroupId,
                    application_order = component.applicationOrder.toLong(),
                    is_compounding = if (component.isCompounding) 1L else 0L,
                    created_at = now,
                    updated_at = now,
                    sync_status = "PENDING",
                )
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(DatabaseException(e.message ?: "Failed to insert compound tax component", operation = "insertComponent", cause = e))
            }
        }

    override suspend fun deleteComponent(componentId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                queries.deleteComponent(componentId)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(DatabaseException(e.message ?: "Failed to delete compound tax component", operation = "deleteComponent", cause = e))
            }
        }

    override suspend fun deleteAllForTaxGroup(parentTaxGroupId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                queries.deleteComponentsForTaxGroup(parentTaxGroupId)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(DatabaseException(
                    e.message ?: "Failed to delete compound tax components",
                    operation = "deleteComponentsForTaxGroup",
                    cause = e,
                ))
            }
        }
}
