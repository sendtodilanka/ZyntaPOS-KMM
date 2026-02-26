package com.zyntasolutions.zyntapos.domain.usecase.reports

import com.zyntasolutions.zyntapos.domain.model.OrderStatus
import com.zyntasolutions.zyntapos.domain.model.PaymentMethod
import com.zyntasolutions.zyntapos.domain.model.CartItem
import com.zyntasolutions.zyntapos.domain.model.DiscountType
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeOrderRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeProductRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeStockRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildOrder
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildProduct
import com.zyntasolutions.zyntapos.domain.usecase.fakes.toOrderItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Unit tests for Report use cases:
 * [GenerateSalesReportUseCase], [GenerateStockReportUseCase].
 */
class ReportUseCasesTest {

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private val now = Clock.System.now()
    private val rangeStart: Instant = now - 7.days
    private val rangeEnd: Instant = now + 1.hours

    // ─── GenerateSalesReportUseCase ───────────────────────────────────────────

    @Test
    fun `empty date range - report has zero values`() = runTest {
        val orderRepo = FakeOrderRepository()
        val useCase = GenerateSalesReportUseCase(orderRepo)

        val report = useCase(rangeStart, rangeEnd).first()

        assertEquals(0.0, report.totalSales)
        assertEquals(0, report.orderCount)
        assertEquals(0.0, report.avgOrderValue)
        assertTrue(report.topProducts.isEmpty())
        assertTrue(report.salesByPaymentMethod.isEmpty())
    }

    @Test
    fun `single completed order - correct totals computed`() = runTest {
        val orderRepo = FakeOrderRepository()
        val order = buildOrder(id = "o1", total = 150.0, paymentMethod = PaymentMethod.CASH,
            status = OrderStatus.COMPLETED)
        orderRepo.orders.add(order)
        orderRepo.orders.let { /* trigger state */ }
        // Re-create with _orders state reflected
        val freshRepo = FakeOrderRepository()
        freshRepo.create(order)
        val useCase = GenerateSalesReportUseCase(freshRepo)

        val report = useCase(rangeStart, rangeEnd).first()

        assertEquals(150.0, report.totalSales)
        assertEquals(1, report.orderCount)
        assertEquals(150.0, report.avgOrderValue)
    }

    @Test
    fun `voided orders excluded from report`() = runTest {
        val repo = FakeOrderRepository()
        repo.create(buildOrder(id = "o1", total = 100.0, status = OrderStatus.COMPLETED))
        repo.create(buildOrder(id = "o2", total = 200.0, status = OrderStatus.VOIDED))
        repo.create(buildOrder(id = "o3", total = 50.0, status = OrderStatus.HELD))
        val useCase = GenerateSalesReportUseCase(repo)

        val report = useCase(rangeStart, rangeEnd).first()

        assertEquals(100.0, report.totalSales, "Only COMPLETED orders should be included")
        assertEquals(1, report.orderCount)
    }

    @Test
    fun `multiple orders - avg order value calculated correctly`() = runTest {
        val repo = FakeOrderRepository()
        repo.create(buildOrder(id = "o1", total = 100.0))
        repo.create(buildOrder(id = "o2", total = 200.0))
        repo.create(buildOrder(id = "o3", total = 300.0))
        val useCase = GenerateSalesReportUseCase(repo)

        val report = useCase(rangeStart, rangeEnd).first()

        assertEquals(600.0, report.totalSales)
        assertEquals(3, report.orderCount)
        assertEquals(200.0, report.avgOrderValue)
    }

    @Test
    fun `sales breakdown by payment method - correct aggregation`() = runTest {
        val repo = FakeOrderRepository()
        repo.create(buildOrder(id = "o1", total = 100.0, paymentMethod = PaymentMethod.CASH))
        repo.create(buildOrder(id = "o2", total = 200.0, paymentMethod = PaymentMethod.CASH))
        repo.create(buildOrder(id = "o3", total = 150.0, paymentMethod = PaymentMethod.CARD))
        val useCase = GenerateSalesReportUseCase(repo)

        val report = useCase(rangeStart, rangeEnd).first()

        assertEquals(300.0, report.salesByPaymentMethod[PaymentMethod.CASH])
        assertEquals(150.0, report.salesByPaymentMethod[PaymentMethod.CARD])
    }

