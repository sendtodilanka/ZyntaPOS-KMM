package com.zyntasolutions.zyntapos.domain.model

/**
 * A request to swap shifts between two employees.
 *
 * The workflow is:
 * 1. Requesting employee creates a swap request ([PENDING]).
 * 2. Target employee accepts ([TARGET_ACCEPTED]) or the request is cancelled/rejected.
 * 3. Manager approves ([MANAGER_APPROVED]), which triggers the actual schedule swap.
 *
 * @property id Unique identifier (UUID v4).
 * @property requestingEmployeeId Employee who initiated the swap.
 * @property targetEmployeeId Employee who holds the desired shift.
 * @property requestingShiftId Shift the requesting employee is offering.
 * @property targetShiftId Shift the requesting employee wants.
 * @property status Current workflow status.
 * @property reason Employee-provided reason for the swap.
 * @property managerNotes Optional notes from the approving manager.
 * @property createdAt Epoch millis of record creation.
 * @property updatedAt Epoch millis of last update.
 */
data class ShiftSwapRequest(
    val id: String,
    val requestingEmployeeId: String,
    val targetEmployeeId: String,
    val requestingShiftId: String,
    val targetShiftId: String,
    val status: ShiftSwapStatus = ShiftSwapStatus.PENDING,
    val reason: String = "",
    val managerNotes: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/**
 * Workflow states for a [ShiftSwapRequest].
 */
enum class ShiftSwapStatus {
    /** Awaiting target employee response. */
    PENDING,

    /** Target employee accepted; awaiting manager approval. */
    TARGET_ACCEPTED,

    /** Manager approved; shifts have been swapped in the schedule. */
    MANAGER_APPROVED,

    /** Rejected by either the target employee or the manager. */
    REJECTED,

    /** Cancelled by the requesting employee. */
    CANCELLED,
}
