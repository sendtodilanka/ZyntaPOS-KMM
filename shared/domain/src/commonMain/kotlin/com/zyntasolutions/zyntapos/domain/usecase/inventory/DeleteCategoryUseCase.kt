package com.zyntasolutions.zyntapos.domain.usecase.inventory

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.repository.CategoryRepository

/**
 * Soft-deletes a category by [categoryId].
 *
 * Delegates referential-integrity checks (active products, child categories)
 * to [CategoryRepository.delete], which returns [Result.Error] with a
 * [com.zyntasolutions.zyntapos.core.result.ZyntaException.ValidationException]
 * if the category cannot be safely removed.
 *
 * @param categoryRepository Provides the underlying delete operation.
 */
class DeleteCategoryUseCase(
    private val categoryRepository: CategoryRepository,
) {
    /**
     * Attempts to delete the category identified by [categoryId].
     *
     * @return [Result.Success] on success.
     * @return [Result.Error] if the category has active products or child categories.
     */
    suspend operator fun invoke(categoryId: String): Result<Unit> {
        if (categoryId.isBlank()) {
            return Result.Error(
                com.zyntasolutions.zyntapos.core.result.ValidationException(
                    message = "Category ID must not be blank.",
                    field = "categoryId",
                    rule = "REQUIRED",
                )
            )
        }
        return categoryRepository.delete(categoryId)
    }
}
