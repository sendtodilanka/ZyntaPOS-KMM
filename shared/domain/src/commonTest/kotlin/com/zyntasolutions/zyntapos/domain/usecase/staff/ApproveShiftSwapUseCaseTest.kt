package com.zyntasolutions.zyntapos.domain.usecase.staff

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.ShiftSwapRequest
import com.zyntasolutions.zyntapos.domain.model.ShiftSwapStatus
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeShiftRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeShiftSwapRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — ApproveShiftSwapUseCase Unit Tests (commonTest)
 *
 * Coverage:
 *  A.  Swap request not found returns EXISTS error
 *  B.  Swap request in PENDING status returns STATUS_TARGET_ACCEPTED error
 *  C.  Swap request in MANAGER_APPROVED status returns STATUS_TARGET_ACCEPTED error
 *  D.  Requesting shift not found returns EXISTS error
 *  E.  Target shift not found returns EXISTS error
 *  F.  Successful approval swaps employee IDs on both shifts
 *  G.  Successful approval marks swap status as MANAGER_APPROVED
 *  H.  Shift update failure propagates
 *  I.  Swap status update failure propagates
 */
class ApproveShiftSwapUseCaseTest {

    private fun makeUseCase(
        swapShouldFailUpdate: Boolean = false,
        shiftShouldFailUpsert: Boolean = false,
    ): Triple<ApproveShiftSwapUseCase, FakeShiftSwapRepository, FakeShiftRepository> {
        val swapRepo = FakeShiftSwapRepository().also { it.shouldFailUpdate = swapShouldFailUpdate }
        val shiftRepo = FakeShiftRepository().also { it.shouldFailUpdate = shiftShouldFailUpsert }
        return Triple(ApproveShiftSwapUseCase(swapRepo, shiftRepo), swapRepo, shiftRepo)
    }

    private fun swapRequest(
        id: String = "swap-1",
        status: ShiftSwapStatus = ShiftSwapStatus.TARGET_ACCEPTED,
        requestingShiftId: String = "shift-req",
        targetShiftId: String = "shift-tgt",
        requestingEmployeeId: String = "emp-req",
        targetEmployeeId: String = "emp-tgt",
    ) = ShiftSwapRequest(
        id = id,
        requestingEmployeeId = requestingEmployeeId,
        targetEmployeeId = targetEmployeeId,
        requestingShiftId = requestingShiftId,
        targetShiftId = targetShiftId,
        status = status,
    )

