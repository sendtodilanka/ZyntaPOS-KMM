package com.zyntasolutions.zyntapos.feature.diagnostic

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.DiagnosticDataScope
import com.zyntasolutions.zyntapos.domain.model.DiagnosticSession
import com.zyntasolutions.zyntapos.domain.model.DiagnosticSessionStatus
import com.zyntasolutions.zyntapos.domain.repository.DiagnosticConsentRepository
import com.zyntasolutions.zyntapos.security.auth.DiagnosticClaims
import com.zyntasolutions.zyntapos.security.auth.DiagnosticTokenValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ─────────────────────────────────────────────────────────────────────────────
// DiagnosticViewModelTest
// Tests consent flow state transitions for the diagnostic session screen.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    // ── Fake token validator ──────────────────────────────────────────────────

    private var tokenResult: Result<DiagnosticClaims> = Result.Error(
        DatabaseException("token not set"),
    )

    private val fakeTokenValidator = object : DiagnosticTokenValidator() {
        override fun validateToken(token: String): Result<DiagnosticClaims> = tokenResult
    }

    // ── Fake consent repository ───────────────────────────────────────────────

    private var grantCalled = false
    private var revokeCalled = false
    private var throwOnGrant = false
    private var throwOnRevoke = false

    private val fakeConsentRepository = object : DiagnosticConsentRepository {
        override suspend fun grantConsent(sessionId: String, grantedAtMs: Long) {
            if (throwOnGrant) throw Exception("Network error")
            grantCalled = true
        }

        override suspend fun revokeConsent(sessionId: String) {
            if (throwOnRevoke) throw Exception("Network error")
            revokeCalled = true
        }
    }

    private fun makeViewModel() = DiagnosticViewModel(
        tokenValidator = fakeTokenValidator,
        consentRepository = fakeConsentRepository,
    )

    private val validClaims = DiagnosticClaims(
        sessionId = "session-001",
        technicianId = "tech-001",
        storeId = "store-001",
        scope = "FULL_READ_ONLY",
        exp = (System.currentTimeMillis() / 1000) + 900L,
        iat = (System.currentTimeMillis() / 1000),
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── loadToken tests ───────────────────────────────────────────────────────

    @Test
    fun `loadToken with valid token populates pendingSession`() = runTest {
        tokenResult = Result.Success(validClaims)
        val vm = makeViewModel()

        vm.state.test {
            awaitItem() // initial

            vm.loadToken("valid.token.here")
            testDispatcher.scheduler.advanceUntilIdle()

            val loading = awaitItem()
            assertTrue(loading.isLoading)

            val loaded = awaitItem()
            assertFalse(loaded.isLoading)
            assertNotNull(loaded.pendingSession)
            assertEquals("session-001", loaded.pendingSession!!.id)
            assertEquals("store-001", loaded.pendingSession!!.storeId)
            assertEquals(DiagnosticDataScope.FULL_READ_ONLY, loaded.pendingSession!!.dataScope)
            assertEquals(DiagnosticSessionStatus.PENDING_CONSENT, loaded.pendingSession!!.status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadToken with invalid token sets errorMessage`() = runTest {
        tokenResult = Result.Error(DatabaseException("Token expired"))
        val vm = makeViewModel()

        vm.state.test {
            awaitItem() // initial

            vm.loadToken("invalid.token")
            testDispatcher.scheduler.advanceUntilIdle()

            val loading = awaitItem()
            assertTrue(loading.isLoading)

            val errored = awaitItem()
            assertFalse(errored.isLoading)
            assertNull(errored.pendingSession)
            assertEquals("Token expired", errored.errorMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadToken with invalid token sends ShowError effect`() = runTest {
        tokenResult = Result.Error(DatabaseException("Token expired"))
        val vm = makeViewModel()

        vm.effects.test {
            vm.loadToken("invalid.token")
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is DiagnosticEffect.ShowError)
            assertEquals("Token expired", (effect as DiagnosticEffect.ShowError).message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── AcceptConsent intent tests ────────────────────────────────────────────

    @Test
    fun `AcceptConsent when no pending session does nothing`() = runTest {
        val vm = makeViewModel()
        vm.dispatch(DiagnosticIntent.AcceptConsent)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(grantCalled)
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun `AcceptConsent with valid session calls grantConsent`() = runTest {
        tokenResult = Result.Success(validClaims)
        val vm = makeViewModel()

        vm.loadToken("valid.token")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.state.test {
            awaitItem() // current state with session
            vm.dispatch(DiagnosticIntent.AcceptConsent)
            testDispatcher.scheduler.advanceUntilIdle()

            val loading = awaitItem()
            assertTrue(loading.isLoading)

            val done = awaitItem()
            assertFalse(done.isLoading)
            assertNull(done.pendingSession)
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(grantCalled)
    }

    @Test
    fun `AcceptConsent sends ConsentAccepted effect on success`() = runTest {
        tokenResult = Result.Success(validClaims)
        val vm = makeViewModel()

        vm.loadToken("valid.token")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.effects.test {
            vm.dispatch(DiagnosticIntent.AcceptConsent)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is DiagnosticEffect.ConsentAccepted)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `AcceptConsent sends ShowError effect when grantConsent throws`() = runTest {
        tokenResult = Result.Success(validClaims)
        throwOnGrant = true
        val vm = makeViewModel()

        vm.loadToken("valid.token")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.effects.test {
            vm.dispatch(DiagnosticIntent.AcceptConsent)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is DiagnosticEffect.ShowError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── DenyConsent intent tests ──────────────────────────────────────────────

    @Test
    fun `DenyConsent with valid session calls revokeConsent`() = runTest {
        tokenResult = Result.Success(validClaims)
        val vm = makeViewModel()

        vm.loadToken("valid.token")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.dispatch(DiagnosticIntent.DenyConsent)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(revokeCalled)
    }

    @Test
    fun `DenyConsent sends ConsentDenied effect on success`() = runTest {
        tokenResult = Result.Success(validClaims)
        val vm = makeViewModel()

        vm.loadToken("valid.token")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.effects.test {
            vm.dispatch(DiagnosticIntent.DenyConsent)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is DiagnosticEffect.ConsentDenied)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── DismissError intent tests ─────────────────────────────────────────────

    @Test
    fun `DismissError clears errorMessage`() = runTest {
        tokenResult = Result.Error(DatabaseException("Something went wrong"))
        val vm = makeViewModel()

        vm.loadToken("bad.token")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Something went wrong", vm.state.value.errorMessage)

        vm.dispatch(DiagnosticIntent.DismissError)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(vm.state.value.errorMessage)
    }

    // ── Initial state tests ───────────────────────────────────────────────────

    @Test
    fun `initial state has no pending session`() {
        val vm = makeViewModel()
        assertNull(vm.state.value.pendingSession)
        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun `READ_ONLY_DIAGNOSTICS scope is applied for unknown scope values`() = runTest {
        val unknownScopeClaims = validClaims.copy(scope = "UNKNOWN_SCOPE")
        tokenResult = Result.Success(unknownScopeClaims)
        val vm = makeViewModel()

        vm.loadToken("valid.token")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            DiagnosticDataScope.READ_ONLY_DIAGNOSTICS,
            vm.state.value.pendingSession?.dataScope,
        )
    }
}
