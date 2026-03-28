package com.zyntasolutions.zyntapos.domain.usecase.settings

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.RegionalTaxOverride
import com.zyntasolutions.zyntapos.domain.repository.RegionalTaxOverrideRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeRegionalTaxOverrideRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// ─── Inline fakes with controlled failure ─────────────────────────────────────

private class FailingUpsertRepository : RegionalTaxOverrideRepository {
    private val store = MutableStateFlow<List<RegionalTaxOverride>>(emptyList())
    override fun getOverridesForStore(storeId: String): Flow<List<RegionalTaxOverride>> =
        store.map { it.filter { o -> o.storeId == storeId && o.isActive } }
    override suspend fun getEffectiveOverride(taxGroupId: String, storeId: String, nowEpochMs: Long) =
        Result.Success<RegionalTaxOverride?>(null)
    override fun getOverridesForTaxGroup(taxGroupId: String): Flow<List<RegionalTaxOverride>> =
        store.map { it.filter { o -> o.taxGroupId == taxGroupId } }
    override suspend fun upsert(override: RegionalTaxOverride): Result<Unit> =
        Result.Error(DatabaseException("DB error"))
    override suspend fun delete(id: String): Result<Unit> = Result.Success(Unit)
}

private class FailingDeleteRepository : RegionalTaxOverrideRepository {
    private val store = MutableStateFlow<List<RegionalTaxOverride>>(emptyList())
    override fun getOverridesForStore(storeId: String): Flow<List<RegionalTaxOverride>> =
        store.map { it.filter { o -> o.storeId == storeId && o.isActive } }
    override suspend fun getEffectiveOverride(taxGroupId: String, storeId: String, nowEpochMs: Long) =
        Result.Success<RegionalTaxOverride?>(null)
    override fun getOverridesForTaxGroup(taxGroupId: String): Flow<List<RegionalTaxOverride>> =
        store.map { it.filter { o -> o.taxGroupId == taxGroupId } }
    override suspend fun upsert(override: RegionalTaxOverride): Result<Unit> = Result.Success(Unit)
    override suspend fun delete(id: String): Result<Unit> =
        Result.Error(DatabaseException("DB write failure"))
}

private class TrackingDeleteRepository : RegionalTaxOverrideRepository {
    private val store = MutableStateFlow<List<RegionalTaxOverride>>(emptyList())
    var deleteCalled = false
    override fun getOverridesForStore(storeId: String): Flow<List<RegionalTaxOverride>> =
        store.map { it.filter { o -> o.storeId == storeId && o.isActive } }
    override suspend fun getEffectiveOverride(taxGroupId: String, storeId: String, nowEpochMs: Long) =
        Result.Success<RegionalTaxOverride?>(null)
    override fun getOverridesForTaxGroup(taxGroupId: String): Flow<List<RegionalTaxOverride>> =
        store.map { it.filter { o -> o.taxGroupId == taxGroupId } }
    override suspend fun upsert(override: RegionalTaxOverride): Result<Unit> = Result.Success(Unit)
    override suspend fun delete(id: String): Result<Unit> {
        deleteCalled = true
        return Result.Success(Unit)
    }
}

// ─── Tests ────────────────────────────────────────────────────────────────────

class TaxOverrideUseCasesTest {

    // ─── GetTaxOverridesUseCase ────────────────────────────────────────────────

