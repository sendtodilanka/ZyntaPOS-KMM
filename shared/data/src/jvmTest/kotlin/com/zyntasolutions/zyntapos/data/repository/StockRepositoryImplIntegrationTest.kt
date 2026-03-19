package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.Product
import com.zyntasolutions.zyntapos.domain.model.StockAdjustment
import com.zyntasolutions.zyntapos.domain.model.SyncStatus
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — StockRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [StockRepositoryImpl] against a real in-memory SQLite database.
 * No mocks are used; every test exercises the full SQLDelight query layer.
 *
 * Coverage:
 *  A. adjustStock INCREASE raises product stock_qty by the adjustment quantity
 *  B. adjustStock DECREASE lowers product stock_qty by the adjustment quantity
 *  C. adjustStock DECREASE below zero is rejected with Result.Error
 *  D. getMovements returns all adjustment records for the given productId
 *  E. getMovements returns empty list when no adjustments exist for productId
 *  F. getAlerts (null threshold) returns products whose stock_qty <= min_stock_qty
 *  G. getAlerts (explicit threshold) filters by the provided threshold value
 *  H. adjustStock DECREASE to exactly min_stock_qty creates a low-stock alert
 */
class StockRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: StockRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        val syncEnqueuer = SyncEnqueuer(db)
        repo = StockRepositoryImpl(db, syncEnqueuer)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private val now get() = Clock.System.now().toEpochMilliseconds()

    /** Insert a product row directly via productsQueries so stock tests have a valid FK target. */
    private fun insertProduct(
        id: String,
        stockQty: Double = 50.0,
        minStockQty: Double = 5.0,
        isActive: Boolean = true,
    ) {
        db.productsQueries.insertProduct(
            id            = id,
            name          = "Test Product $id",
            barcode       = null,
            sku           = null,
            category_id   = null,
            unit_id       = "pcs",
            price         = 10.0,
            cost_price    = 5.0,
            tax_group_id  = null,
            stock_qty     = stockQty,
            min_stock_qty = minStockQty,
            image_url     = null,
            description   = null,
            is_active     = if (isActive) 1L else 0L,
            created_at    = now,
            updated_at    = now,
            sync_status   = "PENDING",
            master_product_id = null,
        )
    }

    /** Build a [StockAdjustment] with the given parameters. */
    private fun buildAdjustment(
        id: String,
        productId: String,
        type: StockAdjustment.Type,
        quantity: Double = 10.0,
        reason: String = "Integration test",
        adjustedBy: String = "user-1",
    ): StockAdjustment = StockAdjustment(
        id         = id,
        productId  = productId,
        type       = type,
        quantity   = quantity,
        reason     = reason,
        adjustedBy = adjustedBy,
        timestamp  = Instant.fromEpochMilliseconds(now),
        syncStatus = SyncStatus.pending(),
    )

    // ── A. adjustStock INCREASE raises stock_qty ──────────────────────────────

    @Test
    fun adjustStock_INCREASE_raises_product_stock_qty() = runTest {
        insertProduct(id = "prod-inc", stockQty = 20.0)

        val adjustment = buildAdjustment(
            id        = "adj-inc-1",
            productId = "prod-inc",
            type      = StockAdjustment.Type.INCREASE,
            quantity  = 15.0,
        )

        val result = repo.adjustStock(adjustment)
        assertIs<Result.Success<Unit>>(result, "adjustStock INCREASE should succeed")

        val row = db.productsQueries.getProductById("prod-inc").executeAsOneOrNull()
        assertEquals(35.0, row?.stock_qty, "stock_qty should be 20 + 15 = 35 after INCREASE")
    }

    // ── B. adjustStock DECREASE lowers stock_qty ──────────────────────────────

    @Test
    fun adjustStock_DECREASE_lowers_product_stock_qty() = runTest {
        insertProduct(id = "prod-dec", stockQty = 30.0)

        val adjustment = buildAdjustment(
            id        = "adj-dec-1",
            productId = "prod-dec",
            type      = StockAdjustment.Type.DECREASE,
            quantity  = 8.0,
        )

        val result = repo.adjustStock(adjustment)
        assertIs<Result.Success<Unit>>(result, "adjustStock DECREASE should succeed")

        val row = db.productsQueries.getProductById("prod-dec").executeAsOneOrNull()
        assertEquals(22.0, row?.stock_qty, "stock_qty should be 30 - 8 = 22 after DECREASE")
    }

    // ── C. adjustStock DECREASE below zero is rejected ────────────────────────

    @Test
    fun adjustStock_DECREASE_beyond_available_stock_returns_error() = runTest {
        insertProduct(id = "prod-neg", stockQty = 5.0)

        val adjustment = buildAdjustment(
            id        = "adj-neg-1",
            productId = "prod-neg",
            type      = StockAdjustment.Type.DECREASE,
            quantity  = 10.0,   // more than the available 5.0
        )

        val result = repo.adjustStock(adjustment)
        assertIs<Result.Error>(result, "adjustStock DECREASE beyond available stock should return Result.Error")

        // Stock should remain unchanged after the rejected adjustment
        val row = db.productsQueries.getProductById("prod-neg").executeAsOneOrNull()
        assertEquals(5.0, row?.stock_qty, "stock_qty should be unchanged when DECREASE is rejected")
    }

    // ── D. getMovements returns all records for productId ─────────────────────

    @Test
    fun getMovements_returns_all_adjustment_records_for_productId() = runTest {
        insertProduct(id = "prod-mv", stockQty = 100.0)

        repo.adjustStock(buildAdjustment("adj-mv-1", "prod-mv", StockAdjustment.Type.INCREASE, 20.0))
        repo.adjustStock(buildAdjustment("adj-mv-2", "prod-mv", StockAdjustment.Type.DECREASE, 5.0))

        repo.getMovements("prod-mv").test {
            val movements = awaitItem()
            assertEquals(2, movements.size, "getMovements should return 2 adjustment records for prod-mv")
            assertTrue(movements.any { it.id == "adj-mv-1" && it.type == StockAdjustment.Type.INCREASE })
            assertTrue(movements.any { it.id == "adj-mv-2" && it.type == StockAdjustment.Type.DECREASE })
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── E. getMovements returns empty list when no adjustments exist ──────────

    @Test
    fun getMovements_returns_empty_list_for_product_with_no_adjustments() = runTest {
        insertProduct(id = "prod-empty-mv", stockQty = 10.0)

        repo.getMovements("prod-empty-mv").test {
            val movements = awaitItem()
            assertTrue(movements.isEmpty(), "getMovements should return empty list when no adjustments exist")
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── F. getAlerts (null threshold) uses per-product min_stock_qty ──────────

    @Test
    fun getAlerts_null_threshold_returns_products_at_or_below_min_stock_qty() = runTest {
        // low-stock: qty (3) <= min_stock_qty (5)
        insertProduct(id = "prod-low",  stockQty = 3.0,  minStockQty = 5.0)
        // normal stock: qty (50) > min_stock_qty (5)
        insertProduct(id = "prod-ok",   stockQty = 50.0, minStockQty = 5.0)
        // zero stock: qty (0) <= min_stock_qty (10)
        insertProduct(id = "prod-zero", stockQty = 0.0,  minStockQty = 10.0)

        repo.getAlerts(threshold = null).test {
            val alerts = awaitItem()
            assertEquals(2, alerts.size, "getAlerts should return 2 low-stock products")
            assertTrue(alerts.any { it.id == "prod-low"  }, "prod-low should appear in alerts")
            assertTrue(alerts.any { it.id == "prod-zero" }, "prod-zero should appear in alerts")
            assertTrue(alerts.none { it.id == "prod-ok"  }, "prod-ok should NOT appear in alerts")
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── G. getAlerts (explicit threshold) filters by threshold value ──────────
    //
    // getAlerts(threshold) first calls getLowStockProducts (SQL: stock_qty <= min_stock_qty)
    // then further filters the Kotlin-side result set with stock_qty < threshold.
    // So only products already flagged as low-stock by their own min_stock_qty can appear,
    // and the threshold provides an additional ceiling.
    //
    // prod-alert-a: stockQty=3, minStockQty=5  → low-stock (3<=5),  3 < 5 → included
    // prod-alert-b: stockQty=8, minStockQty=10 → low-stock (8<=10), 8 < 5 is false → excluded

    @Test
    fun getAlerts_with_explicit_threshold_filters_below_that_value() = runTest {
        // Below both per-product min and threshold
        insertProduct(id = "prod-alert-a", stockQty = 3.0, minStockQty = 5.0)
        // Low-stock by per-product min (8 <= 10), but NOT below the explicit threshold (8 >= 5)
        insertProduct(id = "prod-alert-b", stockQty = 8.0, minStockQty = 10.0)

        repo.getAlerts(threshold = 5.0).test {
            val alerts = awaitItem()
            assertTrue(alerts.any  { it.id == "prod-alert-a" }, "Product with qty 3 should appear below threshold 5")
            assertTrue(alerts.none { it.id == "prod-alert-b" }, "Product with qty 8 should NOT appear below threshold 5")
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── H. adjustStock DECREASE to min_stock_qty creates a low-stock alert row ─

    @Test
    fun adjustStock_DECREASE_to_min_stock_level_creates_stock_alert_row() = runTest {
        // Start above min; decrease so that qty == min_stock_qty → alert should be triggered
        insertProduct(id = "prod-trigger", stockQty = 15.0, minStockQty = 10.0)

        val adjustment = buildAdjustment(
            id        = "adj-trigger-1",
            productId = "prod-trigger",
            type      = StockAdjustment.Type.DECREASE,
            quantity  = 5.0,   // 15 - 5 = 10, which equals min_stock_qty
        )

        val result = repo.adjustStock(adjustment)
        assertIs<Result.Success<Unit>>(result, "adjustStock to exactly min_stock_qty should succeed")

        // The stock_alerts row should have been upserted; getAlerts(null) must include this product
        repo.getAlerts(threshold = null).test {
            val alerts = awaitItem()
            assertTrue(
                alerts.any { it.id == "prod-trigger" },
                "Product at min_stock_qty should appear in getAlerts after DECREASE"
            )
            cancelAndIgnoreRemainingEvents()
        }
    }
}