    private fun shift(
        id: String,
        employeeId: String,
    ) = com.zyntasolutions.zyntapos.domain.model.ShiftSchedule(
        id = id,
        employeeId = employeeId,
        storeId = "store-1",
        shiftDate = "2026-03-28",
        startTime = "09:00",
        endTime = "17:00",
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun `A - swap request not found returns EXISTS error`() = runTest {
        val (useCase, _, _) = makeUseCase()
        // swap repo is empty — nothing with id "swap-99"
        val result = useCase(id = "swap-99", updatedAt = 0L)
        assertIs<Result.Error>(result)
        val ex = result.exception as ValidationException
        assertEquals("EXISTS", ex.rule)
        assertEquals("id", ex.field)
    }

    @Test
    fun `B - swap in PENDING status returns STATUS_TARGET_ACCEPTED error`() = runTest {
        val (useCase, swapRepo, _) = makeUseCase()
        swapRepo.requests.add(swapRequest(status = ShiftSwapStatus.PENDING))
        val result = useCase(id = "swap-1", updatedAt = 0L)
        assertIs<Result.Error>(result)
        assertEquals("STATUS_TARGET_ACCEPTED", (result.exception as ValidationException).rule)
    }

    @Test
    fun `C - swap in MANAGER_APPROVED status returns STATUS_TARGET_ACCEPTED error`() = runTest {
        val (useCase, swapRepo, _) = makeUseCase()
        swapRepo.requests.add(swapRequest(status = ShiftSwapStatus.MANAGER_APPROVED))
        val result = useCase(id = "swap-1", updatedAt = 0L)
        assertIs<Result.Error>(result)
        assertEquals("STATUS_TARGET_ACCEPTED", (result.exception as ValidationException).rule)
    }

    @Test
    fun `D - requesting shift not found returns EXISTS error`() = runTest {
        val (useCase, swapRepo, _) = makeUseCase()
        swapRepo.requests.add(swapRequest()) // shifts NOT added to shiftRepo
        val result = useCase(id = "swap-1", updatedAt = 0L)
        assertIs<Result.Error>(result)
        assertEquals("EXISTS", (result.exception as ValidationException).rule)
    }

    @Test
    fun `E - target shift not found returns EXISTS error`() = runTest {
        val (useCase, swapRepo, shiftRepo) = makeUseCase()
        swapRepo.requests.add(swapRequest(requestingShiftId = "shift-req", targetShiftId = "shift-tgt"))
        shiftRepo.shifts.add(shift("shift-req", "emp-req"))  // only requesting shift, not target
        val result = useCase(id = "swap-1", updatedAt = 0L)
        assertIs<Result.Error>(result)
        assertEquals("EXISTS", (result.exception as ValidationException).rule)
    }

    @Test
    fun `F - successful approval swaps employee IDs on both shifts`() = runTest {
        val (useCase, swapRepo, shiftRepo) = makeUseCase()
        swapRepo.requests.add(
            swapRequest(requestingShiftId = "shift-req", targetShiftId = "shift-tgt",
                requestingEmployeeId = "emp-req", targetEmployeeId = "emp-tgt")
        )
        shiftRepo.shifts.add(shift("shift-req", "emp-req"))
        shiftRepo.shifts.add(shift("shift-tgt", "emp-tgt"))

        val result = useCase(id = "swap-1", updatedAt = 100L)
        assertIs<Result.Success<Unit>>(result)

        // After swap: shift-req should have emp-tgt, shift-tgt should have emp-req
        assertEquals("emp-tgt", shiftRepo.shifts.first { it.id == "shift-req" }.employeeId)
        assertEquals("emp-req", shiftRepo.shifts.first { it.id == "shift-tgt" }.employeeId)
    }

    @Test
    fun `G - successful approval marks swap as MANAGER_APPROVED`() = runTest {
        val (useCase, swapRepo, shiftRepo) = makeUseCase()
        swapRepo.requests.add(swapRequest())
        shiftRepo.shifts.add(shift("shift-req", "emp-req"))
        shiftRepo.shifts.add(shift("shift-tgt", "emp-tgt"))

        useCase(id = "swap-1", updatedAt = 0L)

        assertEquals(ShiftSwapStatus.MANAGER_APPROVED, swapRepo.lastUpdatedStatus)
        assertEquals("swap-1", swapRepo.lastUpdatedId)
    }

    @Test
    fun `H - shift update failure propagates`() = runTest {
        val (useCase, swapRepo, shiftRepo) = makeUseCase(shiftShouldFailUpsert = true)
        swapRepo.requests.add(swapRequest())
        shiftRepo.shifts.add(shift("shift-req", "emp-req"))
        shiftRepo.shifts.add(shift("shift-tgt", "emp-tgt"))

        val result = useCase(id = "swap-1", updatedAt = 0L)
        assertIs<Result.Error>(result)
    }

    @Test
    fun `I - swap status update failure propagates`() = runTest {
        val (useCase, swapRepo, shiftRepo) = makeUseCase(swapShouldFailUpdate = true)
        swapRepo.requests.add(swapRequest())
        shiftRepo.shifts.add(shift("shift-req", "emp-req"))
        shiftRepo.shifts.add(shift("shift-tgt", "emp-tgt"))

        val result = useCase(id = "swap-1", updatedAt = 0L)
        assertIs<Result.Error>(result)
    }
}
