package com.zyntasolutions.zyntapos.feature.settings

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.RegionalTaxOverride
import com.zyntasolutions.zyntapos.domain.model.TaxGroup
import com.zyntasolutions.zyntapos.domain.repository.RegionalTaxOverrideRepository
import com.zyntasolutions.zyntapos.domain.repository.TaxGroupRepository
import com.zyntasolutions.zyntapos.feature.settings.screen.RegionalTaxOverrideEffect
import com.zyntasolutions.zyntapos.feature.settings.screen.RegionalTaxOverrideIntent
import com.zyntasolutions.zyntapos.feature.settings.screen.RegionalTaxOverrideViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
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

// ─────────────────────────────────────────────────────────────────────────────
// RegionalTaxOverrideViewModelTest
// Tests MVI logic for per-store tax rate override management screen.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class RegionalTaxOverrideViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    // ── Fake repositories ─────────────────────────────────────────────────────

    private val taxGroup1 = TaxGroup(id = "tg1", name = "VAT", rate = 12.0)
    private val taxGroup2 = TaxGroup(id = "tg2", name = "Service Tax", rate = 5.0)

    private val taxGroupsFlow = MutableStateFlow<List<TaxGroup>>(listOf(taxGroup1, taxGroup2))

    private val fakeTaxGroupRepo = object : TaxGroupRepository {
        override fun getAll(): Flow<List<TaxGroup>> = taxGroupsFlow
        override suspend fun getById(id: String): Result<TaxGroup> =
            taxGroupsFlow.value.firstOrNull { it.id == id }
                ?.let { Result.Success(it) }
                ?: Result.Error(DatabaseException("Not found"))
        override suspend fun insert(taxGroup: TaxGroup): Result<Unit> = Result.Success(Unit)
        override suspend fun update(taxGroup: TaxGroup): Result<Unit> = Result.Success(Unit)
        override suspend fun delete(id: String): Result<Unit> = Result.Success(Unit)
    }

    private val overridesStore = MutableStateFlow<List<RegionalTaxOverride>>(emptyList())

    private val fakeTaxOverrideRepo = object : RegionalTaxOverrideRepository {
        override fun getOverridesForStore(storeId: String): Flow<List<RegionalTaxOverride>> =
            overridesStore.map { it.filter { o -> o.storeId == storeId && o.isActive } }
        override suspend fun getEffectiveOverride(taxGroupId: String, storeId: String, nowEpochMs: Long) =
            Result.Success<RegionalTaxOverride?>(null)
        override fun getOverridesForTaxGroup(taxGroupId: String): Flow<List<RegionalTaxOverride>> =
            overridesStore.map { it.filter { o -> o.taxGroupId == taxGroupId } }
        override suspend fun upsert(override: RegionalTaxOverride): Result<Unit> {
            overridesStore.value = overridesStore.value.filter { it.id != override.id } + override
            return Result.Success(Unit)
        }
        override suspend fun delete(id: String): Result<Unit> {
            overridesStore.value = overridesStore.value.filter { it.id != id }
            return Result.Success(Unit)
        }
    }

    private fun createViewModel() = RegionalTaxOverrideViewModel(
        taxOverrideRepository = fakeTaxOverrideRepo,
        taxGroupRepository = fakeTaxGroupRepo,
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── LoadOverrides ──────────────────────────────────────────────────────────

    @Test
    fun `A - LoadOverrides - sets storeId in state`() = runTest {
        val vm = createViewModel()
        vm.dispatch(RegionalTaxOverrideIntent.LoadOverrides("s1"))
        advanceUntilIdle()

        assertEquals("s1", vm.state.value.storeId)
    }

    @Test
    fun `B - LoadOverrides - populates taxGroups for dropdown`() = runTest {
        val vm = createViewModel()
        vm.dispatch(RegionalTaxOverrideIntent.LoadOverrides("s1"))
        advanceUntilIdle()

        val groups = vm.state.value.taxGroups
        assertEquals(2, groups.size)
        assertTrue(groups.any { it.id == "tg1" })
        assertTrue(groups.any { it.id == "tg2" })
    }

    @Test
    fun `C - LoadOverrides - populates overrides for store`() = runTest {
        overridesStore.value = listOf(
            RegionalTaxOverride(id = "o1", taxGroupId = "tg1", storeId = "s1", effectiveRate = 15.0),
            RegionalTaxOverride(id = "o2", taxGroupId = "tg2", storeId = "s2", effectiveRate = 8.0),
        )
        val vm = createViewModel()
        vm.dispatch(RegionalTaxOverrideIntent.LoadOverrides("s1"))
        advanceUntilIdle()

        val overrides = vm.state.value.overrides
        assertEquals(1, overrides.size)
        assertEquals("o1", overrides.first().id)
    }

    // ── OpenCreateForm / DismissForm ───────────────────────────────────────────

    @Test
    fun `D - OpenCreateForm - sets showForm=true with blank form`() = runTest {
        val vm = createViewModel()
        vm.dispatch(RegionalTaxOverrideIntent.LoadOverrides("s1"))
        advanceUntilIdle()

        vm.dispatch(RegionalTaxOverrideIntent.OpenCreateForm)
        advanceUntilIdle()

        val s = vm.state.value
        assertTrue(s.showForm)
        assertNull(s.editingOverrideId)
        assertTrue(s.formIsActive)
    }

    @Test
    fun `E - DismissForm - closes form and clears editingOverrideId`() = runTest {
        val vm = createViewModel()
        vm.dispatch(RegionalTaxOverrideIntent.LoadOverrides("s1"))
        advanceUntilIdle()
        vm.dispatch(RegionalTaxOverrideIntent.OpenCreateForm)
        advanceUntilIdle()

        vm.dispatch(RegionalTaxOverrideIntent.DismissForm)
        advanceUntilIdle()

        assertFalse(vm.state.value.showForm)
        assertNull(vm.state.value.editingOverrideId)
    }

    // ── OpenEditForm ──────────────────────────────────────────────────────────

    @Test
    fun `F - OpenEditForm - populates form fields from override`() = runTest {
        val override = RegionalTaxOverride(
            id = "o1", taxGroupId = "tg1", storeId = "s1",
            effectiveRate = 20.0, jurisdictionCode = "LK", taxRegistrationNumber = "VAT123",
        )
        val vm = createViewModel()
        vm.dispatch(RegionalTaxOverrideIntent.OpenEditForm(override))
        advanceUntilIdle()

        val s = vm.state.value
        assertTrue(s.showForm)
        assertEquals("o1", s.editingOverrideId)
        assertEquals("tg1", s.formTaxGroupId)
        assertEquals("20.0", s.formEffectiveRate)
        assertEquals("LK", s.formJurisdictionCode)
        assertEquals("VAT123", s.formTaxRegistrationNumber)
    }

    // ── Form field updates ─────────────────────────────────────────────────────

    @Test
    fun `G - UpdateFormTaxGroupId - updates state`() = runTest {
        val vm = createViewModel()
        vm.dispatch(RegionalTaxOverrideIntent.UpdateFormTaxGroupId("tg2"))
        advanceUntilIdle()

        assertEquals("tg2", vm.state.value.formTaxGroupId)
    }

    @Test
    fun `H - UpdateFormEffectiveRate - updates state`() = runTest {
        val vm = createViewModel()
        vm.dispatch(RegionalTaxOverrideIntent.UpdateFormEffectiveRate("18.5"))
        advanceUntilIdle()

        assertEquals("18.5", vm.state.value.formEffectiveRate)
    }

    @Test
    fun `I - UpdateFormIsActive - updates state`() = runTest {
        val vm = createViewModel()
        vm.dispatch(RegionalTaxOverrideIntent.UpdateFormIsActive(false))
        advanceUntilIdle()

        assertFalse(vm.state.value.formIsActive)
    }

    // ── SaveOverride ───────────────────────────────────────────────────────────

    @Test
    fun `J - SaveOverride - invalid rate emits ShowError`() = runTest {
        val vm = createViewModel()
        vm.dispatch(RegionalTaxOverrideIntent.UpdateFormTaxGroupId("tg1"))
        vm.dispatch(RegionalTaxOverrideIntent.UpdateFormEffectiveRate("not-a-number"))
        advanceUntilIdle()

        vm.effects.test {
            vm.dispatch(RegionalTaxOverrideIntent.SaveOverride)
            advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is RegionalTaxOverrideEffect.ShowError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `K - SaveOverride - blank taxGroupId emits ShowError`() = runTest {
        val vm = createViewModel()
        vm.dispatch(RegionalTaxOverrideIntent.UpdateFormTaxGroupId(""))
        vm.dispatch(RegionalTaxOverrideIntent.UpdateFormEffectiveRate("10.0"))
        vm.dispatch(RegionalTaxOverrideIntent.LoadOverrides("s1"))
        advanceUntilIdle()

        vm.effects.test {
            vm.dispatch(RegionalTaxOverrideIntent.SaveOverride)
            advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is RegionalTaxOverrideEffect.ShowError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `L - SaveOverride - valid create - persists and shows success`() = runTest {
        val vm = createViewModel()
        vm.dispatch(RegionalTaxOverrideIntent.LoadOverrides("s1"))
        advanceUntilIdle()
        vm.dispatch(RegionalTaxOverrideIntent.UpdateFormTaxGroupId("tg1"))
        vm.dispatch(RegionalTaxOverrideIntent.UpdateFormEffectiveRate("15.0"))
        advanceUntilIdle()

        vm.effects.test {
            vm.dispatch(RegionalTaxOverrideIntent.SaveOverride)
            advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is RegionalTaxOverrideEffect.ShowSuccess)
            cancelAndIgnoreRemainingEvents()
        }

        // Form should be closed
        assertFalse(vm.state.value.showForm)
    }

    @Test
    fun `M - SaveOverride - valid edit - persists with existing id`() = runTest {
        val existing = RegionalTaxOverride(id = "o1", taxGroupId = "tg1", storeId = "s1", effectiveRate = 10.0)
        overridesStore.value = listOf(existing)

        val vm = createViewModel()
        vm.dispatch(RegionalTaxOverrideIntent.LoadOverrides("s1"))
        vm.dispatch(RegionalTaxOverrideIntent.OpenEditForm(existing))
        advanceUntilIdle()

        vm.dispatch(RegionalTaxOverrideIntent.UpdateFormEffectiveRate("25.0"))
        advanceUntilIdle()

        vm.effects.test {
            vm.dispatch(RegionalTaxOverrideIntent.SaveOverride)
            advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is RegionalTaxOverrideEffect.ShowSuccess)
            cancelAndIgnoreRemainingEvents()
        }

        // Original id preserved
        assertEquals("o1", overridesStore.value.first().id)
        assertEquals(25.0, overridesStore.value.first().effectiveRate)
    }

    // ── Delete flow ────────────────────────────────────────────────────────────

    @Test
    fun `N - RequestDelete - sets pendingDeleteId`() = runTest {
        val vm = createViewModel()
        vm.dispatch(RegionalTaxOverrideIntent.RequestDelete("o1"))
        advanceUntilIdle()

        assertEquals("o1", vm.state.value.pendingDeleteId)
    }

    @Test
    fun `O - CancelDelete - clears pendingDeleteId`() = runTest {
        val vm = createViewModel()
        vm.dispatch(RegionalTaxOverrideIntent.RequestDelete("o1"))
        advanceUntilIdle()

        vm.dispatch(RegionalTaxOverrideIntent.CancelDelete)
        advanceUntilIdle()

        assertNull(vm.state.value.pendingDeleteId)
    }

    @Test
    fun `P - ConfirmDelete - removes override and shows success`() = runTest {
        overridesStore.value = listOf(
            RegionalTaxOverride(id = "o1", taxGroupId = "tg1", storeId = "s1", effectiveRate = 5.0),
        )
        val vm = createViewModel()
        vm.dispatch(RegionalTaxOverrideIntent.LoadOverrides("s1"))
        vm.dispatch(RegionalTaxOverrideIntent.RequestDelete("o1"))
        advanceUntilIdle()

        vm.effects.test {
            vm.dispatch(RegionalTaxOverrideIntent.ConfirmDelete)
            advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is RegionalTaxOverrideEffect.ShowSuccess)
            cancelAndIgnoreRemainingEvents()
        }

        assertNull(vm.state.value.pendingDeleteId)
        assertTrue(overridesStore.value.isEmpty())
    }

    @Test
    fun `Q - ConfirmDelete - no pendingDeleteId - no-op`() = runTest {
        val vm = createViewModel()
        // pendingDeleteId is null — ConfirmDelete should be a no-op, no effect emitted

        vm.effects.test {
            vm.dispatch(RegionalTaxOverrideIntent.ConfirmDelete)
            advanceUntilIdle()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
