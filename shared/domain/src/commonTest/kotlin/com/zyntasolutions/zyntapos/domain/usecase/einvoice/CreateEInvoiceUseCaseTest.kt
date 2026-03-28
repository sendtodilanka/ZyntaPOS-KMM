package com.zyntasolutions.zyntapos.domain.usecase.einvoice

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.EInvoice
import com.zyntasolutions.zyntapos.domain.model.EInvoiceLineItem
import com.zyntasolutions.zyntapos.domain.model.EInvoiceStatus
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeEInvoiceRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — CreateEInvoiceUseCase Unit Tests (commonTest)
 *
 * Coverage:
 *  A.  Blank invoice number returns REQUIRED error
 *  B.  Empty line items returns REQUIRED error
 *  C.  Total <= 0 returns MIN_VALUE error
 *  D.  subtotal + tax != total returns BALANCE_MISMATCH error
 *  E.  subtotal + tax within 0.01 tolerance passes
 *  F.  Valid invoice with exact totals is persisted
 *  G.  Repository failure propagates
 */
class CreateEInvoiceUseCaseTest {

    private fun makeUseCase(shouldFail: Boolean = false): Pair<CreateEInvoiceUseCase, FakeEInvoiceRepository> {
        val repo = FakeEInvoiceRepository().also { it.shouldFail = shouldFail }
        return CreateEInvoiceUseCase(repo) to repo
    }

    private fun lineItem(lineTotal: Double = 100.0) = EInvoiceLineItem(
        productId = "p1",
        description = "Test Product",
        quantity = 1.0,
        unitPrice = lineTotal,
        taxRate = 0.0,
        taxAmount = 0.0,
        lineTotal = lineTotal,
    )

    private fun invoice(
        invoiceNumber: String = "INV-001",
        lineItems: List<EInvoiceLineItem> = listOf(lineItem(100.0)),
        subtotal: Double = 100.0,
        totalTax: Double = 15.0,
        total: Double = 115.0,
    ) = EInvoice(
        id = "inv-1",
        orderId = "ord-1",
        storeId = "store-1",
        invoiceNumber = invoiceNumber,
        invoiceDate = "2026-03-28",
        customerName = "Test Customer",
        lineItems = lineItems,
        subtotal = subtotal,
        taxBreakdown = emptyList(),
        totalTax = totalTax,
        total = total,
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun `A - blank invoice number returns REQUIRED error`() = runTest {
        val (useCase, _) = makeUseCase()
        val result = useCase(invoice(invoiceNumber = "   "))
        assertIs<Result.Error>(result)
        val ex = result.exception as ValidationException
        assertEquals("REQUIRED", ex.rule)
        assertEquals("invoiceNumber", ex.field)
    }

    @Test
    fun `B - empty line items returns REQUIRED error`() = runTest {
        val (useCase, _) = makeUseCase()
        val result = useCase(invoice(lineItems = emptyList()))
        assertIs<Result.Error>(result)
        val ex = result.exception as ValidationException
        assertEquals("REQUIRED", ex.rule)
        assertEquals("lineItems", ex.field)
    }

    @Test
    fun `C - total zero returns MIN_VALUE error`() = runTest {
        val (useCase, _) = makeUseCase()
        val result = useCase(invoice(subtotal = 0.0, totalTax = 0.0, total = 0.0))
        assertIs<Result.Error>(result)
        val ex = result.exception as ValidationException
        assertEquals("MIN_VALUE", ex.rule)
        assertEquals("total", ex.field)
    }

    @Test
    fun `D - subtotal plus tax mismatches total by more than 0-01 returns BALANCE_MISMATCH`() = runTest {
        val (useCase, _) = makeUseCase()
        // subtotal=100, tax=15, but total=200 → mismatch
        val result = useCase(invoice(subtotal = 100.0, totalTax = 15.0, total = 200.0))
        assertIs<Result.Error>(result)
        val ex = result.exception as ValidationException
        assertEquals("BALANCE_MISMATCH", ex.rule)
        assertEquals("total", ex.field)
    }

    @Test
    fun `E - subtotal plus tax within 0-01 tolerance passes balance check`() = runTest {
        val (useCase, repo) = makeUseCase()
        // 100.0 + 15.0 = 115.0, total = 115.009 → diff = 0.009 < 0.01
        val result = useCase(invoice(subtotal = 100.0, totalTax = 15.0, total = 115.009))
        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, repo.invoices.size)
    }

    @Test
    fun `F - valid invoice with exact totals is persisted`() = runTest {
        val (useCase, repo) = makeUseCase()
        val result = useCase(invoice(subtotal = 100.0, totalTax = 15.0, total = 115.0))
        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, repo.invoices.size)
        assertEquals("INV-001", repo.invoices.first().invoiceNumber)
        assertEquals(EInvoiceStatus.DRAFT, repo.invoices.first().status)
    }

    @Test
    fun `G - repository failure propagates`() = runTest {
        val (useCase, _) = makeUseCase(shouldFail = true)
        val result = useCase(invoice())
        assertIs<Result.Error>(result)
    }
}
