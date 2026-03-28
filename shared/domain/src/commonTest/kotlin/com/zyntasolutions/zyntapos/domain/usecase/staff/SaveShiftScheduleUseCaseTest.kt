package com.zyntasolutions.zyntapos.domain.usecase.staff

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.ShiftSchedule
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeShiftRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — SaveShiftScheduleUseCase Unit Tests (commonTest)
 *
 * Coverage:
 *  A.  Blank shiftDate returns REQUIRED error
 *  B.  Invalid startTime format returns FORMAT error
 *  C.  Invalid endTime format returns FORMAT error
 *  D.  endTime equal to startTime returns TIME_ORDER error
 *  E.  endTime before startTime returns TIME_ORDER error
 *  F.  Valid non-overlapping shift is saved
 *  G.  Overlapping shift (contained) returns SHIFT_OVERLAP error
 *  H.  Adjacent shifts (touching boundaries) are allowed
 *  I.  Editing a shift excludes itself from overlap check
 *  J.  Midnight boundary time "23:59" is valid HH:MM format
 *  K.  "00:00" start time is valid HH:MM format
 */
class SaveShiftScheduleUseCaseTest {

    private fun makeUseCase(shouldFailUpsert: Boolean = false): Pair<SaveShiftScheduleUseCase, FakeShiftRepository> {
        val repo = FakeShiftRepository().also { it.shouldFailUpsert = shouldFailUpsert }
        return SaveShiftScheduleUseCase(repo) to repo
    }

    private fun shift(
        id: String = "shift-1",
        employeeId: String = "emp-1",
        shiftDate: String = "2026-03-28",
        startTime: String = "09:00",
        endTime: String = "17:00",
    ) = ShiftSchedule(
        id = id,
        employeeId = employeeId,
        storeId = "store-1",
        shiftDate = shiftDate,
        startTime = startTime,
        endTime = endTime,
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun `A - blank shiftDate returns REQUIRED error`() = runTest {
        val (useCase, _) = makeUseCase()
        val result = useCase(shift(shiftDate = ""))
        assertIs<Result.Error>(result)
        val ex = result.exception as ValidationException
        assertEquals("REQUIRED", ex.rule)
        assertEquals("shiftDate", ex.field)
    }

    @Test
    fun `B - invalid startTime format returns FORMAT error`() = runTest {
        val (useCase, _) = makeUseCase()
        val result = useCase(shift(startTime = "9:00"))  // missing leading zero
        assertIs<Result.Error>(result)
        val ex = result.exception as ValidationException
        assertEquals("FORMAT", ex.rule)
        assertEquals("startTime", ex.field)
    }

    @Test
    fun `C - invalid endTime format returns FORMAT error`() = runTest {
        val (useCase, _) = makeUseCase()
        val result = useCase(shift(endTime = "25:00"))  // hour > 23
        assertIs<Result.Error>(result)
        val ex = result.exception as ValidationException
        assertEquals("FORMAT", ex.rule)
        assertEquals("endTime", ex.field)
    }

    @Test
    fun `D - endTime equal to startTime returns TIME_ORDER error`() = runTest {
        val (useCase, _) = makeUseCase()
        val result = useCase(shift(startTime = "09:00", endTime = "09:00"))
        assertIs<Result.Error>(result)
        val ex = result.exception as ValidationException
        assertEquals("TIME_ORDER", ex.rule)
    }

    @Test
    fun `E - endTime before startTime returns TIME_ORDER error`() = runTest {
        val (useCase, _) = makeUseCase()
        val result = useCase(shift(startTime = "17:00", endTime = "09:00"))
        assertIs<Result.Error>(result)
        assertEquals("TIME_ORDER", (result.exception as ValidationException).rule)
    }

    @Test
    fun `F - valid non-overlapping shift is saved`() = runTest {
        val (useCase, repo) = makeUseCase()
        val result = useCase(shift(startTime = "09:00", endTime = "17:00"))
        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, repo.shifts.size)
    }

    @Test
    fun `G - overlapping shift returns SHIFT_OVERLAP error`() = runTest {
        val (useCase, repo) = makeUseCase()
        // Insert existing 09:00–17:00 shift for same employee/date
        repo.shifts.add(shift(id = "existing-1", startTime = "09:00", endTime = "17:00"))
        // New shift 12:00–20:00 overlaps (starts inside existing)
        val result = useCase(shift(id = "new-1", startTime = "12:00", endTime = "20:00"))
        assertIs<Result.Error>(result)
        assertEquals("SHIFT_OVERLAP", (result.exception as ValidationException).rule)
    }

    @Test
    fun `H - adjacent shifts touching at boundary are allowed`() = runTest {
        val (useCase, repo) = makeUseCase()
        // Existing 09:00–13:00
        repo.shifts.add(shift(id = "morning", startTime = "09:00", endTime = "13:00"))
        // New 13:00–17:00 starts exactly when existing ends — NOT an overlap
        val result = useCase(shift(id = "afternoon", startTime = "13:00", endTime = "17:00"))
        assertIs<Result.Success<Unit>>(result)
    }

    @Test
    fun `I - editing a shift excludes itself from overlap check`() = runTest {
        val (useCase, repo) = makeUseCase()
        // Same shift is already persisted — editing it should not trigger overlap
        repo.shifts.add(shift(id = "shift-1", startTime = "09:00", endTime = "17:00"))
        val updated = shift(id = "shift-1", startTime = "08:00", endTime = "16:00")
        val result = useCase(updated)
        assertIs<Result.Success<Unit>>(result)
    }

    @Test
    fun `J - 2359 is valid HHMM format and passes format check`() = runTest {
        val (useCase, repo) = makeUseCase()
        val result = useCase(shift(startTime = "22:00", endTime = "23:59"))
        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, repo.shifts.size)
    }

    @Test
    fun `K - 0000 start time is valid HHMM format`() = runTest {
        val (useCase, repo) = makeUseCase()
        val result = useCase(shift(startTime = "00:00", endTime = "08:00"))
        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, repo.shifts.size)
    }
}
