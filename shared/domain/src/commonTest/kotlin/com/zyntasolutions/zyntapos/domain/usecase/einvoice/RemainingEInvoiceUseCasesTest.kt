package com.zyntasolutions.zyntapos.domain.usecase.einvoice

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.EInvoiceStatus
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeEInvoiceRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildEInvoice
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ─────────────────────────────────────────────────────────────────────────────
// GetEInvoiceByOrderUseCaseTest
// ─────────────────────────────────────────────────────────────────────────────

class GetEInvoiceByOrderUseCaseTest {

    @Test
    fun `existingInvoice_returnsByOrderId`() = runTest {
        val repo = FakeEInvoiceRepository()
        repo.invoices.add(buildEInvoice(id = "inv-01", orderId = "order-01"))

        val result = GetEInvoiceByOrderUseCase(repo).invoke("order-01")
        assertIs<Result.Success<*>>(result)
        assertEquals("inv-01", (result as Result.Success).data?.id)
    }

    @Test
    fun `noInvoiceForOrder_returnsNullInSuccess`() = runTest {
        val repo = FakeEInvoiceRepository()

        val result = GetEInvoiceByOrderUseCase(repo).invoke("order-99")
        assertIs<Result.Success<*>>(result)
        assertNull((result as Result.Success).data)
    }

    @Test
    fun `repoFailure_propagatesError`() = runTest {
        val repo = FakeEInvoiceRepository().also { it.shouldFail = true }

        val result = GetEInvoiceByOrderUseCase(repo).invoke("order-01")
        assertIs<Result.Error>(result)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SubmitEInvoiceToIrdUseCaseTest
// ─────────────────────────────────────────────────────────────────────────────

class SubmitEInvoiceToIrdUseCaseTest {

    @Test
    fun `existingDraftInvoice_submitsSuccessfully`() = runTest {
        val repo = FakeEInvoiceRepository()
        repo.invoices.add(buildEInvoice(id = "inv-01", status = EInvoiceStatus.DRAFT))

        val result = SubmitEInvoiceToIrdUseCase(repo).invoke("inv-01", 1_000_000L)
        assertIs<Result.Success<*>>(result)
        val submission = (result as Result.Success).data
        assertTrue(submission.success)
        assertEquals(1_000_000L, submission.submittedAt)
    }

    @Test
    fun `invoiceTransitionsToSubmitted`() = runTest {
        val repo = FakeEInvoiceRepository()
        repo.invoices.add(buildEInvoice(id = "inv-01", status = EInvoiceStatus.DRAFT))

        SubmitEInvoiceToIrdUseCase(repo).invoke("inv-01", 1_000_000L)

        val updated = repo.invoices.first { it.id == "inv-01" }
        assertEquals(EInvoiceStatus.SUBMITTED, updated.status)
        assertEquals(1_000_000L, updated.submittedAt)
    }

    @Test
    fun `nonExistingInvoice_returnsError`() = runTest {
        val result = SubmitEInvoiceToIrdUseCase(FakeEInvoiceRepository()).invoke("missing", 0L)
        assertIs<Result.Error>(result)
    }

    @Test
    fun `repoFailure_propagatesError`() = runTest {
        val repo = FakeEInvoiceRepository().also { it.shouldFail = true }
        val result = SubmitEInvoiceToIrdUseCase(repo).invoke("inv-01", 0L)
        assertIs<Result.Error>(result)
    }
}
