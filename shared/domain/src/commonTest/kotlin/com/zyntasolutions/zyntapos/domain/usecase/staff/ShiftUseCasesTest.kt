package com.zyntasolutions.zyntapos.domain.usecase.staff

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.ShiftSwapStatus
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeShiftRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeShiftSwapRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildShiftSchedule
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildShiftSwapRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for shift-management and shift-swap use cases:
 * [GetShiftScheduleUseCase], [DeleteShiftScheduleUseCase], [SaveShiftScheduleUseCase],
 * [RequestShiftSwapUseCase], [RespondToShiftSwapUseCase], [ApproveShiftSwapUseCase].
 */
class ShiftUseCasesTest {

    // ─────────────────────────────────────────────────────────────────────────
    // GetShiftScheduleUseCase
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `getShiftSchedule_emptyRepository_emitsEmptyList`() = runTest {
        GetShiftScheduleUseCase(FakeShiftRepository())("store-01", "2026-03-09", "2026-03-15").test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getShiftSchedule_filtersCorrectStoreAndWeek`() = runTest {
        val repo = FakeShiftRepository()
        repo.shifts.add(buildShiftSchedule(id = "s1", storeId = "store-01", shiftDate = "2026-03-10"))
        repo.shifts.add(buildShiftSchedule(id = "s2", storeId = "store-01", shiftDate = "2026-03-20")) // outside week
        repo.shifts.add(buildShiftSchedule(id = "s3", storeId = "store-02", shiftDate = "2026-03-10")) // wrong store

        GetShiftScheduleUseCase(repo)("store-01", "2026-03-09", "2026-03-15").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("s1", list.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getShiftSchedule_multipleShiftsInWeek_returnsAll`() = runTest {
        val repo = FakeShiftRepository()
        repo.shifts.add(buildShiftSchedule(id = "s1", storeId = "store-01", shiftDate = "2026-03-09"))
        repo.shifts.add(buildShiftSchedule(id = "s2", storeId = "store-01", shiftDate = "2026-03-12"))
        repo.shifts.add(buildShiftSchedule(id = "s3", storeId = "store-01", shiftDate = "2026-03-15"))

        GetShiftScheduleUseCase(repo)("store-01", "2026-03-09", "2026-03-15").test {
            assertEquals(3, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DeleteShiftScheduleUseCase
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `deleteShiftSchedule_existingId_deletesAndReturnsSuccess`() = runTest {
        val repo = FakeShiftRepository()
        repo.shifts.add(buildShiftSchedule(id = "s1"))

        val result = DeleteShiftScheduleUseCase(repo)("s1")
        assertIs<Result.Success<*>>(result)
        assertTrue(repo.shifts.isEmpty())
    }

    @Test
    fun `deleteShiftSchedule_repositoryFailure_returnsError`() = runTest {
        val repo = FakeShiftRepository().also { it.shouldFailDelete = true }
        val result = DeleteShiftScheduleUseCase(repo)("s1")
        assertIs<Result.Error>(result)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SaveShiftScheduleUseCase
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `saveShiftSchedule_validShift_persists`() = runTest {
        val repo = FakeShiftRepository()
        val shift = buildShiftSchedule(shiftDate = "2026-03-10", startTime = "09:00", endTime = "17:00")

        val result = SaveShiftScheduleUseCase(repo)(shift)
        assertIs<Result.Success<*>>(result)
        assertEquals(1, repo.shifts.size)
    }

    @Test
    fun `saveShiftSchedule_blankShiftDate_returnsValidationError`() = runTest {
        val result = SaveShiftScheduleUseCase(FakeShiftRepository())(
            buildShiftSchedule(shiftDate = ""),
        )
        assertIs<Result.Error>(result)
        assertEquals("shiftDate", ((result as Result.Error).exception as ValidationException).field)
    }

    @Test
    fun `saveShiftSchedule_invalidStartTimeFormat_returnsValidationError`() = runTest {
        val result = SaveShiftScheduleUseCase(FakeShiftRepository())(
            buildShiftSchedule(startTime = "9:00", endTime = "17:00"),
        )
        assertIs<Result.Error>(result)
        assertEquals("startTime", ((result as Result.Error).exception as ValidationException).field)
    }

    @Test
    fun `saveShiftSchedule_invalidEndTimeFormat_returnsValidationError`() = runTest {
        val result = SaveShiftScheduleUseCase(FakeShiftRepository())(
            buildShiftSchedule(startTime = "09:00", endTime = "5pm"),
        )
        assertIs<Result.Error>(result)
        assertEquals("endTime", ((result as Result.Error).exception as ValidationException).field)
    }

    @Test
    fun `saveShiftSchedule_endTimeBeforeStartTime_returnsTimeOrderError`() = runTest {
        val result = SaveShiftScheduleUseCase(FakeShiftRepository())(
            buildShiftSchedule(startTime = "17:00", endTime = "09:00"),
        )
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("endTime", ex.field)
        assertEquals("TIME_ORDER", ex.rule)
    }

    @Test
    fun `saveShiftSchedule_overlapWithExistingShift_returnsOverlapError`() = runTest {
        val repo = FakeShiftRepository()
        // Existing shift: 08:00–12:00 on same date for same employee
        repo.shifts.add(buildShiftSchedule(id = "existing", startTime = "08:00", endTime = "12:00"))

        // New shift: 10:00–14:00 overlaps with existing
        val result = SaveShiftScheduleUseCase(repo)(
            buildShiftSchedule(id = "new-shift", startTime = "10:00", endTime = "14:00"),
        )
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("SHIFT_OVERLAP", ex.rule)
    }

    @Test
    fun `saveShiftSchedule_editOwnShift_noOverlapWithSelf`() = runTest {
        val repo = FakeShiftRepository()
        val existing = buildShiftSchedule(id = "s1", startTime = "09:00", endTime = "17:00")
        repo.shifts.add(existing)

        // Re-saving same shift with updated times — should not conflict with itself
        val updated = existing.copy(startTime = "10:00", endTime = "18:00")
        val result = SaveShiftScheduleUseCase(repo)(updated)
        assertIs<Result.Success<*>>(result)
    }

    @Test
    fun `saveShiftSchedule_repositoryGetAllFails_returnsError`() = runTest {
        val repo = FakeShiftRepository().also { it.shouldFailGetAll = true }
        val result = SaveShiftScheduleUseCase(repo)(
            buildShiftSchedule(startTime = "09:00", endTime = "17:00"),
        )
        assertIs<Result.Error>(result)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RequestShiftSwapUseCase
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `requestShiftSwap_validRequest_persists`() = runTest {
        val repo = FakeShiftSwapRepository()
        val request = buildShiftSwapRequest(
            requestingEmployeeId = "emp-01",
            targetEmployeeId = "emp-02",
            requestingShiftId = "shift-01",
            targetShiftId = "shift-02",
            reason = "Personal appointment",
        )

        val result = RequestShiftSwapUseCase(repo)(request)
        assertIs<Result.Success<*>>(result)
        assertEquals(1, repo.requests.size)
    }

    @Test
    fun `requestShiftSwap_sameEmployee_returnsValidationError`() = runTest {
        val result = RequestShiftSwapUseCase(FakeShiftSwapRepository())(
            buildShiftSwapRequest(requestingEmployeeId = "emp-01", targetEmployeeId = "emp-01"),
        )
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("targetEmployeeId", ex.field)
        assertEquals("DIFFERENT_EMPLOYEE", ex.rule)
    }

    @Test
    fun `requestShiftSwap_sameShift_returnsValidationError`() = runTest {
        val result = RequestShiftSwapUseCase(FakeShiftSwapRepository())(
            buildShiftSwapRequest(requestingShiftId = "shift-01", targetShiftId = "shift-01"),
        )
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("targetShiftId", ex.field)
        assertEquals("DIFFERENT_SHIFT", ex.rule)
    }

    @Test
    fun `requestShiftSwap_blankReason_returnsValidationError`() = runTest {
        val result = RequestShiftSwapUseCase(FakeShiftSwapRepository())(
            buildShiftSwapRequest(reason = ""),
        )
        assertIs<Result.Error>(result)
        assertEquals("reason", ((result as Result.Error).exception as ValidationException).field)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RespondToShiftSwapUseCase
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `respondToShiftSwap_accept_setsTargetAcceptedStatus`() = runTest {
        val repo = FakeShiftSwapRepository()
        repo.requests.add(buildShiftSwapRequest(id = "swap-01", status = ShiftSwapStatus.PENDING, targetEmployeeId = "emp-02"))

        val result = RespondToShiftSwapUseCase(repo)(
            id = "swap-01",
            targetEmployeeId = "emp-02",
            accept = true,
            updatedAt = 2_000_000L,
        )
        assertIs<Result.Success<*>>(result)
        assertEquals(ShiftSwapStatus.TARGET_ACCEPTED, repo.lastUpdatedStatus)
    }

    @Test
    fun `respondToShiftSwap_decline_setsRejectedStatus`() = runTest {
        val repo = FakeShiftSwapRepository()
        repo.requests.add(buildShiftSwapRequest(id = "swap-01", status = ShiftSwapStatus.PENDING, targetEmployeeId = "emp-02"))

        val result = RespondToShiftSwapUseCase(repo)(
            id = "swap-01",
            targetEmployeeId = "emp-02",
            accept = false,
            updatedAt = 2_000_000L,
        )
        assertIs<Result.Success<*>>(result)
        assertEquals(ShiftSwapStatus.REJECTED, repo.lastUpdatedStatus)
    }

    @Test
    fun `respondToShiftSwap_notFound_returnsError`() = runTest {
        val result = RespondToShiftSwapUseCase(FakeShiftSwapRepository())(
            id = "unknown",
            targetEmployeeId = "emp-02",
            accept = true,
            updatedAt = 2_000_000L,
        )
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("id", ex.field)
        assertEquals("EXISTS", ex.rule)
    }

    @Test
    fun `respondToShiftSwap_alreadyApproved_returnsStatusError`() = runTest {
        val repo = FakeShiftSwapRepository()
        repo.requests.add(buildShiftSwapRequest(id = "swap-01", status = ShiftSwapStatus.MANAGER_APPROVED, targetEmployeeId = "emp-02"))

        val result = RespondToShiftSwapUseCase(repo)(
            id = "swap-01",
            targetEmployeeId = "emp-02",
            accept = true,
            updatedAt = 2_000_000L,
        )
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("status", ex.field)
        assertEquals("STATUS_PENDING", ex.rule)
    }

    @Test
    fun `respondToShiftSwap_wrongEmployee_returnsAuthorizationError`() = runTest {
        val repo = FakeShiftSwapRepository()
        repo.requests.add(buildShiftSwapRequest(id = "swap-01", status = ShiftSwapStatus.PENDING, targetEmployeeId = "emp-02"))

        val result = RespondToShiftSwapUseCase(repo)(
            id = "swap-01",
            targetEmployeeId = "emp-99",  // different from request's target
            accept = true,
            updatedAt = 2_000_000L,
        )
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("targetEmployeeId", ex.field)
        assertEquals("AUTHORIZED", ex.rule)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ApproveShiftSwapUseCase
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `approveShiftSwap_success_swapsEmployeesAndApprovesRequest`() = runTest {
        val swapRepo = FakeShiftSwapRepository()
        val shiftRepo = FakeShiftRepository()

        val requestingShift = buildShiftSchedule(id = "shift-01", employeeId = "emp-01")
        val targetShift = buildShiftSchedule(id = "shift-02", employeeId = "emp-02")
        shiftRepo.shifts.add(requestingShift)
        shiftRepo.shifts.add(targetShift)

        val swap = buildShiftSwapRequest(
            id = "swap-01",
            requestingEmployeeId = "emp-01",
            targetEmployeeId = "emp-02",
            requestingShiftId = "shift-01",
            targetShiftId = "shift-02",
            status = ShiftSwapStatus.TARGET_ACCEPTED,
        )
        swapRepo.requests.add(swap)

        val result = ApproveShiftSwapUseCase(swapRepo, shiftRepo)(
            id = "swap-01",
            managerNotes = "Approved",
            updatedAt = 3_000_000L,
        )
        assertIs<Result.Success<*>>(result)

        // Shifts should have swapped employees
        val updatedShift1 = shiftRepo.shifts.first { it.id == "shift-01" }
        val updatedShift2 = shiftRepo.shifts.first { it.id == "shift-02" }
        assertEquals("emp-02", updatedShift1.employeeId)
        assertEquals("emp-01", updatedShift2.employeeId)

        // Swap request should be approved
        assertEquals(ShiftSwapStatus.MANAGER_APPROVED, swapRepo.lastUpdatedStatus)
    }

    @Test
    fun `approveShiftSwap_notFound_returnsExistsError`() = runTest {
        val result = ApproveShiftSwapUseCase(FakeShiftSwapRepository(), FakeShiftRepository())(
            id = "unknown",
            updatedAt = 3_000_000L,
        )
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("id", ex.field)
    }

    @Test
    fun `approveShiftSwap_wrongStatus_returnsStatusError`() = runTest {
        val swapRepo = FakeShiftSwapRepository()
        swapRepo.requests.add(buildShiftSwapRequest(id = "swap-01", status = ShiftSwapStatus.PENDING))

        val result = ApproveShiftSwapUseCase(swapRepo, FakeShiftRepository())(
            id = "swap-01",
            updatedAt = 3_000_000L,
        )
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("status", ex.field)
        assertEquals("STATUS_TARGET_ACCEPTED", ex.rule)
    }

    @Test
    fun `approveShiftSwap_requestingShiftMissing_returnsExistsError`() = runTest {
        val swapRepo = FakeShiftSwapRepository()
        val shiftRepo = FakeShiftRepository()

        // Only target shift exists, requesting shift is missing
        shiftRepo.shifts.add(buildShiftSchedule(id = "shift-02", employeeId = "emp-02"))

        swapRepo.requests.add(
            buildShiftSwapRequest(
                id = "swap-01",
                requestingShiftId = "shift-01",  // doesn't exist in repo
                targetShiftId = "shift-02",
                status = ShiftSwapStatus.TARGET_ACCEPTED,
            ),
        )

        val result = ApproveShiftSwapUseCase(swapRepo, shiftRepo)(
            id = "swap-01",
            updatedAt = 3_000_000L,
        )
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("requestingShiftId", ex.field)
    }

    @Test
    fun `approveShiftSwap_targetShiftMissing_returnsExistsError`() = runTest {
        val swapRepo = FakeShiftSwapRepository()
        val shiftRepo = FakeShiftRepository()

        // Only requesting shift exists, target shift is missing
        shiftRepo.shifts.add(buildShiftSchedule(id = "shift-01", employeeId = "emp-01"))

        swapRepo.requests.add(
            buildShiftSwapRequest(
                id = "swap-01",
                requestingShiftId = "shift-01",
                targetShiftId = "shift-02",  // doesn't exist in repo
                status = ShiftSwapStatus.TARGET_ACCEPTED,
            ),
        )

        val result = ApproveShiftSwapUseCase(swapRepo, shiftRepo)(
            id = "swap-01",
            updatedAt = 3_000_000L,
        )
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("targetShiftId", ex.field)
    }
}
