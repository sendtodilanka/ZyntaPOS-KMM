package com.zyntasolutions.zyntapos.domain.usecase.staff

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.LeaveRequestStatus
import com.zyntasolutions.zyntapos.domain.model.LeaveStatus
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeLeaveRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildLeaveRecord
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildLeaveRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for [SubmitLeaveRequestUseCase], [ApproveLeaveUseCase], and [RejectLeaveUseCase].
 */
class LeaveManagementUseCasesTest {

    private lateinit var fakeLeaveRepo: FakeLeaveRepository
    private lateinit var submitLeaveUseCase: SubmitLeaveRequestUseCase
    private lateinit var approveLeaveUseCase: ApproveLeaveUseCase
    private lateinit var rejectLeaveUseCase: RejectLeaveUseCase

    @BeforeTest
    fun setUp() {
        fakeLeaveRepo = FakeLeaveRepository()
        submitLeaveUseCase = SubmitLeaveRequestUseCase(fakeLeaveRepo)
        approveLeaveUseCase = ApproveLeaveUseCase(fakeLeaveRepo)
        rejectLeaveUseCase = RejectLeaveUseCase(fakeLeaveRepo)
    }

    // ─── SubmitLeaveRequestUseCase ────────────────────────────────────────────

    @Test
    fun `submitLeaveRequest success with valid dates`() = runTest {
        val record = buildLeaveRecord(startDate = "2026-03-01", endDate = "2026-03-05")

        val result = submitLeaveUseCase(record)

        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, fakeLeaveRepo.leaveRecords.size)
        assertEquals("leave-01", fakeLeaveRepo.leaveRecords.first().id)
    }

    @Test
    fun `submitLeaveRequest success with same start and end date`() = runTest {
        // Single-day leave is valid
        val record = buildLeaveRecord(startDate = "2026-03-10", endDate = "2026-03-10")

        val result = submitLeaveUseCase(record)

        assertIs<Result.Success<Unit>>(result)
    }

    @Test
    fun `submitLeaveRequest returns REQUIRED when startDate is blank`() = runTest {
        val record = buildLeaveRecord(startDate = "", endDate = "2026-03-05")

        val result = submitLeaveUseCase(record)

        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception
        assertIs<ValidationException>(ex)
        assertEquals("REQUIRED", ex.rule)
        assertEquals("startDate", ex.field)
    }

    @Test
    fun `submitLeaveRequest returns REQUIRED when endDate is blank`() = runTest {
        val record = buildLeaveRecord(startDate = "2026-03-01", endDate = "")

        val result = submitLeaveUseCase(record)

        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception
        assertIs<ValidationException>(ex)
        assertEquals("REQUIRED", ex.rule)
        assertEquals("endDate", ex.field)
    }

    @Test
    fun `submitLeaveRequest returns DATE_ORDER when endDate is before startDate`() = runTest {
        // endDate < startDate violates DATE_ORDER rule
        val record = buildLeaveRecord(startDate = "2026-03-10", endDate = "2026-03-05")

        val result = submitLeaveUseCase(record)

        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception
        assertIs<ValidationException>(ex)
        assertEquals("DATE_ORDER", ex.rule)
        assertEquals("endDate", ex.field)
    }

    @Test
    fun `submitLeaveRequest does not persist record on validation failure`() = runTest {
        val record = buildLeaveRecord(startDate = "", endDate = "2026-03-05")

        submitLeaveUseCase(record)

        assertTrue(fakeLeaveRepo.leaveRecords.isEmpty())
    }

    @Test
    fun `submitLeaveRequest propagates repository error`() = runTest {
        fakeLeaveRepo.shouldFailInsert = true
        val record = buildLeaveRecord(startDate = "2026-03-01", endDate = "2026-03-05")

        val result = submitLeaveUseCase(record)

        assertIs<Result.Error>(result)
        assertTrue(fakeLeaveRepo.leaveRecords.isEmpty())
    }

    // ─── ApproveLeaveUseCase ──────────────────────────────────────────────────

    @Test
    fun `approveLeave delegates to repository with APPROVED status`() = runTest {
        fakeLeaveRepo.leaveRequests.add(buildLeaveRequest(id = "leave-01"))

        val result = approveLeaveUseCase(
            id = "leave-01",
            approve = true,
            approverNotes = null,
            updatedAt = 1_500_001L,
        )

        assertIs<Result.Success<Unit>>(result)
        val req = fakeLeaveRepo.leaveRequests.first()
        assertEquals("leave-01", req.id)
        assertEquals(LeaveRequestStatus.APPROVED, req.status)
        assertEquals(1_500_001L, req.updatedAt)
    }

    @Test
    fun `approveLeave passes null approverNotes`() = runTest {
        fakeLeaveRepo.leaveRequests.add(buildLeaveRequest(id = "leave-01"))

        approveLeaveUseCase(
            id = "leave-01",
            approve = true,
            approverNotes = null,
            updatedAt = 1_500_001L,
        )

        val req = fakeLeaveRepo.leaveRequests.first()
        assertEquals(null, req.approverNotes)
    }

    @Test
    fun `approveLeave propagates repository error for missing request`() = runTest {
        // No leave request added — getLeaveRequestById returns null
        val result = approveLeaveUseCase(
            id = "leave-01",
            approve = true,
            approverNotes = null,
            updatedAt = 1_500_001L,
        )

        assertIs<Result.Error>(result)
    }

    @Test
    fun `approveLeave updates request status in fake repository`() = runTest {
        fakeLeaveRepo.leaveRequests.add(buildLeaveRequest(id = "leave-01"))

        approveLeaveUseCase(
            id = "leave-01",
            approve = true,
            approverNotes = null,
            updatedAt = 1_500_001L,
        )

        val updated = fakeLeaveRepo.leaveRequests.first()
        assertEquals(LeaveRequestStatus.APPROVED, updated.status)
    }

    // ─── RejectLeaveUseCase ───────────────────────────────────────────────────

    @Test
    fun `rejectLeave success with valid reason`() = runTest {
        val result = rejectLeaveUseCase(
            id = "leave-01",
            rejectedBy = "manager-01",
            rejectedAt = 1_600_000L,
            reason = "Insufficient coverage during the period.",
            updatedAt = 1_600_001L,
        )

        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, fakeLeaveRepo.updateStatusCalls.size)
        val call = fakeLeaveRepo.updateStatusCalls.first()
        assertEquals("leave-01", call.id)
        assertEquals(LeaveStatus.REJECTED, call.status)
        assertEquals("manager-01", call.decidedBy)
        assertEquals("Insufficient coverage during the period.", call.rejectionReason)
        assertEquals(1_600_000L, call.decidedAt)
        assertEquals(1_600_001L, call.updatedAt)
    }

    @Test
    fun `rejectLeave returns REQUIRED when reason is blank`() = runTest {
        val result = rejectLeaveUseCase(
            id = "leave-01",
            rejectedBy = "manager-01",
            rejectedAt = 1_600_000L,
            reason = "",
            updatedAt = 1_600_001L,
        )

        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception
        assertIs<ValidationException>(ex)
        assertEquals("REQUIRED", ex.rule)
        assertEquals("reason", ex.field)
    }

    @Test
    fun `rejectLeave returns REQUIRED when reason is only whitespace`() = runTest {
        val result = rejectLeaveUseCase(
            id = "leave-01",
            rejectedBy = "manager-01",
            rejectedAt = 1_600_000L,
            reason = "   ",
            updatedAt = 1_600_001L,
        )

        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception
        assertIs<ValidationException>(ex)
        assertEquals("REQUIRED", ex.rule)
    }

    @Test
    fun `rejectLeave does not call repository when reason is blank`() = runTest {
        rejectLeaveUseCase(
            id = "leave-01",
            rejectedBy = "manager-01",
            rejectedAt = 1_600_000L,
            reason = "",
            updatedAt = 1_600_001L,
        )

        assertTrue(fakeLeaveRepo.updateStatusCalls.isEmpty())
    }

    @Test
    fun `rejectLeave propagates repository error`() = runTest {
        fakeLeaveRepo.shouldFailUpdateStatus = true

        val result = rejectLeaveUseCase(
            id = "leave-01",
            rejectedBy = "manager-01",
            rejectedAt = 1_600_000L,
            reason = "Insufficient coverage.",
            updatedAt = 1_600_001L,
        )

        assertIs<Result.Error>(result)
    }

    @Test
    fun `rejectLeave updates record status and rejection reason in fake repository`() = runTest {
        val record = buildLeaveRecord(id = "leave-01", status = LeaveStatus.PENDING)
        fakeLeaveRepo.leaveRecords.add(record)

        rejectLeaveUseCase(
            id = "leave-01",
            rejectedBy = "manager-01",
            rejectedAt = 1_600_000L,
            reason = "Operational needs.",
            updatedAt = 1_600_001L,
        )

        val updated = fakeLeaveRepo.leaveRecords.first()
        assertEquals(LeaveStatus.REJECTED, updated.status)
        assertEquals("Operational needs.", updated.rejectionReason)
    }
}
