package com.zyntasolutions.zyntapos.domain.usecase.staff

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.AttendanceRecord
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeAttendanceRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — ClockOutUseCase Unit Tests (commonTest)
 *
 * Coverage:
 *  A.  No open record returns NOT_CLOCKED_IN error
 *  B.  Repository getOpenRecord failure propagates
 *  C.  Standard 8-hour shift — totalHours=8, overtimeHours=0
 *  D.  9-hour shift — totalHours=9, overtimeHours=1 (default threshold=8)
 *  E.  Exactly at threshold — totalHours=8, overtimeHours=0
 *  F.  Custom overtimeThreshold of 6 hours triggers overtime sooner
 *  G.  Clock-out arguments are passed correctly to repository
 *  H.  Repository clockOut failure propagates
 *  I.  Malformed time strings result in totalHours=0.0 (not an exception)
 */
class ClockOutUseCaseTest {

    private fun makeRepo(
        shouldFailGetOpenRecord: Boolean = false,
        shouldFailClockOut: Boolean = false,
    ) = FakeAttendanceRepository().also {
        it.shouldFailGetOpenRecord = shouldFailGetOpenRecord
        it.shouldFailClockOut = shouldFailClockOut
    }

    private fun makeRecord(
        id: String = "att-1",
        employeeId: String = "emp-1",
        clockIn: String = "2026-03-28T09:00:00",
    ) = AttendanceRecord(
        id = id,
        employeeId = employeeId,
        clockIn = clockIn,
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun `A - no open record returns NOT_CLOCKED_IN error`() = runTest {
        val repo = makeRepo()
        repo.openRecord = null
        val useCase = ClockOutUseCase(repo)

        val result = useCase(employeeId = "emp-1", clockOutTime = "2026-03-28T17:00:00", updatedAt = 0L)
        assertIs<Result.Error>(result)
        val ex = result.exception as ValidationException
        assertEquals("NOT_CLOCKED_IN", ex.rule)
    }

    @Test
    fun `B - repository getOpenRecord failure propagates`() = runTest {
        val repo = makeRepo(shouldFailGetOpenRecord = true)
        val useCase = ClockOutUseCase(repo)

        val result = useCase(employeeId = "emp-1", clockOutTime = "2026-03-28T17:00:00", updatedAt = 0L)
        assertIs<Result.Error>(result)
    }

    @Test
    fun `C - standard 8-hour shift produces totalHours=8 and overtimeHours=0`() = runTest {
        val repo = makeRepo()
        repo.openRecord = makeRecord(clockIn = "2026-03-28T09:00:00")
        repo.records.add(repo.openRecord!!)
        val useCase = ClockOutUseCase(repo)

        val result = useCase(employeeId = "emp-1", clockOutTime = "2026-03-28T17:00:00", updatedAt = 100L)
        assertIs<Result.Success<Unit>>(result)

        val call = repo.clockOutCalls.single()
        assertEquals(8.0, call.totalHours)
        assertEquals(0.0, call.overtimeHours)
    }

    @Test
    fun `D - 9-hour shift produces totalHours=9 and overtimeHours=1`() = runTest {
        val repo = makeRepo()
        repo.openRecord = makeRecord(clockIn = "2026-03-28T08:00:00")
        repo.records.add(repo.openRecord!!)
        val useCase = ClockOutUseCase(repo)

        val result = useCase(employeeId = "emp-1", clockOutTime = "2026-03-28T17:00:00", updatedAt = 0L)
        assertIs<Result.Success<Unit>>(result)
        val call = repo.clockOutCalls.single()
        assertEquals(9.0, call.totalHours)
        assertEquals(1.0, call.overtimeHours)
    }

    @Test
    fun `E - exactly at threshold produces overtimeHours=0`() = runTest {
        val repo = makeRepo()
        repo.openRecord = makeRecord(clockIn = "2026-03-28T09:00:00")
        repo.records.add(repo.openRecord!!)
        val useCase = ClockOutUseCase(repo)  // threshold=8

        useCase(employeeId = "emp-1", clockOutTime = "2026-03-28T17:00:00", updatedAt = 0L)
        assertEquals(0.0, repo.clockOutCalls.single().overtimeHours)
    }

    @Test
    fun `F - custom threshold 6h triggers overtime for 8h shift`() = runTest {
        val repo = makeRepo()
        repo.openRecord = makeRecord(clockIn = "2026-03-28T09:00:00")
        repo.records.add(repo.openRecord!!)
        val useCase = ClockOutUseCase(repo, overtimeThresholdHours = 6.0)

        useCase(employeeId = "emp-1", clockOutTime = "2026-03-28T17:00:00", updatedAt = 0L)
        // 8h total - 6h threshold = 2h overtime
        assertEquals(2.0, repo.clockOutCalls.single().overtimeHours)
    }

    @Test
    fun `G - clockOut arguments passed correctly to repository`() = runTest {
        val repo = makeRepo()
        repo.openRecord = makeRecord(id = "att-99", clockIn = "2026-03-28T10:00:00")
        repo.records.add(repo.openRecord!!)
        val useCase = ClockOutUseCase(repo)

        useCase(employeeId = "emp-1", clockOutTime = "2026-03-28T18:00:00", updatedAt = 9999L)
        val call = repo.clockOutCalls.single()
        assertEquals("att-99", call.id)
        assertEquals("2026-03-28T18:00:00", call.clockOut)
        assertEquals(9999L, call.updatedAt)
    }

    @Test
    fun `H - repository clockOut failure propagates`() = runTest {
        val repo = makeRepo(shouldFailClockOut = true)
        repo.openRecord = makeRecord(clockIn = "2026-03-28T09:00:00")
        repo.records.add(repo.openRecord!!)
        val useCase = ClockOutUseCase(repo)

        val result = useCase(employeeId = "emp-1", clockOutTime = "2026-03-28T17:00:00", updatedAt = 0L)
        assertIs<Result.Error>(result)
    }

    @Test
    fun `I - malformed time strings do not throw and produce 0h total`() = runTest {
        val repo = makeRepo()
        repo.openRecord = makeRecord(clockIn = "not-a-datetime")
        repo.records.add(repo.openRecord!!)
        val useCase = ClockOutUseCase(repo)

        // Should not throw; calculateHours catches the exception and returns 0.0
        val result = useCase(employeeId = "emp-1", clockOutTime = "also-invalid", updatedAt = 0L)
        assertIs<Result.Success<Unit>>(result)
        assertEquals(0.0, repo.clockOutCalls.single().totalHours)
    }
}
