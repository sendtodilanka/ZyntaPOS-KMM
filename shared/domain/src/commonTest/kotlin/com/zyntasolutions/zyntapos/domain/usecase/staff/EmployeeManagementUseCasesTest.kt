package com.zyntasolutions.zyntapos.domain.usecase.staff

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeEmployeeRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildEmployee
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for [SaveEmployeeUseCase] (insert and update paths) and [DeleteEmployeeUseCase].
 *
 * Business rules under test:
 * - firstName, lastName, and position must not be blank (REQUIRED)
 * - commissionRate must be within 0.0–100.0 (RANGE)
 * - salary, when present, must be non-negative (MIN_VALUE)
 * - isNew=true routes to insert; isNew=false routes to update
 */
class EmployeeManagementUseCasesTest {

    private lateinit var fakeEmployeeRepo: FakeEmployeeRepository
    private lateinit var saveEmployeeUseCase: SaveEmployeeUseCase
    private lateinit var deleteEmployeeUseCase: DeleteEmployeeUseCase

    @BeforeTest
    fun setUp() {
        fakeEmployeeRepo = FakeEmployeeRepository()
        saveEmployeeUseCase = SaveEmployeeUseCase(fakeEmployeeRepo)
        deleteEmployeeUseCase = DeleteEmployeeUseCase(fakeEmployeeRepo)
    }

    // ─── SaveEmployeeUseCase — new employee ───────────────────────────────────

