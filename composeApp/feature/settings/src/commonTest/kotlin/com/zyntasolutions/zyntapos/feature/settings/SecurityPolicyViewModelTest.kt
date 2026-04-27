package com.zyntasolutions.zyntapos.feature.settings

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.SecurityPolicy
import com.zyntasolutions.zyntapos.domain.repository.SettingsRepository
import com.zyntasolutions.zyntapos.domain.usecase.settings.GetSecurityPolicyUseCase
import com.zyntasolutions.zyntapos.domain.usecase.settings.SaveSecurityPolicyUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

class SecurityPolicyViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(testDispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    /** Mirrors the in-memory test double used by AuditPolicyViewModelTest. */
    private class InMemorySettingsRepository : SettingsRepository {
        private val store = mutableMapOf<String, String>()
        override suspend fun get(key: String): String? = store[key]
        override suspend fun set(key: String, value: String): Result<Unit> {
            store[key] = value
            return Result.Success(Unit)
        }
        override suspend fun getAll(): Map<String, String> = store.toMap()
        override fun observe(key: String): Flow<String?> = MutableStateFlow(store[key])
    }

    private fun newVm(repo: InMemorySettingsRepository = InMemorySettingsRepository()) = SecurityPolicyViewModel(
        getSecurityPolicyUseCase = GetSecurityPolicyUseCase(repo),
        saveSecurityPolicyUseCase = SaveSecurityPolicyUseCase(repo),
    ) to repo

    @Test
    fun `Load resolves to default policy when settings store is empty`() =
        runTest(UnconfinedTestDispatcher()) {
            val (vm, _) = newVm()
            vm.dispatch(SecurityPolicyIntent.Load)
            val state = vm.state.value
            assertEquals(SecurityPolicy(), state.policy)
            assertFalse(state.isLoading)
            assertNull(state.error)
        }

    @Test
    fun `Apply persists every field and applies optimistically`() =
        runTest(UnconfinedTestDispatcher()) {
            val (vm, repo) = newVm()
            vm.dispatch(SecurityPolicyIntent.Load)
            val target = SecurityPolicy(
                sessionTimeoutMinutes = 60,
                pinComplexity = SecurityPolicy.PinComplexity.ALPHANUMERIC,
                failedLoginLockoutAttempts = 3,
                lockoutDurationMinutes = 5,
                biometricEnabled = false,
            )

            vm.dispatch(SecurityPolicyIntent.Apply(target))

            assertEquals(target, vm.state.value.policy)
            assertEquals("60", repo.get("security.session_timeout_minutes"))
            assertEquals("ALPHANUMERIC", repo.get("security.pin_complexity"))
            assertEquals("3", repo.get("security.failed_login_lockout_attempts"))
            assertEquals("5", repo.get("security.lockout_duration_minutes"))
            assertEquals("false", repo.get("security.biometric_enabled"))
            assertNull(vm.state.value.error)
        }

    @Test
    fun `Apply with out-of-spec value rewinds optimistic update and surfaces error`() =
        runTest(UnconfinedTestDispatcher()) {
            val (vm, repo) = newVm()
            vm.dispatch(SecurityPolicyIntent.Load)
            val original = vm.state.value.policy

            vm.dispatch(
                SecurityPolicyIntent.Apply(original.copy(sessionTimeoutMinutes = 999)),
            )

            assertEquals(original, vm.state.value.policy, "State must rewind on validation rejection.")
            assertNotNull(vm.state.value.error, "Error message should be surfaced.")
            assertNull(repo.get("security.session_timeout_minutes"))
        }
}
