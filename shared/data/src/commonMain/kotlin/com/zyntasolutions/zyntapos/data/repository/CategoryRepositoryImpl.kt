package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.data.local.mapper.CategoryMapper
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.Category
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.repository.CategoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/**
 * Concrete implementation of [CategoryRepository].
 *
 * [getTree] uses the recursive CTE in `getCategoryTree` to return a depth-ordered
 * flat list — parents immediately followed by their children — without recursive
 * Kotlin post-processing.
 *
 * Soft-deletes validate that no active products reference the category before proceeding.
 */
class CategoryRepositoryImpl(
    private val db: ZyntaDatabase,
    private val syncEnqueuer: SyncEnqueuer,
) : CategoryRepository {

    private val q get() = db.categoriesQueries

    override fun getAll(): Flow<List<Category>> =
        q.getAllCategories()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(CategoryMapper::toDomain) }

    override suspend fun getById(id: String): Result<Category> = withContext(Dispatchers.IO) {
        runCatching {
            q.getCategoryById(id).executeAsOneOrNull()
                ?: return@withContext Result.Error(
                    DatabaseException("Category not found: $id", operation = "getCategoryById")
                )
        }.fold(
            onSuccess = { row -> Result.Success(CategoryMapper.toDomain(row)) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override fun getTree(): Flow<List<Category>> =
        q.getCategoryTree()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows ->
                rows.map { row ->
                    Category(
                        id           = row.id,
                        name         = row.name,
                        parentId     = row.parent_id,
                        imageUrl     = row.image_url,
                        displayOrder = row.display_order.toInt(),
                        isActive     = row.is_active == 1L,
                    )
                }
            }

    override suspend fun insert(category: Category): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val p = CategoryMapper.toInsertParams(category)
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                q.insertCategory(
                    id = p.id, name = p.name, parent_id = p.parentId,
                    image_url = p.imageUrl, display_order = p.displayOrder,
                    is_active = p.isActive, created_at = now, updated_at = now,
                    sync_status = p.syncStatus,
                )
                syncEnqueuer.enqueue(SyncOperation.EntityType.CATEGORY, p.id, SyncOperation.Operation.INSERT)
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Insert failed", operation = "insertCategory", cause = t)) },
        )
    }

    override suspend fun update(category: Category): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val p = CategoryMapper.toInsertParams(category)
            val now = Clock.System.now().toEpochMilliseconds()
            db.transaction {
                q.updateCategory(
                    name = p.name, parent_id = p.parentId, image_url = p.imageUrl,
                    display_order = p.displayOrder, is_active = p.isActive,
                    updated_at = now, sync_status = "PENDING", id = p.id,
                )
                syncEnqueuer.enqueue(SyncOperation.EntityType.CATEGORY, p.id, SyncOperation.Operation.UPDATE)
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Update failed", operation = "updateCategory", cause = t)) },
        )
    }

    override suspend fun delete(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Guard: reject if any active product references this category
            val productCount = db.productsQueries.getProductsByCategory(id).executeAsList().size
            if (productCount > 0) {
                return@withContext Result.Error(
                    ValidationException(
                        message = "Cannot delete category: $productCount active product(s) still assigned",
                        field   = "categoryId",
                        rule    = "CATEGORY_IN_USE",
                    )
                )
            }
            val now = Clock.System.now().toEpochMilliseconds()
            val row = q.getCategoryById(id).executeAsOne()
            db.transaction {
                q.updateCategory(
                    name = row.name, parent_id = row.parent_id, image_url = row.image_url,
                    display_order = row.display_order, is_active = 0L,
                    updated_at = now, sync_status = "PENDING", id = id,
                )
                syncEnqueuer.enqueue(SyncOperation.EntityType.CATEGORY, id, SyncOperation.Operation.DELETE)
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t ->
                if (t is ValidationException) Result.Error(t)
                else Result.Error(DatabaseException(t.message ?: "Delete failed", cause = t))
            },
        )
    }
}
