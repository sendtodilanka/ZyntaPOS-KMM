package com.zyntasolutions.zyntapos.core.extensions

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime

/**
 * Extension functions for [Long] (Unix epoch milliseconds) used across ZentaPOS.
 *
 * All date/time conversions respect the [TimeZone.currentSystemDefault] unless
 * an explicit [tz] is provided, ensuring correct local-time display for the
 * store's operating timezone.
 */

// ── Conversion ────────────────────────────────────────────────────────────────

/**
 * Converts this Unix epoch milliseconds value to a [LocalDateTime] in the given [tz].
 *
 * ```kotlin
 * order.createdAt.toLocalDateTime()  // → LocalDateTime(2025, 3, 14, 10, 30, 0)
 * ```
 */
fun Long.toLocalDateTime(tz: TimeZone = TimeZone.currentSystemDefault()): LocalDateTime =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(tz)

/**
 * Formats this epoch milliseconds value as a localised date string.
 *
 * Default format: `dd/MM/yyyy` (e.g., `14/03/2025`).
 */
fun Long.toFormattedDate(
    tz: TimeZone = TimeZone.currentSystemDefault(),
    separator: String = "/",
): String {
    val ldt = toLocalDateTime(tz)
    return "${ldt.dayOfMonth.toString().padStart(2, '0')}$separator" +
           "${ldt.monthNumber.toString().padStart(2, '0')}$separator" +
           "${ldt.year}"
}

/**
 * Formats this epoch milliseconds value as a localised time string.
 *
 * Default format: `HH:mm` (24-hour, e.g., `14:35`).
 * Pass [showSeconds] = `true` for `HH:mm:ss`.
 */
fun Long.toFormattedTime(
    tz: TimeZone = TimeZone.currentSystemDefault(),
    showSeconds: Boolean = false,
): String {
    val ldt = toLocalDateTime(tz)
    val hhmm = "${ldt.hour.toString().padStart(2, '0')}:" +
               ldt.minute.toString().padStart(2, '0')
    return if (showSeconds) "$hhmm:${ldt.second.toString().padStart(2, '0')}" else hhmm
}

/**
 * Returns `true` if this epoch millisecond timestamp falls on today's date
 * (compared in [tz]).
 */
fun Long.isToday(tz: TimeZone = TimeZone.currentSystemDefault()): Boolean {
    val today: LocalDate = Clock.System.now().toLocalDateTime(tz).date
    return toLocalDateTime(tz).date == today
}

/**
 * Returns the number of whole days between this timestamp and [other] (both in [tz]).
 *
 * Positive if [other] is after this; negative if [other] is before.
 *
 * ```kotlin
 * val daysDiff = startMs.daysBetween(endMs)
 * ```
 */
fun Long.daysBetween(
    other: Long,
    tz: TimeZone = TimeZone.currentSystemDefault(),
): Int {
    val startDate = toLocalDateTime(tz).date
    val endDate   = Instant.fromEpochMilliseconds(other).toLocalDateTime(tz).date
    return startDate.daysUntil(endDate)
}

// ── Formatting ────────────────────────────────────────────────────────────────

/**
 * Returns a combined date-time string for display (e.g., `14/03/2025 14:35`).
 */
fun Long.toFormattedDateTime(
    tz: TimeZone = TimeZone.currentSystemDefault(),
    showSeconds: Boolean = false,
): String = "${toFormattedDate(tz)} ${toFormattedTime(tz, showSeconds)}"

/**
 * Returns a relative time label such as "Today", "Yesterday", or the formatted date.
 */
fun Long.toRelativeDate(tz: TimeZone = TimeZone.currentSystemDefault()): String {
    val days = daysBetween(Clock.System.now().toEpochMilliseconds(), tz)
    return when (days) {
        0    -> "Today"
        -1   -> "Yesterday"
        else -> toFormattedDate(tz)
    }
}
