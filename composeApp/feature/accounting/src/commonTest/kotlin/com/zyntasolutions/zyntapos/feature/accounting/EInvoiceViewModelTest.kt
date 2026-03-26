package com.zyntasolutions.zyntapos.feature.accounting

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.EInvoice
import com.zyntasolutions.zyntapos.domain.model.EInvoiceLineItem
import com.zyntasolutions.zyntapos.domain.model.EInvoiceStatus
import com.zyntasolutions.zyntapos.domain.model.IrdSubmissionResult
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.TaxBreakdownItem
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.repository.EInvoiceRepository
import com.zyntasolutions.zyntapos.domain.usecase.einvoice.CancelEInvoiceUseCase
import com.zyntasolutions.zyntapos.domain.usecase.einvoice.GetEInvoicesUseCase
import com.zyntasolutions.zyntapos.domain.usecase.einvoice.SubmitEInvoiceToIrdUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [EInvoiceViewModel].
 *
 * Uses hand-rolled fakes instead of Mockative (KSP1 is incompatible with Kotlin 2.3+).
 * All tests run under [StandardTestDispatcher] for deterministic coroutine execution.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EInvoiceViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    // ── Test fixtures ─────────────────────────────────────────────────────────

    private fun buildInvoice(
        id: String = "inv-001",
        status: EInvoiceStatus = EInvoiceStatus.DRAFT,
        invoiceNumber: String = "INV-2026-001",
    ) = EInvoice(
        id = id,
        orderId = "order-001",
        storeId = "store-001",
        invoiceNumber = invoiceNumber,
        invoiceDate = "2026-02-27",
        customerName = "Test Customer",
        lineItems = listOf(
            EInvoiceLineItem(
                productId = "prod-001",
                description = "Widget",
                quantity = 2.0,
                unitPrice = 500.0,
                taxRate = 0.15,
                taxAmount = 150.0,
                lineTotal = 1150.0,
            )
        ),
        subtotal = 1000.0,
        taxBreakdown = listOf(TaxBreakdownItem(taxRate = 0.15, taxablAmount = 1000.0, taxAmount = 150.0)),
        totalTax = 150.0,
        total = 1150.0,
        currency = "LKR",
        status = status,
        createdAt = 0L,
        updatedAt = 0L,
    )

    // ── Fake AuthRepository ────────────────────────────────────────────────────

    private val fakeUser = User(
        id = "user-001",
        name = "Test User",
        email = "test@zynta.com",
        role = Role.ADMIN,
        storeId = "store-001",
        isActive = true,
        pinHash = null,
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = Instant.fromEpochMilliseconds(0),
    )

    private val sessionFlow = MutableStateFlow<User?>(fakeUser)

    private val fakeAuthRepository = object : AuthRepository {
        override fun getSession(): Flow<User?> = sessionFlow
        override suspend fun login(email: String, password: String): Result<User> =
            Result.Success(fakeUser)
        override suspend fun logout() { sessionFlow.value = null }
        override suspend fun refreshToken(): Result<Unit> = Result.Success(Unit)
        override suspend fun updatePin(userId: String, pin: String): Result<Unit> =
            Result.Success(Unit)
        override suspend fun validatePin(userId: String, pin: String): Result<Boolean> =
            Result.Success(true)
        override suspend fun quickSwitch(userId: String, pin: String): Result<User> =
            Result.Error(DatabaseException("not used"))
        override suspend fun validateManagerPin(pin: String): Result<Boolean> =
            Result.Success(false)
    }

    // ── Fake EInvoiceRepository ────────────────────────────────────────────────

    private val invoicesFlow = MutableStateFlow<List<EInvoice>>(emptyList())
    private var submitResult: Result<IrdSubmissionResult> = Result.Success(
        IrdSubmissionResult(success = true, referenceNumber = "IRD-REF-001", submittedAt = 0L)
    )
    private var cancelResult: Result<Unit> = Result.Success(Unit)
    private var submitCalledWithId: String? = null
    private var cancelCalledWithId: String? = null

    private val fakeEInvoiceRepository = object : EInvoiceRepository {
        override fun getAll(storeId: String): Flow<List<EInvoice>> = invoicesFlow

        override fun getByStatus(storeId: String, status: EInvoiceStatus): Flow<List<EInvoice>> =
            MutableStateFlow(invoicesFlow.value.filter { it.status == status })

        override suspend fun getById(id: String): Result<EInvoice> {
            val invoice = invoicesFlow.value.find { it.id == id }
            return if (invoice != null) Result.Success(invoice)
            else Result.Error(DatabaseException("Invoice not found: $id"))
        }

        override suspend fun getByOrderId(orderId: String): Result<EInvoice?> =
            Result.Success(invoicesFlow.value.find { it.orderId == orderId })

        override suspend fun insert(invoice: EInvoice): Result<Unit> {
            invoicesFlow.value = invoicesFlow.value + invoice
            return Result.Success(Unit)
        }

        override suspend fun submitToIrd(id: String, submittedAt: Long): Result<IrdSubmissionResult> {
            submitCalledWithId = id
            return submitResult
        }

        override suspend fun updateStatus(
            id: String,
            status: EInvoiceStatus,
            irdReferenceNumber: String?,
            rejectionReason: String?,
            updatedAt: Long,
        ): Result<Unit> = Result.Success(Unit)

        override suspend fun cancel(id: String, updatedAt: Long): Result<Unit> {
            cancelCalledWithId = id
            return cancelResult
        }
    }

    private val getEInvoicesUseCase = GetEInvoicesUseCase(fakeEInvoiceRepository)
    private val submitEInvoiceToIrdUseCase = SubmitEInvoiceToIrdUseCase(fakeEInvoiceRepository)
    private val cancelEInvoiceUseCase = CancelEInvoiceUseCase(fakeEInvoiceRepository)

    private lateinit var viewModel: EInvoiceViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        invoicesFlow.value = emptyList()
        submitResult = Result.Success(
            IrdSubmissionResult(success = true, referenceNumber = "IRD-REF-001", submittedAt = 0L)
        )
        cancelResult = Result.Success(Unit)
        submitCalledWithId = null
        cancelCalledWithId = null
        sessionFlow.value = fakeUser
        viewModel = EInvoiceViewModel(
            getEInvoicesUseCase = getEInvoicesUseCase,
            submitEInvoiceToIrdUseCase = submitEInvoiceToIrdUseCase,
            cancelEInvoiceUseCase = cancelEInvoiceUseCase,
            authRepository = fakeAuthRepository,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun `initial state has empty invoice list`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.state.value
        assertTrue(state.invoices.isEmpty(), "Invoices should be empty initially")
    }

    @Test
    fun `initial state has no selected invoice`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.state.value.selectedInvoice)
    }

    @Test
    fun `initial state has no errors`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.state.value.error)
        assertNull(viewModel.state.value.successMessage)
    }

    @Test
    fun `initial state has no cancel confirmation dialog`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.state.value.showCancelConfirm)
    }

    // ── Reactive invoice loading ───────────────────────────────────────────────

    @Test
    fun `invoices emitted from repository flow are reflected in state`() = runTest {
        val invoice = buildInvoice()
        testDispatcher.scheduler.advanceUntilIdle()

        invoicesFlow.value = listOf(invoice)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(invoice), viewModel.state.value.invoices)
    }

    @Test
    fun `multiple invoices from repository are reflected in state`() = runTest {
        val inv1 = buildInvoice(id = "inv-001")
        val inv2 = buildInvoice(id = "inv-002", invoiceNumber = "INV-2026-002")
        testDispatcher.scheduler.advanceUntilIdle()

        invoicesFlow.value = listOf(inv1, inv2)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.state.value.invoices.size)
    }

    // ── FilterByStatus ─────────────────────────────────────────────────────────

    @Test
    fun `FilterByStatus updates statusFilter in state`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dispatch(EInvoiceIntent.FilterByStatus(EInvoiceStatus.DRAFT))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(EInvoiceStatus.DRAFT, viewModel.state.value.statusFilter)
    }

    @Test
    fun `FilterByStatus with null clears statusFilter`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.dispatch(EInvoiceIntent.FilterByStatus(EInvoiceStatus.SUBMITTED))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dispatch(EInvoiceIntent.FilterByStatus(null))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.statusFilter)
    }

    @Test
    fun `FilterByStatus with ACCEPTED status updates statusFilter`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.dispatch(EInvoiceIntent.FilterByStatus(EInvoiceStatus.ACCEPTED))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(EInvoiceStatus.ACCEPTED, viewModel.state.value.statusFilter)
    }

    // ── LoadInvoice ────────────────────────────────────────────────────────────

    @Test
    fun `LoadInvoice sets selectedInvoice when invoice exists in list`() = runTest {
        val invoice = buildInvoice(id = "inv-001")
        invoicesFlow.value = listOf(invoice)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dispatch(EInvoiceIntent.LoadInvoice("inv-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(invoice, viewModel.state.value.selectedInvoice)
    }

    @Test
    fun `LoadInvoice with unknown id sets selectedInvoice to null`() = runTest {
        invoicesFlow.value = listOf(buildInvoice(id = "inv-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dispatch(EInvoiceIntent.LoadInvoice("inv-nonexistent"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.selectedInvoice)
    }

    // ── SubmitToIrd ────────────────────────────────────────────────────────────

    @Test
    fun `SubmitToIrd on DRAFT invoice sets successMessage on IRD success`() = runTest {
        val invoice = buildInvoice(id = "inv-001", status = EInvoiceStatus.DRAFT)
        invoicesFlow.value = listOf(invoice)
        testDispatcher.scheduler.advanceUntilIdle()

        submitResult = Result.Success(
            IrdSubmissionResult(success = true, referenceNumber = "IRD-999", submittedAt = 0L)
        )
        viewModel.dispatch(EInvoiceIntent.SubmitToIrd("inv-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.successMessage)
        assertTrue(
            viewModel.state.value.successMessage!!.contains("IRD-999"),
            "Success message should contain IRD reference number",
        )
    }

    @Test
    fun `SubmitToIrd success clears isSubmitting`() = runTest {
        val invoice = buildInvoice(id = "inv-001", status = EInvoiceStatus.DRAFT)
        invoicesFlow.value = listOf(invoice)
        testDispatcher.scheduler.advanceUntilIdle()

        submitResult = Result.Success(
            IrdSubmissionResult(success = true, referenceNumber = "IRD-001", submittedAt = 0L)
        )
        viewModel.dispatch(EInvoiceIntent.SubmitToIrd("inv-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.isSubmitting)
    }

    @Test
    fun `SubmitToIrd success emits ShowSnackbar effect`() = runTest {
        val invoice = buildInvoice(id = "inv-001", status = EInvoiceStatus.DRAFT)
        invoicesFlow.value = listOf(invoice)
        testDispatcher.scheduler.advanceUntilIdle()

        submitResult = Result.Success(
            IrdSubmissionResult(success = true, referenceNumber = "IRD-001", submittedAt = 0L)
        )

        viewModel.effects.test {
            viewModel.dispatch(EInvoiceIntent.SubmitToIrd("inv-001"))
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is EInvoiceEffect.ShowSnackbar, "Expected ShowSnackbar effect, got: $effect")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `SubmitToIrd on DRAFT invoice calls repository with correct id`() = runTest {
        val invoice = buildInvoice(id = "inv-001", status = EInvoiceStatus.DRAFT)
        invoicesFlow.value = listOf(invoice)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dispatch(EInvoiceIntent.SubmitToIrd("inv-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("inv-001", submitCalledWithId)
    }

    @Test
    fun `SubmitToIrd when invoice not found sets error state`() = runTest {
        // Invoices list is empty — no matching invoice
        invoicesFlow.value = emptyList()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dispatch(EInvoiceIntent.SubmitToIrd("inv-nonexistent"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.error)
        assertTrue(
            viewModel.state.value.error!!.contains("not found", ignoreCase = true),
            "Error should mention 'not found': ${viewModel.state.value.error}",
        )
    }

    @Test
    fun `SubmitToIrd on non-DRAFT and non-REJECTED invoice sets error state`() = runTest {
        val invoice = buildInvoice(id = "inv-001", status = EInvoiceStatus.ACCEPTED)
        invoicesFlow.value = listOf(invoice)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dispatch(EInvoiceIntent.SubmitToIrd("inv-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.error)
        assertTrue(
            viewModel.state.value.error!!.contains("DRAFT", ignoreCase = true),
            "Error should mention DRAFT: ${viewModel.state.value.error}",
        )
    }

    @Test
    fun `SubmitToIrd on REJECTED invoice succeeds`() = runTest {
        val invoice = buildInvoice(id = "inv-001", status = EInvoiceStatus.REJECTED)
        invoicesFlow.value = listOf(invoice)
        testDispatcher.scheduler.advanceUntilIdle()

        submitResult = Result.Success(
            IrdSubmissionResult(success = true, referenceNumber = "IRD-RESUBMIT-001", submittedAt = 0L)
        )
        viewModel.dispatch(EInvoiceIntent.SubmitToIrd("inv-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.successMessage)
        assertTrue(
            viewModel.state.value.successMessage!!.contains("IRD-RESUBMIT-001"),
            "Success message should contain IRD reference number",
        )
        assertEquals("inv-001", submitCalledWithId)
    }

    @Test
    fun `SubmitToIrd on SUBMITTED invoice sets error state`() = runTest {
        val invoice = buildInvoice(id = "inv-001", status = EInvoiceStatus.SUBMITTED)
        invoicesFlow.value = listOf(invoice)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dispatch(EInvoiceIntent.SubmitToIrd("inv-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.error)
        assertTrue(
            viewModel.state.value.error!!.contains("DRAFT", ignoreCase = true),
            "Error should mention DRAFT: ${viewModel.state.value.error}",
        )
    }

    @Test
    fun `SubmitToIrd on CANCELLED invoice sets error state`() = runTest {
        val invoice = buildInvoice(id = "inv-001", status = EInvoiceStatus.CANCELLED)
        invoicesFlow.value = listOf(invoice)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dispatch(EInvoiceIntent.SubmitToIrd("inv-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.error)
        assertTrue(
            viewModel.state.value.error!!.contains("DRAFT", ignoreCase = true),
            "Error should mention DRAFT: ${viewModel.state.value.error}",
        )
    }

    @Test
    fun `SubmitToIrd on repository failure sets error in state`() = runTest {
        val invoice = buildInvoice(id = "inv-001", status = EInvoiceStatus.DRAFT)
        invoicesFlow.value = listOf(invoice)
        testDispatcher.scheduler.advanceUntilIdle()

        submitResult = Result.Error(DatabaseException("Network unreachable"))
        viewModel.dispatch(EInvoiceIntent.SubmitToIrd("inv-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.error)
        assertFalse(viewModel.state.value.isSubmitting)
    }

    @Test
    fun `SubmitToIrd when IRD returns failure sets error in state`() = runTest {
        val invoice = buildInvoice(id = "inv-001", status = EInvoiceStatus.DRAFT)
        invoicesFlow.value = listOf(invoice)
        testDispatcher.scheduler.advanceUntilIdle()

        submitResult = Result.Success(
            IrdSubmissionResult(
                success = false,
                errorCode = "400",
                errorMessage = "Invalid tax registration number",
                submittedAt = 0L,
            )
        )
        viewModel.dispatch(EInvoiceIntent.SubmitToIrd("inv-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.error)
        assertNull(viewModel.state.value.successMessage)
    }

    // ── RequestCancel / DismissCancel / ConfirmCancel ─────────────────────────

    @Test
    fun `RequestCancel shows cancel confirmation dialog with invoice`() = runTest {
        val invoice = buildInvoice(id = "inv-001")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dispatch(EInvoiceIntent.RequestCancel(invoice))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(invoice, viewModel.state.value.showCancelConfirm)
    }

    @Test
    fun `DismissCancel clears the cancel confirmation dialog`() = runTest {
        val invoice = buildInvoice(id = "inv-001")
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.dispatch(EInvoiceIntent.RequestCancel(invoice))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dispatch(EInvoiceIntent.DismissCancel)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.showCancelConfirm)
    }

    @Test
    fun `ConfirmCancel on success sets successMessage`() = runTest {
        val invoice = buildInvoice(id = "inv-001", invoiceNumber = "INV-2026-001")
        cancelResult = Result.Success(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dispatch(EInvoiceIntent.RequestCancel(invoice))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.dispatch(EInvoiceIntent.ConfirmCancel)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.successMessage)
        assertTrue(
            viewModel.state.value.successMessage!!.contains("INV-2026-001"),
            "Success message should contain invoice number",
        )
    }

    @Test
    fun `ConfirmCancel clears showCancelConfirm after execution`() = runTest {
        val invoice = buildInvoice(id = "inv-001")
        cancelResult = Result.Success(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dispatch(EInvoiceIntent.RequestCancel(invoice))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.dispatch(EInvoiceIntent.ConfirmCancel)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.showCancelConfirm)
    }

    @Test
    fun `ConfirmCancel calls repository with correct invoice id`() = runTest {
        val invoice = buildInvoice(id = "inv-to-cancel")
        cancelResult = Result.Success(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dispatch(EInvoiceIntent.RequestCancel(invoice))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.dispatch(EInvoiceIntent.ConfirmCancel)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("inv-to-cancel", cancelCalledWithId)
    }

    @Test
    fun `ConfirmCancel success emits ShowSnackbar effect`() = runTest {
        val invoice = buildInvoice(id = "inv-001")
        cancelResult = Result.Success(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.dispatch(EInvoiceIntent.RequestCancel(invoice))
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.dispatch(EInvoiceIntent.ConfirmCancel)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is EInvoiceEffect.ShowSnackbar, "Expected ShowSnackbar, got: $effect")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ConfirmCancel on no pending confirmation is a no-op`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        val stateBefore = viewModel.state.value

        viewModel.dispatch(EInvoiceIntent.ConfirmCancel)
        testDispatcher.scheduler.advanceUntilIdle()

        // State should remain unchanged — no error, no success message
        assertNull(viewModel.state.value.error)
        assertNull(viewModel.state.value.successMessage)
        assertNull(viewModel.state.value.showCancelConfirm)
    }

    @Test
    fun `ConfirmCancel on repository error sets error state`() = runTest {
        val invoice = buildInvoice(id = "inv-001")
        cancelResult = Result.Error(DatabaseException("Cancel not permitted"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dispatch(EInvoiceIntent.RequestCancel(invoice))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.dispatch(EInvoiceIntent.ConfirmCancel)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.error)
        assertFalse(viewModel.state.value.isLoading)
    }

    // ── DismissError / DismissSuccess ─────────────────────────────────────────

    @Test
    fun `DismissError clears error field`() = runTest {
        // Trigger an error by submitting a non-existent invoice
        invoicesFlow.value = emptyList()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.dispatch(EInvoiceIntent.SubmitToIrd("inv-nonexistent"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.error)

        viewModel.dispatch(EInvoiceIntent.DismissError)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `DismissSuccess clears successMessage field`() = runTest {
        // Trigger success by cancelling an invoice
        val invoice = buildInvoice(id = "inv-001")
        cancelResult = Result.Success(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.dispatch(EInvoiceIntent.RequestCancel(invoice))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.dispatch(EInvoiceIntent.ConfirmCancel)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.successMessage)

        viewModel.dispatch(EInvoiceIntent.DismissSuccess)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.successMessage)
    }
}
