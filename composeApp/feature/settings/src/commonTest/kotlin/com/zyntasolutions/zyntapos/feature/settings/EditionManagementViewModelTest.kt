package com.zyntasolutions.zyntapos.feature.settings

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.FeatureConfig
import com.zyntasolutions.zyntapos.domain.model.ZyntaEdition
import com.zyntasolutions.zyntapos.domain.model.ZyntaFeature
import com.zyntasolutions.zyntapos.domain.repository.FeatureRegistryRepository
import com.zyntasolutions.zyntapos.domain.usecase.feature.GetAllFeatureConfigsUseCase
import com.zyntasolutions.zyntapos.domain.usecase.feature.SetFeatureEnabledUseCase
import com.zyntasolutions.zyntapos.feature.settings.edition.EditionManagementEffect
import com.zyntasolutions.zyntapos.feature.settings.edition.EditionManagementIntent
import com.zyntasolutions.zyntapos.feature.settings.edition.EditionManagementViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ─────────────────────────────────────────────────────────────────────────────
// EditionManagementViewModelTest
// Tests reactive feature-flag loading and toggle state transitions.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class EditionManagementViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    // ── Fake repository ───────────────────────────────────────────────────────

    private val configsFlow = MutableStateFlow<List<FeatureConfig>>(emptyList())
    private var toggleResult: Result<Unit> = Result.Success(Unit)

    private val fakeRepo = object : FeatureRegistryRepository {
        override fun observeAll(): Flow<List<FeatureConfig>> = configsFlow
        override fun observe(feature: ZyntaFeature): Flow<FeatureConfig> =
            MutableStateFlow(makeConfig(feature, true))

        override suspend fun isEnabled(feature: ZyntaFeature): Boolean = true
        override suspend fun setEnabled(feature: ZyntaFeature, enabled: Boolean, updatedAt: Long, expiresAt: Long?): Result<Unit> =
            toggleResult
        override suspend fun initDefaults(now: Long): Result<Unit> = Result.Success(Unit)
    }

    private fun makeConfig(feature: ZyntaFeature, enabled: Boolean) = FeatureConfig(
        feature = feature,
        isEnabled = enabled,
        activatedAt = null,
        expiresAt = null,
        updatedAt = System.currentTimeMillis(),
    )

    private val getAllUseCase = GetAllFeatureConfigsUseCase(fakeRepo)
    private val setEnabledUseCase = SetFeatureEnabledUseCase(fakeRepo)

    private fun makeViewModel() = EditionManagementViewModel(
        getAllFeatureConfigs = getAllUseCase,
        setFeatureEnabled = setEnabledUseCase,
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Initial load tests ────────────────────────────────────────────────────

    @Test
    fun `initial state has isLoading true`() {
        val vm = makeViewModel()
        assertTrue(vm.state.value.isLoading)
    }

    @Test
    fun `feature configs loaded from repository on init`() = runTest {
        val configs = listOf(
            makeConfig(ZyntaFeature.POS_CORE, true),
            makeConfig(ZyntaFeature.INVENTORY_ADVANCED, true),
        )
        configsFlow.value = configs

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, vm.state.value.featureConfigs.size)
        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun `state updates when repository emits new configs`() = runTest {
        val vm = makeViewModel()

        vm.state.test {
            awaitItem() // initial

            configsFlow.value = listOf(makeConfig(ZyntaFeature.POS_CORE, true))
            testDispatcher.scheduler.advanceUntilIdle()

            val updated = awaitItem()
            assertEquals(1, updated.featureConfigs.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── ToggleFeature intent tests ────────────────────────────────────────────

    @Test
    fun `ToggleFeature sends ShowSuccess effect on success`() = runTest {
        toggleResult = Result.Success(Unit)
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.effects.test {
            vm.dispatch(EditionManagementIntent.ToggleFeature(ZyntaFeature.MULTISTORE, true))
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is EditionManagementEffect.ShowSuccess)
            val msg = (effect as EditionManagementEffect.ShowSuccess).message
            assertTrue(msg.contains("enabled") || msg.contains("MULTI_STORE"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ToggleFeature sends ShowSuccess with disabled message when disabling`() = runTest {
        toggleResult = Result.Success(Unit)
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.effects.test {
            vm.dispatch(EditionManagementIntent.ToggleFeature(ZyntaFeature.MULTISTORE, false))
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is EditionManagementEffect.ShowSuccess)
            assertTrue((effect as EditionManagementEffect.ShowSuccess).message.contains("disabled"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ToggleFeature sends ShowError effect when use case returns error`() = runTest {
        toggleResult = Result.Error(
            ValidationException("STANDARD features cannot be disabled", "isEnabled", "STANDARD_GUARD"),
        )
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.effects.test {
            vm.dispatch(EditionManagementIntent.ToggleFeature(ZyntaFeature.POS_CORE, false))
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is EditionManagementEffect.ShowError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Load intent test ──────────────────────────────────────────────────────

    @Test
    fun `Load intent refreshes the feature config stream`() = runTest {
        configsFlow.value = listOf(makeConfig(ZyntaFeature.POS_CORE, true))
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, vm.state.value.featureConfigs.size)

        configsFlow.value = listOf(
            makeConfig(ZyntaFeature.POS_CORE, true),
            makeConfig(ZyntaFeature.INVENTORY_ADVANCED, true),
            makeConfig(ZyntaFeature.MULTISTORE, false),
        )
        vm.dispatch(EditionManagementIntent.Load)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(3, vm.state.value.featureConfigs.size)
    }

    // ── No error on load ──────────────────────────────────────────────────────

    @Test
    fun `no error when configs load successfully`() = runTest {
        configsFlow.value = listOf(makeConfig(ZyntaFeature.POS_CORE, true))
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(vm.state.value.errorMessage)
    }
}
