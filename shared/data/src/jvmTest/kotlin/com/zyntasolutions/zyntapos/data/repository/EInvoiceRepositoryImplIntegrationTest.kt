package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.data.remote.ird.IrdApiClient
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.EInvoice
import com.zyntasolutions.zyntapos.domain.model.EInvoiceLineItem
import com.zyntasolutions.zyntapos.domain.model.EInvoiceStatus
import com.zyntasolutions.zyntapos.domain.model.TaxBreakdownItem
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
 * ZyntaPOS — EInvoiceRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [EInvoiceRepositoryImpl] against a real in-memory SQLite database.
 * [IrdApiClient] is instantiated with a blank endpoint (no mTLS) — it is never
 * invoked in these tests (no [submitToIrd] calls).
 *
 * Coverage:
 *  A. insert → getById round-trip preserves all fields including JSON columns
 *  B. getAll returns all invoices for storeId (Turbine)
 *  C. getByStatus returns only invoices matching the given status (Turbine)
 *  D. getByOrderId returns the invoice for a given orderId
 *  E. updateStatus transitions status and sets irdReferenceNumber
 *  F. cancel transitions status to CANCELLED
 *  G. getById for unknown ID returns error
 *  H. getByOrderId for unknown orderId returns Success(null)
 */
class EInvoiceRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: EInvoiceRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        // IrdApiClient with blank endpoint — falls back to standard HTTPS (never called in tests)
        val fakeIrdClient = IrdApiClient(endpoint = "", certPath = "", certPassword = "")
        repo = EInvoiceRepositoryImpl(db, SyncEnqueuer(db), fakeIrdClient)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private val now get() = Clock.System.now().toEpochMilliseconds()

    private fun makeLineItem(
        productId: String = "prod-01",
        description: String = "Widget",
        quantity: Double = 2.0,
        unitPrice: Double = 50.0,
        taxRate: Double = 15.0,
        taxAmount: Double = 15.0,
        lineTotal: Double = 100.0,
    ) = EInvoiceLineItem(
        productId = productId,
        description = description,
        quantity = quantity,
        unitPrice = unitPrice,
        taxRate = taxRate,
        taxAmount = taxAmount,
        lineTotal = lineTotal,
    )

    private fun makeTaxBreakdown(
        taxRate: Double = 15.0,
        taxablAmount: Double = 100.0,
        taxAmount: Double = 15.0,
    ) = TaxBreakdownItem(
        taxRate = taxRate,
        taxablAmount = taxablAmount,
        taxAmount = taxAmount,
    )

    private fun makeInvoice(
        id: String = "inv-01",
        orderId: String = "order-01",
        storeId: String = "store-01",
        invoiceNumber: String = "INV-2026-001",
        customerName: String = "Test Customer",
        status: EInvoiceStatus = EInvoiceStatus.DRAFT,
    ) = EInvoice(
        id = id,
        orderId = orderId,
        storeId = storeId,
        invoiceNumber = invoiceNumber,
        invoiceDate = "2026-03-27",
        customerName = customerName,
        lineItems = listOf(makeLineItem()),
        subtotal = 100.0,
        taxBreakdown = listOf(makeTaxBreakdown()),
        totalTax = 15.0,
        total = 115.0,
        currency = "LKR",
        status = status,
        createdAt = 1_000_000L,
        updatedAt = 1_000_000L,
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - insert then getById returns full invoice`() = runTest {
        val inv = makeInvoice(id = "inv-01", invoiceNumber = "INV-2026-001", customerName = "Acme Corp")
        val insertResult = repo.insert(inv)
        assertIs<Result.Success<Unit>>(insertResult)

        val fetchResult = repo.getById("inv-01")
        assertIs<Result.Success<EInvoice>>(fetchResult)
        val fetched = fetchResult.data
        assertEquals("inv-01", fetched.id)
        assertEquals("order-01", fetched.orderId)
        assertEquals("store-01", fetched.storeId)
        assertEquals("INV-2026-001", fetched.invoiceNumber)
        assertEquals("Acme Corp", fetched.customerName)
        assertEquals(EInvoiceStatus.DRAFT, fetched.status)
        assertEquals(100.0, fetched.subtotal)
        assertEquals(15.0, fetched.totalTax)
        assertEquals(115.0, fetched.total)
        assertEquals("LKR", fetched.currency)
        assertEquals(1, fetched.lineItems.size)
        assertEquals("prod-01", fetched.lineItems.first().productId)
        assertEquals(1, fetched.taxBreakdown.size)
        assertEquals(15.0, fetched.taxBreakdown.first().taxRate)
    }

    @Test
    fun `B - getAll returns all invoices for storeId`() = runTest {
        repo.insert(makeInvoice(id = "inv-01", storeId = "store-01", orderId = "order-01", invoiceNumber = "INV-001"))
        repo.insert(makeInvoice(id = "inv-02", storeId = "store-01", orderId = "order-02", invoiceNumber = "INV-002"))
        repo.insert(makeInvoice(id = "inv-03", storeId = "store-02", orderId = "order-03", invoiceNumber = "INV-003"))

        repo.getAll("store-01").test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertTrue(list.none { it.storeId == "store-02" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `C - getByStatus returns only invoices matching the status`() = runTest {
        repo.insert(makeInvoice(id = "inv-01", orderId = "order-01", invoiceNumber = "INV-001", status = EInvoiceStatus.DRAFT))
        repo.insert(makeInvoice(id = "inv-02", orderId = "order-02", invoiceNumber = "INV-002", status = EInvoiceStatus.ACCEPTED))

        repo.getByStatus("store-01", EInvoiceStatus.DRAFT).test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("inv-01", list.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `D - getByOrderId returns the invoice for a given orderId`() = runTest {
        repo.insert(makeInvoice(id = "inv-01", orderId = "order-01", invoiceNumber = "INV-001"))

        val result = repo.getByOrderId("order-01")
        assertIs<Result.Success<EInvoice?>>(result)
        assertNotNull(result.data)
        assertEquals("inv-01", result.data!!.id)
    }

    @Test
    fun `E - updateStatus transitions status and sets irdReferenceNumber`() = runTest {
        repo.insert(makeInvoice(id = "inv-01", orderId = "order-01", invoiceNumber = "INV-001", status = EInvoiceStatus.SUBMITTED))

        val updateResult = repo.updateStatus(
            id = "inv-01",
            status = EInvoiceStatus.ACCEPTED,
            irdReferenceNumber = "IRD-REF-2026-001",
            rejectionReason = null,
            updatedAt = now,
        )
        assertIs<Result.Success<Unit>>(updateResult)

        val fetched = (repo.getById("inv-01") as Result.Success).data
        assertEquals(EInvoiceStatus.ACCEPTED, fetched.status)
        assertEquals("IRD-REF-2026-001", fetched.irdReferenceNumber)
    }

    @Test
    fun `F - cancel transitions status to CANCELLED`() = runTest {
        repo.insert(makeInvoice(id = "inv-01", orderId = "order-01", invoiceNumber = "INV-001", status = EInvoiceStatus.DRAFT))

        val cancelResult = repo.cancel("inv-01", now)
        assertIs<Result.Success<Unit>>(cancelResult)

        val fetched = (repo.getById("inv-01") as Result.Success).data
        assertEquals(EInvoiceStatus.CANCELLED, fetched.status)
    }

    @Test
    fun `G - getById for unknown ID returns error`() = runTest {
        val result = repo.getById("non-existent")
        assertIs<Result.Error>(result)
        assertNotNull((result as Result.Error).exception)
    }

    @Test
    fun `H - getByOrderId for unknown orderId returns Success(null)`() = runTest {
        val result = repo.getByOrderId("non-existent-order")
        assertIs<Result.Success<EInvoice?>>(result)
        assertNull(result.data)
    }
}
