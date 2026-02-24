package com.zyntasolutions.zyntapos.feature.onboarding

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.repository.SettingsRepository
import com.zyntasolutions.zyntapos.domain.repository.UserRepository
import com.zyntasolutions.zyntapos.feature.onboarding.mvi.OnboardingEffect
import com.zyntasolutions.zyntapos.feature.onboarding.mvi.OnboardingIntent
import com.zyntasolutions.zyntapos.feature.onboarding.mvi.OnboardingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
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
 * Unit tests for [OnboardingViewModel].
 *
 * Tests cover:
 * - Initial state
 * - Step 1 (business name) validation and advancement
 * - Step 2 (admin account) field validation
 * - Back navigation between steps
 * - Successful completion (persists settings + creates user + navigates)
 * - Error handling (settings failure, user creation failure)
 * - Toggle password visibility
 * - Dismiss error
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    // ── Fake SettingsRepository ────────────────────────────────────────────────

    private val settingsStore = mutableMapOf<String, String>()
    private var settingsSetError: DatabaseException? = null

    private val fakeSettingsRepository = object : SettingsRepository {
        private val store = MutableStateFlow(settingsStore.toMap())
        override suspend fun get(key: String): String? = settingsStore[key]
        override suspend fun set(key: String, value: String): Result<Unit> {
            settingsSetError?.let { return Result.Error(it) }
            settingsStore[key] = value
            store.value = settingsStore.toMap()
            return Result.Success(Unit)
        }
        override suspend fun getAll(): Map<String, String> = settingsStore.toMap()
        override fun observe(key: String): Flow<String?> = store.map { it[key] }
    }

    // ── Fake UserRepository ────────────────────────────────────────────────────

    private val createdUsers = mutableListOf<Pair<User, String>>()
    private var userCreateError: DatabaseException? = null

    private val fakeUserRepository = object : UserRepository {
        override fun getAll(storeId: String?): Flow<List<User>> = MutableStateFlow(emptyList())
        override suspend fun getById(id: String): Result<User> = Result.Error(DatabaseException("Not found"))
        override suspend fun create(user: User, plainPassword: String): Result<Unit> {
            userCreateError?.let { return Result.Error(it) }
            createdUsers.add(user to plainPassword)
            return Result.Success(Unit)
        }
        override suspend fun update(user: User): Result<Unit> = Result.Success(Unit)
        override suspend fun updatePassword(userId: String, newPlainPassword: String): Result<Unit> = Result.Success(Unit)
        override suspend fun deactivate(userId: String): Result<Unit> = Result.Success(Unit)
    }

    private lateinit var viewModel: OnboardingViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        settingsStore.clear()
        createdUsers.clear()
        settingsSetError = null
        userCreateError = null
        viewModel = OnboardingViewModel(
            userRepository = fakeUserRepository,
            settingsRepository = fakeSettingsRepository,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ──────────────────────────────────────────────────────────

    @Test
    fun `initial state starts at BUSINESS_INFO step`() = runTest {
        assertEquals(OnboardingState.Step.BUSINESS_INFO, viewModel.state.value.currentStep)
    }

    @Test
    fun `initial state has all fields empty`() = runTest {
        val s = viewModel.state.value
        assertEquals("", s.businessName)
        assertEquals("", s.adminName)
        assertEquals("", s.adminEmail)
        assertEquals("", s.adminPassword)
        assertEquals("", s.adminConfirmPassword)
    }

    @Test
    fun `initial state is not loading`() = runTest {
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `isLastStep is false on BUSINESS_INFO step`() = runTest {
        assertFalse(viewModel.state.value.isLastStep)
    }

    // ── Step 1: Business name field validation ─────────────────────────────────

    @Test
    fun `BusinessNameChanged updates businessName`() = runTest {
        viewModel.dispatch(OnboardingIntent.BusinessNameChanged("Acme Corp"))
        advanceUntilIdle()
        assertEquals("Acme Corp", viewModel.state.value.businessName)
    }

    @Test
    fun `BusinessNameChanged clears error when name is valid`() = runTest {
        viewModel.dispatch(OnboardingIntent.BusinessNameChanged("x"))
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.businessNameError)

        viewModel.dispatch(OnboardingIntent.BusinessNameChanged("Acme Corp"))
        advanceUntilIdle()
        assertNull(viewModel.state.value.businessNameError)
    }

    @Test
    fun `BusinessNameChanged shows error for name shorter than 2 chars`() = runTest {
        viewModel.dispatch(OnboardingIntent.BusinessNameChanged("x"))
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.businessNameError)
    }

    @Test
    fun `BusinessNameChanged shows no error for empty name (shown on submit)`() = runTest {
        viewModel.dispatch(OnboardingIntent.BusinessNameChanged(""))
        advanceUntilIdle()
        // Empty is only validated on NextStep, not on field change
        assertNull(viewModel.state.value.businessNameError)
    }

    // ── Step 1: Next step validation ─────────────────────────────────────────

    @Test
    fun `NextStep shows error when business name is blank`() = runTest {
        viewModel.dispatch(OnboardingIntent.NextStep)
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.businessNameError)
        assertEquals(OnboardingState.Step.BUSINESS_INFO, viewModel.state.value.currentStep)
    }

    @Test
    fun `NextStep shows error when business name is too short`() = runTest {
        viewModel.dispatch(OnboardingIntent.BusinessNameChanged("A"))
        viewModel.dispatch(OnboardingIntent.NextStep)
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.businessNameError)
        assertEquals(OnboardingState.Step.BUSINESS_INFO, viewModel.state.value.currentStep)
    }

    @Test
    fun `NextStep shows error when business name exceeds 100 chars`() = runTest {
        viewModel.dispatch(OnboardingIntent.BusinessNameChanged("A".repeat(101)))
        viewModel.dispatch(OnboardingIntent.NextStep)
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.businessNameError)
        assertEquals(OnboardingState.Step.BUSINESS_INFO, viewModel.state.value.currentStep)
    }

    @Test
    fun `NextStep advances to ADMIN_ACCOUNT when business name is valid`() = runTest {
        viewModel.dispatch(OnboardingIntent.BusinessNameChanged("Acme Corp"))
        viewModel.dispatch(OnboardingIntent.NextStep)
        advanceUntilIdle()
        assertEquals(OnboardingState.Step.ADMIN_ACCOUNT, viewModel.state.value.currentStep)
    }

    @Test
    fun `NextStep clears businessNameError on valid input`() = runTest {
        viewModel.dispatch(OnboardingIntent.BusinessNameChanged("Acme Corp"))
        viewModel.dispatch(OnboardingIntent.NextStep)
        advanceUntilIdle()
        assertNull(viewModel.state.value.businessNameError)
    }

    // ── Back navigation ────────────────────────────────────────────────────────

    @Test
    fun `BackStep on ADMIN_ACCOUNT navigates to BUSINESS_INFO`() = runTest {
        viewModel.dispatch(OnboardingIntent.BusinessNameChanged("Acme"))
        viewModel.dispatch(OnboardingIntent.NextStep)
        viewModel.dispatch(OnboardingIntent.BackStep)
        advanceUntilIdle()
        assertEquals(OnboardingState.Step.BUSINESS_INFO, viewModel.state.value.currentStep)
    }

    @Test
    fun `BackStep on BUSINESS_INFO is a no-op`() = runTest {
        viewModel.dispatch(OnboardingIntent.BackStep)
        advanceUntilIdle()
        assertEquals(OnboardingState.Step.BUSINESS_INFO, viewModel.state.value.currentStep)
    }

    // ── Step 2: Admin account field validation ────────────────────────────────

    @Test
    fun `AdminNameChanged updates adminName`() = runTest {
        viewModel.dispatch(OnboardingIntent.AdminNameChanged("Jane Smith"))
        advanceUntilIdle()
        assertEquals("Jane Smith", viewModel.state.value.adminName)
    }

    @Test
    fun `AdminNameChanged shows error for name shorter than 2 chars`() = runTest {
        viewModel.dispatch(OnboardingIntent.AdminNameChanged("J"))
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.adminNameError)
    }

    @Test
    fun `AdminEmailChanged updates adminEmail`() = runTest {
        viewModel.dispatch(OnboardingIntent.AdminEmailChanged("jane@example.com"))
        advanceUntilIdle()
        assertEquals("jane@example.com", viewModel.state.value.adminEmail)
    }

    @Test
    fun `AdminEmailChanged shows error for invalid email`() = runTest {
        viewModel.dispatch(OnboardingIntent.AdminEmailChanged("not-an-email"))
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.adminEmailError)
    }

    @Test
    fun `AdminEmailChanged clears error for valid email`() = runTest {
        viewModel.dispatch(OnboardingIntent.AdminEmailChanged("bad"))
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.adminEmailError)

        viewModel.dispatch(OnboardingIntent.AdminEmailChanged("good@email.com"))
        advanceUntilIdle()
        assertNull(viewModel.state.value.adminEmailError)
    }

    @Test
    fun `AdminPasswordChanged shows error for password under 8 chars`() = runTest {
        viewModel.dispatch(OnboardingIntent.AdminPasswordChanged("short"))
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.adminPasswordError)
    }

    @Test
    fun `AdminPasswordChanged clears error for valid password`() = runTest {
        viewModel.dispatch(OnboardingIntent.AdminPasswordChanged("ValidP@ss"))
        advanceUntilIdle()
        assertNull(viewModel.state.value.adminPasswordError)
    }

    @Test
    fun `AdminConfirmPasswordChanged shows error when passwords do not match`() = runTest {
        viewModel.dispatch(OnboardingIntent.AdminPasswordChanged("Password1!"))
        viewModel.dispatch(OnboardingIntent.AdminConfirmPasswordChanged("Different!"))
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.adminConfirmPasswordError)
    }

    @Test
    fun `AdminConfirmPasswordChanged clears error when passwords match`() = runTest {
        viewModel.dispatch(OnboardingIntent.AdminPasswordChanged("Password1!"))
        viewModel.dispatch(OnboardingIntent.AdminConfirmPasswordChanged("Password1!"))
        advanceUntilIdle()
        assertNull(viewModel.state.value.adminConfirmPasswordError)
    }

    @Test
    fun `TogglePasswordVisibility flips isPasswordVisible`() = runTest {
        assertFalse(viewModel.state.value.isPasswordVisible)
        viewModel.dispatch(OnboardingIntent.TogglePasswordVisibility)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isPasswordVisible)
        viewModel.dispatch(OnboardingIntent.TogglePasswordVisibility)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isPasswordVisible)
    }

    // ── Completion: validation ────────────────────────────────────────────────

    @Test
    fun `CompleteOnboarding shows all field errors when all fields are blank`() = runTest {
        viewModel.dispatch(OnboardingIntent.CompleteOnboarding)
        advanceUntilIdle()
        val s = viewModel.state.value
        assertNotNull(s.adminNameError)
        assertNotNull(s.adminEmailError)
        assertNotNull(s.adminPasswordError)
        assertNotNull(s.adminConfirmPasswordError)
    }

    @Test
    fun `CompleteOnboarding shows error when admin name is blank`() = runTest {
        fillAdminAccount(name = "")
        viewModel.dispatch(OnboardingIntent.CompleteOnboarding)
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.adminNameError)
    }

    @Test
    fun `CompleteOnboarding shows error when email is invalid`() = runTest {
        fillAdminAccount(email = "not-valid")
        viewModel.dispatch(OnboardingIntent.CompleteOnboarding)
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.adminEmailError)
    }

    @Test
    fun `CompleteOnboarding shows error when password is too short`() = runTest {
        fillAdminAccount(password = "short", confirmPassword = "short")
        viewModel.dispatch(OnboardingIntent.CompleteOnboarding)
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.adminPasswordError)
    }

    @Test
    fun `CompleteOnboarding shows error when passwords do not match`() = runTest {
        fillAdminAccount(password = "Password1!", confirmPassword = "Different!")
        viewModel.dispatch(OnboardingIntent.CompleteOnboarding)
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.adminConfirmPasswordError)
    }

    // ── Completion: success path ─────────────────────────────────────────────

    @Test
    fun `CompleteOnboarding creates admin user with correct role`() = runTest {
        setupForCompletion()
        viewModel.dispatch(OnboardingIntent.CompleteOnboarding)
        advanceUntilIdle()

        assertEquals(1, createdUsers.size)
        assertEquals(com.zyntasolutions.zyntapos.domain.model.Role.ADMIN, createdUsers[0].first.role)
    }

    @Test
    fun `CompleteOnboarding creates admin user with lowercased email`() = runTest {
        setupForCompletion(email = "Admin@Example.COM")
        viewModel.dispatch(OnboardingIntent.CompleteOnboarding)
        advanceUntilIdle()

        assertEquals("admin@example.com", createdUsers[0].first.email)
    }

    @Test
    fun `CompleteOnboarding creates admin user with trimmed business name`() = runTest {
        setupForCompletion(businessName = "  Acme Corp  ")
        viewModel.dispatch(OnboardingIntent.CompleteOnboarding)
        advanceUntilIdle()

        assertEquals("Acme Corp", settingsStore["general.business_name"])
    }

    @Test
    fun `CompleteOnboarding persists onboarding completed key`() = runTest {
        setupForCompletion()
        viewModel.dispatch(OnboardingIntent.CompleteOnboarding)
        advanceUntilIdle()

        assertEquals("true", settingsStore[OnboardingViewModel.ONBOARDING_COMPLETED_KEY])
    }

    @Test
    fun `CompleteOnboarding emits NavigateToLogin effect on success`() = runTest {
        setupForCompletion()

        viewModel.effects.test {
            viewModel.dispatch(OnboardingIntent.CompleteOnboarding)
            advanceUntilIdle()

            assertEquals(OnboardingEffect.NavigateToLogin, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `CompleteOnboarding clears isLoading on success`() = runTest {
        setupForCompletion()
        viewModel.dispatch(OnboardingIntent.CompleteOnboarding)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isLoading)
    }

    // ── Completion: error paths ───────────────────────────────────────────────

    @Test
    fun `CompleteOnboarding shows error when settings persistence fails`() = runTest {
        settingsSetError = DatabaseException("DB write error")
        setupForCompletion()
        viewModel.dispatch(OnboardingIntent.CompleteOnboarding)
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.error)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `CompleteOnboarding shows error when user creation fails`() = runTest {
        userCreateError = DatabaseException("Duplicate email")
        setupForCompletion()
        viewModel.dispatch(OnboardingIntent.CompleteOnboarding)
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.error)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `CompleteOnboarding does not emit NavigateToLogin when user creation fails`() = runTest {
        userCreateError = DatabaseException("Duplicate email")
        setupForCompletion()

        viewModel.effects.test {
            viewModel.dispatch(OnboardingIntent.CompleteOnboarding)
            advanceUntilIdle()

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── DismissError ──────────────────────────────────────────────────────────

    @Test
    fun `DismissError clears the error message`() = runTest {
        userCreateError = DatabaseException("Some error")
        setupForCompletion()
        viewModel.dispatch(OnboardingIntent.CompleteOnboarding)
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.error)

        viewModel.dispatch(OnboardingIntent.DismissError)
        advanceUntilIdle()
        assertNull(viewModel.state.value.error)
    }

    // ── isLastStep helper ─────────────────────────────────────────────────────

    @Test
    fun `isLastStep is true on ADMIN_ACCOUNT step`() = runTest {
        viewModel.dispatch(OnboardingIntent.BusinessNameChanged("Acme Corp"))
        viewModel.dispatch(OnboardingIntent.NextStep)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isLastStep)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun TestScope.setupForCompletion(
        businessName: String = "Acme Corp",
        name: String = "Jane Smith",
        email: String = "admin@acme.com",
        password: String = "SecureP@ss1",
        confirmPassword: String = "SecureP@ss1",
    ) {
        viewModel.dispatch(OnboardingIntent.BusinessNameChanged(businessName))
        viewModel.dispatch(OnboardingIntent.NextStep)
        advanceUntilIdle()
        fillAdminAccount(name, email, password, confirmPassword)
    }

    private suspend fun TestScope.fillAdminAccount(
        name: String = "Jane Smith",
        email: String = "admin@acme.com",
        password: String = "SecureP@ss1",
        confirmPassword: String = "SecureP@ss1",
    ) {
        viewModel.dispatch(OnboardingIntent.AdminNameChanged(name))
        viewModel.dispatch(OnboardingIntent.AdminEmailChanged(email))
        viewModel.dispatch(OnboardingIntent.AdminPasswordChanged(password))
        viewModel.dispatch(OnboardingIntent.AdminConfirmPasswordChanged(confirmPassword))
        advanceUntilIdle()
    }
}
