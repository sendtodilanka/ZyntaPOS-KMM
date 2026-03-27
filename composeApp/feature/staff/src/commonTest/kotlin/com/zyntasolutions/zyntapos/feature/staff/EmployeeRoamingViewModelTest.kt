package com.zyntasolutions.zyntapos.feature.staff

import com.zyntasolutions.zyntapos.domain.model.EmployeeStoreAssignment
import com.zyntasolutions.zyntapos.domain.repository.EmployeeStoreAssignmentRepository
import com.zyntasolutions.zyntapos.domain.usecase.staff.AssignEmployeeToStoreUseCase
import com.zyntasolutions.zyntapos.domain.usecase.staff.GetEmployeeStoresUseCase
import com.zyntasolutions.zyntapos.domain.usecase.staff.RevokeEmployeeStoreAssignmentUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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

@OptIn(ExperimentalCoroutinesApi::class)
class EmployeeRoamingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    // ── In-memory fake ─────────────────────────────────────────────────────────

    private class FakeEmployeeStoreAssignmentRepository : EmployeeStoreAssignmentRepository {

        val assignments = mutableListOf<EmployeeStoreAssignment>()
        private val _trigger = MutableStateFlow(0)
        var deactivateCalled = false
        var lastDeactivatedEmployeeId: String? = null
        var lastDeactivatedStoreId: String? = null

        override fun getAssignmentsForEmployee(employeeId: String): Flow<List<EmployeeStoreAssignment>> =
            _trigger.map { assignments.filter { it.employeeId == employeeId && it.isActive } }

        override fun getEmployeesAssignedToStore(storeId: String): Flow<List<EmployeeStoreAssignment>> =
            _trigger.map { assignments.filter { it.storeId == storeId && it.isActive } }

        override suspend fun getById(id: String): EmployeeStoreAssignment? =
            assignments.find { it.id == id }

        override suspend fun getByEmployeeAndStore(employeeId: String, storeId: String): EmployeeStoreAssignment? =
            assignments.find { it.employeeId == employeeId && it.storeId == storeId }

        override suspend fun upsert(assignment: EmployeeStoreAssignment) {
            val idx = assignments.indexOfFirst {
                it.employeeId == assignment.employeeId && it.storeId == assignment.storeId
            }
            if (idx >= 0) assignments[idx] = assignment else assignments.add(assignment)
            _trigger.value++
        }

        override suspend fun deactivate(employeeId: String, storeId: String) {
            deactivateCalled = true
            lastDeactivatedEmployeeId = employeeId
            lastDeactivatedStoreId = storeId
            val idx = assignments.indexOfFirst { it.employeeId == employeeId && it.storeId == storeId }
            if (idx >= 0) {
                assignments[idx] = assignments[idx].copy(isActive = false)
                _trigger.value++
            }
        }

        override suspend fun isAssigned(employeeId: String, storeId: String): Boolean =
            assignments.any { it.employeeId == employeeId && it.storeId == storeId && it.isActive }

        override suspend fun upsertFromSync(assignment: EmployeeStoreAssignment) = upsert(assignment)
    }

    // ── Fixture builder ────────────────────────────────────────────────────────

    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000L)

    private fun buildAssignment(
        id: String = "esa-01",
        employeeId: String = "emp-01",
        storeId: String = "store-02",
        startDate: String = "2026-01-01",
        isActive: Boolean = true,
        isTemporary: Boolean = false,
    ) = EmployeeStoreAssignment(
        id = id,
        employeeId = employeeId,
        storeId = storeId,
        startDate = startDate,
        endDate = null,
        isTemporary = isTemporary,
        isActive = isActive,
        createdAt = now,
        updatedAt = now,
    )

    // ── Subject ────────────────────────────────────────────────────────────────

    private lateinit var fakeRepo: FakeEmployeeStoreAssignmentRepository
    private lateinit var viewModel: EmployeeRoamingViewModel

    private fun buildViewModel() = EmployeeRoamingViewModel(
        getEmployeeStoresUseCase = GetEmployeeStoresUseCase(fakeRepo),
        assignEmployeeToStoreUseCase = AssignEmployeeToStoreUseCase(fakeRepo),
        revokeEmployeeStoreAssignmentUseCase = RevokeEmployeeStoreAssignmentUseCase(fakeRepo),
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeEmployeeStoreAssignmentRepository()
        viewModel = buildViewModel()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ──────────────────────────────────────────────────────────

    @Test
    fun `initial state has blank employee and empty assignments`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        val s = viewModel.state.value
        assertEquals("", s.employeeId)
        assertEquals("", s.employeeName)
        assertTrue(s.assignments.isEmpty())
        assertFalse(s.showAddDialog)
        assertNull(s.error)
        assertNull(s.successMessage)
        assertFalse(s.isLoading)
    }

    // ── LoadAssignments ────────────────────────────────────────────────────────

    @Test
    fun `LoadAssignments updates employeeId and name in state`() = runTest {
        viewModel.dispatch(EmployeeRoamingIntent.LoadAssignments("emp-01", "Jane Doe"))
        testDispatcher.scheduler.advanceUntilIdle()

        val s = viewModel.state.value
        assertEquals("emp-01", s.employeeId)
        assertEquals("Jane Doe", s.employeeName)
    }

    @Test
    fun `LoadAssignments populates assignments from repository`() = runTest {
        fakeRepo.assignments.add(buildAssignment(employeeId = "emp-01", storeId = "store-02"))
        fakeRepo.assignments.add(buildAssignment(id = "esa-02", employeeId = "emp-01", storeId = "store-03"))

        viewModel.dispatch(EmployeeRoamingIntent.LoadAssignments("emp-01", "Jane"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.state.value.assignments.size)
    }

    @Test
    fun `LoadAssignments does not show inactive assignments`() = runTest {
        fakeRepo.assignments.add(buildAssignment(employeeId = "emp-01", isActive = false))

        viewModel.dispatch(EmployeeRoamingIntent.LoadAssignments("emp-01", "Jane"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.assignments.isEmpty())
    }

    @Test
    fun `LoadAssignments with blank employeeId results in empty list`() = runTest {
        fakeRepo.assignments.add(buildAssignment())

        viewModel.dispatch(EmployeeRoamingIntent.LoadAssignments("", ""))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.assignments.isEmpty())
    }

    // ── Dialog open/close ──────────────────────────────────────────────────────

    @Test
    fun `ShowAddDialog sets showAddDialog true and resets form`() = runTest {
        viewModel.dispatch(EmployeeRoamingIntent.ShowAddDialog)
        testDispatcher.scheduler.advanceUntilIdle()

        val s = viewModel.state.value
        assertTrue(s.showAddDialog)
        assertEquals("", s.addForm.storeId)
        assertEquals("", s.addForm.startDate)
    }

    @Test
    fun `HideAddDialog clears dialog and resets form`() = runTest {
        viewModel.dispatch(EmployeeRoamingIntent.ShowAddDialog)
        viewModel.dispatch(EmployeeRoamingIntent.UpdateField("storeId", "store-99"))
        viewModel.dispatch(EmployeeRoamingIntent.HideAddDialog)
        testDispatcher.scheduler.advanceUntilIdle()

        val s = viewModel.state.value
        assertFalse(s.showAddDialog)
        assertEquals("", s.addForm.storeId)
    }

    // ── UpdateField ────────────────────────────────────────────────────────────

    @Test
    fun `UpdateField storeId updates form`() = runTest {
        viewModel.dispatch(EmployeeRoamingIntent.UpdateField("storeId", "store-99"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("store-99", viewModel.state.value.addForm.storeId)
    }

    @Test
    fun `UpdateField startDate and endDate update form`() = runTest {
        viewModel.dispatch(EmployeeRoamingIntent.UpdateField("startDate", "2026-04-01"))
        viewModel.dispatch(EmployeeRoamingIntent.UpdateField("endDate", "2026-06-30"))
        testDispatcher.scheduler.advanceUntilIdle()

        val form = viewModel.state.value.addForm
        assertEquals("2026-04-01", form.startDate)
        assertEquals("2026-06-30", form.endDate)
    }

    // ── ToggleTemporary ────────────────────────────────────────────────────────

    @Test
    fun `ToggleTemporary flips isTemporary flag`() = runTest {
        assertFalse(viewModel.state.value.addForm.isTemporary)
        viewModel.dispatch(EmployeeRoamingIntent.ToggleTemporary)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value.addForm.isTemporary)

        viewModel.dispatch(EmployeeRoamingIntent.ToggleTemporary)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.state.value.addForm.isTemporary)
    }

    // ── Validation on ConfirmAssignment ────────────────────────────────────────

    @Test
    fun `ConfirmAssignment shows validation error when storeId is blank`() = runTest {
        viewModel.dispatch(EmployeeRoamingIntent.UpdateField("startDate", "2026-04-01"))
        viewModel.dispatch(EmployeeRoamingIntent.ConfirmAssignment)
        testDispatcher.scheduler.advanceUntilIdle()

        val errors = viewModel.state.value.addForm.validationErrors
        assertTrue(errors.containsKey("storeId"))
    }

    @Test
    fun `ConfirmAssignment shows validation error when startDate is blank`() = runTest {
        viewModel.dispatch(EmployeeRoamingIntent.UpdateField("storeId", "store-02"))
        viewModel.dispatch(EmployeeRoamingIntent.ConfirmAssignment)
        testDispatcher.scheduler.advanceUntilIdle()

        val errors = viewModel.state.value.addForm.validationErrors
        assertTrue(errors.containsKey("startDate"))
    }

    @Test
    fun `ConfirmAssignment rejects duplicate active assignment`() = runTest {
        // Seed the duplicate BEFORE loading so the flow emits it on first collection
        fakeRepo.assignments.add(buildAssignment(employeeId = "emp-01", storeId = "store-02", isActive = true))

        viewModel.dispatch(EmployeeRoamingIntent.LoadAssignments("emp-01", "Jane"))
        testDispatcher.scheduler.advanceUntilIdle()  // let flatMapLatest populate state.assignments

        viewModel.dispatch(EmployeeRoamingIntent.UpdateField("storeId", "store-02"))
        viewModel.dispatch(EmployeeRoamingIntent.UpdateField("startDate", "2026-04-01"))
        viewModel.dispatch(EmployeeRoamingIntent.ConfirmAssignment)
        testDispatcher.scheduler.advanceUntilIdle()

        val errors = viewModel.state.value.addForm.validationErrors
        assertTrue(errors.containsKey("storeId"))
        assertTrue(errors["storeId"]!!.contains("already assigned", ignoreCase = true))
    }

    @Test
    fun `ConfirmAssignment with valid form saves assignment and closes dialog`() = runTest {
        viewModel.dispatch(EmployeeRoamingIntent.LoadAssignments("emp-01", "Jane"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dispatch(EmployeeRoamingIntent.ShowAddDialog)
        viewModel.dispatch(EmployeeRoamingIntent.UpdateField("storeId", "store-99"))
        viewModel.dispatch(EmployeeRoamingIntent.UpdateField("startDate", "2026-04-01"))
        viewModel.dispatch(EmployeeRoamingIntent.ConfirmAssignment)
        testDispatcher.scheduler.advanceUntilIdle()

        val s = viewModel.state.value
        assertFalse(s.showAddDialog)
        assertFalse(s.isLoading)
        assertNotNull(s.successMessage)

        val saved = fakeRepo.assignments.find { it.storeId == "store-99" }
        assertNotNull(saved)
        assertEquals("emp-01", saved.employeeId)
    }

    // ── RevokeAssignment ───────────────────────────────────────────────────────

    @Test
    fun `RevokeAssignment calls deactivate on repository`() = runTest {
        viewModel.dispatch(EmployeeRoamingIntent.LoadAssignments("emp-01", "Jane"))
        fakeRepo.assignments.add(buildAssignment(employeeId = "emp-01", storeId = "store-02"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dispatch(EmployeeRoamingIntent.RevokeAssignment("store-02"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(fakeRepo.deactivateCalled)
        assertEquals("emp-01", fakeRepo.lastDeactivatedEmployeeId)
        assertEquals("store-02", fakeRepo.lastDeactivatedStoreId)
        assertNotNull(viewModel.state.value.successMessage)
    }

    // ── DismissError / DismissSuccess ──────────────────────────────────────────

    @Test
    fun `DismissError clears error`() = runTest {
        // Trigger an error via confirm with no form data
        viewModel.dispatch(EmployeeRoamingIntent.ConfirmAssignment)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.dispatch(EmployeeRoamingIntent.DismissError)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `DismissSuccess clears successMessage`() = runTest {
        viewModel.dispatch(EmployeeRoamingIntent.LoadAssignments("emp-01", "Jane"))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.dispatch(EmployeeRoamingIntent.UpdateField("storeId", "store-99"))
        viewModel.dispatch(EmployeeRoamingIntent.UpdateField("startDate", "2026-04-01"))
        viewModel.dispatch(EmployeeRoamingIntent.ConfirmAssignment)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.successMessage)
        viewModel.dispatch(EmployeeRoamingIntent.DismissSuccess)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.state.value.successMessage)
    }

    // ── Reactive update ────────────────────────────────────────────────────────

    @Test
    fun `assignments list updates reactively when repository changes`() = runTest {
        viewModel.dispatch(EmployeeRoamingIntent.LoadAssignments("emp-01", "Jane"))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, viewModel.state.value.assignments.size)

        // Add directly to repo, trigger re-emission
        fakeRepo.upsert(buildAssignment(employeeId = "emp-01", storeId = "store-02"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.state.value.assignments.size)
        assertEquals("store-02", viewModel.state.value.assignments.first().storeId)
    }
}
