package com.zyntasolutions.zyntapos.feature.auth

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Role
import com.zyntasolutions.zyntapos.domain.model.User
import com.zyntasolutions.zyntapos.domain.repository.UserRepository
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
 * Unit tests for [SignUpViewModel].
 *
 * Uses hand-rolled fakes instead of Mockative (KSP1 is incompatible with Kotlin 2.3+).
 * All tests run under [StandardTestDispatcher] for deterministic coroutine execution.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SignUpViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    // ── Fake UserRepository ────────────────────────────────────────────────────

    private var createResult: Result<Unit> = Result.Success(Unit)
    private var createCalledWith: Pair<User, String>? = null

    private val fakeUserRepository = object : UserRepository {
        override fun getAll(storeId: String?): Flow<List<User>> = MutableStateFlow(emptyList())

        override suspend fun getById(id: String): Result<User> =
            Result.Error(DatabaseException("Not found"))

        override suspend fun create(user: User, plainPassword: String): Result<Unit> {
            createCalledWith = user to plainPassword
            return createResult
        }

        override suspend fun update(user: User): Result<Unit> = Result.Success(Unit)

        override suspend fun updatePassword(
            userId: String,
            newPlainPassword: String,
        ): Result<Unit> = Result.Success(Unit)

        override suspend fun deactivate(userId: String): Result<Unit> = Result.Success(Unit)
    }

    private lateinit var viewModel: SignUpViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        createResult = Result.Success(Unit)
        createCalledWith = null
        viewModel = SignUpViewModel(userRepository = fakeUserRepository)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun `initial state has all fields blank and no errors`() = runTest {
        val state = viewModel.state.value
        assertEquals("", state.name)
        assertEquals("", state.email)
        assertEquals("", state.password)
        assertEquals("", state.confirmPassword)
        assertNull(state.nameError)
        assertNull(state.emailError)
        assertNull(state.passwordError)
        assertNull(state.confirmPasswordError)
        assertFalse(state.isLoading)
        assertFalse(state.isPasswordVisible)
        assertNull(state.error)
    }

    // ── NameChanged ───────────────────────────────────────────────────────────

    @Test
    fun `NameChanged updates name in state`() = runTest {
        viewModel.dispatch(SignUpIntent.NameChanged("Alice"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("Alice", viewModel.state.value.name)
    }

    @Test
    fun `NameChanged clears nameError`() = runTest {
        // First trigger a name error by submitting with blank name
        viewModel.dispatch(SignUpIntent.SignUpClicked)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.nameError)

        // Typing a new name clears the error
        viewModel.dispatch(SignUpIntent.NameChanged("Alice"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.state.value.nameError)
    }

    @Test
    fun `NameChanged clears top-level error`() = runTest {
        createResult = Result.Error(DatabaseException("Server error"))
        viewModel.dispatch(SignUpIntent.NameChanged("Alice"))
        viewModel.dispatch(SignUpIntent.EmailChanged("alice@test.com"))
        viewModel.dispatch(SignUpIntent.PasswordChanged("secret123"))
        viewModel.dispatch(SignUpIntent.ConfirmPasswordChanged("secret123"))
        viewModel.dispatch(SignUpIntent.SignUpClicked)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.error)

        viewModel.dispatch(SignUpIntent.NameChanged("Alice Updated"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.state.value.error)
    }

    // ── EmailChanged ──────────────────────────────────────────────────────────

    @Test
    fun `EmailChanged updates email in state`() = runTest {
        viewModel.dispatch(SignUpIntent.EmailChanged("alice@test.com"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("alice@test.com", viewModel.state.value.email)
    }

    @Test
    fun `EmailChanged with invalid email sets emailError`() = runTest {
        viewModel.dispatch(SignUpIntent.EmailChanged("notanemail"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.emailError)
    }

    @Test
    fun `EmailChanged with valid email clears emailError`() = runTest {
        viewModel.dispatch(SignUpIntent.EmailChanged("notanemail"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.emailError)

        viewModel.dispatch(SignUpIntent.EmailChanged("valid@test.com"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.state.value.emailError)
    }

    @Test
    fun `EmailChanged with blank email does not set emailError inline`() = runTest {
        // The ViewModel only validates inline when the email is not blank
        viewModel.dispatch(SignUpIntent.EmailChanged(""))
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.state.value.emailError)
    }

    // ── PasswordChanged ───────────────────────────────────────────────────────

    @Test
    fun `PasswordChanged updates password in state`() = runTest {
        viewModel.dispatch(SignUpIntent.PasswordChanged("secret123"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("secret123", viewModel.state.value.password)
    }

    @Test
    fun `PasswordChanged with short password sets passwordError`() = runTest {
        viewModel.dispatch(SignUpIntent.PasswordChanged("123"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.passwordError)
    }

    @Test
    fun `PasswordChanged with valid password clears passwordError`() = runTest {
        viewModel.dispatch(SignUpIntent.PasswordChanged("123"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.passwordError)

        viewModel.dispatch(SignUpIntent.PasswordChanged("validpassword"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.state.value.passwordError)
    }

    @Test
    fun `PasswordChanged with blank password does not set passwordError inline`() = runTest {
        viewModel.dispatch(SignUpIntent.PasswordChanged(""))
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.state.value.passwordError)
    }

    // ── ConfirmPasswordChanged ────────────────────────────────────────────────

    @Test
    fun `ConfirmPasswordChanged updates confirmPassword in state`() = runTest {
        viewModel.dispatch(SignUpIntent.PasswordChanged("secret123"))
        viewModel.dispatch(SignUpIntent.ConfirmPasswordChanged("secret123"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("secret123", viewModel.state.value.confirmPassword)
    }

    @Test
    fun `ConfirmPasswordChanged with mismatched password sets confirmPasswordError`() = runTest {
        viewModel.dispatch(SignUpIntent.PasswordChanged("secret123"))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.dispatch(SignUpIntent.ConfirmPasswordChanged("different"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.confirmPasswordError)
    }

    @Test
    fun `ConfirmPasswordChanged matching password clears confirmPasswordError`() = runTest {
        viewModel.dispatch(SignUpIntent.PasswordChanged("secret123"))
        viewModel.dispatch(SignUpIntent.ConfirmPasswordChanged("different"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.confirmPasswordError)

        viewModel.dispatch(SignUpIntent.ConfirmPasswordChanged("secret123"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.state.value.confirmPasswordError)
    }

    @Test
    fun `ConfirmPasswordChanged with blank value does not set confirmPasswordError inline`() = runTest {
        viewModel.dispatch(SignUpIntent.PasswordChanged("secret123"))
        viewModel.dispatch(SignUpIntent.ConfirmPasswordChanged(""))
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.state.value.confirmPasswordError)
    }

    // ── TogglePasswordVisibility ──────────────────────────────────────────────

    @Test
    fun `TogglePasswordVisibility toggles isPasswordVisible from false to true`() = runTest {
        assertFalse(viewModel.state.value.isPasswordVisible)
        viewModel.dispatch(SignUpIntent.TogglePasswordVisibility)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value.isPasswordVisible)
    }

    @Test
    fun `TogglePasswordVisibility toggles isPasswordVisible back to false`() = runTest {
        viewModel.dispatch(SignUpIntent.TogglePasswordVisibility)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value.isPasswordVisible)

        viewModel.dispatch(SignUpIntent.TogglePasswordVisibility)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.state.value.isPasswordVisible)
    }

    // ── SignUpClicked — validation failures ───────────────────────────────────

    @Test
    fun `SignUpClicked with all blank fields sets all field errors`() = runTest {
        viewModel.dispatch(SignUpIntent.SignUpClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertNotNull(state.nameError)
        assertNotNull(state.emailError)
        assertNotNull(state.passwordError)
        assertNotNull(state.confirmPasswordError)
    }

    @Test
    fun `SignUpClicked with blank fields does not set isLoading`() = runTest {
        viewModel.dispatch(SignUpIntent.SignUpClicked)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `SignUpClicked with invalid email sets emailError`() = runTest {
        viewModel.dispatch(SignUpIntent.NameChanged("Alice"))
        viewModel.dispatch(SignUpIntent.EmailChanged("notanemail"))
        viewModel.dispatch(SignUpIntent.PasswordChanged("secret123"))
        viewModel.dispatch(SignUpIntent.ConfirmPasswordChanged("secret123"))
        viewModel.dispatch(SignUpIntent.SignUpClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.emailError)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `SignUpClicked with short password sets passwordError`() = runTest {
        viewModel.dispatch(SignUpIntent.NameChanged("Alice"))
        viewModel.dispatch(SignUpIntent.EmailChanged("alice@test.com"))
        viewModel.dispatch(SignUpIntent.PasswordChanged("123"))
        viewModel.dispatch(SignUpIntent.ConfirmPasswordChanged("123"))
        viewModel.dispatch(SignUpIntent.SignUpClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.passwordError)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `SignUpClicked with mismatched passwords sets confirmPasswordError`() = runTest {
        viewModel.dispatch(SignUpIntent.NameChanged("Alice"))
        viewModel.dispatch(SignUpIntent.EmailChanged("alice@test.com"))
        viewModel.dispatch(SignUpIntent.PasswordChanged("secret123"))
        viewModel.dispatch(SignUpIntent.ConfirmPasswordChanged("different456"))
        viewModel.dispatch(SignUpIntent.SignUpClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.confirmPasswordError)
        assertFalse(viewModel.state.value.isLoading)
    }

    // ── SignUpClicked — success ───────────────────────────────────────────────

    @Test
    fun `SignUpClicked with valid fields emits NavigateToLogin effect`() = runTest {
        createResult = Result.Success(Unit)

        viewModel.effects.test {
            viewModel.dispatch(SignUpIntent.NameChanged("Alice Smith"))
            viewModel.dispatch(SignUpIntent.EmailChanged("alice@test.com"))
            viewModel.dispatch(SignUpIntent.PasswordChanged("secret123"))
            viewModel.dispatch(SignUpIntent.ConfirmPasswordChanged("secret123"))
            viewModel.dispatch(SignUpIntent.SignUpClicked)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is SignUpEffect.NavigateToLogin, "Expected NavigateToLogin effect, got: $effect")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `SignUpClicked success clears isLoading`() = runTest {
        createResult = Result.Success(Unit)

        viewModel.dispatch(SignUpIntent.NameChanged("Alice Smith"))
        viewModel.dispatch(SignUpIntent.EmailChanged("alice@test.com"))
        viewModel.dispatch(SignUpIntent.PasswordChanged("secret123"))
        viewModel.dispatch(SignUpIntent.ConfirmPasswordChanged("secret123"))
        viewModel.dispatch(SignUpIntent.SignUpClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `SignUpClicked success passes correct password to repository`() = runTest {
        createResult = Result.Success(Unit)

        viewModel.dispatch(SignUpIntent.NameChanged("Alice Smith"))
        viewModel.dispatch(SignUpIntent.EmailChanged("alice@test.com"))
        viewModel.dispatch(SignUpIntent.PasswordChanged("secret123"))
        viewModel.dispatch(SignUpIntent.ConfirmPasswordChanged("secret123"))
        viewModel.dispatch(SignUpIntent.SignUpClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(createCalledWith)
        assertEquals("secret123", createCalledWith!!.second)
    }

    @Test
    fun `SignUpClicked success passes correct user name and email to repository`() = runTest {
        createResult = Result.Success(Unit)

        viewModel.dispatch(SignUpIntent.NameChanged("Alice Smith"))
        viewModel.dispatch(SignUpIntent.EmailChanged("alice@test.com"))
        viewModel.dispatch(SignUpIntent.PasswordChanged("secret123"))
        viewModel.dispatch(SignUpIntent.ConfirmPasswordChanged("secret123"))
        viewModel.dispatch(SignUpIntent.SignUpClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        val createdUser = createCalledWith!!.first
        assertEquals("Alice Smith", createdUser.name)
        assertEquals("alice@test.com", createdUser.email)
    }

    @Test
    fun `SignUpClicked success creates user with ADMIN role`() = runTest {
        createResult = Result.Success(Unit)

        viewModel.dispatch(SignUpIntent.NameChanged("Alice Smith"))
        viewModel.dispatch(SignUpIntent.EmailChanged("alice@test.com"))
        viewModel.dispatch(SignUpIntent.PasswordChanged("secret123"))
        viewModel.dispatch(SignUpIntent.ConfirmPasswordChanged("secret123"))
        viewModel.dispatch(SignUpIntent.SignUpClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(Role.ADMIN, createCalledWith!!.first.role)
    }

    @Test
    fun `SignUpClicked success clears error field`() = runTest {
        createResult = Result.Success(Unit)

        viewModel.dispatch(SignUpIntent.NameChanged("Alice Smith"))
        viewModel.dispatch(SignUpIntent.EmailChanged("alice@test.com"))
        viewModel.dispatch(SignUpIntent.PasswordChanged("secret123"))
        viewModel.dispatch(SignUpIntent.ConfirmPasswordChanged("secret123"))
        viewModel.dispatch(SignUpIntent.SignUpClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.error)
    }

    // ── SignUpClicked — error ─────────────────────────────────────────────────

    @Test
    fun `SignUpClicked on repository error sets error in state`() = runTest {
        createResult = Result.Error(DatabaseException("Email already in use"))

        viewModel.dispatch(SignUpIntent.NameChanged("Alice Smith"))
        viewModel.dispatch(SignUpIntent.EmailChanged("alice@test.com"))
        viewModel.dispatch(SignUpIntent.PasswordChanged("secret123"))
        viewModel.dispatch(SignUpIntent.ConfirmPasswordChanged("secret123"))
        viewModel.dispatch(SignUpIntent.SignUpClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.error)
    }

    @Test
    fun `SignUpClicked on repository error clears isLoading`() = runTest {
        createResult = Result.Error(DatabaseException("Email already in use"))

        viewModel.dispatch(SignUpIntent.NameChanged("Alice Smith"))
        viewModel.dispatch(SignUpIntent.EmailChanged("alice@test.com"))
        viewModel.dispatch(SignUpIntent.PasswordChanged("secret123"))
        viewModel.dispatch(SignUpIntent.ConfirmPasswordChanged("secret123"))
        viewModel.dispatch(SignUpIntent.SignUpClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `SignUpClicked on repository error does not emit NavigateToLogin`() = runTest {
        createResult = Result.Error(DatabaseException("Registration failed"))

        viewModel.effects.test {
            viewModel.dispatch(SignUpIntent.NameChanged("Alice Smith"))
            viewModel.dispatch(SignUpIntent.EmailChanged("alice@test.com"))
            viewModel.dispatch(SignUpIntent.PasswordChanged("secret123"))
            viewModel.dispatch(SignUpIntent.ConfirmPasswordChanged("secret123"))
            viewModel.dispatch(SignUpIntent.SignUpClicked)
            testDispatcher.scheduler.advanceUntilIdle()

            // The effects channel should be empty — no navigation emitted on failure
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `SignUpClicked on error with message sets that message in state`() = runTest {
        val errorMessage = "Email already in use"
        createResult = Result.Error(DatabaseException(errorMessage))

        viewModel.dispatch(SignUpIntent.NameChanged("Alice Smith"))
        viewModel.dispatch(SignUpIntent.EmailChanged("alice@test.com"))
        viewModel.dispatch(SignUpIntent.PasswordChanged("secret123"))
        viewModel.dispatch(SignUpIntent.ConfirmPasswordChanged("secret123"))
        viewModel.dispatch(SignUpIntent.SignUpClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(errorMessage, viewModel.state.value.error)
    }
}
