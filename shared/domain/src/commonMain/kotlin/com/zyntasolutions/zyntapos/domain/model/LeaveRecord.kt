package com.zyntasolutions.zyntapos.domain.model

/**
 * A leave request submitted by an employee.
 *
 * @property id Unique identifier (UUID v4).
 * @property employeeId FK to [Employee].
 * @property leaveType Type of leave being requested.
 * @property startDate ISO date string: YYYY-MM-DD.
 * @property endDate ISO date string: YYYY-MM-DD (inclusive).
 * @property reason Employee's stated reason for the leave.
 * @property status Current approval workflow status.
 * @property approvedBy User ID of the approver/rejector.
 * @property approvedAt Epoch millis of approval or rejection decision.
 * @property rejectionReason Explanation provided when rejecting.
 * @property createdAt Epoch millis of record creation.
 * @property updatedAt Epoch millis of last update.
 */
data class LeaveRecord(
    val id: String,
    val employeeId: String,
    val leaveType: LeaveType,
    val startDate: String,
    val endDate: String,
    val reason: String? = null,
    val status: LeaveStatus = LeaveStatus.PENDING,
    val approvedBy: String? = null,
    val approvedAt: Long? = null,
    val rejectionReason: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Category of leave. */
enum class LeaveType {
    SICK,
    ANNUAL,
    PERSONAL,
    UNPAID,
}

/** Approval workflow status for a leave request. */
enum class LeaveStatus {
    PENDING,
    APPROVED,
    REJECTED,
}
