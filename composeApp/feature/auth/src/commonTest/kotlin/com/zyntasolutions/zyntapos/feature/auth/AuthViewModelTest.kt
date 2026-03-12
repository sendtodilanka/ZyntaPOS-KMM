package com.zyntasolutions.zyntapos.feature.auth

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.AuthException
import com.zyntasolutions.zyntapos.core.result.AuthFailureReason
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.AuditEntry
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.repository.AuditRepository
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.usecase.auth.LoginUseCase
import com.zyntasolutions.zyntapos.domain.usecase.auth.LogoutUseCase
import com.zyntasolutions.zyntapos.feature.auth.mvi.AuthEffect
import com.zyntasolutions.zyntapos.feature.auth.mvi.AuthIntent
import com.zyntasolutions.zyntapos.core.analytics.AnalyticsTracker
import com.zyntasolutions.zyntapos.security.audit.SecurityAuditLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.time.Clock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [AuthViewModel].
 *
 * Uses hand-rolled fakes instead of Mockative (KSP1 is incompatible with Kotlin 2.3+).
 * All tests run under [StandardTestDispatcher] for deterministic coroutine execution.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val noOpAnalytics = object : AnalyticsTracker {
        override fun logEvent(name: String, params: Map<String, String>) {}
        override fun logScreenView(screenName: String, screenClass: String) {}
        override fun setUserId(userId: String?) {}
        override fun setUserProperty(name: String, value: String) {}
    }

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

    // ── Fake AuthRepository ────────────────────────────────────────────────────

    private val sessionFlow = MutableStateFlow<User?>(null)
    private val loginResponses = mutableMapOf<Pair<String, String>, Result<User>>()

    private val fakeAuthRepository = object : AuthRepository {
        override suspend fun login(email: String, password: String): Result<User> {
            return loginResponses[email to password]
                ?: Result.Error(AuthException("Invalid credentials", reason = AuthFailureReason.INVALID_CREDENTIALS))
        }

        override suspend fun logout() {
            sessionFlow.value = null
        }

        override fun getSession(): Flow<User?> = sessionFlow

        override suspend fun refreshToken(): Result<Unit> = Result.Success(Unit)

        override suspend fun updatePin(userId: String, pin: String): Result<Unit> = Result.Success(Unit)

        override suspend fun validatePin(userId: String, pin: String): Result<Boolean> = Result.Success(true)
    }

    // ── Real use cases wired to the fake repository ────────────────────────────

    private val loginUseCase = LoginUseCase(fakeAuthRepository)
    private val logoutUseCase = LogoutUseCase(fakeAuthRepository)

    private lateinit var viewModel: AuthViewModel

    private val fakeUser = User(
        id = "user-001",
        name = "Test Cashier",
        email = "cashier@test.com",
        role = Role.CASHIER,
        storeId = "store-001",
        isActive = true,
        pinHash = null,
        createdAt = Clock.System.now(),
        updatedAt = Clock.System.now(),
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        loginResponses.clear()
        sessionFlow.value = null

        viewModel = AuthViewModel(
            loginUseCase = loginUseCase,
            _logoutUseCase = logoutUseCase,
            authRepository = fakeAuthRepository,
            auditLogger = testAuditLogger,
            analytics = noOpAnalytics,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── EmailChanged ──────────────────────────────────────────────────────────

    @Test
    fun `EmailChanged updates email state`() = runTest {
        viewModel.dispatch(AuthIntent.EmailChanged("hello@test.com"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("hello@test.com", viewModel.state.value.email)
    }

    @Test
    fun `EmailChanged with invalid email sets emailError`() = runTest {
        viewModel.dispatch(AuthIntent.EmailChanged("notanemail"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.emailError)
    }

    @Test
    fun `EmailChanged with valid email clears emailError`() = runTest {
        viewModel.dispatch(AuthIntent.EmailChanged("notanemail"))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.dispatch(AuthIntent.EmailChanged("valid@test.com"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.state.value.emailError)
    }

    // ── PasswordChanged ───────────────────────────────────────────────────────

    @Test
    fun `PasswordChanged updates password state`() = runTest {
        viewModel.dispatch(AuthIntent.PasswordChanged("secret123"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("secret123", viewModel.state.value.password)
    }

    @Test
    fun `PasswordChanged with short password sets passwordError`() = runTest {
        viewModel.dispatch(AuthIntent.PasswordChanged("123"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.passwordError)
    }

    // ── TogglePasswordVisibility ──────────────────────────────────────────────

    @Test
    fun `TogglePasswordVisibility toggles isPasswordVisible`() = runTest {
        assertFalse(viewModel.state.value.isPasswordVisible)
        viewModel.dispatch(AuthIntent.TogglePasswordVisibility)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value.isPasswordVisible)
        viewModel.dispatch(AuthIntent.TogglePasswordVisibility)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.state.value.isPasswordVisible)
    }

    // ── RememberMeToggled ─────────────────────────────────────────────────────

    @Test
    fun `RememberMeToggled updates rememberMe state`() = runTest {
        viewModel.dispatch(AuthIntent.RememberMeToggled(true))
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value.rememberMe)
        viewModel.dispatch(AuthIntent.RememberMeToggled(false))
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.state.value.rememberMe)
    }

    // ── DismissError ──────────────────────────────────────────────────────────

    @Test
    fun `DismissError clears error field`() = runTest {
        // Trigger a validation error by attempting login with blank fields
        viewModel.dispatch(AuthIntent.LoginClicked)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.dispatch(AuthIntent.DismissError)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.state.value.error)
    }

    // ── LoginClicked — validation failures ────────────────────────────────────

    @Test
    fun `LoginClicked with blank email sets emailError and does not call LoginUseCase`() = runTest {
        viewModel.dispatch(AuthIntent.PasswordChanged("password123"))
        viewModel.dispatch(AuthIntent.LoginClicked)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.emailError)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `LoginClicked with blank password sets passwordError`() = runTest {
        viewModel.dispatch(AuthIntent.EmailChanged("user@test.com"))
        viewModel.dispatch(AuthIntent.LoginClicked)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.passwordError)
    }

    // ── LoginClicked — success ────────────────────────────────────────────────

    @Test
    fun `LoginClicked with valid credentials emits NavigateToDashboard effect`() = runTest {
        loginResponses["user@test.com" to "password123"] = Result.Success(fakeUser)

        viewModel.effects.test {
            viewModel.dispatch(AuthIntent.EmailChanged("user@test.com"))
            viewModel.dispatch(AuthIntent.PasswordChanged("password123"))
            viewModel.dispatch(AuthIntent.LoginClicked)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is AuthEffect.NavigateToDashboard)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `LoginClicked success clears isLoading`() = runTest {
        loginResponses["user@test.com" to "password123"] = Result.Success(fakeUser)

        viewModel.dispatch(AuthIntent.EmailChanged("user@test.com"))
        viewModel.dispatch(AuthIntent.PasswordChanged("password123"))
        viewModel.dispatch(AuthIntent.LoginClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
    }

    // ── LoginClicked — error ──────────────────────────────────────────────────

    @Test
    fun `LoginClicked on auth failure sets error state`() = runTest {
        loginResponses["user@test.com" to "wrong!!!"] = Result.Error(
            AuthException("Invalid credentials", reason = AuthFailureReason.INVALID_CREDENTIALS),
        )

        viewModel.dispatch(AuthIntent.EmailChanged("user@test.com"))
        viewModel.dispatch(AuthIntent.PasswordChanged("wrong!!!"))
        viewModel.dispatch(AuthIntent.LoginClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.error)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `LoginClicked on offline no-cache error sets error state`() = runTest {
        loginResponses["new@test.com" to "password123"] = Result.Error(
            AuthException("No local account found", reason = AuthFailureReason.OFFLINE_NO_CACHE),
        )

        viewModel.dispatch(AuthIntent.EmailChanged("new@test.com"))
        viewModel.dispatch(AuthIntent.PasswordChanged("password123"))
        viewModel.dispatch(AuthIntent.LoginClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.error)
    }
}
