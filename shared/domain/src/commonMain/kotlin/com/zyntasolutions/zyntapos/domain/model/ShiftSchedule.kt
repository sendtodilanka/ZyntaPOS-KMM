package com.zyntasolutions.zyntapos.domain.model

/**
 * A scheduled work shift for one employee on a specific date.
 *
 * One shift per employee per day is enforced at the database level.
 *
 * @property id Unique identifier (UUID v4).
 * @property employeeId FK to [Employee].
 * @property storeId Store the shift is scheduled at.
 * @property shiftDate ISO date: YYYY-MM-DD.
 * @property startTime Start time: HH:MM (24-hour format).
 * @property endTime End time: HH:MM (24-hour format).
 * @property notes Optional scheduling notes.
 * @property createdAt Epoch millis of record creation.
 * @property updatedAt Epoch millis of last update.
 */
data class ShiftSchedule(
    val id: String,
    val employeeId: String,
    val storeId: String,
    val shiftDate: String,
    val startTime: String,
    val endTime: String,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
) {
    /**
     * Approximate shift duration in hours.
     *
     * Parses HH:MM strings and computes the difference. Returns null if either time is malformed.
     * Does not handle overnight shifts (endTime < startTime).
     */
    val durationHours: Double?
        get() {
            val (startH, startM) = startTime.split(":").map { it.toIntOrNull() ?: return null }
            val (endH, endM) = endTime.split(":").map { it.toIntOrNull() ?: return null }
            val startMinutes = startH * 60 + startM
            val endMinutes = endH * 60 + endM
            if (endMinutes <= startMinutes) return null
            return (endMinutes - startMinutes) / 60.0
        }
}
