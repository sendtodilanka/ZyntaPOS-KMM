package com.zyntasolutions.zyntapos.domain.usecase.inventory

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.repository.LabelTemplateRepository

/** Deletes a label template by ID. Does nothing if the template does not exist. */
class DeleteLabelTemplateUseCase(
    private val repository: LabelTemplateRepository,
) {
    suspend fun execute(templateId: String): Result<Unit> = repository.delete(templateId)
}