    @Test
    fun `A - GetTaxOverrides - returns active overrides for store`() = runTest {
        val repo = FakeRegionalTaxOverrideRepository()
        val useCase = GetTaxOverridesUseCase(repo)

        repo.addOverride(
            RegionalTaxOverride(id = "o1", taxGroupId = "tg1", storeId = "s1", effectiveRate = 12.0, isActive = true),
        )

        useCase("s1").test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("o1", items.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `B - GetTaxOverrides - inactive overrides are excluded`() = runTest {
        val repo = FakeRegionalTaxOverrideRepository()
        val useCase = GetTaxOverridesUseCase(repo)

        repo.addOverride(
            RegionalTaxOverride(id = "o1", taxGroupId = "tg1", storeId = "s1", effectiveRate = 5.0, isActive = false),
        )

        useCase("s1").test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `C - GetTaxOverrides - overrides for different store are excluded`() = runTest {
        val repo = FakeRegionalTaxOverrideRepository()
        val useCase = GetTaxOverridesUseCase(repo)

        repo.addOverride(
            RegionalTaxOverride(id = "o1", taxGroupId = "tg1", storeId = "s2", effectiveRate = 5.0),
        )

        useCase("s1").test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `D - GetTaxOverrides - empty store returns empty list`() = runTest {
        val repo = FakeRegionalTaxOverrideRepository()
        val useCase = GetTaxOverridesUseCase(repo)

        useCase("s1").test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `E - GetTaxOverrides - multiple overrides for same store all returned`() = runTest {
        val repo = FakeRegionalTaxOverrideRepository()
        val useCase = GetTaxOverridesUseCase(repo)

        repo.addOverride(RegionalTaxOverride(id = "o1", taxGroupId = "tg1", storeId = "s1", effectiveRate = 5.0))
        repo.addOverride(RegionalTaxOverride(id = "o2", taxGroupId = "tg2", storeId = "s1", effectiveRate = 10.0))
        repo.addOverride(RegionalTaxOverride(id = "o3", taxGroupId = "tg3", storeId = "s2", effectiveRate = 8.0))

        useCase("s1").test {
            val items = awaitItem()
            assertEquals(2, items.size)
            assertTrue(items.any { it.id == "o1" })
            assertTrue(items.any { it.id == "o2" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── SaveTaxOverrideUseCase ────────────────────────────────────────────────

    @Test
    fun `F - SaveTaxOverride - blank taxGroupId returns REQUIRED error`() = runTest {
        val useCase = SaveTaxOverrideUseCase(FakeRegionalTaxOverrideRepository())

        val result = useCase(
            RegionalTaxOverride(id = "o1", taxGroupId = "   ", storeId = "s1", effectiveRate = 5.0),
        )

        assertIs<Result.Error>(result)
        val ex = assertIs<ValidationException>(result.exception)
        assertEquals("taxGroupId", ex.field)
        assertEquals("REQUIRED", ex.rule)
    }

    @Test
    fun `G - SaveTaxOverride - blank storeId returns REQUIRED error`() = runTest {
        val useCase = SaveTaxOverrideUseCase(FakeRegionalTaxOverrideRepository())

        val result = useCase(
            RegionalTaxOverride(id = "o1", taxGroupId = "tg1", storeId = "", effectiveRate = 5.0),
        )

        assertIs<Result.Error>(result)
        val ex = assertIs<ValidationException>(result.exception)
        assertEquals("storeId", ex.field)
        assertEquals("REQUIRED", ex.rule)
    }

    @Test
    fun `H - SaveTaxOverride - effectiveRate 0 boundary is valid`() = runTest {
        val useCase = SaveTaxOverrideUseCase(FakeRegionalTaxOverrideRepository())

        assertIs<Result.Success<Unit>>(
            useCase(RegionalTaxOverride(id = "o1", taxGroupId = "tg1", storeId = "s1", effectiveRate = 0.0)),
        )
    }

    @Test
    fun `I - SaveTaxOverride - effectiveRate 100 boundary is valid`() = runTest {
        val useCase = SaveTaxOverrideUseCase(FakeRegionalTaxOverrideRepository())

        assertIs<Result.Success<Unit>>(
            useCase(RegionalTaxOverride(id = "o1", taxGroupId = "tg1", storeId = "s1", effectiveRate = 100.0)),
        )
    }

    @Test
    fun `J - SaveTaxOverride - effectiveRate mid-range is valid`() = runTest {
        val useCase = SaveTaxOverrideUseCase(FakeRegionalTaxOverrideRepository())

        assertIs<Result.Success<Unit>>(
            useCase(RegionalTaxOverride(id = "o1", taxGroupId = "tg1", storeId = "s1", effectiveRate = 15.0)),
        )
    }

    @Test
    fun `K - SaveTaxOverride - valid override is persisted via upsert`() = runTest {
        val repo = FakeRegionalTaxOverrideRepository()
        val useCase = SaveTaxOverrideUseCase(repo)

        assertIs<Result.Success<Unit>>(
            useCase(RegionalTaxOverride(id = "o1", taxGroupId = "tg1", storeId = "s1", effectiveRate = 15.0)),
        )

        repo.getOverridesForStore("s1").test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals(15.0, items.first().effectiveRate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `L - SaveTaxOverride - repository failure propagates`() = runTest {
        val useCase = SaveTaxOverrideUseCase(FailingUpsertRepository())

        assertIs<Result.Error>(
            useCase(RegionalTaxOverride(id = "o1", taxGroupId = "tg1", storeId = "s1", effectiveRate = 5.0)),
        )
    }

    @Test
    fun `M - SaveTaxOverride - upsert updates existing override`() = runTest {
        val repo = FakeRegionalTaxOverrideRepository()
        val useCase = SaveTaxOverrideUseCase(repo)

        repo.addOverride(RegionalTaxOverride(id = "o1", taxGroupId = "tg1", storeId = "s1", effectiveRate = 5.0))
        assertIs<Result.Success<Unit>>(
            useCase(RegionalTaxOverride(id = "o1", taxGroupId = "tg1", storeId = "s1", effectiveRate = 18.0)),
        )

        repo.getOverridesForStore("s1").test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals(18.0, items.first().effectiveRate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── DeleteTaxOverrideUseCase ──────────────────────────────────────────────

    @Test
    fun `N - DeleteTaxOverride - blank id returns REQUIRED error`() = runTest {
        val useCase = DeleteTaxOverrideUseCase(FakeRegionalTaxOverrideRepository())

        val result = useCase("   ")

        assertIs<Result.Error>(result)
        val ex = assertIs<ValidationException>(result.exception)
        assertEquals("id", ex.field)
        assertEquals("REQUIRED", ex.rule)
    }

    @Test
    fun `O - DeleteTaxOverride - empty id returns REQUIRED error`() = runTest {
        val useCase = DeleteTaxOverrideUseCase(FakeRegionalTaxOverrideRepository())

        val result = useCase("")

        assertIs<Result.Error>(result)
        val ex = assertIs<ValidationException>(result.exception)
        assertEquals("id", ex.field)
    }

    @Test
    fun `P - DeleteTaxOverride - blank id does not call repository`() = runTest {
        val trackingRepo = TrackingDeleteRepository()
        val useCase = DeleteTaxOverrideUseCase(trackingRepo)

        useCase("")

        assertTrue(!trackingRepo.deleteCalled, "Repository.delete should not be called for blank id")
    }

    @Test
    fun `Q - DeleteTaxOverride - valid id removes override from repository`() = runTest {
        val repo = FakeRegionalTaxOverrideRepository()
        val useCase = DeleteTaxOverrideUseCase(repo)

        repo.addOverride(
            RegionalTaxOverride(id = "o1", taxGroupId = "tg1", storeId = "s1", effectiveRate = 5.0),
        )

        assertIs<Result.Success<Unit>>(useCase("o1"))

        repo.getOverridesForStore("s1").test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `R - DeleteTaxOverride - repository failure propagates`() = runTest {
        val useCase = DeleteTaxOverrideUseCase(FailingDeleteRepository())

        assertIs<Result.Error>(useCase("o1"))
    }
}
