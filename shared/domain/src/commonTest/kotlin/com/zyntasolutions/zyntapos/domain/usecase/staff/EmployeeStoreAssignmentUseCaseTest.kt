package com.zyntasolutions.zyntapos.domain.usecase.staff

import com.zyntasolutions.zyntapos.domain.model.EmployeeStoreAssignment
import com.zyntasolutions.zyntapos.domain.repository.EmployeeStoreAssignmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EmployeeStoreAssignmentUseCaseTest {

    private val now = Instant.fromEpochMilliseconds(1700000000000)

    private val assignment1 = EmployeeStoreAssignment(
        id = "esa-001",
        employeeId = "emp-001",
        storeId = "store-002",
        startDate = "2026-03-01",
        endDate = null,
        isTemporary = false,
        isActive = true,
        createdAt = now,
        updatedAt = now,
    )

    private val assignment2 = EmployeeStoreAssignment(
        id = "esa-002",
        employeeId = "emp-001",
        storeId = "store-003",
        startDate = "2026-03-15",
        endDate = "2026-04-15",
        isTemporary = true,
        isActive = true,
        createdAt = now,
        updatedAt = now,
    )

    private val fakeRepository = FakeEmployeeStoreAssignmentRepository()

    // ── GetEmployeeStoresUseCase ──────────────────────────────

    @Test
    fun `GetEmployeeStores returns all active assignments`() = runTest {
        fakeRepository.assignments.add(assignment1)
        fakeRepository.assignments.add(assignment2)
        val useCase = GetEmployeeStoresUseCase(fakeRepository)

        val result = useCase("emp-001").first()

        assertEquals(2, result.size)
        assertEquals("store-002", result[0].storeId)
        assertEquals("store-003", result[1].storeId)
    }

    @Test
    fun `GetEmployeeStores returns empty for unknown employee`() = runTest {
        val useCase = GetEmployeeStoresUseCase(fakeRepository)
        val result = useCase("unknown").first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `GetEmployeeStores filters by employee`() = runTest {
        fakeRepository.assignments.add(assignment1)
        fakeRepository.assignments.add(
            assignment2.copy(employeeId = "emp-002"),
        )
        val useCase = GetEmployeeStoresUseCase(fakeRepository)

        val result = useCase("emp-001").first()
        assertEquals(1, result.size)
        assertEquals("store-002", result[0].storeId)
    }

    // ── AssignEmployeeToStoreUseCase ──────────────────────────

    @Test
    fun `AssignEmployeeToStore adds new assignment`() = runTest {
        val useCase = AssignEmployeeToStoreUseCase(fakeRepository)
        useCase(assignment1)

        val result = fakeRepository.getByEmployeeAndStore("emp-001", "store-002")
        assertNotNull(result)
        assertEquals("esa-001", result.id)
    }

    @Test
    fun `AssignEmployeeToStore is idempotent`() = runTest {
        val useCase = AssignEmployeeToStoreUseCase(fakeRepository)
        useCase(assignment1)
        useCase(assignment1.copy(isTemporary = true))

        val result = fakeRepository.getByEmployeeAndStore("emp-001", "store-002")
        assertNotNull(result)
        assertTrue(result.isTemporary)
    }

    // ── RevokeEmployeeStoreAssignmentUseCase ──────────────────

    @Test
    fun `RevokeAssignment deactivates assignment`() = runTest {
        fakeRepository.assignments.add(assignment1)
        val useCase = RevokeEmployeeStoreAssignmentUseCase(fakeRepository)

        useCase("emp-001", "store-002")

        val result = fakeRepository.getByEmployeeAndStore("emp-001", "store-002")
        assertNotNull(result)
        assertFalse(result.isActive)
    }

    @Test
    fun `RevokeAssignment is no-op for non-existing`() = runTest {
        val useCase = RevokeEmployeeStoreAssignmentUseCase(fakeRepository)
        useCase("emp-999", "store-999")
        // No exception thrown
    }

    @Test
    fun `isAssigned returns true for active assignment`() = runTest {
        fakeRepository.assignments.add(assignment1)
        assertTrue(fakeRepository.isAssigned("emp-001", "store-002"))
    }

    @Test
    fun `isAssigned returns false for inactive assignment`() = runTest {
        fakeRepository.assignments.add(assignment1.copy(isActive = false))
        assertFalse(fakeRepository.isAssigned("emp-001", "store-002"))
    }

    @Test
    fun `isAssigned returns false for unknown pair`() = runTest {
        assertFalse(fakeRepository.isAssigned("emp-999", "store-999"))
    }

    @Test
    fun `temporary assignment has end date`() = runTest {
        val useCase = AssignEmployeeToStoreUseCase(fakeRepository)
        useCase(assignment2)

        val result = fakeRepository.getByEmployeeAndStore("emp-001", "store-003")
        assertNotNull(result)
        assertTrue(result.isTemporary)
        assertEquals("2026-04-15", result.endDate)
    }
}

/** In-memory fake for testing. */
private class FakeEmployeeStoreAssignmentRepository : EmployeeStoreAssignmentRepository {

    val assignments = mutableListOf<EmployeeStoreAssignment>()
    private val _flow = MutableStateFlow(0) // trigger re-emission

    override fun getAssignmentsForEmployee(employeeId: String): Flow<List<EmployeeStoreAssignment>> =
        _flow.map { assignments.filter { it.employeeId == employeeId && it.isActive } }

    override fun getEmployeesAssignedToStore(storeId: String): Flow<List<EmployeeStoreAssignment>> =
        _flow.map { assignments.filter { it.storeId == storeId && it.isActive } }

    override suspend fun getById(id: String): EmployeeStoreAssignment? =
        assignments.find { it.id == id }

    override suspend fun getByEmployeeAndStore(employeeId: String, storeId: String): EmployeeStoreAssignment? =
        assignments.find { it.employeeId == employeeId && it.storeId == storeId }

    override suspend fun upsert(assignment: EmployeeStoreAssignment) {
        val idx = assignments.indexOfFirst {
            it.employeeId == assignment.employeeId && it.storeId == assignment.storeId
        }
        if (idx >= 0) {
            assignments[idx] = assignment
        } else {
            assignments.add(assignment)
        }
        _flow.value++
    }

    override suspend fun deactivate(employeeId: String, storeId: String) {
        val idx = assignments.indexOfFirst {
            it.employeeId == employeeId && it.storeId == storeId
        }
        if (idx >= 0) {
            assignments[idx] = assignments[idx].copy(isActive = false)
            _flow.value++
        }
    }

    override suspend fun isAssigned(employeeId: String, storeId: String): Boolean =
        assignments.any { it.employeeId == employeeId && it.storeId == storeId && it.isActive }

    override suspend fun upsertFromSync(assignment: EmployeeStoreAssignment) = upsert(assignment)
}
