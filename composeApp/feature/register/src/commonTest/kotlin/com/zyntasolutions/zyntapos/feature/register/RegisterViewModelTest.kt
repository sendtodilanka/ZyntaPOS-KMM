package com.zyntasolutions.zyntapos.feature.register

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.CashMovement
import com.zyntasolutions.zyntapos.domain.model.CashRegister
import com.zyntasolutions.zyntapos.domain.model.Order
import com.zyntasolutions.zyntapos.domain.model.RegisterSession
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.printer.A4InvoicePrinterPort
import com.zyntasolutions.zyntapos.domain.printer.ZReportPrinterPort
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.repository.OrderRepository
import com.zyntasolutions.zyntapos.domain.repository.RegisterRepository
import com.zyntasolutions.zyntapos.domain.usecase.auth.CheckPermissionUseCase
import com.zyntasolutions.zyntapos.domain.usecase.register.CloseRegisterSessionUseCase
import com.zyntasolutions.zyntapos.domain.usecase.register.OpenRegisterSessionUseCase
import com.zyntasolutions.zyntapos.domain.usecase.register.PrintA4ZReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.register.PrintZReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.register.RecordCashMovementUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateSalesReportUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
import kotlin.time.Clock

// ─────────────────────────────────────────────────────────────────────────────
// RegisterViewModelTest
// Tests RegisterViewModel MVI state transitions using hand-rolled fakes.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val currentUserId = "user-001"
    private val registerId = "reg-001"

    private val fakeAuthRepository = object : AuthRepository {
        private val _session = MutableStateFlow<User?>(
            User(
                id = "user-001", name = "Test User", email = "test@zynta.com",
                role = Role.CASHIER, storeId = "store-001", isActive = true,
                pinHash = null, createdAt = Clock.System.now(), updatedAt = Clock.System.now(),
            )
        )
        override fun getSession(): Flow<User?> = _session
        override suspend fun login(email: String, password: String): Result<User> =
            Result.Success(_session.value!!)
        override suspend fun logout() { _session.value = null }
        override suspend fun refreshToken(): Result<Unit> = Result.Success(Unit)
        override suspend fun updatePin(userId: String, pin: String): Result<Unit> =
            Result.Success(Unit)
        override suspend fun validatePin(userId: String, pin: String): Result<Boolean> =
            Result.Success(true)
    }
    private val sessionId = "sess-001"

    // ── Fake RegisterRepository ───────────────────────────────────────────────

    private val activeSessionFlow = MutableStateFlow<RegisterSession?>(null)
    private val movementsFlow = MutableStateFlow<List<CashMovement>>(emptyList())
    private var shouldFailOpen = false
    private var shouldFailClose = false
    private var shouldFailCashMovement = false

    private val testSession = RegisterSession(
        id = sessionId,
        registerId = registerId,
        openedBy = currentUserId,
        openingBalance = 500.0,
        expectedBalance = 500.0,
        openedAt = Clock.System.now(),
        status = RegisterSession.Status.OPEN,
    )

    private val fakeOrderRepository = object : OrderRepository {
        override fun getByDateRange(from: Instant, to: Instant): Flow<List<Order>> = flowOf(emptyList())
        override suspend fun create(order: Order): Result<Order> = Result.Error(DatabaseException("not used"))
        override suspend fun getById(id: String): Result<Order> = Result.Error(DatabaseException("not used"))
        override fun getAll(filters: Map<String, String>): Flow<List<Order>> = flowOf(emptyList())
        override suspend fun update(order: Order): Result<Unit> = Result.Error(DatabaseException("not used"))
        override suspend fun void(id: String, reason: String): Result<Unit> = Result.Error(DatabaseException("not used"))
        override suspend fun holdOrder(cart: List<com.zyntasolutions.zyntapos.domain.model.CartItem>): Result<String> = Result.Error(DatabaseException("not used"))
        override suspend fun retrieveHeld(holdId: String): Result<Order> = Result.Error(DatabaseException("not used"))
    }

    private val fakeRegisterRepository = object : RegisterRepository {
        override fun getRegisters(): Flow<List<CashRegister>> = flowOf(emptyList())
        override fun getActive(): Flow<RegisterSession?> = activeSessionFlow

        override suspend fun openSession(
            registerId: String,
            openingBalance: Double,
            userId: String,
        ): Result<RegisterSession> {
            if (shouldFailOpen) return Result.Error(DatabaseException("Open failed"))
            val session = RegisterSession(
                id = sessionId,
                registerId = registerId,
                openedBy = userId,
                openingBalance = openingBalance,
                expectedBalance = openingBalance,
                openedAt = Clock.System.now(),
                status = RegisterSession.Status.OPEN,
            )
            activeSessionFlow.value = session
            return Result.Success(session)
        }

        override suspend fun closeSession(
            sessionId: String,
            actualBalance: Double,
            userId: String,
        ): Result<RegisterSession> {
            if (shouldFailClose) return Result.Error(DatabaseException("Close failed"))
            val current = activeSessionFlow.value
                ?: return Result.Error(DatabaseException("No active session"))
            val closed = current.copy(
                status = RegisterSession.Status.CLOSED,
                actualBalance = actualBalance,
                closedBy = userId,
                closedAt = Clock.System.now(),
            )
            activeSessionFlow.value = null
            return Result.Success(closed)
        }

        override suspend fun addCashMovement(movement: CashMovement): Result<Unit> {
            if (shouldFailCashMovement) return Result.Error(DatabaseException("Movement failed"))
            movementsFlow.value = movementsFlow.value + movement
            return Result.Success(Unit)
        }

        override fun getMovements(sessionId: String): Flow<List<CashMovement>> = movementsFlow
        override suspend fun getSession(sessionId: String): Result<RegisterSession> =
            Result.Error(DatabaseException("Not used"))
    }

    // ── Fake ZReportPrinterPort ───────────────────────────────────────────────

    private var shouldFailPrint = false
    private val fakePrinterPort = object : ZReportPrinterPort {
        override suspend fun printZReport(session: RegisterSession): Result<Unit> =
            if (shouldFailPrint) Result.Error(DatabaseException("Print failed"))
            else Result.Success(Unit)
    }

    private val openRegisterSessionUseCase = OpenRegisterSessionUseCase(fakeRegisterRepository)
    private val closeRegisterSessionUseCase = CloseRegisterSessionUseCase(fakeRegisterRepository)
    private val recordCashMovementUseCase = RecordCashMovementUseCase(fakeRegisterRepository)
    private val printZReportUseCase = PrintZReportUseCase(fakePrinterPort)

    private val fakeA4PrinterPort = object : A4InvoicePrinterPort {
        override suspend fun printA4Invoice(order: Order): Result<Unit> = Result.Success(Unit)
        override suspend fun printA4ZReport(session: RegisterSession): Result<Unit> = Result.Success(Unit)
        override suspend fun printA4SalesReport(report: GenerateSalesReportUseCase.SalesReport): Result<Unit> = Result.Success(Unit)
    }

    private lateinit var viewModel: RegisterViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        activeSessionFlow.value = null
        movementsFlow.value = emptyList()
        shouldFailOpen = false
        shouldFailClose = false
        shouldFailCashMovement = false
        shouldFailPrint = false

        val sessionFlow = MutableStateFlow<User?>(null)
        val checkPermissionUseCase = CheckPermissionUseCase(sessionFlow)
        val printA4ZReportUseCase = PrintA4ZReportUseCase(fakeRegisterRepository, fakeA4PrinterPort, checkPermissionUseCase)

        viewModel = RegisterViewModel(
            registerRepository = fakeRegisterRepository,
            orderRepository = fakeOrderRepository,
            openRegisterSessionUseCase = openRegisterSessionUseCase,
            closeRegisterSessionUseCase = closeRegisterSessionUseCase,
            recordCashMovementUseCase = recordCashMovementUseCase,
            printZReportUseCase = printZReportUseCase,
            printA4ZReportUseCase = printA4ZReportUseCase,
            authRepository = fakeAuthRepository,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun `initial state has no active session and not loading`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.state.value
        assertNull(state.activeSession)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    // ── ObserveActiveSession ──────────────────────────────────────────────────

    @Test
    fun `ObserveActiveSession reflects active session from repository`() = runTest {
        viewModel.dispatch(RegisterIntent.ObserveActiveSession)
        testDispatcher.scheduler.advanceUntilIdle()

        // Initially null
        assertNull(viewModel.state.value.activeSession)

        // Simulate external session being set
        activeSessionFlow.value = testSession
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(testSession.id, viewModel.state.value.activeSession?.id)
    }

    // ── Open Register ─────────────────────────────────────────────────────────

    @Test
    fun `ConfirmOpenRegister without selecting a register sets validation error`() = runTest {
        // No register selected (selectedRegisterId == null)
        viewModel.dispatch(RegisterIntent.ConfirmOpenRegister)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.openRegisterForm.validationErrors["register"])
        assertNull(activeSessionFlow.value)
    }

    @Test
    fun `ConfirmOpenRegister with valid register emits NavigateToDashboard and ShowSuccess`() = runTest {
        viewModel.dispatch(RegisterIntent.SelectRegister(registerId))
        viewModel.dispatch(RegisterIntent.OpeningBalanceDigit("5"))
        viewModel.dispatch(RegisterIntent.OpeningBalanceDigit("0"))
        viewModel.dispatch(RegisterIntent.OpeningBalanceDigit("0"))
        viewModel.dispatch(RegisterIntent.OpeningBalanceDigit("0"))
        // openingBalanceRaw = "5000" → 50.00

        viewModel.effects.test {
            viewModel.dispatch(RegisterIntent.ConfirmOpenRegister)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect1 = awaitItem()
            assertTrue(effect1 is RegisterEffect.NavigateToDashboard)
            val effect2 = awaitItem()
            assertTrue(effect2 is RegisterEffect.ShowSuccess)
            cancelAndIgnoreRemainingEvents()
        }

        assertNotNull(activeSessionFlow.value)
        assertEquals(RegisterSession.Status.OPEN, activeSessionFlow.value?.status)
    }

    @Test
    fun `ConfirmOpenRegister on repository failure emits ShowError`() = runTest {
        shouldFailOpen = true
        viewModel.dispatch(RegisterIntent.SelectRegister(registerId))

        viewModel.effects.test {
            viewModel.dispatch(RegisterIntent.ConfirmOpenRegister)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is RegisterEffect.ShowError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Numeric pad helpers ───────────────────────────────────────────────────

    @Test
    fun `OpeningBalanceDigit appends digits correctly`() = runTest {
        viewModel.dispatch(RegisterIntent.OpeningBalanceDigit("1"))
        viewModel.dispatch(RegisterIntent.OpeningBalanceDigit("2"))
        viewModel.dispatch(RegisterIntent.OpeningBalanceDigit("3"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("123", viewModel.state.value.openRegisterForm.openingBalanceRaw)
    }

    @Test
    fun `OpeningBalanceBackspace removes last digit`() = runTest {
        viewModel.dispatch(RegisterIntent.OpeningBalanceDigit("1"))
        viewModel.dispatch(RegisterIntent.OpeningBalanceDigit("2"))
        viewModel.dispatch(RegisterIntent.OpeningBalanceBackspace)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("1", viewModel.state.value.openRegisterForm.openingBalanceRaw)
    }

    @Test
    fun `OpeningBalanceClear resets raw to zero`() = runTest {
        viewModel.dispatch(RegisterIntent.OpeningBalanceDigit("5"))
        viewModel.dispatch(RegisterIntent.OpeningBalanceClear)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("0", viewModel.state.value.openRegisterForm.openingBalanceRaw)
    }

    // ── Cash In/Out ───────────────────────────────────────────────────────────

    @Test
    fun `ShowCashInOutDialog creates dialog state`() = runTest {
        assertNull(viewModel.state.value.cashInOutDialog)

        viewModel.dispatch(RegisterIntent.ShowCashInOutDialog(CashMovement.Type.IN))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.cashInOutDialog)
        assertEquals(CashMovement.Type.IN, viewModel.state.value.cashInOutDialog?.type)
    }

    @Test
    fun `DismissCashInOut clears dialog state`() = runTest {
        viewModel.dispatch(RegisterIntent.ShowCashInOutDialog())
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.cashInOutDialog)

        viewModel.dispatch(RegisterIntent.DismissCashInOut)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.cashInOutDialog)
    }

    @Test
    fun `ConfirmCashInOut without active session does nothing`() = runTest {
        // No active session set
        viewModel.dispatch(RegisterIntent.ShowCashInOutDialog(CashMovement.Type.IN))
        viewModel.dispatch(RegisterIntent.CashInOutAmountDigit("5"))
        viewModel.dispatch(RegisterIntent.CashInOutAmountDigit("0"))
        viewModel.dispatch(RegisterIntent.CashInOutAmountDigit("0"))
        viewModel.dispatch(RegisterIntent.CashInOutReasonChanged("Petty cash"))
        viewModel.dispatch(RegisterIntent.ConfirmCashInOut)
        testDispatcher.scheduler.advanceUntilIdle()

        // No effect emitted (silent no-op when no session)
        assertTrue(movementsFlow.value.isEmpty())
    }

    @Test
    fun `ConfirmCashInOut with active session and valid data emits ShowSuccess`() = runTest {
        // Set up active session
        activeSessionFlow.value = testSession
        viewModel.dispatch(RegisterIntent.ObserveActiveSession)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dispatch(RegisterIntent.ShowCashInOutDialog(CashMovement.Type.IN))
        viewModel.dispatch(RegisterIntent.CashInOutAmountDigit("5"))
        viewModel.dispatch(RegisterIntent.CashInOutAmountDigit("0"))
        viewModel.dispatch(RegisterIntent.CashInOutAmountDigit("0"))
        viewModel.dispatch(RegisterIntent.CashInOutAmountDigit("0"))
        // amountRaw = "5000" → 50.00
        viewModel.dispatch(RegisterIntent.CashInOutReasonChanged("Petty cash"))

        viewModel.effects.test {
            viewModel.dispatch(RegisterIntent.ConfirmCashInOut)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is RegisterEffect.ShowSuccess)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, movementsFlow.value.size)
    }

    @Test
    fun `ConfirmCashInOut with zero amount sets dialog validation error`() = runTest {
        activeSessionFlow.value = testSession
        viewModel.dispatch(RegisterIntent.ObserveActiveSession)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dispatch(RegisterIntent.ShowCashInOutDialog(CashMovement.Type.OUT))
        // amount left as "0"
        viewModel.dispatch(RegisterIntent.CashInOutReasonChanged("Test reason"))
        viewModel.dispatch(RegisterIntent.ConfirmCashInOut)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.cashInOutDialog?.validationErrors?.get("amount"))
    }

    // ── Dashboard ─────────────────────────────────────────────────────────────

    @Test
    fun `LoadDashboardStats populates todayOrderCount and todayRevenue from OrderRepository`() = runTest {
        viewModel.dispatch(RegisterIntent.LoadDashboardStats)
        testDispatcher.scheduler.advanceUntilIdle()

        // Fake repository returns empty list, so both counts are zero
        val state = viewModel.state.value
        assertEquals(0, state.todayOrderCount)
        assertEquals(0.0, state.todayRevenue)
    }

    // ── DismissError / DismissSuccess ─────────────────────────────────────────

    @Test
    fun `DismissError clears error in state`() = runTest {
        viewModel.dispatch(RegisterIntent.DismissError)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `DismissSuccess clears successMessage in state`() = runTest {
        viewModel.dispatch(RegisterIntent.DismissSuccess)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.successMessage)
    }
}
