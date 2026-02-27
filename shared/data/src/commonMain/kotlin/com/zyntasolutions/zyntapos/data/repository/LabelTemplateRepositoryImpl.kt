package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.db.Label_templates
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.LabelTemplate
import com.zyntasolutions.zyntapos.domain.repository.LabelTemplateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class LabelTemplateRepositoryImpl(
    private val db: ZyntaDatabase,
) : LabelTemplateRepository {

    private val q get() = db.label_templatesQueries

    override fun getAll(): Flow<List<LabelTemplate>> =
        q.getAllTemplates()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::toDomain) }

    override suspend fun getById(id: String): Result<LabelTemplate> = withContext(Dispatchers.IO) {
        runCatching {
            q.getTemplateById(id).executeAsOneOrNull()
                ?: return@withContext Result.Error(DatabaseException("Label template not found: $id"))
        }.fold(
            onSuccess = { row -> Result.Success(toDomain(row)) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Failed to load template", cause = t)) },
        )
    }

    override suspend fun save(template: LabelTemplate): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            q.upsertTemplate(
                id               = template.id,
                name             = template.name,
                paper_type       = template.paperType.name,
                paper_width_mm   = template.paperWidthMm,
                label_height_mm  = template.labelHeightMm,
                columns_         = template.columns.toLong(),
                rows_            = template.rows.toLong(),
                gap_h_mm         = template.gapHorizontalMm,
                gap_v_mm         = template.gapVerticalMm,
                margin_top_mm    = template.marginTopMm,
                margin_bottom_mm = template.marginBottomMm,
                margin_left_mm   = template.marginLeftMm,
                margin_right_mm  = template.marginRightMm,
                is_default       = if (template.isDefault) 1L else 0L,
                created_at       = template.createdAt,
                updated_at       = template.updatedAt,
            )
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Failed to save template", cause = t)) },
        )
    }

    override suspend fun delete(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            q.deleteTemplate(id)
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Failed to delete template", cause = t)) },
        )
    }

    override suspend fun count(): Int = withContext(Dispatchers.IO) {
        q.countTemplates().executeAsOne().toInt()
    }

    private fun toDomain(row: Label_templates) = LabelTemplate(
        id               = row.id,
        name             = row.name,
        paperType        = LabelTemplate.PaperType.valueOf(row.paper_type),
        paperWidthMm     = row.paper_width_mm,
        labelHeightMm    = row.label_height_mm,
        columns          = row.columns_.toInt(),
        rows             = row.rows_.toInt(),
        gapHorizontalMm  = row.gap_h_mm,
        gapVerticalMm    = row.gap_v_mm,
        marginTopMm      = row.margin_top_mm,
        marginBottomMm   = row.margin_bottom_mm,
        marginLeftMm     = row.margin_left_mm,
        marginRightMm    = row.margin_right_mm,
        isDefault        = row.is_default == 1L,
        createdAt        = row.created_at,
        updatedAt        = row.updated_at,
    )
}
