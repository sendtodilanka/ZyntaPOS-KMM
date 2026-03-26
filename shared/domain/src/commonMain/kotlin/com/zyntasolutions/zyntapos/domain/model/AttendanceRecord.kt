package com.zyntasolutions.zyntapos.domain.model

/**
 * A single attendance clock-in/out record for an employee.
 *
 * @property id Unique identifier (UUID v4).
 * @property employeeId FK to [Employee].
 * @property clockIn ISO datetime string (YYYY-MM-DDTHH:MM:SS) of clock-in time.
 * @property clockOut ISO datetime string of clock-out time. Null if still clocked in.
 * @property totalHours Total hours worked. Calculated on clock-out.
 * @property overtimeHours Hours beyond the standard shift threshold.
 * @property notes Optional notes from manager.
 * @property status Attendance classification.
 * @property createdAt Epoch millis of record creation.
 * @property updatedAt Epoch millis of last update.
 */
data class AttendanceRecord(
    val id: String,
    val employeeId: String,
    /** C3.4: Store where the employee clocked in. Null = primary store. */
    val storeId: String? = null,
    val clockIn: String,
    val clockOut: String? = null,
    val totalHours: Double? = null,
    val overtimeHours: Double = 0.0,
    val notes: String? = null,
    val status: AttendanceStatus = AttendanceStatus.PRESENT,
    val createdAt: Long,
    val updatedAt: Long,
) {
    /** True if the employee is currently clocked in (no clock-out recorded). */
    val isOpen: Boolean get() = clockOut == null
}

/** Attendance classification for a given shift. */
enum class AttendanceStatus {
    PRESENT,
    ABSENT,
    LATE,
    LEAVE,
}

/**
 * Summary of attendance metrics for an employee over a date range.
 *
 * @property employeeId FK to [Employee].
 * @property totalDays Total days in the period.
 * @property presentDays Days the employee was present.
 * @property absentDays Days absent.
 * @property lateDays Days with a late clock-in.
 * @property leaveDays Days on approved leave.
 * @property totalHours Total hours worked in the period.
 * @property overtimeHours Total overtime hours in the period.
 */
data class AttendanceSummary(
    val employeeId: String,
    val totalDays: Int,
    val presentDays: Int,
    val absentDays: Int,
    val lateDays: Int,
    val leaveDays: Int,
    val totalHours: Double,
    val overtimeHours: Double,
)
