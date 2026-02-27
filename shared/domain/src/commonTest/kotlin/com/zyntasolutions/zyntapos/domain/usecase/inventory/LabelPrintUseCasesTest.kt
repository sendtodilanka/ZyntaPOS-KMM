package com.zyntasolutions.zyntapos.domain.usecase.inventory

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.LabelTemplate
import com.zyntasolutions.zyntapos.domain.repository.LabelTemplateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// ─────────────────────────────────────────────────────────────────────────────
// In-memory fake repository
// ─────────────────────────────────────────────────────────────────────────────

private class FakeLabelTemplateRepository : LabelTemplateRepository {
    val templates = mutableListOf<LabelTemplate>()
    private val flow = MutableStateFlow(emptyList<LabelTemplate>())

    override fun getAll(): Flow<List<LabelTemplate>> = flow

    override suspend fun getById(id: String): Result<LabelTemplate> {
        val t = templates.find { it.id == id }
        return if (t != null) Result.Success(t)
        else Result.Error(com.zyntasolutions.zyntapos.core.result.DatabaseException("Not found"))
    }

    override suspend fun save(template: LabelTemplate): Result<Unit> {
        val idx = templates.indexOfFirst { it.id == template.id }
        if (idx >= 0) templates[idx] = template else templates.add(template)
        flow.value = templates.toList()
        return Result.Success(Unit)
    }

    override suspend fun delete(id: String): Result<Unit> {
        templates.removeAll { it.id == id }
        flow.value = templates.toList()
        return Result.Success(Unit)
    }

    override suspend fun count(): Int = templates.size
}

// ─────────────────────────────────────────────────────────────────────────────
// Builder helper
// ─────────────────────────────────────────────────────────────────────────────

private fun buildTemplate(
    id: String = "tmpl-001",
    name: String = "Test Roll Template",
    paperType: LabelTemplate.PaperType = LabelTemplate.PaperType.CONTINUOUS_ROLL,
    paperWidthMm: Double = 58.0,
    labelHeightMm: Double = 25.0,
    columns: Int = 1,
    rows: Int = 0,
    isDefault: Boolean = false,
): LabelTemplate = LabelTemplate(
    id               = id,
    name             = name,
    paperType        = paperType,
    paperWidthMm     = paperWidthMm,
    labelHeightMm    = labelHeightMm,
    columns          = columns,
    rows             = rows,
    gapHorizontalMm  = 2.0,
    gapVerticalMm    = 3.0,
    marginTopMm      = 2.0,
    marginBottomMm   = 0.0,
    marginLeftMm     = 2.0,
    marginRightMm    = 2.0,
    isDefault        = isDefault,
    createdAt        = 1_700_000_000_000L,
    updatedAt        = 1_700_000_000_000L,
)

// ─────────────────────────────────────────────────────────────────────────────
// Tests
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Unit tests for label-printing use cases:
 * [SeedDefaultLabelTemplatesUseCase], [SaveLabelTemplateUseCase], [DeleteLabelTemplateUseCase].
 *
 * Layout engine tests live in `:composeApp:feature:inventory:commonTest`
 * since [LabelLayoutEngine] is declared in the feature module.
 */
class LabelPrintUseCasesTest {

    // ── 1. Seed inserts exactly 4 defaults on empty repository ───────────────

    @Test
    fun `seed inserts exactly 4 default templates when repository is empty`() = runTest {
        val repo    = FakeLabelTemplateRepository()
        val useCase = SeedDefaultLabelTemplatesUseCase(repo)

        val result = useCase.execute()

        assertIs<Result.Success<Unit>>(result)
        assertEquals(4, repo.templates.size)
    }

    // ── 2. Seed is idempotent ─────────────────────────────────────────────────

    @Test
    fun `seed is idempotent when templates already exist`() = runTest {
        val repo    = FakeLabelTemplateRepository()
        val useCase = SeedDefaultLabelTemplatesUseCase(repo)

        useCase.execute()  // first run — seeds 4 defaults
        useCase.execute()  // second run — must not add more

        assertEquals(4, repo.templates.size)
    }

    // ── 3. Save valid template delegates to repository ────────────────────────

    @Test
    fun `save valid template delegates to repository and returns success`() = runTest {
        val repo     = FakeLabelTemplateRepository()
        val useCase  = SaveLabelTemplateUseCase(repo)
        val template = buildTemplate()

        val result = useCase.execute(template)

        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, repo.templates.size)
        assertEquals(template.id, repo.templates.first().id)
    }

    // ── 4. Save blank name returns NAME_BLANK validation error ────────────────

    @Test
    fun `save template with blank name returns NAME_BLANK validation error`() = runTest {
        val repo    = FakeLabelTemplateRepository()
        val useCase = SaveLabelTemplateUseCase(repo)
        val invalid = buildTemplate(name = "")

        val result = useCase.execute(invalid)

        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("NAME_BLANK", ex.rule)
        assertTrue(repo.templates.isEmpty())
    }

    // ── 5. Delete delegates to repository ────────────────────────────────────

    @Test
    fun `delete template delegates to repository and removes the template`() = runTest {
        val repo    = FakeLabelTemplateRepository()
        val save    = SaveLabelTemplateUseCase(repo)
        val delete  = DeleteLabelTemplateUseCase(repo)
        val template = buildTemplate()

        save.execute(template)
        assertEquals(1, repo.templates.size)

        val result = delete.execute(template.id)

        assertIs<Result.Success<Unit>>(result)
        assertTrue(repo.templates.isEmpty())
    }

    // ── 6. Seed produces a template with isDefault = true ─────────────────────

    @Test
    fun `seed produces at least one template with isDefault = true`() = runTest {
        val repo    = FakeLabelTemplateRepository()
        val useCase = SeedDefaultLabelTemplatesUseCase(repo)

        useCase.execute()

        assertTrue(repo.templates.any { it.isDefault })
    }
}