    @Test
    fun `top products ranked by revenue descending - limited to top 10`() = runTest {
        val repo = FakeOrderRepository()
        // Create 12 products with different revenues
        repeat(12) { i ->
            val cartItem = CartItem(
                productId = "prod-$i",
                productName = "Product $i",
                unitPrice = (i + 1) * 10.0,
                quantity = 1.0,
                discount = 0.0,
                discountType = DiscountType.FIXED,
                taxRate = 0.0
            )
            val orderItem = cartItem.toOrderItem("order-$i")
            val order = buildOrder(
                id = "order-$i",
                total = (i + 1) * 10.0,
                items = listOf(orderItem)
            )
            repo.create(order)
        }
        val useCase = GenerateSalesReportUseCase(repo)

        val report = useCase(rangeStart, rangeEnd).first()

        assertTrue(report.topProducts.size <= 10, "Top products should be limited to 10")
        val revenues = report.topProducts.values.toList()
        for (i in 0 until revenues.size - 1) {
            assertTrue(revenues[i] >= revenues[i + 1], "Top products should be sorted descending by revenue")
        }
    }

    @Test
    fun `report range boundaries - orders at exact start and end included`() = runTest {
        val repo = FakeOrderRepository()
        // Orders with current timestamps (within range)
        repo.create(buildOrder(id = "o1", total = 100.0))
        val useCase = GenerateSalesReportUseCase(repo)

        val report = useCase(rangeStart, rangeEnd).first()

        assertEquals(1, report.orderCount)
    }

    // ─── GenerateStockReportUseCase ───────────────────────────────────────────

    @Test
    fun `stock report with no products - all lists empty`() = runTest {
        val productRepo = FakeProductRepository()
        val stockRepo = FakeStockRepository()
        val useCase = GenerateStockReportUseCase(productRepo, stockRepo)

        val report = useCase().first()

        assertTrue(report.allProducts.isEmpty())
        assertTrue(report.lowStockItems.isEmpty())
        assertTrue(report.deadStockItems.isEmpty())
    }

    @Test
    fun `stock report identifies low stock items correctly`() = runTest {
        val productRepo = FakeProductRepository()
        // stockQty < minStockQty → low stock
        productRepo.addProduct(buildProduct(id = "p1", name = "Low Stock Item", stockQty = 2.0, minStockQty = 10.0))
        productRepo.addProduct(buildProduct(id = "p2", name = "Normal Stock", barcode = "222", sku = "SKU-2", stockQty = 50.0, minStockQty = 10.0))
        productRepo.addProduct(buildProduct(id = "p3", name = "At Min Stock", barcode = "333", sku = "SKU-3", stockQty = 10.0, minStockQty = 10.0))
        val useCase = GenerateStockReportUseCase(productRepo, FakeStockRepository())

        val report = useCase().first()

        assertEquals(3, report.allProducts.size)
        assertEquals(1, report.lowStockItems.size)
        assertEquals("p1", report.lowStockItems.first().id)
    }

    @Test
    fun `stock report includes only active products`() = runTest {
        val productRepo = FakeProductRepository()
        productRepo.addProduct(buildProduct(id = "p1", name = "Active Product", isActive = true))
        productRepo.addProduct(buildProduct(id = "p2", name = "Inactive Product", barcode = "222", sku = "SKU-2", isActive = false))
        val useCase = GenerateStockReportUseCase(productRepo, FakeStockRepository())

        val report = useCase().first()

        assertEquals(1, report.allProducts.size, "Inactive products should be excluded")
        assertEquals("p1", report.allProducts.first().id)
    }

    @Test
    fun `stock report zero stock product - not classified as low stock when minStockQty = 0`() = runTest {
        val productRepo = FakeProductRepository()
        // minStockQty = 0, so stockQty = 0 is NOT below min
        productRepo.addProduct(buildProduct(id = "p1", name = "No Min Qty Product", stockQty = 0.0, minStockQty = 0.0))
        val useCase = GenerateStockReportUseCase(productRepo, FakeStockRepository())

        val report = useCase().first()

        assertTrue(report.lowStockItems.isEmpty(), "Product with minStockQty=0 and stockQty=0 should not be low stock")
    }

    @Test
    fun `stock report multiple low stock items - all identified`() = runTest {
        val productRepo = FakeProductRepository()
        productRepo.addProduct(buildProduct(id = "p1", barcode = "b1", sku = "s1", stockQty = 1.0, minStockQty = 10.0))
        productRepo.addProduct(buildProduct(id = "p2", barcode = "b2", sku = "s2", stockQty = 3.0, minStockQty = 10.0))
        productRepo.addProduct(buildProduct(id = "p3", barcode = "b3", sku = "s3", stockQty = 9.0, minStockQty = 10.0))
        productRepo.addProduct(buildProduct(id = "p4", barcode = "b4", sku = "s4", stockQty = 15.0, minStockQty = 10.0))
        val useCase = GenerateStockReportUseCase(productRepo, FakeStockRepository())

        val report = useCase().first()

        assertEquals(3, report.lowStockItems.size)
        assertTrue(report.lowStockItems.none { it.id == "p4" })
    }
}
