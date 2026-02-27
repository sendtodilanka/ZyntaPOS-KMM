package com.zyntasolutions.zyntapos.domain.usecase.einvoice

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.EInvoiceStatus
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeEInvoiceRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildEInvoice
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildEInvoiceLineItem
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for e-invoice use cases:
 * [CreateEInvoiceUseCase], [GetEInvoicesUseCase], [CancelEInvoiceUseCase].
 *
 * Covers:
 * - CreateEInvoiceUseCase: blank invoiceNumber → ValidationException (field=invoiceNumber, rule=REQUIRED)
 * - CreateEInvoiceUseCase: empty lineItems → ValidationException (field=lineItems, rule=REQUIRED)
 * - CreateEInvoiceUseCase: non-positive total → ValidationException (field=total, rule=MIN_VALUE)
 * - CreateEInvoiceUseCase: total mismatch (subtotal + tax ≠ total) → ValidationException (rule=BALANCE_MISMATCH)
 * - CreateEInvoiceUseCase: valid invoice → delegates to repository.insert, returns Success
 * - CreateEInvoiceUseCase: repository failure → propagates Result.Error
 * - GetEInvoicesUseCase: returns Flow from repository filtered by storeId
 * - GetEInvoicesUseCase: new inserts re-emit through the Flow
 * - CancelEInvoiceUseCase: delegates cancel call to repository with correct id and updatedAt
 * - CancelEInvoiceUseCase: repository failure → propagates Result.Error
 */
class EInvoiceUseCasesTest {

    // ─── CreateEInvoiceUseCase ────────────────────────────────────────────────

    private fun makeCreateUseCase(repo: FakeEInvoiceRepository = FakeEInvoiceRepository()) =
        CreateEInvoiceUseCase(repo) to repo

    @Test
    fun `blank invoiceNumber returns REQUIRED ValidationException without DB write`() = runTest {
        val (useCase, repo) = makeCreateUseCase()
        val invoice = buildEInvoice(invoiceNumber = "   ")

        val result = useCase(invoice)

        assertIs<Result.Error>(result)
        val ex = result.exception
        assertIs<ValidationException>(ex)
        assertEquals("invoiceNumber", ex.field)
        assertEquals("REQUIRED", ex.rule)
        assertTrue(repo.invoices.isEmpty(), "No write should occur for blank invoiceNumber")
    }

    @Test
    fun `empty lineItems returns REQUIRED ValidationException without DB write`() = runTest {
        val (useCase, repo) = makeCreateUseCase()
        val invoice = buildEInvoice(lineItems = emptyList())

        val result = useCase(invoice)

        assertIs<Result.Error>(result)
        val ex = result.exception
        assertIs<ValidationException>(ex)
        assertEquals("lineItems", ex.field)
        assertEquals("REQUIRED", ex.rule)
        assertTrue(repo.invoices.isEmpty())
    }

    @Test
    fun `non-positive total returns MIN_VALUE ValidationException without DB write`() = runTest {
        val (useCase, repo) = makeCreateUseCase()
        val invoice = buildEInvoice(
            lineItems = listOf(buildEInvoiceLineItem(lineTotal = 100.0)),
            subtotal = 0.0,
            totalTax = 0.0,
            total = 0.0,
        )

        val result = useCase(invoice)

        assertIs<Result.Error>(result)
        val ex = result.exception
        assertIs<ValidationException>(ex)
        assertEquals("total", ex.field)
        assertEquals("MIN_VALUE", ex.rule)
        assertTrue(repo.invoices.isEmpty())
    }

    @Test
    fun `negative total returns MIN_VALUE ValidationException`() = runTest {
        val (useCase, repo) = makeCreateUseCase()
        val invoice = buildEInvoice(
            lineItems = listOf(buildEInvoiceLineItem(lineTotal = 100.0)),
            subtotal = -10.0,
            totalTax = 0.0,
            total = -10.0,
        )

        val result = useCase(invoice)

        assertIs<Result.Error>(result)
        val ex = result.exception
        assertIs<ValidationException>(ex)
        assertEquals("MIN_VALUE", ex.rule)
    }

    @Test
    fun `total mismatch subtotal plus tax not equal total returns BALANCE_MISMATCH`() = runTest {
        val (useCase, repo) = makeCreateUseCase()
        // subtotal=80, tax=10 → computed=90, but total=100 (mismatch > 0.01)
        val invoice = buildEInvoice(
            lineItems = listOf(buildEInvoiceLineItem(lineTotal = 100.0)),
            subtotal = 80.0,
            totalTax = 10.0,
            total = 100.0,
        )

        val result = useCase(invoice)

        assertIs<Result.Error>(result)
        val ex = result.exception
        assertIs<ValidationException>(ex)
        assertEquals("total", ex.field)
        assertEquals("BALANCE_MISMATCH", ex.rule)
        assertTrue(repo.invoices.isEmpty())
    }

