package com.zyntasolutions.zyntapos.domain.usecase.reports

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.domain.model.report.CashMovementRecord
import com.zyntasolutions.zyntapos.domain.model.report.CategorySalesData
import com.zyntasolutions.zyntapos.domain.model.report.CustomerSpendData
import com.zyntasolutions.zyntapos.domain.model.report.DiscountVoidData
import com.zyntasolutions.zyntapos.domain.model.report.ProfitLossData
import com.zyntasolutions.zyntapos.domain.model.report.ProductPerformanceData
import com.zyntasolutions.zyntapos.domain.model.report.ProductVolumeData
import com.zyntasolutions.zyntapos.domain.model.report.TaxCollectionData
import com.zyntasolutions.zyntapos.domain.model.report.CouponCodeData
import com.zyntasolutions.zyntapos.domain.model.report.CouponUsageData
import com.zyntasolutions.zyntapos.domain.model.report.DailySalesSummaryData
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeReportRepository
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for the thin-wrapper standard report use cases:
 * [GenerateCategorySalesReportUseCase], [GenerateCashMovementReportUseCase],
 * [GenerateCouponUsageReportUseCase], [GenerateDailySalesSummaryUseCase],
 * [GenerateDiscountVoidAnalysisReportUseCase], [GeneratePaymentMethodReportUseCase],
 * [GenerateProductPerformanceReportUseCase], [GenerateProfitLossReportUseCase],
 * [GenerateTaxCollectionReportUseCase], [GenerateTopCustomersReportUseCase],
 * [GenerateTopProductsReportUseCase].
 */
class StandardReportUseCasesTest {

    // ── Shared fixtures ─────────────────────────────────────────────────────────

    private val from: Instant = Instant.fromEpochMilliseconds(1_700_000_000_000L)
    private val to: Instant   = Instant.fromEpochMilliseconds(1_700_086_400_000L) // +1 day

    private fun buildCategoryData(categoryId: String = "cat-01", revenue: Double = 1000.0) =
        CategorySalesData(
            categoryId = categoryId,
            categoryName = "Test Category",
            revenue = revenue,
            orderCount = 5,
            itemCount = 10,
            revenueSharePct = 50.0,
        )

    private fun buildCashMovement(id: String = "mov-01", amount: Double = 200.0) =
        CashMovementRecord(
            id = id,
            type = "CASH_IN",
            amount = amount,
            reason = "Daily float",
            recordedBy = "cashier-01",
            recordedAt = from,
        )

    private fun buildProductVolume(rank: Int = 1, productId: String = "prod-01") =
        ProductVolumeData(
            rank = rank,
            productId = productId,
            productName = "Widget",
            unitsSold = 100,
            revenue = 5000.0,
        )

    private fun buildProductPerformance(productId: String = "prod-01") =
        ProductPerformanceData(
            productId = productId,
            productName = "Widget",
            unitsSold = 100,
            revenue = 5000.0,
            cogs = 3000.0,
            grossMargin = 2000.0,
            grossMarginPct = 40.0,
            returnCount = 2,
        )

    private fun buildCustomerSpend(customerId: String = "cust-01", spend: Double = 1500.0) =
        CustomerSpendData(
            customerId = customerId,
            customerName = "Alice",
            totalSpend = spend,
            orderCount = 8,
            averageOrderValue = 187.5,
        )

    private fun buildTaxCollection(taxGroupId: String = "tg-01") =
        TaxCollectionData(
            taxGroupId = taxGroupId,
            taxGroupName = "VAT 15%",
            taxRate = 0.15,
            taxableAmount = 10000.0,
            taxCollected = 1500.0,
        )

    // ── GenerateCategorySalesReportUseCase ──────────────────────────────────────

