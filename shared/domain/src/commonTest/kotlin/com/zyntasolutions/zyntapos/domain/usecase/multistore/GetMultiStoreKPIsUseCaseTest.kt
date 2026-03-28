package com.zyntasolutions.zyntapos.domain.usecase.multistore

import com.zyntasolutions.zyntapos.domain.model.report.StoreSalesData
import com.zyntasolutions.zyntapos.domain.repository.ReportRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class GetMultiStoreKPIsUseCaseTest {

    private val testData = listOf(
        StoreSalesData("s1", "Store A", 100_000.0, 40, 2_500.0),
        StoreSalesData("s2", "Store B", 75_000.0, 25, 3_000.0),
    )

    private val fakeRepository = object : ReportRepository {
        var capturedFrom: Instant? = null
        var capturedTo: Instant? = null

        override suspend fun getMultiStoreComparison(from: Instant, to: Instant): List<StoreSalesData> {
            capturedFrom = from
            capturedTo = to
            return testData
        }

        // Stub remaining methods
        override suspend fun getDailySalesSummary(date: LocalDate) = throw NotImplementedError()
        override suspend fun getSalesByCategory(from: Instant, to: Instant) = throw NotImplementedError()
        override suspend fun getCashMovementLog(from: Instant, to: Instant) = throw NotImplementedError()
        override suspend fun getTopProductsByVolume(from: Instant, to: Instant, limit: Int) = throw NotImplementedError()
        override suspend fun getProductPerformance(from: Instant, to: Instant) = throw NotImplementedError()
        override suspend fun getCouponUsage(from: Instant, to: Instant) = throw NotImplementedError()
        override suspend fun getPaymentMethodBreakdown(from: Instant, to: Instant) = throw NotImplementedError()
        override suspend fun getTaxCollection(from: Instant, to: Instant) = throw NotImplementedError()
        override suspend fun getDiscountVoidAnalysis(from: Instant, to: Instant) = throw NotImplementedError()
        override suspend fun getTopCustomers(from: Instant, to: Instant, limit: Int) = throw NotImplementedError()
        override suspend fun getProfitLoss(from: Instant, to: Instant) = throw NotImplementedError()
        override suspend fun getStaffAttendanceSummary(from: Instant, to: Instant) = throw NotImplementedError()
        override suspend fun getStaffSalesSummary(from: Instant, to: Instant) = throw NotImplementedError()
        override suspend fun getPayrollSummary(payPeriodId: String) = throw NotImplementedError()
        override suspend fun getLeaveBalances(asOf: Instant) = throw NotImplementedError()
        override suspend fun getShiftCoverage(from: Instant, to: Instant) = throw NotImplementedError()
        override suspend fun getInterStoreTransfers(from: Instant, to: Instant) = throw NotImplementedError()
        override suspend fun getWarehouseInventory() = throw NotImplementedError()
        override suspend fun getStockAging(noSalesDays: Int) = throw NotImplementedError()
        override suspend fun getStockReorderAlerts() = throw NotImplementedError()
        override suspend fun getSupplierPurchases(from: Instant, to: Instant) = throw NotImplementedError()
        override suspend fun getReturnRefundSummary(from: Instant, to: Instant) = throw NotImplementedError()
        override suspend fun getAccountingLedger(from: Instant, to: Instant) = throw NotImplementedError()
        override suspend fun getHourlySales(date: LocalDate) = throw NotImplementedError()
        override suspend fun getCustomerLoyaltySummary(from: Instant, to: Instant) = throw NotImplementedError()
        override suspend fun getWalletBalances() = throw NotImplementedError()
        override suspend fun getCOGS(from: Instant, to: Instant) = throw NotImplementedError()
        override suspend fun getGrossMargin(from: Instant, to: Instant) = throw NotImplementedError()
        override suspend fun getAnnualSalesTrend(year: Int) = throw NotImplementedError()
        override suspend fun getClockInOutLog(from: Instant, to: Instant, employeeId: String?) = throw NotImplementedError()
        override suspend fun getInventoryValuation() = throw NotImplementedError()
        override suspend fun getCustomerRetentionMetrics(from: Instant, to: Instant) = throw NotImplementedError()
        override suspend fun getPurchaseOrders(from: Instant, to: Instant) = throw NotImplementedError()
        override suspend fun getExpensesByDepartment(from: Instant, to: Instant) = throw NotImplementedError()
    }

    @Test
    fun `returns store sales data for date range`() = runTest {
        val useCase = GetMultiStoreKPIsUseCase(fakeRepository)
        val now = Clock.System.now()
        val from = now.minus(7.days)

        val result = useCase(from, now).first()

        assertEquals(2, result.size)
        assertEquals("Store A", result[0].storeName)
        assertEquals(100_000.0, result[0].totalRevenue)
        assertEquals("Store B", result[1].storeName)
    }

    @Test
    fun `passes date range to repository`() = runTest {
        val useCase = GetMultiStoreKPIsUseCase(fakeRepository)
        val from = Instant.fromEpochMilliseconds(1000)
        val to = Instant.fromEpochMilliseconds(2000)

        useCase(from, to).first()

        assertEquals(from, fakeRepository.capturedFrom)
        assertEquals(to, fakeRepository.capturedTo)
    }

    @Test
    fun `returns empty list when no data`() = runTest {
        val emptyRepo = object : ReportRepository {
            override suspend fun getMultiStoreComparison(from: Instant, to: Instant) = emptyList<StoreSalesData>()
            override suspend fun getDailySalesSummary(date: LocalDate) = throw NotImplementedError()
            override suspend fun getSalesByCategory(from: Instant, to: Instant) = throw NotImplementedError()
            override suspend fun getCashMovementLog(from: Instant, to: Instant) = throw NotImplementedError()
            override suspend fun getTopProductsByVolume(from: Instant, to: Instant, limit: Int) = throw NotImplementedError()
            override suspend fun getProductPerformance(from: Instant, to: Instant) = throw NotImplementedError()
            override suspend fun getCouponUsage(from: Instant, to: Instant) = throw NotImplementedError()
            override suspend fun getPaymentMethodBreakdown(from: Instant, to: Instant) = throw NotImplementedError()
            override suspend fun getTaxCollection(from: Instant, to: Instant) = throw NotImplementedError()
            override suspend fun getDiscountVoidAnalysis(from: Instant, to: Instant) = throw NotImplementedError()
            override suspend fun getTopCustomers(from: Instant, to: Instant, limit: Int) = throw NotImplementedError()
            override suspend fun getProfitLoss(from: Instant, to: Instant) = throw NotImplementedError()
            override suspend fun getStaffAttendanceSummary(from: Instant, to: Instant) = throw NotImplementedError()
            override suspend fun getStaffSalesSummary(from: Instant, to: Instant) = throw NotImplementedError()
            override suspend fun getPayrollSummary(payPeriodId: String) = throw NotImplementedError()
            override suspend fun getLeaveBalances(asOf: Instant) = throw NotImplementedError()
            override suspend fun getShiftCoverage(from: Instant, to: Instant) = throw NotImplementedError()
            override suspend fun getInterStoreTransfers(from: Instant, to: Instant) = throw NotImplementedError()
            override suspend fun getWarehouseInventory() = throw NotImplementedError()
            override suspend fun getStockAging(noSalesDays: Int) = throw NotImplementedError()
            override suspend fun getStockReorderAlerts() = throw NotImplementedError()
            override suspend fun getSupplierPurchases(from: Instant, to: Instant) = throw NotImplementedError()
            override suspend fun getReturnRefundSummary(from: Instant, to: Instant) = throw NotImplementedError()
            override suspend fun getAccountingLedger(from: Instant, to: Instant) = throw NotImplementedError()
            override suspend fun getHourlySales(date: LocalDate) = throw NotImplementedError()
            override suspend fun getCustomerLoyaltySummary(from: Instant, to: Instant) = throw NotImplementedError()
            override suspend fun getWalletBalances() = throw NotImplementedError()
            override suspend fun getCOGS(from: Instant, to: Instant) = throw NotImplementedError()
            override suspend fun getGrossMargin(from: Instant, to: Instant) = throw NotImplementedError()
            override suspend fun getAnnualSalesTrend(year: Int) = throw NotImplementedError()
            override suspend fun getClockInOutLog(from: Instant, to: Instant, employeeId: String?) = throw NotImplementedError()
            override suspend fun getInventoryValuation() = throw NotImplementedError()
            override suspend fun getCustomerRetentionMetrics(from: Instant, to: Instant) = throw NotImplementedError()
            override suspend fun getPurchaseOrders(from: Instant, to: Instant) = throw NotImplementedError()
            override suspend fun getExpensesByDepartment(from: Instant, to: Instant) = throw NotImplementedError()
        }
        val useCase = GetMultiStoreKPIsUseCase(emptyRepo)
        val result = useCase(Instant.fromEpochMilliseconds(0), Clock.System.now()).first()
        assertTrue(result.isEmpty())
    }
}
