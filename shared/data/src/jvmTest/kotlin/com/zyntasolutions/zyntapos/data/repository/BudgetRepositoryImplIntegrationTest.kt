package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.Budget
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — BudgetRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [BudgetRepositoryImpl] against a real in-memory SQLite database.
 * budgets has no external FK constraints (category_id is optional TEXT with no FK).
 *
 * Coverage:
 *  A. insert → getById round-trip preserves all fields
 *  B. getByStore emits budgets for store via Turbine
 *  C. getByStore excludes budgets for other stores
 *  D. getByStoreAndPeriod returns budgets matching the date
 *  E. updateSpent increases spent amount
 *  F. delete removes budget and getById returns error
 *  G. null categoryId round-trips correctly
 */
class BudgetRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: BudgetRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = BudgetRepositoryImpl(db, SyncEnqueuer(db))
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private val now get() = Clock.System.now().toEpochMilliseconds()

    private fun makeBudget(
        id: String = "budget-01",
        storeId: String = "store-01",
        categoryId: String? = null,
        periodStart: String = "2026-04-01",
        periodEnd: String = "2026-04-30",
        budgetAmount: Double = 100000.0,
        spentAmount: Double = 0.0,
        name: String = "April 2026 Operations",
    ) = Budget(
        id = id,
        storeId = storeId,
        categoryId = categoryId,
        periodStart = periodStart,
        periodEnd = periodEnd,
        budgetAmount = budgetAmount,
        spentAmount = spentAmount,
        name = name,
        createdAt = now,
        updatedAt = now,
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - insert then getById round-trip preserves all fields`() = runTest {
        val budget = makeBudget(
            id = "budget-01",
            storeId = "store-01",
            categoryId = "cat-marketing",
            periodStart = "2026-04-01",
            periodEnd = "2026-04-30",
            budgetAmount = 50000.0,
            name = "April Marketing",
        )
        val insertResult = repo.insert(budget)
        assertIs<Result.Success<Unit>>(insertResult)

        val fetchResult = repo.getById("budget-01")
        assertIs<Result.Success<Budget>>(fetchResult)
        val fetched = fetchResult.data
        assertEquals("budget-01", fetched.id)
        assertEquals("store-01", fetched.storeId)
        assertEquals("cat-marketing", fetched.categoryId)
        assertEquals("2026-04-01", fetched.periodStart)
        assertEquals("2026-04-30", fetched.periodEnd)
        assertEquals(50000.0, fetched.budgetAmount)
        assertEquals(0.0, fetched.spentAmount)
        assertEquals("April Marketing", fetched.name)
    }

    @Test
    fun `B - getByStore emits budgets for specific store via Turbine`() = runTest {
        repo.insert(makeBudget(id = "budget-01", storeId = "store-01", periodStart = "2026-03-01", periodEnd = "2026-03-31", name = "Mar"))
        repo.insert(makeBudget(id = "budget-02", storeId = "store-01", periodStart = "2026-04-01", periodEnd = "2026-04-30", name = "Apr"))

        repo.getByStore("store-01").test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertTrue(list.all { it.storeId == "store-01" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `C - getByStore excludes budgets for other stores`() = runTest {
        repo.insert(makeBudget(id = "budget-01", storeId = "store-01", name = "Store 1 Budget"))
        repo.insert(makeBudget(id = "budget-02", storeId = "store-02", name = "Store 2 Budget"))

        repo.getByStore("store-01").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("store-01", list.first().storeId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `D - getByStoreAndPeriod returns budgets with overlapping period`() = runTest {
        repo.insert(makeBudget(id = "budget-jan", periodStart = "2026-01-01", periodEnd = "2026-01-31", name = "Jan"))
        repo.insert(makeBudget(id = "budget-apr", periodStart = "2026-04-01", periodEnd = "2026-04-30", name = "Apr"))

        val result = repo.getByStoreAndPeriod("store-01", "2026-04-15")
        assertIs<Result.Success<List<Budget>>>(result)
        assertEquals(1, result.data.size)
        assertEquals("budget-apr", result.data.first().id)
    }

    @Test
    fun `E - updateSpent increases the spent amount`() = runTest {
        repo.insert(makeBudget(id = "budget-01", budgetAmount = 100000.0, spentAmount = 0.0))

        val updateResult = repo.updateSpent("budget-01", 25000.0)
        assertIs<Result.Success<Unit>>(updateResult)

        val fetched = (repo.getById("budget-01") as Result.Success).data
        assertEquals(25000.0, fetched.spentAmount)
    }

    @Test
    fun `F - delete removes budget and getById returns error`() = runTest {
        repo.insert(makeBudget(id = "budget-01"))
        repo.insert(makeBudget(id = "budget-02", periodStart = "2026-03-01", periodEnd = "2026-03-31", name = "Mar"))

        val deleteResult = repo.delete("budget-01")
        assertIs<Result.Success<Unit>>(deleteResult)

        val result = repo.getById("budget-01")
        assertIs<Result.Error>(result)

        // budget-02 still present
        repo.getByStore("store-01").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("budget-02", list.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `G - null categoryId round-trips correctly`() = runTest {
        repo.insert(makeBudget(id = "budget-01", categoryId = null))

        val fetched = (repo.getById("budget-01") as Result.Success).data
        assertNull(fetched.categoryId)
    }
}
