package com.zyntasolutions.zyntapos.feature.settings

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.AuditPolicy
import com.zyntasolutions.zyntapos.domain.repository.SettingsRepository
import com.zyntasolutions.zyntapos.domain.usecase.audit.GetAuditPolicyUseCase
import com.zyntasolutions.zyntapos.domain.usecase.audit.SetAuditPolicyEnabledUseCase
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
import kotlin.test.assertTrue

class AuditPolicyViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(testDispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    /**
     * In-memory test double. Mirrors `shared/domain/.../FakeSettingsRepository`
     * but is replicated inline because `composeApp:feature:settings:commonTest`
     * does not see other modules' test-source classpaths in this project's
     * Gradle setup.
     */
    private class InMemorySettingsRepository : SettingsRepository {
        private val store = mutableMapOf<String, String>()
        fun put(key: String, value: String) { store[key] = value }
        override suspend fun get(key: String): String? = store[key]
        override suspend fun set(key: String, value: String): Result<Unit> {
            store[key] = value
            return Result.Success(Unit)
        }
        override suspend fun getAll(): Map<String, String> = store.toMap()
        override fun observe(key: String): Flow<String?> = MutableStateFlow(store[key])
    }

    private fun newVm(repo: InMemorySettingsRepository = InMemorySettingsRepository()) = AuditPolicyViewModel(
        getAuditPolicyUseCase = GetAuditPolicyUseCase(repo),
        setAuditPolicyEnabledUseCase = SetAuditPolicyEnabledUseCase(repo),
    ) to repo

    @Test
    fun `Load resolves to all-enabled policy when settings store is empty`() =
        runTest(UnconfinedTestDispatcher()) {
            val (vm, _) = newVm()
            vm.dispatch(AuditPolicyIntent.Load)
            val state = vm.state.value
            assertEquals(AuditPolicy(), state.policy)
            assertFalse(state.isLoading)
            assertNull(state.error)
        }

    @Test
    fun `Toggle on a normal category persists and applies optimistically`() =
        runTest(UnconfinedTestDispatcher()) {
            val (vm, repo) = newVm()
            vm.dispatch(AuditPolicyIntent.Load)

            vm.dispatch(AuditPolicyIntent.Toggle(AuditPolicy.Category.PAYROLL))

            assertFalse(vm.state.value.policy.payroll)
            assertEquals("false", repo.get("audit.payroll.enabled"))
            assertNull(vm.state.value.error)
        }

    @Test
    fun `Toggle is reversible (off then on)`() = runTest(UnconfinedTestDispatcher()) {
        val (vm, repo) = newVm()
        vm.dispatch(AuditPolicyIntent.Load)

        vm.dispatch(AuditPolicyIntent.Toggle(AuditPolicy.Category.LOGIN))
        assertFalse(vm.state.value.policy.login)
        assertEquals("false", repo.get("audit.login.enabled"))

        vm.dispatch(AuditPolicyIntent.Toggle(AuditPolicy.Category.LOGIN))
        assertTrue(vm.state.value.policy.login)
        assertEquals("true", repo.get("audit.login.enabled"))
    }

    @Test
    fun `Toggling ROLE_CHANGES off rewinds the optimistic update and surfaces an error`() =
        runTest(UnconfinedTestDispatcher()) {
            val (vm, repo) = newVm()
            vm.dispatch(AuditPolicyIntent.Load)
            // Sanity: starts true.
            assertTrue(vm.state.value.policy.roleChanges)

            vm.dispatch(AuditPolicyIntent.Toggle(AuditPolicy.Category.ROLE_CHANGES))

            // State rewound to original (still true).
            assertTrue(
                vm.state.value.policy.roleChanges,
                "ROLE_CHANGES toggle must rewind on validation rejection.",
            )
            assertNotNull(vm.state.value.error, "Error message should be surfaced.")
            // Persistence side untouched — the use case rejected before writing.
            assertNull(repo.get("audit.role.enabled"))
        }
}
