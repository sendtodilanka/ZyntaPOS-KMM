package com.zyntasolutions.zyntapos.domain.usecase.pos

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.CompoundTaxComponent
import com.zyntasolutions.zyntapos.domain.repository.CompoundTaxRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — GetCompoundTaxComponentsUseCase Unit Tests (commonTest)
 *
 * Validates compound tax component resolution for a given tax group (C2.3).
 *
 * Coverage:
 *  A. returns components when repository returns Result.Success with data
 *  B. returns empty list when repository returns Result.Success with empty list
 *  C. returns empty list when repository returns Result.Error (graceful degradation)
 *  D. components are returned in the order provided by repository
 *  E. components for the correct taxGroupId are requested
 */
class GetCompoundTaxComponentsUseCaseTest {

    // ── Fake ──────────────────────────────────────────────────────────────────

    private class FakeCompoundTaxRepository : CompoundTaxRepository {
        var componentsResult: Result<List<CompoundTaxComponent>> =
            Result.Success(emptyList())
        var lastRequestedTaxGroupId: String? = null

        override suspend fun getComponentsForTaxGroup(
            parentTaxGroupId: String,
        ): Result<List<CompoundTaxComponent>> {
            lastRequestedTaxGroupId = parentTaxGroupId
            return componentsResult
        }

        override suspend fun getAllCompoundTaxGroupIds(): Result<List<String>> =
            Result.Success(emptyList())

        override suspend fun insertComponent(component: CompoundTaxComponent): Result<Unit> =
            Result.Success(Unit)

        override suspend fun deleteComponent(componentId: String): Result<Unit> =
            Result.Success(Unit)

        override suspend fun deleteAllForTaxGroup(parentTaxGroupId: String): Result<Unit> =
            Result.Success(Unit)
    }

    private fun buildComponent(
        id: String = "comp-1",
        parentTaxGroupId: String = "tax-01",
        componentTaxGroupId: String = "tax-comp-01",
        componentName: String = "VAT",
        componentRate: Double = 15.0,
        applicationOrder: Int = 0,
        isCompounding: Boolean = false,
    ) = CompoundTaxComponent(
        id = id,
        parentTaxGroupId = parentTaxGroupId,
        componentTaxGroupId = componentTaxGroupId,
        componentName = componentName,
        componentRate = componentRate,
        applicationOrder = applicationOrder,
        isCompounding = isCompounding,
    )

    private fun makeUseCase(): Pair<GetCompoundTaxComponentsUseCase, FakeCompoundTaxRepository> {
        val repo = FakeCompoundTaxRepository()
        return GetCompoundTaxComponentsUseCase(repo) to repo
    }

    // ── A — Success with data ─────────────────────────────────────────────────

    @Test
    fun `A - returns components when repository returns Success`() = runTest {
        val (useCase, repo) = makeUseCase()
        val components = listOf(
            buildComponent(id = "c1", componentName = "VAT", componentRate = 15.0),
            buildComponent(id = "c2", componentName = "Service Charge", componentRate = 10.0, applicationOrder = 1),
        )
        repo.componentsResult = Result.Success(components)

        val result = useCase("tax-01")

        assertEquals(2, result.size)
        assertEquals("c1", result[0].id)
        assertEquals("c2", result[1].id)
    }

    // ── B — Success with empty list ───────────────────────────────────────────

    @Test
    fun `B - returns empty list when repository returns Success with empty list`() = runTest {
        val (useCase, _) = makeUseCase()
        // repo.componentsResult defaults to Result.Success(emptyList())

        val result = useCase("tax-no-compound")

        assertTrue(result.isEmpty(), "Expected empty list for tax group with no compound components")
    }

    // ── C — Error returns empty list (graceful degradation) ───────────────────

    @Test
    fun `C - returns empty list when repository returns Error`() = runTest {
        val (useCase, repo) = makeUseCase()
        repo.componentsResult = Result.Error(DatabaseException("DB error"))

        val result = useCase("tax-01")

        assertTrue(result.isEmpty(), "Expected empty list on repository error (backward-compatible fallback)")
    }

    // ── D — Preserves order ───────────────────────────────────────────────────

    @Test
    fun `D - components are returned in repository order`() = runTest {
        val (useCase, repo) = makeUseCase()
        val components = listOf(
            buildComponent(id = "c1", applicationOrder = 0),
            buildComponent(id = "c2", applicationOrder = 1),
            buildComponent(id = "c3", applicationOrder = 2),
        )
        repo.componentsResult = Result.Success(components)

        val result = useCase("tax-01")

        assertEquals(listOf("c1", "c2", "c3"), result.map { it.id })
    }

    // ── E — Correct taxGroupId forwarded ─────────────────────────────────────

    @Test
    fun `E - use case forwards the taxGroupId to repository`() = runTest {
        val (useCase, repo) = makeUseCase()

        useCase("tax-compound-99")

        assertEquals("tax-compound-99", repo.lastRequestedTaxGroupId)
    }
}
