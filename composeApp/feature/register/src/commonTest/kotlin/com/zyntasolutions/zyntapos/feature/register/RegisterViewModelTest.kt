package com.zyntasolutions.zyntapos.feature.register

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.AuditEntry
import com.zyntasolutions.zyntapos.domain.model.CashMovement
import com.zyntasolutions.zyntapos.domain.model.CashRegister
import com.zyntasolutions.zyntapos.domain.model.Order
import com.zyntasolutions.zyntapos.domain.model.RegisterSession
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.printer.A4InvoicePrinterPort
import com.zyntasolutions.zyntapos.domain.printer.ReceiptPrinterPort
import com.zyntasolutions.zyntapos.domain.printer.ZReportPrinterPort
import com.zyntasolutions.zyntapos.domain.repository.AuditRepository
import com.zyntasolutions.zyntapos.domain.model.Store
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.repository.OrderRepository
import com.zyntasolutions.zyntapos.domain.repository.RegisterRepository
import com.zyntasolutions.zyntapos.domain.repository.StoreRepository
import com.zyntasolutions.zyntapos.domain.usecase.auth.CheckPermissionUseCase
import com.zyntasolutions.zyntapos.domain.usecase.register.CloseRegisterSessionUseCase
import com.zyntasolutions.zyntapos.domain.usecase.register.OpenRegisterSessionUseCase
import com.zyntasolutions.zyntapos.domain.usecase.register.PrintA4ZReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.register.PrintZReportUseCase
import com.zyntasolutions.zyntapos.domain.usecase.pos.OpenCashDrawerUseCase
import com.zyntasolutions.zyntapos.domain.usecase.register.RecordCashMovementUseCase
import com.zyntasolutions.zyntapos.domain.usecase.reports.GenerateSalesReportUseCase
import com.zyntasolutions.zyntapos.security.audit.SecurityAuditLogger
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
import com.zyntasolutions.zyntapos.core.analytics.AnalyticsTracker
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

    // ── No-op AuditRepository + SecurityAuditLogger ───────────────────────────

    private val noOpAuditRepository = object : AuditRepository {
        override suspend fun insert(entry: AuditEntry) = Unit
        override fun observeAll(): Flow<List<AuditEntry>> = MutableStateFlow(emptyList())
        override fun observeByUserId(userId: String): Flow<List<AuditEntry>> = MutableStateFlow(emptyList())
        override suspend fun getAllChronological(): List<AuditEntry> = emptyList()
        override suspend fun getLatestHash(): String? = null
        override suspend fun countEntries(): Long = 0L
        override suspend fun getRecentLoginFailureCount(userId: String, sinceEpochMillis: Long): Long = 0L
    }
    private val testAuditLogger = SecurityAuditLogger(noOpAuditRepository, "test-device")

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
        override suspend fun quickSwitch(userId: String, pin: String): Result<User> =
            Result.Success(_session.value!!)
        override suspend fun validateManagerPin(pin: String): Result<Boolean> =
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
        override suspend fun getPage(pageRequest: com.zyntasolutions.zyntapos.core.pagination.PageRequest, from: Instant?, to: Instant?, customerId: String?): com.zyntasolutions.zyntapos.core.pagination.PaginatedResult<Order> =
            com.zyntasolutions.zyntapos.core.pagination.PaginatedResult(items = emptyList(), totalCount = 0L, hasMore = false)
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

    private val fakeStoreRepository = object : StoreRepository {
        override fun getAllStores(): Flow<List<Store>> = flowOf(emptyList())
        override suspend fun getById(storeId: String): Store? = null
        override suspend fun getStoreName(storeId: String): String? =
            if (storeId == "store-001") "Test Store" else null
        override suspend fun upsertFromSync(store: Store) = Unit
    }

    private val noOpAnalytics = object : AnalyticsTracker {
        override fun logEvent(name: String, params: Map<String, String>) = Unit
        override fun logScreenView(screenName: String, screenClass: String) = Unit
        override fun setUserId(userId: String?) = Unit
        override fun setUserProperty(name: String, value: String) = Unit
    }

    private val fakeUserRepository = object : com.zyntasolutions.zyntapos.domain.repository.UserRepository {
        override fun getAll(storeId: String?): Flow<List<User>> = MutableStateFlow(emptyList())
        override suspend fun getById(id: String): Result<User> = Result.Error(DatabaseException("Not found"))
        override suspend fun create(user: User, plainPassword: String): Result<Unit> = Result.Success(Unit)
        override suspend fun update(user: User): Result<Unit> = Result.Success(Unit)
        override suspend fun updatePassword(userId: String, newPlainPassword: String): Result<Unit> = Result.Success(Unit)
        override suspend fun deactivate(userId: String): Result<Unit> = Result.Success(Unit)
        override suspend fun getSystemAdmin(): Result<User?> = Result.Success(null)
        override suspend fun adminExists(): Result<Boolean> = Result.Success(false)
        override suspend fun transferSystemAdmin(fromUserId: String, toUserId: String): Result<Unit> = Result.Success(Unit)
        override suspend fun getQuickSwitchCandidates(storeId: String): Result<List<com.zyntasolutions.zyntapos.domain.model.QuickSwitchCandidate>> = Result.Success(emptyList())
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

    private val fakeReceiptPrinterPort = object : ReceiptPrinterPort {
        override suspend fun print(order: Order, cashierId: String): Result<Unit> =
            Result.Success(Unit)
        override suspend fun openCashDrawer(): Result<Unit> =
            Result.Success(Unit)
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
            storeRepository = fakeStoreRepository,
            userRepository = fakeUserRepository,
            openCashDrawerUseCase = OpenCashDrawerUseCase(fakeReceiptPrinterPort),
            auditLogger = testAuditLogger,
            analytics = noOpAnalytics,
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

    // ── Open Cash Drawer (G5) ─────────────────────────────────────────────────

    @Test
    fun `OpenCashDrawer on success sets successMessage`() = runTest {
        viewModel.dispatch(RegisterIntent.OpenCashDrawer)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Cash drawer opened", viewModel.state.value.successMessage)
    }

    // ── Manager Approval — ManagerApprovalPinChanged ──────────────────────────

    @Test
    fun `ManagerApprovalPinChanged updates managerPin in closeRegisterForm`() = runTest {
        viewModel.dispatch(RegisterIntent.ManagerApprovalPinChanged("1234"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("1234", viewModel.state.value.closeRegisterForm.managerPin)
    }

    @Test
    fun `ManagerApprovalPinChanged clears prior managerApprovalError`() = runTest {
        viewModel.dispatch(RegisterIntent.ManagerApprovalPinChanged("12"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.closeRegisterForm.managerApprovalError)
    }

    @Test
    fun `SubmitManagerApproval with PIN shorter than 4 digits sets error`() = runTest {
        viewModel.dispatch(RegisterIntent.ManagerApprovalPinChanged("12"))
        viewModel.dispatch(RegisterIntent.SubmitManagerApproval)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.closeRegisterForm.managerApprovalError)
    }

    @Test
    fun `CancelManagerApproval clears awaitingManagerApproval state`() = runTest {
        viewModel.dispatch(RegisterIntent.CancelManagerApproval)
        testDispatcher.scheduler.advanceUntilIdle()

        val form = viewModel.state.value.closeRegisterForm
        assertFalse(form.awaitingManagerApproval)
        assertEquals("", form.managerPin)
        assertNull(form.managerApprovalError)
    }

    // ── Cash Out Approval (G5) ────────────────────────────────────────────────

    @Test
    fun `CashOutApprovalPinChanged updates approvalPin in dialog state`() = runTest {
        viewModel.dispatch(RegisterIntent.ShowCashInOutDialog(CashMovement.Type.OUT))
        viewModel.dispatch(RegisterIntent.CashOutApprovalPinChanged("5678"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("5678", viewModel.state.value.cashInOutDialog?.approvalPin)
    }

    @Test
    fun `CashOutApprovalPinChanged clears prior approvalError`() = runTest {
        viewModel.dispatch(RegisterIntent.ShowCashInOutDialog(CashMovement.Type.OUT))
        viewModel.dispatch(RegisterIntent.CashOutApprovalPinChanged("1"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.cashInOutDialog?.approvalError)
    }

    @Test
    fun `SubmitCashOutApproval with PIN shorter than 4 digits sets approvalError`() = runTest {
        viewModel.dispatch(RegisterIntent.ShowCashInOutDialog(CashMovement.Type.OUT))
        viewModel.dispatch(RegisterIntent.CashOutApprovalPinChanged("12"))
        viewModel.dispatch(RegisterIntent.SubmitCashOutApproval)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.cashInOutDialog?.approvalError)
    }

    @Test
    fun `CancelCashOutApproval clears approval state in dialog`() = runTest {
        viewModel.dispatch(RegisterIntent.ShowCashInOutDialog(CashMovement.Type.OUT))
        viewModel.dispatch(RegisterIntent.CancelCashOutApproval)
        testDispatcher.scheduler.advanceUntilIdle()

        val dialog = viewModel.state.value.cashInOutDialog
        // dialog is still open, but approval fields are cleared
        assertEquals("", dialog?.approvalPin ?: "")
        assertFalse(dialog?.awaitingCashOutApproval ?: false)
        assertNull(dialog?.approvalError)
    }

    // ── Shift Handoff (G5) ────────────────────────────────────────────────────

    @Test
    fun `DismissShiftHandoff hides dialog and clears handoff state`() = runTest {
        viewModel.dispatch(RegisterIntent.DismissShiftHandoff)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.showHandoffDialog)
        assertNull(state.handoffTargetUserId)
        assertEquals("", state.handoffPin)
        assertNull(state.handoffError)
    }

    @Test
    fun `SelectHandoffTarget sets handoffTargetUserId`() = runTest {
        viewModel.dispatch(RegisterIntent.SelectHandoffTarget("user-cashier-002"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("user-cashier-002", viewModel.state.value.handoffTargetUserId)
    }

    @Test
    fun `SelectHandoffTarget clears prior handoffError`() = runTest {
        viewModel.dispatch(RegisterIntent.SelectHandoffTarget("user-002"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.handoffError)
    }

    @Test
    fun `HandoffPinChanged updates handoffPin`() = runTest {
        viewModel.dispatch(RegisterIntent.HandoffPinChanged("4321"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("4321", viewModel.state.value.handoffPin)
    }

    @Test
    fun `HandoffPinChanged clears prior handoffError`() = runTest {
        viewModel.dispatch(RegisterIntent.HandoffPinChanged("4"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.handoffError)
    }

    @Test
    fun `ConfirmShiftHandoff without selected target sets handoffError`() = runTest {
        // No target selected — confirm should set error
        viewModel.dispatch(RegisterIntent.ConfirmShiftHandoff)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.handoffError)
    }

    @Test
    fun `ConfirmShiftHandoff with target but short PIN sets handoffError`() = runTest {
        viewModel.dispatch(RegisterIntent.SelectHandoffTarget("user-002"))
        viewModel.dispatch(RegisterIntent.HandoffPinChanged("12"))  // < 4 digits
        viewModel.dispatch(RegisterIntent.ConfirmShiftHandoff)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.handoffError)
    }

    // ── Z-Report ──────────────────────────────────────────────────────────────

    @Test
    fun `LoadZReport when zReportSession is null does not crash`() = runTest {
        // zReportSession is null initially
        assertNull(viewModel.state.value.zReportSession)
        viewModel.dispatch(RegisterIntent.LoadZReport("sess-001"))
        testDispatcher.scheduler.advanceUntilIdle()
        // Should not crash; isLoading returns to false
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `PrintZReport when zReportSession is null does not crash`() = runTest {
        assertNull(viewModel.state.value.zReportSession)
        viewModel.dispatch(RegisterIntent.PrintZReport("sess-001"))
        testDispatcher.scheduler.advanceUntilIdle()
        // No-op when session is null; no error
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `PrintZReport with non-matching session ID is a no-op`() = runTest {
        assertNull(viewModel.state.value.zReportSession)
        viewModel.dispatch(RegisterIntent.PrintZReport("wrong-session-id"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.state.value.error)
    }
}
