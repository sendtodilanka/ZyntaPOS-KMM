package com.zyntasolutions.zyntapos.domain.model

/**
 * A leave request submitted by an employee through the leave management workflow.
 *
 * Extends the basic leave tracking with additional leave types (maternity, paternity,
 * bereavement) and a CANCELLED status for employee-initiated withdrawals.
 *
 * @property id Unique identifier (UUID v4).
 * @property employeeId FK to [Employee].
 * @property leaveType Category of leave being requested.
 * @property startDate ISO date string: YYYY-MM-DD.
 * @property endDate ISO date string: YYYY-MM-DD (inclusive).
 * @property reason Employee's stated reason for the leave.
 * @property status Current approval workflow status.
 * @property approverNotes Optional notes from the approver when approving or rejecting.
 * @property createdAt Epoch millis of record creation.
 * @property updatedAt Epoch millis of last update.
 */
data class LeaveRequest(
    val id: String,
    val employeeId: String,
    val leaveType: LeaveRequestType,
    val startDate: String,
    val endDate: String,
    val reason: String,
    val status: LeaveRequestStatus = LeaveRequestStatus.PENDING,
    val approverNotes: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/** Category of leave for [LeaveRequest]. */
enum class LeaveRequestType {
    ANNUAL,
    SICK,
    PERSONAL,
    UNPAID,
    MATERNITY,
    PATERNITY,
    BEREAVEMENT,
}

/** Approval workflow status for a [LeaveRequest]. */
enum class LeaveRequestStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED,
}
