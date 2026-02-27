package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.LabelTemplate
import kotlinx.coroutines.flow.Flow

interface LabelTemplateRepository {
    fun getAll(): Flow<List<LabelTemplate>>
    suspend fun getById(id: String): Result<LabelTemplate>
    suspend fun save(template: LabelTemplate): Result<Unit>
    suspend fun delete(id: String): Result<Unit>
    suspend fun count(): Int
}