    @Test
    fun `saveEmployee new - success with valid employee data`() = runTest {
        val employee = buildEmployee()

        val result = saveEmployeeUseCase(employee, isNew = true)

        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, fakeEmployeeRepo.employees.size)
        assertEquals("emp-01", fakeEmployeeRepo.employees.first().id)
    }

    @Test
    fun `saveEmployee new - delegates to insert`() = runTest {
        val employee = buildEmployee(id = "emp-new")

        saveEmployeeUseCase(employee, isNew = true)

        // Insert adds the record; update would require pre-existing record
        assertEquals(1, fakeEmployeeRepo.employees.size)
        assertEquals("emp-new", fakeEmployeeRepo.employees.first().id)
    }

    @Test
    fun `saveEmployee new - returns REQUIRED when firstName is blank`() = runTest {
        val employee = buildEmployee(firstName = "")

        val result = saveEmployeeUseCase(employee, isNew = true)

        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception
        assertIs<ValidationException>(ex)
        assertEquals("REQUIRED", ex.rule)
        assertEquals("firstName", ex.field)
        assertTrue(fakeEmployeeRepo.employees.isEmpty())
    }

    @Test
    fun `saveEmployee new - returns REQUIRED when firstName is only whitespace`() = runTest {
        val employee = buildEmployee(firstName = "   ")

        val result = saveEmployeeUseCase(employee, isNew = true)

        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception
        assertIs<ValidationException>(ex)
        assertEquals("REQUIRED", ex.rule)
        assertEquals("firstName", ex.field)
    }

    @Test
    fun `saveEmployee new - returns REQUIRED when lastName is blank`() = runTest {
        val employee = buildEmployee(lastName = "")

        val result = saveEmployeeUseCase(employee, isNew = true)

        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception
        assertIs<ValidationException>(ex)
        assertEquals("REQUIRED", ex.rule)
        assertEquals("lastName", ex.field)
    }

    @Test
    fun `saveEmployee new - returns REQUIRED when position is blank`() = runTest {
        val employee = buildEmployee(position = "")

        val result = saveEmployeeUseCase(employee, isNew = true)

        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception
        assertIs<ValidationException>(ex)
        assertEquals("REQUIRED", ex.rule)
        assertEquals("position", ex.field)
    }

    @Test
    fun `saveEmployee new - returns RANGE when commissionRate is negative`() = runTest {
        val employee = buildEmployee(commissionRate = -1.0)

        val result = saveEmployeeUseCase(employee, isNew = true)

        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception
        assertIs<ValidationException>(ex)
        assertEquals("RANGE", ex.rule)
        assertEquals("commissionRate", ex.field)
    }

    @Test
    fun `saveEmployee new - returns RANGE when commissionRate exceeds 100`() = runTest {
        val employee = buildEmployee(commissionRate = 100.1)

        val result = saveEmployeeUseCase(employee, isNew = true)

        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception
        assertIs<ValidationException>(ex)
        assertEquals("RANGE", ex.rule)
        assertEquals("commissionRate", ex.field)
    }

    @Test
    fun `saveEmployee new - accepts commissionRate at boundary 0`() = runTest {
        val employee = buildEmployee(commissionRate = 0.0)

        val result = saveEmployeeUseCase(employee, isNew = true)

        assertIs<Result.Success<Unit>>(result)
    }

    @Test
    fun `saveEmployee new - accepts commissionRate at boundary 100`() = runTest {
        val employee = buildEmployee(commissionRate = 100.0)

        val result = saveEmployeeUseCase(employee, isNew = true)

        assertIs<Result.Success<Unit>>(result)
    }

    @Test
    fun `saveEmployee new - returns MIN_VALUE when salary is negative`() = runTest {
        val employee = buildEmployee(salary = -500.0)

        val result = saveEmployeeUseCase(employee, isNew = true)

        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception
        assertIs<ValidationException>(ex)
        assertEquals("MIN_VALUE", ex.rule)
        assertEquals("salary", ex.field)
    }

    @Test
    fun `saveEmployee new - accepts null salary`() = runTest {
        // salary is optional — null means no salary configured yet
        val employee = buildEmployee(salary = null)

        val result = saveEmployeeUseCase(employee, isNew = true)

        assertIs<Result.Success<Unit>>(result)
    }

    @Test
    fun `saveEmployee new - accepts zero salary`() = runTest {
        val employee = buildEmployee(salary = 0.0)

        val result = saveEmployeeUseCase(employee, isNew = true)

        assertIs<Result.Success<Unit>>(result)
    }

    @Test
    fun `saveEmployee new - propagates repository error`() = runTest {
        fakeEmployeeRepo.shouldFailInsert = true
        val employee = buildEmployee()

        val result = saveEmployeeUseCase(employee, isNew = true)

        assertIs<Result.Error>(result)
        assertTrue(fakeEmployeeRepo.employees.isEmpty())
    }

    // ─── SaveEmployeeUseCase — update existing employee ───────────────────────

    @Test
    fun `saveEmployee update - success delegates to update`() = runTest {
        // Pre-insert a record so the update index lookup succeeds
        val existing = buildEmployee(id = "emp-01", firstName = "OldName")
        fakeEmployeeRepo.employees.add(existing)

        val updated = buildEmployee(id = "emp-01", firstName = "NewName")

        val result = saveEmployeeUseCase(updated, isNew = false)

        assertIs<Result.Success<Unit>>(result)
        // Employee count must not change — only updated in place
        assertEquals(1, fakeEmployeeRepo.employees.size)
        assertEquals("NewName", fakeEmployeeRepo.employees.first().firstName)
    }

    @Test
    fun `saveEmployee update - still validates fields before delegating`() = runTest {
        val existing = buildEmployee(id = "emp-01")
        fakeEmployeeRepo.employees.add(existing)

        val invalid = buildEmployee(id = "emp-01", firstName = "")

        val result = saveEmployeeUseCase(invalid, isNew = false)

        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception
        assertIs<ValidationException>(ex)
        assertEquals("firstName", ex.field)
        // Original record must remain unchanged
        assertEquals("Jane", fakeEmployeeRepo.employees.first().firstName)
    }

    @Test
    fun `saveEmployee update - propagates repository error`() = runTest {
        fakeEmployeeRepo.shouldFailUpdate = true
        val existing = buildEmployee(id = "emp-01")
        fakeEmployeeRepo.employees.add(existing)

        val result = saveEmployeeUseCase(existing, isNew = false)

        assertIs<Result.Error>(result)
    }

    // ─── DeleteEmployeeUseCase ────────────────────────────────────────────────

    @Test
    fun `deleteEmployee success delegates to repository`() = runTest {
        val employee = buildEmployee(id = "emp-01")
        fakeEmployeeRepo.employees.add(employee)

        val result = deleteEmployeeUseCase("emp-01")

        assertIs<Result.Success<Unit>>(result)
        assertTrue(fakeEmployeeRepo.employees.isEmpty())
    }

    @Test
    fun `deleteEmployee passes the correct id to repository`() = runTest {
        val emp1 = buildEmployee(id = "emp-01", firstName = "Alice")
        val emp2 = buildEmployee(id = "emp-02", firstName = "Bob")
        fakeEmployeeRepo.employees.addAll(listOf(emp1, emp2))

        deleteEmployeeUseCase("emp-01")

        // Only emp-02 should remain
        assertEquals(1, fakeEmployeeRepo.employees.size)
        assertEquals("emp-02", fakeEmployeeRepo.employees.first().id)
    }

    @Test
    fun `deleteEmployee propagates repository error`() = runTest {
        fakeEmployeeRepo.shouldFailDelete = true
        fakeEmployeeRepo.employees.add(buildEmployee(id = "emp-01"))

        val result = deleteEmployeeUseCase("emp-01")

        assertIs<Result.Error>(result)
        // Employee must still be in repo since the fake returned an error
        assertEquals(1, fakeEmployeeRepo.employees.size)
    }

    @Test
    fun `deleteEmployee returns error when employee does not exist`() = runTest {
        // Repo has no employees — fake returns error for missing id
        val result = deleteEmployeeUseCase("non-existent")

        assertIs<Result.Error>(result)
    }
}
