package com.zyntasolutions.zyntapos.domain.usecase.reports

import com.zyntasolutions.zyntapos.core.result.AuthException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.PaymentMethod
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.printer.ReportPrinterPort
import com.zyntasolutions.zyntapos.domain.usecase.auth.CheckPermissionUseCase
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeA4InvoicePrinterPort
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeAuthRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildUser
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// ─────────────────────────────────────────────────────────────────────────────
// Shared fixture
// ─────────────────────────────────────────────────────────────────────────────

private fun buildSalesReport() = GenerateSalesReportUseCase.SalesReport(
    from = Instant.fromEpochSeconds(0),
    to = Instant.fromEpochSeconds(86_400),
    totalSales = 1_000.0,
    orderCount = 10,
    avgOrderValue = 100.0,
    topProducts = mapOf("prod-01" to 500.0),
    salesByPaymentMethod = mapOf(PaymentMethod.CASH to 1_000.0),
)

// ─────────────────────────────────────────────────────────────────────────────
// Inline fake for ReportPrinterPort (returns stdlib kotlin.Result<Unit>)
// ─────────────────────────────────────────────────────────────────────────────

private class FakeReportPrinterPort : ReportPrinterPort {
    val printedReports = mutableListOf<GenerateSalesReportUseCase.SalesReport>()
    var shouldFail = false

    override suspend fun printSalesSummary(
        report: GenerateSalesReportUseCase.SalesReport,
    ): kotlin.Result<Unit> {
        if (shouldFail) return kotlin.Result.failure(Exception("Thermal printer error"))
        printedReports.add(report)
        return kotlin.Result.success(Unit)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PrintReportUseCaseTest
// ─────────────────────────────────────────────────────────────────────────────

class PrintReportUseCaseTest {

    @Test
    fun `printSalesSummary_delegatesToPort_onSuccess`() = runTest {
        val port = FakeReportPrinterPort()
        val report = buildSalesReport()

        val result = PrintReportUseCase(port).printSalesSummary(report)

        assertTrue(result.isSuccess)
        assertEquals(1, port.printedReports.size)
        assertEquals(1_000.0, port.printedReports.first().totalSales)
    }

    @Test
    fun `printSalesSummary_printerFailure_returnsFailure`() = runTest {
        val port = FakeReportPrinterPort().apply { shouldFail = true }

        val result = PrintReportUseCase(port).printSalesSummary(buildSalesReport())

        assertTrue(result.isFailure)
        assertTrue(port.printedReports.isEmpty())
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PrintA4SalesReportUseCaseTest
// ─────────────────────────────────────────────────────────────────────────────

class PrintA4SalesReportUseCaseTest {

    private fun makeCheckPermission(role: Role, userId: String = "user-01"): CheckPermissionUseCase {
        val authRepo = FakeAuthRepository()
        val useCase = CheckPermissionUseCase(authRepo.getSession())
        useCase.updateSession(buildUser(id = userId, role = role))
        return useCase
    }

    @Test
    fun `execute_adminUser_printsSuccessfully`() = runTest {
        val port = FakeA4InvoicePrinterPort()
        val checkPermission = makeCheckPermission(Role.ADMIN)
        val report = buildSalesReport()

        val result = PrintA4SalesReportUseCase(port, checkPermission).execute(report, "user-01")

        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, port.salesReportJobs.size)
    }

    @Test
    fun `execute_storeManagerUser_printsSuccessfully`() = runTest {
        val port = FakeA4InvoicePrinterPort()
        val checkPermission = makeCheckPermission(Role.STORE_MANAGER)

        val result = PrintA4SalesReportUseCase(port, checkPermission).execute(buildSalesReport(), "user-01")

        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, port.salesReportJobs.size)
    }

    @Test
    fun `execute_accountantUser_printsSuccessfully`() = runTest {
        val port = FakeA4InvoicePrinterPort()
        val checkPermission = makeCheckPermission(Role.ACCOUNTANT)

        val result = PrintA4SalesReportUseCase(port, checkPermission).execute(buildSalesReport(), "user-01")

        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, port.salesReportJobs.size)
    }

    @Test
    fun `execute_cashierUser_returnsAuthError`() = runTest {
        val port = FakeA4InvoicePrinterPort()
        val checkPermission = makeCheckPermission(Role.CASHIER)

        val result = PrintA4SalesReportUseCase(port, checkPermission).execute(buildSalesReport(), "user-01")

        assertIs<Result.Error>(result)
        assertIs<AuthException>((result as Result.Error).exception)
        assertTrue(port.salesReportJobs.isEmpty())
    }

    @Test
    fun `execute_stockManagerUser_returnsAuthError`() = runTest {
        val port = FakeA4InvoicePrinterPort()
        val checkPermission = makeCheckPermission(Role.STOCK_MANAGER)

        val result = PrintA4SalesReportUseCase(port, checkPermission).execute(buildSalesReport(), "user-01")

        assertIs<Result.Error>(result)
        assertTrue(port.salesReportJobs.isEmpty())
    }

    @Test
    fun `execute_printerFailure_returnsError`() = runTest {
        val port = FakeA4InvoicePrinterPort().apply { shouldFail = true }
        val checkPermission = makeCheckPermission(Role.ADMIN)

        val result = PrintA4SalesReportUseCase(port, checkPermission).execute(buildSalesReport(), "user-01")

        assertIs<Result.Error>(result)
        assertTrue(port.salesReportJobs.isEmpty())
    }
}