    @Test
    fun `valid invoice delegates to repository insert and returns Success`() = runTest {
        val (useCase, repo) = makeCreateUseCase()
        val invoice = buildEInvoice(
            invoiceNumber = "INV-0001",
            lineItems = listOf(buildEInvoiceLineItem(lineTotal = 100.0)),
            subtotal = 100.0,
            totalTax = 0.0,
            total = 100.0,
        )

        val result = useCase(invoice)

        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, repo.invoices.size)
        assertEquals("INV-0001", repo.invoices.first().invoiceNumber)
    }

    @Test
    fun `total within tolerance of 0_01 is accepted`() = runTest {
        val (useCase, repo) = makeCreateUseCase()
        // subtotal=99.999, tax=0.0 → computed=99.999, total=100.0 (diff < 0.01)
        val invoice = buildEInvoice(
            lineItems = listOf(buildEInvoiceLineItem(lineTotal = 100.0)),
            subtotal = 99.999,
            totalTax = 0.0,
            total = 100.0,
        )

        val result = useCase(invoice)

        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, repo.invoices.size)
    }

    @Test
    fun `repository failure propagates as Result Error`() = runTest {
        val repo = FakeEInvoiceRepository().also { it.shouldFail = true }
        val useCase = CreateEInvoiceUseCase(repo)
        val invoice = buildEInvoice(
            lineItems = listOf(buildEInvoiceLineItem(lineTotal = 100.0)),
            subtotal = 100.0,
            totalTax = 0.0,
            total = 100.0,
        )

        val result = useCase(invoice)

        assertIs<Result.Error>(result)
    }

    // ─── GetEInvoicesUseCase ──────────────────────────────────────────────────

    @Test
    fun `returns Flow from repository for given storeId`() = runTest {
        val repo = FakeEInvoiceRepository()
        val invoice = buildEInvoice(storeId = "store-01")
        repo.insert(invoice)
        val useCase = GetEInvoicesUseCase(repo)

        useCase("store-01").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("store-01", list.first().storeId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `only returns invoices for the requested storeId`() = runTest {
        val repo = FakeEInvoiceRepository()
        repo.insert(buildEInvoice(id = "inv-s1", storeId = "store-01"))
        repo.insert(buildEInvoice(id = "inv-s2", storeId = "store-02"))
        val useCase = GetEInvoicesUseCase(repo)

        useCase("store-01").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("inv-s1", list.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `new insert re-emits through the Flow`() = runTest {
        val repo = FakeEInvoiceRepository()
        val useCase = GetEInvoicesUseCase(repo)

        useCase("store-01").test {
            val empty = awaitItem()
            assertTrue(empty.isEmpty())

            val invoice = buildEInvoice(id = "inv-new", storeId = "store-01")
            repo.insert(invoice)

            val updated = awaitItem()
            assertEquals(1, updated.size)
            assertEquals("inv-new", updated.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `empty store returns empty list`() = runTest {
        val repo = FakeEInvoiceRepository()
        val useCase = GetEInvoicesUseCase(repo)

        useCase("store-99").test {
            val list = awaitItem()
            assertTrue(list.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── CancelEInvoiceUseCase ────────────────────────────────────────────────

    @Test
    fun `delegates cancel to repository with correct id and updatedAt`() = runTest {
        val repo = FakeEInvoiceRepository()
        repo.insert(buildEInvoice(id = "inv-01", status = EInvoiceStatus.DRAFT))
        val useCase = CancelEInvoiceUseCase(repo)
        val cancelledAt = 1_700_000_000_000L

        val result = useCase("inv-01", cancelledAt)

        assertIs<Result.Success<Unit>>(result)
        assertEquals("inv-01", repo.lastCancelledId)
        assertEquals(cancelledAt, repo.lastCancelledAt)
        assertEquals(EInvoiceStatus.CANCELLED, repo.invoices.first().status)
    }

    @Test
    fun `cancel of non-existent id propagates Result Error`() = runTest {
        val repo = FakeEInvoiceRepository()
        val useCase = CancelEInvoiceUseCase(repo)

        val result = useCase("non-existent", 1_700_000_000_000L)

        assertIs<Result.Error>(result)
    }

    @Test
    fun `repository failure on cancel propagates as Result Error`() = runTest {
        val repo = FakeEInvoiceRepository().also { it.shouldFail = true }
        val useCase = CancelEInvoiceUseCase(repo)

        val result = useCase("inv-01", 1_700_000_000_000L)

        assertIs<Result.Error>(result)
    }
}