    @Test
    fun `categorySales_emits_empty_list_when_repository_returns_empty`() = runTest {
        GenerateCategorySalesReportUseCase(FakeReportRepository())(from, to).test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `categorySales_emits_repository_data`() = runTest {
        val repo = FakeReportRepository().apply {
            stubCategoryBreakdown = listOf(buildCategoryData("cat-01"), buildCategoryData("cat-02"))
        }
        GenerateCategorySalesReportUseCase(repo)(from, to).test {
            val result = awaitItem()
            assertEquals(2, result.size)
            assertEquals("cat-01", result[0].categoryId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `categorySales_revenue_sum_is_preserved`() = runTest {
        val repo = FakeReportRepository().apply {
            stubCategoryBreakdown = listOf(buildCategoryData(revenue = 999.99))
        }
        GenerateCategorySalesReportUseCase(repo)(from, to).test {
            assertEquals(999.99, awaitItem().first().revenue)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── GenerateCashMovementReportUseCase ───────────────────────────────────────

    @Test
    fun `cashMovement_emits_empty_list_when_repository_returns_empty`() = runTest {
        GenerateCashMovementReportUseCase(FakeReportRepository())(from, to).test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cashMovement_emits_repository_records`() = runTest {
        val repo = FakeReportRepository().apply {
            stubCashMovements = listOf(buildCashMovement("m1"), buildCashMovement("m2"))
        }
        GenerateCashMovementReportUseCase(repo)(from, to).test {
            val result = awaitItem()
            assertEquals(2, result.size)
            assertEquals("m1", result[0].id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── GenerateCouponUsageReportUseCase ────────────────────────────────────────

    @Test
    fun `couponUsage_emits_zero_totals_for_empty_repository`() = runTest {
        GenerateCouponUsageReportUseCase(FakeReportRepository())(from, to).test {
            val result = awaitItem()
            assertEquals(0, result.totalRedemptions)
            assertEquals(0.0, result.totalDiscountGiven)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `couponUsage_emits_repository_data`() = runTest {
        val repo = FakeReportRepository().apply {
            stubCouponUsage = CouponUsageData(
                totalRedemptions = 42,
                totalDiscountGiven = 1250.0,
                byCode = listOf(CouponCodeData("SUMMER20", 20, 500.0)),
            )
        }
        GenerateCouponUsageReportUseCase(repo)(from, to).test {
            val result = awaitItem()
            assertEquals(42, result.totalRedemptions)
            assertEquals(1250.0, result.totalDiscountGiven)
            assertEquals(1, result.byCode.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── GenerateDailySalesSummaryUseCase ────────────────────────────────────────

    @Test
    fun `dailySummary_emits_zero_data_for_default_stub`() = runTest {
        GenerateDailySalesSummaryUseCase(FakeReportRepository())(LocalDate(2026, 3, 1)).test {
            val result = awaitItem()
            assertEquals(0.0, result.totalRevenue)
            assertEquals(0, result.totalOrders)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dailySummary_emits_repository_data`() = runTest {
        val date = LocalDate(2026, 3, 15)
        val repo = FakeReportRepository().apply {
            stubDailySummary = DailySalesSummaryData(
                date = date,
                totalRevenue = 8500.0,
                totalOrders = 47,
                averageOrderValue = 180.85,
                totalItemsSold = 120,
                openingCash = 500.0,
                closingCash = 1200.0,
                cashRevenue = 5000.0,
                cardRevenue = 3500.0,
            )
        }
        GenerateDailySalesSummaryUseCase(repo)(date).test {
            val result = awaitItem()
            assertEquals(8500.0, result.totalRevenue)
            assertEquals(47, result.totalOrders)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── GenerateDiscountVoidAnalysisReportUseCase ───────────────────────────────

    @Test
    fun `discountVoidAnalysis_emits_zero_data_for_default_stub`() = runTest {
        GenerateDiscountVoidAnalysisReportUseCase(FakeReportRepository())(from, to).test {
            val result = awaitItem()
            assertEquals(0.0, result.totalDiscountAmount)
            assertEquals(0, result.totalVoidCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `discountVoidAnalysis_emits_repository_data`() = runTest {
        val repo = FakeReportRepository().apply {
            stubDiscountVoid = DiscountVoidData(
                totalDiscountAmount = 450.0,
                totalVoidCount = 3,
                totalVoidAmount = 210.0,
                byCashier = emptyList(),
            )
        }
        GenerateDiscountVoidAnalysisReportUseCase(repo)(from, to).test {
            val result = awaitItem()
            assertEquals(450.0, result.totalDiscountAmount)
            assertEquals(3, result.totalVoidCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── GeneratePaymentMethodReportUseCase ──────────────────────────────────────

    @Test
    fun `paymentMethod_emits_empty_map_for_default_stub`() = runTest {
        GeneratePaymentMethodReportUseCase(FakeReportRepository())(from, to).test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `paymentMethod_emits_repository_data`() = runTest {
        val repo = FakeReportRepository().apply {
            stubPaymentMethods = mapOf("CASH" to 5000.0, "CARD" to 3200.0)
        }
        GeneratePaymentMethodReportUseCase(repo)(from, to).test {
            val result = awaitItem()
            assertEquals(2, result.size)
            assertEquals(5000.0, result["CASH"])
            assertEquals(3200.0, result["CARD"])
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── GenerateProductPerformanceReportUseCase ─────────────────────────────────

    @Test
    fun `productPerformance_emits_empty_list_for_default_stub`() = runTest {
        GenerateProductPerformanceReportUseCase(FakeReportRepository())(from, to).test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `productPerformance_emits_repository_data`() = runTest {
        val repo = FakeReportRepository().apply {
            stubProductPerformance = listOf(buildProductPerformance("prod-A"), buildProductPerformance("prod-B"))
        }
        GenerateProductPerformanceReportUseCase(repo)(from, to).test {
            val result = awaitItem()
            assertEquals(2, result.size)
            assertEquals("prod-A", result[0].productId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── GenerateProfitLossReportUseCase ─────────────────────────────────────────

    @Test
    fun `profitLoss_emits_zero_data_for_default_stub`() = runTest {
        GenerateProfitLossReportUseCase(FakeReportRepository())(from, to).test {
            val result = awaitItem()
            assertEquals(0.0, result.grossProfit)
            assertEquals(0.0, result.netProfit)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `profitLoss_emits_repository_data`() = runTest {
        val repo = FakeReportRepository().apply {
            stubProfitLoss = ProfitLossData(
                from = from,
                to = to,
                totalRevenue = 50_000.0,
                totalCOGS = 30_000.0,
                grossProfit = 20_000.0,
                totalExpenses = 8_000.0,
                netProfit = 12_000.0,
                grossMarginPct = 40.0,
            )
        }
        GenerateProfitLossReportUseCase(repo)(from, to).test {
            val result = awaitItem()
            assertEquals(20_000.0, result.grossProfit)
            assertEquals(12_000.0, result.netProfit)
            assertEquals(40.0, result.grossMarginPct)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── GenerateTaxCollectionReportUseCase ──────────────────────────────────────

    @Test
    fun `taxCollection_emits_empty_list_for_default_stub`() = runTest {
        GenerateTaxCollectionReportUseCase(FakeReportRepository())(from, to).test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `taxCollection_emits_repository_data`() = runTest {
        val repo = FakeReportRepository().apply {
            stubTaxCollection = listOf(buildTaxCollection("tg-01"), buildTaxCollection("tg-02"))
        }
        GenerateTaxCollectionReportUseCase(repo)(from, to).test {
            val result = awaitItem()
            assertEquals(2, result.size)
            assertEquals("tg-01", result[0].taxGroupId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── GenerateTopProductsReportUseCase ────────────────────────────────────────

    @Test
    fun `topProducts_emits_empty_list_for_default_stub`() = runTest {
        GenerateTopProductsReportUseCase(FakeReportRepository())(from, to).test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `topProducts_emits_repository_data`() = runTest {
        val repo = FakeReportRepository().apply {
            stubTopProducts = listOf(buildProductVolume(1, "prod-A"), buildProductVolume(2, "prod-B"))
        }
        GenerateTopProductsReportUseCase(repo)(from, to).test {
            val result = awaitItem()
            assertEquals(2, result.size)
            assertEquals(1, result[0].rank)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `topProducts_forwards_limit_to_repository`() = runTest {
        val repo = FakeReportRepository()
        GenerateTopProductsReportUseCase(repo)(from, to, limit = 5).test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(5, repo.lastTopProductsLimit)
    }

    @Test
    fun `topProducts_uses_default_limit_of_20`() = runTest {
        val repo = FakeReportRepository()
        GenerateTopProductsReportUseCase(repo)(from, to).test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(20, repo.lastTopProductsLimit)
    }

    // ── GenerateTopCustomersReportUseCase ───────────────────────────────────────

    @Test
    fun `topCustomers_emits_empty_list_for_default_stub`() = runTest {
        GenerateTopCustomersReportUseCase(FakeReportRepository())(from, to).test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `topCustomers_emits_repository_data`() = runTest {
        val repo = FakeReportRepository().apply {
            stubTopCustomers = listOf(buildCustomerSpend("c1", 2000.0), buildCustomerSpend("c2", 1500.0))
        }
        GenerateTopCustomersReportUseCase(repo)(from, to).test {
            val result = awaitItem()
            assertEquals(2, result.size)
            assertEquals("c1", result[0].customerId)
            assertEquals(2000.0, result[0].totalSpend)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `topCustomers_forwards_limit_to_repository`() = runTest {
        val repo = FakeReportRepository()
        GenerateTopCustomersReportUseCase(repo)(from, to, limit = 10).test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(10, repo.lastTopCustomersLimit)
    }

    @Test
    fun `topCustomers_uses_default_limit_of_20`() = runTest {
        val repo = FakeReportRepository()
        GenerateTopCustomersReportUseCase(repo)(from, to).test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(20, repo.lastTopCustomersLimit)
    }
}
