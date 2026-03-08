package com.zyntasolutions.zyntapos.core.utils

import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * ZyntaPOS date/time utilities wrapping [kotlinx.datetime].
 *
 * All functions accept an optional [tz] parameter (defaults to system timezone)
 * so the POS can handle stores in any timezone without configuration changes.
 *
 * ### Epoch convention
 * ZyntaPOS stores all timestamps as **Unix epoch milliseconds** (`Long`).
 * Use these utilities to convert to/from display-friendly formats.
 */
object DateTimeUtils {

    // ── Current time ──────────────────────────────────────────────────────────

    /** Returns the current UTC time as a [kotlinx.datetime.Instant]. */
    fun nowInstant(): Instant = Clock.System.now()

    /**
     * Returns the current time in epoch milliseconds (UTC).
     * Suitable for storing in the database (`createdAt`, `updatedAt`, etc.).
     */
    fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    /** Returns the current [LocalDateTime] in [tz]. */
    fun nowLocal(tz: TimeZone = AppTimezone.current): LocalDateTime =
        Clock.System.now().toLocalDateTime(tz)

    // ── ISO 8601 serialisation ────────────────────────────────────────────────

    /**
     * Converts epoch milliseconds to an ISO 8601 string (e.g., `2025-03-14T10:30:00Z`).
     */
    fun toIso(epochMs: Long): String =
        Instant.fromEpochMilliseconds(epochMs).toString()

    /**
     * Parses an ISO 8601 string (e.g., `2025-03-14T10:30:00Z`) back to epoch milliseconds.
     *
     * @throws IllegalArgumentException if the string is not valid ISO 8601.
     */
    fun fromIso(iso: String): Long =
        Instant.parse(iso).toEpochMilliseconds()

    // ── Day boundaries ────────────────────────────────────────────────────────

    /**
     * Returns the epoch milliseconds of the **start of day** (00:00:00.000) for the
     * given [epochMs] in [tz].
     */
    fun startOfDay(
        epochMs: Long = nowMillis(),
        tz: TimeZone = AppTimezone.current,
    ): Long {
        val date: LocalDate = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(tz).date
        return date.atStartOfDayIn(tz).toEpochMilliseconds()
    }

    /**
     * Returns the epoch milliseconds of the **end of day** (23:59:59.999) for the
     * given [epochMs] in [tz].
     */
    fun endOfDay(
        epochMs: Long = nowMillis(),
        tz: TimeZone = AppTimezone.current,
    ): Long {
        val startOfNext = startOfDay(epochMs, tz).let { start ->
            val nextDay = Instant.fromEpochMilliseconds(start)
                .toLocalDateTime(tz).date.plus(1, DateTimeUnit.DAY)
            nextDay.atStartOfDayIn(tz).toEpochMilliseconds()
        }
        return startOfNext - 1L
    }

    // ── Display formatting ────────────────────────────────────────────────────

    /**
     * Formats epoch milliseconds for human-readable POS display.
     *
     * Output example: `14 Mar 2025, 14:35`
     */
    fun formatForDisplay(
        epochMs: Long,
        tz: TimeZone = AppTimezone.current,
    ): String {
        val ldt = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(tz)
        val day = ldt.dayOfMonth.toString().padStart(2, '0')
        val month = MONTH_ABBREV[ldt.monthNumber - 1]
        val year = ldt.year
        val time = "${ldt.hour.toString().padStart(2, '0')}:${ldt.minute.toString().padStart(2, '0')}"
        return "$day $month $year, $time"
    }

    // ── Date arithmetic ───────────────────────────────────────────────────────

    /**
     * Returns epoch milliseconds for [daysAgo] days before the given [epochMs].
     */
    fun daysAgo(
        daysAgo: Int,
        epochMs: Long = nowMillis(),
        tz: TimeZone = AppTimezone.current,
    ): Long {
        val date = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(tz).date
        return date.plus(-daysAgo, DateTimeUnit.DAY).atStartOfDayIn(tz).toEpochMilliseconds()
    }

    /**
     * Converts a [LocalDateTime] to epoch milliseconds in [tz].
     */
    fun LocalDateTime.toEpochMillis(tz: TimeZone = AppTimezone.current): Long =
        toInstant(tz).toEpochMilliseconds()

    // ── Private ───────────────────────────────────────────────────────────────

    private val MONTH_ABBREV = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )
}
