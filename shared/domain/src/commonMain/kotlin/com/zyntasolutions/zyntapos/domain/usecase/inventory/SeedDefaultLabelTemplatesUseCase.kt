package com.zyntasolutions.zyntapos.domain.usecase.inventory

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.LabelTemplate
import com.zyntasolutions.zyntapos.domain.repository.LabelTemplateRepository
import kotlin.time.Clock

/**
 * Seeds the 4 factory default label templates if the repository is empty.
 *
 * Safe to call multiple times — checks [LabelTemplateRepository.count] before inserting.
 * Templates are inserted via [LabelTemplateRepository.save] using stable hard-coded IDs.
 *
 * Defaults seeded:
 * 1. "58mm Roll (1-up)"       — CONTINUOUS_ROLL, 58mm, 1 column
 * 2. "80mm Roll (2-up)"       — CONTINUOUS_ROLL, 80mm, 2 columns
 * 3. "A4 24-up (3×8)"         — A4_SHEET, 3 cols × 8 rows
 * 4. "A4 48-up (4×12)"        — A4_SHEET, 4 cols × 12 rows
 */
class SeedDefaultLabelTemplatesUseCase(
    private val repository: LabelTemplateRepository,
) {
    suspend fun execute(): Result<Unit> {
        if (repository.count() > 0) return Result.Success(Unit)

        val now = Clock.System.now().toEpochMilliseconds()

        val defaults = listOf(
            LabelTemplate(
                id = "built-in-58mm-1up",
                name = "58mm Roll (1-up)",
                paperType = LabelTemplate.PaperType.CONTINUOUS_ROLL,
                paperWidthMm = 58.0,
                labelHeightMm = 30.0,
                columns = 1,
                rows = 0,
                gapHorizontalMm = 0.0,
                gapVerticalMm = 3.0,
                marginTopMm = 2.0,
                marginBottomMm = 0.0,
                marginLeftMm = 2.0,
                marginRightMm = 2.0,
                isDefault = true,
                createdAt = now,
                updatedAt = now,
            ),
            LabelTemplate(
                id = "built-in-80mm-2up",
                name = "80mm Roll (2-up)",
                paperType = LabelTemplate.PaperType.CONTINUOUS_ROLL,
                paperWidthMm = 80.0,
                labelHeightMm = 30.0,
                columns = 2,
                rows = 0,
                gapHorizontalMm = 2.0,
                gapVerticalMm = 3.0,
                marginTopMm = 2.0,
                marginBottomMm = 0.0,
                marginLeftMm = 2.0,
                marginRightMm = 2.0,
                isDefault = false,
                createdAt = now,
                updatedAt = now,
            ),
            LabelTemplate(
                id = "built-in-a4-24up",
                name = "A4 Label Sheet (3×8, 24-up)",
                paperType = LabelTemplate.PaperType.A4_SHEET,
                paperWidthMm = 210.0,
                labelHeightMm = 37.125,
                columns = 3,
                rows = 8,
                gapHorizontalMm = 0.0,
                gapVerticalMm = 0.0,
                marginTopMm = 10.0,
                marginBottomMm = 10.0,
                marginLeftMm = 7.2,
                marginRightMm = 7.2,
                isDefault = false,
                createdAt = now,
                updatedAt = now,
            ),
            LabelTemplate(
                id = "built-in-a4-48up",
                name = "A4 Label Sheet (4×12, 48-up)",
                paperType = LabelTemplate.PaperType.A4_SHEET,
                paperWidthMm = 210.0,
                labelHeightMm = 22.9,
                columns = 4,
                rows = 12,
                gapHorizontalMm = 0.0,
                gapVerticalMm = 0.0,
                marginTopMm = 10.0,
                marginBottomMm = 10.0,
                marginLeftMm = 5.0,
                marginRightMm = 5.0,
                isDefault = false,
                createdAt = now,
                updatedAt = now,
            ),
        )

        defaults.forEach { template ->
            val result = repository.save(template)
            if (result is Result.Error) return result
        }
        return Result.Success(Unit)
    }
}
