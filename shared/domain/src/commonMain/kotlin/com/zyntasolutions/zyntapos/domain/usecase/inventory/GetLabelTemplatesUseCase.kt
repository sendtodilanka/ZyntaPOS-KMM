package com.zyntasolutions.zyntapos.domain.usecase.inventory

import com.zyntasolutions.zyntapos.domain.model.LabelTemplate
import com.zyntasolutions.zyntapos.domain.repository.LabelTemplateRepository
import kotlinx.coroutines.flow.Flow

/** Returns a live [Flow] of all saved label templates (default first, then alphabetical). */
class GetLabelTemplatesUseCase(
    private val repository: LabelTemplateRepository,
) {
    fun execute(): Flow<List<LabelTemplate>> = repository.getAll()
}
