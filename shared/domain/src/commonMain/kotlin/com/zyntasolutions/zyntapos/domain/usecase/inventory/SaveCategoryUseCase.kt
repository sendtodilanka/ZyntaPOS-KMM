package com.zyntasolutions.zyntapos.domain.usecase.inventory

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.Category
import com.zyntasolutions.zyntapos.domain.repository.CategoryRepository

/**
 * Validates and persists a [Category] (insert or update).
 *
 * **Validation rules:**
 * - [Category.name] must not be blank.
 * - A category must not designate itself as its own parent.
 * - [Category.displayOrder] must be ≥ 0.
 *
 * If [Category.id] already exists in [categoryRepository], [CategoryRepository.update]
 * is called; otherwise [CategoryRepository.insert] is used.
 *
 * @param categoryRepository Source of truth for category persistence.
 */
class SaveCategoryUseCase(
    private val categoryRepository: CategoryRepository,
) {

    /**
     * Saves [category] after validating all business rules.
     *
     * @return [Result.Success] on success.
     * @return [Result.Error] wrapping a [ValidationException] for rule violations
     *         or a database error for persistence failures.
     */
    suspend operator fun invoke(
        category: Category,
        isUpdate: Boolean,
    ): Result<Unit> {
        // ── Validate name ───────────────────────────────────────────────────
        if (category.name.isBlank()) {
            return Result.Error(
                ValidationException(
                    message = "Category name is required.",
                    field = "name",
                    rule = "REQUIRED",
                )
            )
        }

        // ── Self-referential parent check ───────────────────────────────────
        if (category.parentId != null && category.parentId == category.id) {
            return Result.Error(
                ValidationException(
                    message = "A category cannot be its own parent.",
                    field = "parentId",
                    rule = "SELF_REFERENCE",
                )
            )
        }

        // ── Display order ────────────────────────────────────────────────────
        if (category.displayOrder < 0) {
            return Result.Error(
                ValidationException(
                    message = "Display order must be 0 or greater.",
                    field = "displayOrder",
                    rule = "MIN_VALUE",
                )
            )
        }

        return if (isUpdate) {
            categoryRepository.update(category)
        } else {
            categoryRepository.insert(category)
        }
    }
}
