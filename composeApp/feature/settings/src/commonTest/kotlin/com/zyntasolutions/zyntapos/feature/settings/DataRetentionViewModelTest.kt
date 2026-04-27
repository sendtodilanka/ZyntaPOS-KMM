package com.zyntasolutions.zyntapos.feature.settings

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.DataRetentionPolicy
import com.zyntasolutions.zyntapos.domain.repository.SettingsRepository
import com.zyntasolutions.zyntapos.domain.usecase.settings.GetDataRetentionPolicyUseCase
import com.zyntasolutions.zyntapos.domain.usecase.settings.SaveDataRetentionPolicyUseCase
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

class DataRetentionViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(testDispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

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

    private fun newVm(repo: InMemorySettingsRepository = InMemorySettingsRepository()) = DataRetentionViewModel(
        getDataRetentionPolicyUseCase = GetDataRetentionPolicyUseCase(repo),
        saveDataRetentionPolicyUseCase = SaveDataRetentionPolicyUseCase(repo),
    ) to repo

    @Test
    fun `Load resolves to default policy when settings store is empty`() =
        runTest(UnconfinedTestDispatcher()) {
            val (vm, _) = newVm()
            vm.dispatch(DataRetentionIntent.Load)
            val state = vm.state.value
            assertEquals(DataRetentionPolicy(), state.policy)
            assertFalse(state.isLoading)
            assertNull(state.error)
        }

    @Test
    fun `Apply persists every field and applies optimistically`() =
        runTest(UnconfinedTestDispatcher()) {
            val (vm, repo) = newVm()
            vm.dispatch(DataRetentionIntent.Load)
            val target = DataRetentionPolicy(
                auditLogRetentionDays = 365,
                syncQueueRetentionDays = 7,
                reportRetentionMonths = 24,
            )

            vm.dispatch(DataRetentionIntent.Apply(target))

            assertEquals(target, vm.state.value.policy)
            assertEquals("365", repo.get("data_retention.audit_log_days"))
            assertEquals("7", repo.get("data_retention.sync_queue_days"))
            assertEquals("24", repo.get("data_retention.report_months"))
            assertNull(vm.state.value.error)
        }

    @Test
    fun `Apply with out-of-spec value rewinds optimistic update and surfaces error`() =
        runTest(UnconfinedTestDispatcher()) {
            val (vm, repo) = newVm()
            vm.dispatch(DataRetentionIntent.Load)
            val original = vm.state.value.policy

            vm.dispatch(
                DataRetentionIntent.Apply(original.copy(auditLogRetentionDays = 1000)),
            )

            assertEquals(original, vm.state.value.policy, "State must rewind on validation rejection.")
            assertNotNull(vm.state.value.error, "Error message should be surfaced.")
            assertNull(repo.get("data_retention.audit_log_days"))
        }
}
