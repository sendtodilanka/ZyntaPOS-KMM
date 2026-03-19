package com.zyntasolutions.zyntapos.data.repository

import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.StocktakeStatus
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock

class StocktakeRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: StocktakeRepositoryImpl
    private val now get() = Clock.System.now().toEpochMilliseconds()

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = StocktakeRepositoryImpl(db)
        // Seed a test product for barcode lookups
        db.productsQueries.insertProduct(
            id            = "prod-001",
            name          = "Test Product",
            barcode       = "1234567890123",
            sku           = null,
            category_id   = null,
            unit_id       = "pcs",
            price         = 9.99,
            cost_price    = 5.00,
            tax_group_id  = null,
            stock_qty     = 25.0,
            min_stock_qty = 5.0,
            image_url     = null,
            description   = null,
            is_active     = 1L,
            created_at    = now,
            updated_at    = now,
            sync_status   = "PENDING",
            master_product_id = null,
        )
    }

    // ── startSession ────────────────────────────────────────────────────────

    @Test
    fun `startSession creates IN_PROGRESS session`() = runTest {
        val result = repo.startSession("user-1")

        assertIs<Result.Success<*>>(result)
        val session = (result as Result.Success).data
        assertEquals("user-1",                  session.startedBy)
        assertEquals(StocktakeStatus.IN_PROGRESS, session.status)
        assertTrue(session.counts.isEmpty())
    }

    // ── updateCount then getCountsForSession ─────────────────────────────────

    @Test
    fun `updateCount inserts count on first scan`() = runTest {
        val session = (repo.startSession("user-1") as Result.Success).data
        repo.updateCount(session.id, "1234567890123", 3)

        val countsResult = repo.getCountsForSession(session.id)
        assertIs<Result.Success<*>>(countsResult)
        val counts = (countsResult as Result.Success).data
        assertEquals(1, counts.size)
        assertEquals("prod-001",      counts[0].productId)
        assertEquals(3,               counts[0].countedQty)
        assertEquals(25,              counts[0].systemQty)  // captured from products.stock_qty
    }

    @Test
    fun `updateCount updates existing count on second scan`() = runTest {
        val session = (repo.startSession("user-1") as Result.Success).data
        repo.updateCount(session.id, "1234567890123", 2)
        repo.updateCount(session.id, "1234567890123", 5)

        val countsResult = repo.getCountsForSession(session.id)
        val counts = (countsResult as Result.Success).data
        assertEquals(1, counts.size)        // still only 1 row
        assertEquals(5, counts[0].countedQty)
        assertEquals(25, counts[0].systemQty)  // systemQty unchanged from first scan
    }

    // ── getSession ───────────────────────────────────────────────────────────

    @Test
    fun `getSession returns session with counts`() = runTest {
        val session = (repo.startSession("user-1") as Result.Success).data
        repo.updateCount(session.id, "1234567890123", 7)

        val loaded = repo.getSession(session.id)
        assertIs<Result.Success<*>>(loaded)
        val s = (loaded as Result.Success).data
        assertEquals(session.id,               s.id)
        assertEquals(StocktakeStatus.IN_PROGRESS, s.status)
        assertEquals(1,                        s.counts.size)
        assertEquals(7,                        s.counts["prod-001"])
    }

    @Test
    fun `getSession non-existent returns error`() = runTest {
        val result = repo.getSession("no-such-session")
        assertIs<Result.Error>(result)
    }

    // ── complete ─────────────────────────────────────────────────────────────

    @Test
    fun `complete marks session COMPLETED and returns variance map`() = runTest {
        val session = (repo.startSession("user-1") as Result.Success).data
        repo.updateCount(session.id, "1234567890123", 30) // system = 25, counted = 30

        val result = repo.complete(session.id)
        assertIs<Result.Success<*>>(result)
        val variances = (result as Result.Success).data
        assertEquals(1, variances.size)
        assertEquals(5, variances["prod-001"]) // 30 - 25 = +5

        // Verify session is COMPLETED
        val loaded = (repo.getSession(session.id) as Result.Success).data
        assertEquals(StocktakeStatus.COMPLETED, loaded.status)
    }

    @Test
    fun `complete with shortage returns negative variance`() = runTest {
        val session = (repo.startSession("user-1") as Result.Success).data
        repo.updateCount(session.id, "1234567890123", 20) // system = 25, counted = 20

        val variances = (repo.complete(session.id) as Result.Success).data
        assertEquals(-5, variances["prod-001"]) // 20 - 25 = -5
    }

    // ── cancel ───────────────────────────────────────────────────────────────

    @Test
    fun `cancel marks session CANCELLED`() = runTest {
        val session = (repo.startSession("user-1") as Result.Success).data
        repo.cancel(session.id)

        val loaded = (repo.getSession(session.id) as Result.Success).data
        assertEquals(StocktakeStatus.CANCELLED, loaded.status)
    }
}
