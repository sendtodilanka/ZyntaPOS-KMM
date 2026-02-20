package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Category
import kotlinx.coroutines.flow.Flow

/**
 * Contract for product category CRUD and hierarchical tree operations.
 *
 * Categories support a single level of parent–child nesting via [Category.parentId].
 * The [getTree] method returns roots and their children in a flat, ordered list
 * suitable for rendering a tree view without recursive database queries.
 */
interface CategoryRepository {

    /** Emits the full list of active categories, ordered by [Category.displayOrder]. */
    fun getAll(): Flow<List<Category>>

    /**
     * Returns a single [Category] by its UUID [id].
     *
     * @return [Result.Error] with [com.zyntasolutions.zyntapos.core.result.ZentaException.DatabaseException]
     *         if no category with that ID exists.
     */
    suspend fun getById(id: String): Result<Category>

    /** Inserts a new [category] and enqueues a sync operation. */
    suspend fun insert(category: Category): Result<Unit>

    /** Updates all mutable fields of [category] and enqueues a sync operation. */
    suspend fun update(category: Category): Result<Unit>

    /**
     * Soft-deletes the category identified by [id].
     *
     * The data layer must validate that no active products reference this category
     * before deletion and return [Result.Error] with [ValidationException] if they do.
     */
    suspend fun delete(id: String): Result<Unit>

    /**
     * Emits a hierarchically-ordered flat list of categories.
     *
     * The list is structured so that each parent category is immediately followed
     * by its children (depth = 1), ordered by [Category.displayOrder] within each level.
     * Re-emits on any change to the categories table.
     *
     * Example ordering for a "Beverages > Hot > Cold" tree:
     * ```
     * Beverages (parentId=null, depth=0)
     *   Hot      (parentId=beverages, depth=1)
     *   Cold     (parentId=beverages, depth=1)
     * ```
     */
    fun getTree(): Flow<List<Category>>
}
