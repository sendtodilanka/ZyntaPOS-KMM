package com.zyntasolutions.zyntapos.domain.usecase.inventory

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.LabelTemplate
import com.zyntasolutions.zyntapos.domain.repository.LabelTemplateRepository

/**
 * Validates and persists a [LabelTemplate].
 *
 * Validation rules:
 * - [LabelTemplate.name] must not be blank.
 * - [LabelTemplate.paperWidthMm] and [LabelTemplate.labelHeightMm] must be > 0.
 * - [LabelTemplate.columns] must be in 1..4.
 * - [LabelTemplate.rows] must be in 1..20 for A4_SHEET; 0 for CONTINUOUS_ROLL.
 * - Margins and gaps must be >= 0.
 * - [LabelTemplate.salePriceLabel] must not exceed 20 characters.
 */
class SaveLabelTemplateUseCase(
    private val repository: LabelTemplateRepository,
) {
    suspend fun execute(template: LabelTemplate): Result<Unit> {
        if (template.name.isBlank()) {
            return Result.Error(ValidationException("Template name must not be blank", rule = "NAME_BLANK"))
        }
        if (template.paperWidthMm <= 0.0) {
            return Result.Error(ValidationException("Paper width must be greater than 0", rule = "WIDTH_INVALID"))
        }
        if (template.labelHeightMm <= 0.0) {
            return Result.Error(ValidationException("Label height must be greater than 0", rule = "HEIGHT_INVALID"))
        }
        if (template.columns !in 1..4) {
            return Result.Error(ValidationException("Columns must be between 1 and 4", rule = "COLUMNS_INVALID"))
        }
        if (template.paperType == LabelTemplate.PaperType.A4_SHEET && template.rows !in 1..20) {
            return Result.Error(ValidationException("Rows must be between 1 and 20 for A4 sheets", rule = "ROWS_INVALID"))
        }
        if (template.marginTopMm < 0 || template.marginBottomMm < 0 ||
            template.marginLeftMm < 0 || template.marginRightMm < 0) {
            return Result.Error(ValidationException("Margins must be non-negative", rule = "MARGIN_INVALID"))
        }
        if (template.gapHorizontalMm < 0 || template.gapVerticalMm < 0) {
            return Result.Error(ValidationException("Gaps must be non-negative", rule = "GAP_INVALID"))
        }
        if (template.salePriceLabel.length > 20) {
            return Result.Error(
                ValidationException(
                    "Sale price label must not exceed 20 characters",
                    field = "salePriceLabel",
                    rule = "SALE_PRICE_LABEL_TOO_LONG",
                )
            )
        }
        return repository.save(template)
    }
}
