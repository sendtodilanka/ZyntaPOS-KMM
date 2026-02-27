package com.zyntasolutions.zyntapos.domain.usecase.pos

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.OrderStatus
import com.zyntasolutions.zyntapos.domain.model.Permission
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.usecase.auth.CheckPermissionUseCase
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeA4InvoicePrinterPort
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeAuthRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeEmailPort
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeOrderRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeReceiptPrinterPort
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeSettingsRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildOrder
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildUser
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for POS hardware-related use cases:
 * [ReprintLastReceiptUseCase], [SendReceiptByEmailUseCase], [PrintA4TaxInvoiceUseCase].
 */
class HardwarePosUseCasesTest {

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun makeCheckPermission(role: Role = Role.CASHIER): CheckPermissionUseCase {
        val authRepo = FakeAuthRepository()
        val useCase = CheckPermissionUseCase(authRepo.getSession())
        useCase.updateSession(buildUser(id = "user-01", role = role))
        return useCase
    }

    // ─── ReprintLastReceiptUseCase ─────────────────────────────────────────────

    @Test
    fun `reprint - existing order - delegates to printer port`() = runTest {
        val orderRepo = FakeOrderRepository()
        val printerPort = FakeReceiptPrinterPort()
        val order = buildOrder(id = "order-01")
        orderRepo.create(order)
        val useCase = ReprintLastReceiptUseCase(orderRepo, printerPort)

        val result = useCase.execute("order-01", "user-01")

        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, printerPort.printedOrders.size)
        assertEquals("order-01", printerPort.printedOrders.first().first.id)
        assertEquals("user-01", printerPort.printedOrders.first().second)
    }

    @Test
    fun `reprint - order not found - returns error`() = runTest {
        val orderRepo = FakeOrderRepository()
        val printerPort = FakeReceiptPrinterPort()
        val useCase = ReprintLastReceiptUseCase(orderRepo, printerPort)

        val result = useCase.execute("non-existent", "user-01")

        assertIs<Result.Error>(result)
        assertTrue(printerPort.printedOrders.isEmpty())
    }

    @Test
    fun `reprint - printer fails - returns error`() = runTest {
        val orderRepo = FakeOrderRepository()
        val printerPort = FakeReceiptPrinterPort().apply { shouldFail = true }
        val order = buildOrder(id = "order-01")
        orderRepo.create(order)
        val useCase = ReprintLastReceiptUseCase(orderRepo, printerPort)

        val result = useCase.execute("order-01", "user-01")

        assertIs<Result.Error>(result)
    }

    // ─── SendReceiptByEmailUseCase ─────────────────────────────────────────────

    @Test
    fun `email receipt - valid order - sends email with order number in subject`() = runTest {
        val orderRepo = FakeOrderRepository()
        val settingsRepo = FakeSettingsRepository().apply { put("store_name", "Test Shop") }
        val emailPort = FakeEmailPort()
        val order = buildOrder(id = "order-01")
        orderRepo.create(order)
        val useCase = SendReceiptByEmailUseCase(orderRepo, settingsRepo, emailPort)

        val result = useCase.execute("order-01", "customer@example.com")

        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, emailPort.emailsSent.size)
        assertEquals("customer@example.com", emailPort.emailsSent.first().to)
        assertTrue(emailPort.emailsSent.first().subject.contains("Test Shop"))
    }

    @Test
    fun `email receipt - order not found - returns error without sending email`() = runTest {
        val orderRepo = FakeOrderRepository()
        val settingsRepo = FakeSettingsRepository()
        val emailPort = FakeEmailPort()
        val useCase = SendReceiptByEmailUseCase(orderRepo, settingsRepo, emailPort)

        val result = useCase.execute("non-existent", "customer@example.com")

        assertIs<Result.Error>(result)
        assertTrue(emailPort.emailsSent.isEmpty())
    }

    @Test
    fun `email receipt - store name not set - uses default store name`() = runTest {
        val orderRepo = FakeOrderRepository()
        val settingsRepo = FakeSettingsRepository() // no store_name key
        val emailPort = FakeEmailPort()
        val order = buildOrder(id = "order-01")
        orderRepo.create(order)
        val useCase = SendReceiptByEmailUseCase(orderRepo, settingsRepo, emailPort)

        val result = useCase.execute("order-01", "customer@example.com")

        assertIs<Result.Success<Unit>>(result)
        assertTrue(emailPort.emailsSent.first().subject.contains("ZyntaPOS Store"))
    }

    // ─── PrintA4TaxInvoiceUseCase ──────────────────────────────────────────────

    @Test
    fun `print A4 invoice - user has PRINT_INVOICE permission - delegates to printer`() = runTest {
        val orderRepo = FakeOrderRepository()
        val printerPort = FakeA4InvoicePrinterPort()
        val checkPermission = makeCheckPermission(Role.CASHIER)
        val order = buildOrder(id = "order-01")
        orderRepo.create(order)
        val useCase = PrintA4TaxInvoiceUseCase(orderRepo, printerPort, checkPermission)

        val result = useCase.execute("order-01", "user-01")

        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, printerPort.invoiceJobs.size)
    }

    @Test
    fun `print A4 invoice - user lacks PRINT_INVOICE permission - returns auth error`() = runTest {
        val orderRepo = FakeOrderRepository()
        val printerPort = FakeA4InvoicePrinterPort()
        // STOCK_MANAGER role does NOT have PRINT_INVOICE
        val checkPermission = makeCheckPermission(Role.STOCK_MANAGER)
        val order = buildOrder(id = "order-01")
        orderRepo.create(order)
        val useCase = PrintA4TaxInvoiceUseCase(orderRepo, printerPort, checkPermission)

        val result = useCase.execute("order-01", "user-01")

        assertIs<Result.Error>(result)
        assertTrue(printerPort.invoiceJobs.isEmpty())
    }

    @Test
    fun `print A4 invoice - order not found - returns error`() = runTest {
        val orderRepo = FakeOrderRepository()
        val printerPort = FakeA4InvoicePrinterPort()
        val checkPermission = makeCheckPermission(Role.STORE_MANAGER)
        val useCase = PrintA4TaxInvoiceUseCase(orderRepo, printerPort, checkPermission)

        val result = useCase.execute("non-existent", "user-01")

        assertIs<Result.Error>(result)
        assertTrue(printerPort.invoiceJobs.isEmpty())
    }
}
