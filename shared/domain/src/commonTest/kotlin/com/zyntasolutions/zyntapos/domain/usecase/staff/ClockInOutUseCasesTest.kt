package com.zyntasolutions.zyntapos.domain.usecase.staff

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeAttendanceRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildAttendanceRecord
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for [ClockInUseCase] and [ClockOutUseCase].
 *
 * Both use cases delegate to [AttendanceRepository] after performing
 * guard checks against open attendance records.
 */
class ClockInOutUseCasesTest {

    private lateinit var fakeAttendanceRepo: FakeAttendanceRepository
    private lateinit var clockInUseCase: ClockInUseCase
    private lateinit var clockOutUseCase: ClockOutUseCase

    @BeforeTest
    fun setUp() {
        fakeAttendanceRepo = FakeAttendanceRepository()
        clockInUseCase = ClockInUseCase(fakeAttendanceRepo)
        clockOutUseCase = ClockOutUseCase(fakeAttendanceRepo)
    }

    // ─── ClockInUseCase ───────────────────────────────────────────────────────

    @Test
    fun `clockIn success when no open record exists`() = runTest {
        // Arrange: employee has no open record
        fakeAttendanceRepo.openRecord = null
        val record = buildAttendanceRecord(employeeId = "emp-01", clockIn = "2026-02-27T08:00:00")

        // Act
        val result = clockInUseCase(record)

        // Assert
        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, fakeAttendanceRepo.records.size)
        assertEquals("emp-01", fakeAttendanceRepo.records.first().employeeId)
    }

    @Test
    fun `clockIn returns ALREADY_CLOCKED_IN when open record exists`() = runTest {
        // Arrange: employee already has an open attendance record
        val existingRecord = buildAttendanceRecord(
            id = "att-existing",
            employeeId = "emp-01",
            clockIn = "2026-02-27T06:00:00",
            clockOut = null,
        )
        fakeAttendanceRepo.openRecord = existingRecord
        val newRecord = buildAttendanceRecord(employeeId = "emp-01", clockIn = "2026-02-27T08:00:00")

        // Act
        val result = clockInUseCase(newRecord)

        // Assert
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception
        assertIs<ValidationException>(ex)
        assertEquals("ALREADY_CLOCKED_IN", ex.rule)
        assertEquals("employeeId", ex.field)
    }

    @Test
    fun `clockIn propagates repository error from getOpenRecord`() = runTest {
        // Arrange: DB fails when checking for open record
        fakeAttendanceRepo.shouldFailGetOpenRecord = true
        val record = buildAttendanceRecord(employeeId = "emp-01")

        // Act
        val result = clockInUseCase(record)

        // Assert: repository error is propagated directly, no insert attempted
        assertIs<Result.Error>(result)
        assertEquals(0, fakeAttendanceRepo.records.size)
    }

    @Test
    fun `clockIn propagates repository error from insert`() = runTest {
        // Arrange: open record check passes but insert fails
        fakeAttendanceRepo.openRecord = null
        fakeAttendanceRepo.shouldFailInsert = true
        val record = buildAttendanceRecord(employeeId = "emp-01")

        // Act
        val result = clockInUseCase(record)

        // Assert
        assertIs<Result.Error>(result)
    }

    @Test
    fun `clockIn record is persisted with the correct employee id`() = runTest {
        fakeAttendanceRepo.openRecord = null
        val record = buildAttendanceRecord(id = "att-99", employeeId = "emp-42", clockIn = "2026-02-27T09:30:00")

        clockInUseCase(record)

        assertEquals("att-99", fakeAttendanceRepo.records.first().id)
        assertEquals("emp-42", fakeAttendanceRepo.records.first().employeeId)
    }

    // ─── ClockOutUseCase ──────────────────────────────────────────────────────

    @Test
    fun `clockOut success when open record exists`() = runTest {
        // Arrange: employee is clocked in
        val openRecord = buildAttendanceRecord(
            id = "att-01",
            employeeId = "emp-01",
            clockIn = "2026-02-27T08:00:00",
        )
        fakeAttendanceRepo.openRecord = openRecord
        fakeAttendanceRepo.records.add(openRecord)

        // Act
        val result = clockOutUseCase(
            employeeId = "emp-01",
            clockOutTime = "2026-02-27T16:00:00",
            updatedAt = 2_000_000L,
        )

        // Assert
        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, fakeAttendanceRepo.clockOutCalls.size)
        val call = fakeAttendanceRepo.clockOutCalls.first()
        assertEquals("att-01", call.id)
        assertEquals("2026-02-27T16:00:00", call.clockOut)
        assertEquals(2_000_000L, call.updatedAt)
    }

    @Test
    fun `clockOut calculates total hours correctly`() = runTest {
        // Arrange: clocked in at 08:00, out at 17:00 = 9 hours
        val openRecord = buildAttendanceRecord(
            id = "att-01",
            employeeId = "emp-01",
            clockIn = "2026-02-27T08:00:00",
        )
        fakeAttendanceRepo.openRecord = openRecord
        fakeAttendanceRepo.records.add(openRecord)

        // Act
        clockOutUseCase(
            employeeId = "emp-01",
            clockOutTime = "2026-02-27T17:00:00",
            updatedAt = 2_000_000L,
        )

        // Assert: 17:00 - 08:00 = 9h total, 1h overtime (threshold=8h)
        val call = fakeAttendanceRepo.clockOutCalls.first()
        assertEquals(9.0, call.totalHours, 0.001)
        assertEquals(1.0, call.overtimeHours, 0.001)
    }

    @Test
    fun `clockOut calculates zero overtime when within threshold`() = runTest {
        // Arrange: clocked in at 09:00, out at 17:00 = 8 hours exactly — no overtime
        val openRecord = buildAttendanceRecord(
            id = "att-01",
            employeeId = "emp-01",
            clockIn = "2026-02-27T09:00:00",
        )
        fakeAttendanceRepo.openRecord = openRecord
        fakeAttendanceRepo.records.add(openRecord)

        // Act
        clockOutUseCase(
            employeeId = "emp-01",
            clockOutTime = "2026-02-27T17:00:00",
            updatedAt = 2_000_000L,
        )

        // Assert: exactly at threshold = 0 overtime
        val call = fakeAttendanceRepo.clockOutCalls.first()
        assertEquals(8.0, call.totalHours, 0.001)
        assertEquals(0.0, call.overtimeHours, 0.001)
    }

    @Test
    fun `clockOut returns NOT_CLOCKED_IN when no open record exists`() = runTest {
        // Arrange: employee has no open record
        fakeAttendanceRepo.openRecord = null

        // Act
        val result = clockOutUseCase(
            employeeId = "emp-01",
            clockOutTime = "2026-02-27T16:00:00",
            updatedAt = 2_000_000L,
        )

        // Assert
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception
        assertIs<ValidationException>(ex)
        assertEquals("NOT_CLOCKED_IN", ex.rule)
        assertEquals("employeeId", ex.field)
    }

    @Test
    fun `clockOut propagates repository error from getOpenRecord`() = runTest {
        // Arrange: DB fails checking for open record
        fakeAttendanceRepo.shouldFailGetOpenRecord = true

        // Act
        val result = clockOutUseCase(
            employeeId = "emp-01",
            clockOutTime = "2026-02-27T16:00:00",
            updatedAt = 2_000_000L,
        )

        // Assert: DB error propagated, no clockOut call made
        assertIs<Result.Error>(result)
        assertTrue(fakeAttendanceRepo.clockOutCalls.isEmpty())
    }

    @Test
    fun `clockOut propagates repository error from clockOut call`() = runTest {
        // Arrange: open record exists but DB update fails
        val openRecord = buildAttendanceRecord(id = "att-01", employeeId = "emp-01")
        fakeAttendanceRepo.openRecord = openRecord
        fakeAttendanceRepo.records.add(openRecord)
        fakeAttendanceRepo.shouldFailClockOut = true

        // Act
        val result = clockOutUseCase(
            employeeId = "emp-01",
            clockOutTime = "2026-02-27T16:00:00",
            updatedAt = 2_000_000L,
        )

        // Assert
        assertIs<Result.Error>(result)
    }

    @Test
    fun `clockOut with custom overtime threshold`() = runTest {
        // Arrange: threshold set to 6h; clocked in at 08:00, out at 16:00 = 8h → 2h overtime
        val customThresholdUseCase = ClockOutUseCase(fakeAttendanceRepo, overtimeThresholdHours = 6.0)
        val openRecord = buildAttendanceRecord(
            id = "att-01",
            employeeId = "emp-01",
            clockIn = "2026-02-27T08:00:00",
        )
        fakeAttendanceRepo.openRecord = openRecord
        fakeAttendanceRepo.records.add(openRecord)

        // Act
        customThresholdUseCase(
            employeeId = "emp-01",
            clockOutTime = "2026-02-27T16:00:00",
            updatedAt = 2_000_000L,
        )

        // Assert: 8h worked, 6h threshold → 2h overtime
        val call = fakeAttendanceRepo.clockOutCalls.first()
        assertEquals(8.0, call.totalHours, 0.001)
        assertEquals(2.0, call.overtimeHours, 0.001)
    }
}
