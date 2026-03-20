package com.zyntasolutions.zyntapos.feature.auth.license

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Edition
import com.zyntasolutions.zyntapos.domain.model.License
import com.zyntasolutions.zyntapos.domain.model.LicenseStatus
import com.zyntasolutions.zyntapos.domain.repository.LicenseRepository
import com.zyntasolutions.zyntapos.domain.usecase.license.ActivateLicenseUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.datetime.Instant
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

/**
 * Unit tests for [LicenseViewModel].
 *
 * Uses hand-rolled fakes for [LicenseRepository] / [ActivateLicenseUseCase].
 * All tests run under [StandardTestDispatcher].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LicenseViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val mockLicense = License(
        key = "ABCD-EFGH-IJKL-MNOP",
        deviceId = "device-001",
        customerId = "cust-001",
        edition = Edition.PROFESSIONAL,
        status = LicenseStatus.ACTIVE,
        issuedAt = Instant.fromEpochMilliseconds(1_700_000_000_000L),
        expiresAt = null,
        gracePeriodEndsAt = null,
        lastHeartbeatAt = null,
        maxDevices = 5,
    )

    // Hand-rolled fake repository
    private var shouldActivateSucceed = true
    private var activateError: Exception = Exception("Activation failed")

    private val fakeLicenseRepo = object : LicenseRepository {
        override suspend fun activate(
            licenseKey: String,
            deviceId: String,
            deviceName: String?,
            appVersion: String,
            osVersion: String?,
        ): Result<License> = if (shouldActivateSucceed) {
            Result.Success(mockLicense)
        } else {
            Result.Error(activateError)
        }

        override suspend fun sendHeartbeat(
            licenseKey: String,
            deviceId: String,
            appVersion: String,
            dbSizeBytes: Long,
            syncQueueDepth: Int,
            lastErrorCount: Int,
            uptimeHours: Double,
        ): Result<License> = Result.Success(mockLicense)

        override suspend fun getLocalLicense(): License? = null
        override suspend fun clearLocalLicense() {}
    }

    private lateinit var viewModel: LicenseViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = LicenseViewModel(
            activateLicenseUseCase = ActivateLicenseUseCase(fakeLicenseRepo),
            deviceId = "device-001",
            deviceName = "Test Tablet",
            appVersion = "1.0.0",
            osVersion = "Android 14",
        )
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ──────────────────────────────────────────────────────────

    @Test
    fun `initial state has empty license key and no error`() {
        val state = viewModel.currentState
        assertEquals("", state.licenseKey)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    // ── LicenseKeyChanged intent ───────────────────────────────────────────────

    @Test
    fun `LicenseKeyChanged updates licenseKey in state`() = runTest {
        viewModel.state.test {
            awaitItem() // initial state
            viewModel.handleIntentForTest(LicenseIntent.LicenseKeyChanged("AAAA-BBBB-CCCC-DDDD"))
            val updated = awaitItem()
            assertEquals("AAAA-BBBB-CCCC-DDDD", updated.licenseKey)
        }
    }

    @Test
    fun `LicenseKeyChanged clears existing error`() = runTest {
        // Set an error first via blank key activation
        viewModel.handleIntentForTest(LicenseIntent.ActivateClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.state.test {
            awaitItem() // state with error
            viewModel.handleIntentForTest(LicenseIntent.LicenseKeyChanged("AAAA"))
            val updated = awaitItem()
            assertNull(updated.error)
        }
    }

    // ── ActivateClicked — blank key ────────────────────────────────────────────

    @Test
    fun `ActivateClicked with blank key sets error message`() = runTest {
        viewModel.handleIntentForTest(LicenseIntent.LicenseKeyChanged(""))
        viewModel.handleIntentForTest(LicenseIntent.ActivateClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.currentState
        assertNotNull(state.error)
        assertTrue(state.error!!.contains("required", ignoreCase = true))
        assertFalse(state.isLoading)
    }

    @Test
    fun `ActivateClicked with whitespace-only key sets error`() = runTest {
        viewModel.handleIntentForTest(LicenseIntent.LicenseKeyChanged("   "))
        viewModel.handleIntentForTest(LicenseIntent.ActivateClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.currentState.error)
    }

    // ── ActivateClicked — invalid format ──────────────────────────────────────

    @Test
    fun `ActivateClicked with invalid format sets format error`() = runTest {
        viewModel.handleIntentForTest(LicenseIntent.LicenseKeyChanged("INVALID"))
        viewModel.handleIntentForTest(LicenseIntent.ActivateClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        val error = viewModel.currentState.error
        assertNotNull(error)
        assertTrue(error!!.contains("format", ignoreCase = true) || error.contains("XXXX", ignoreCase = true))
    }

    @Test
    fun `ActivateClicked with 3-segment key sets format error`() = runTest {
        viewModel.handleIntentForTest(LicenseIntent.LicenseKeyChanged("AAAA-BBBB-CCCC"))
        viewModel.handleIntentForTest(LicenseIntent.ActivateClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.currentState.error)
    }

    @Test
    fun `ActivateClicked with wrong segment length sets format error`() = runTest {
        viewModel.handleIntentForTest(LicenseIntent.LicenseKeyChanged("AAA-BBBB-CCCC-DDDD"))
        viewModel.handleIntentForTest(LicenseIntent.ActivateClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.currentState.error)
    }

    // ── ActivateClicked — success ──────────────────────────────────────────────

    @Test
    fun `ActivateClicked with valid key emits NavigateToMain effect on success`() = runTest {
        shouldActivateSucceed = true
        viewModel.handleIntentForTest(LicenseIntent.LicenseKeyChanged("ABCD-EFGH-IJKL-MNOP"))

        viewModel.effects.test {
            viewModel.handleIntentForTest(LicenseIntent.ActivateClicked)
            testDispatcher.scheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is LicenseEffect.NavigateToMain)
        }
    }

    @Test
    fun `ActivateClicked clears loading state after success`() = runTest {
        shouldActivateSucceed = true
        viewModel.handleIntentForTest(LicenseIntent.LicenseKeyChanged("ABCD-EFGH-IJKL-MNOP"))
        viewModel.handleIntentForTest(LicenseIntent.ActivateClicked)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.currentState.isLoading)
    }

    // ── ActivateClicked — failure ──────────────────────────────────────────────

    @Test
    fun `ActivateClicked sets error when activation fails`() = runTest {
        shouldActivateSucceed = false
        activateError = Exception("License expired")

        viewModel.handleIntentForTest(LicenseIntent.LicenseKeyChanged("ABCD-EFGH-IJKL-MNOP"))
        viewModel.handleIntentForTest(LicenseIntent.ActivateClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.currentState
        assertNotNull(state.error)
        assertTrue(state.error!!.contains("expired", ignoreCase = true))
        assertFalse(state.isLoading)
    }

    @Test
    fun `ActivateClicked uses fallback message when exception has no message`() = runTest {
        shouldActivateSucceed = false
        activateError = Exception()

        viewModel.handleIntentForTest(LicenseIntent.LicenseKeyChanged("ABCD-EFGH-IJKL-MNOP"))
        viewModel.handleIntentForTest(LicenseIntent.ActivateClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.currentState
        assertNotNull(state.error)
        assertTrue(state.error!!.isNotBlank())
    }

    // ── DismissError intent ────────────────────────────────────────────────────

    @Test
    fun `DismissError clears error from state`() = runTest {
        // Set an error first
        viewModel.handleIntentForTest(LicenseIntent.ActivateClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.state.test {
            awaitItem() // state with error
            viewModel.handleIntentForTest(LicenseIntent.DismissError)
            val cleared = awaitItem()
            assertNull(cleared.error)
        }
    }

    @Test
    fun `DismissError when no error is a no-op`() = runTest {
        viewModel.handleIntentForTest(LicenseIntent.DismissError)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.currentState.error)
    }
}

// ─── Extension to expose handleIntent for testing ────────────────────────────

private suspend fun LicenseViewModel.handleIntentForTest(intent: LicenseIntent) =
    handleIntent(intent)
