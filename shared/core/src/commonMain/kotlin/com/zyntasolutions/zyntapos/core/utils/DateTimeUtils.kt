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

    // ── User-preferred date format ──────────────────────────────────────────

    /** Default date format used when no user preference has been set. */
    const val DEFAULT_DATE_FORMAT = "dd/MM/yyyy"

    /**
     * Settings key for the user-configured date format (stored via [SettingsRepository]).
     *
     * Canonical source of truth — use this constant instead of raw strings
     * in any module that needs to read the date format preference.
     */
    const val SETTINGS_KEY_DATE_FORMAT = "general.date_format"

    /**
     * Formats epoch milliseconds using the given [pattern] string in [tz].
     *
     * Supported pattern tokens:
     * - `yyyy` — four-digit year (e.g. `2025`)
     * - `MM`   — two-digit month (01–12)
     * - `MMM`  — abbreviated month name (e.g. `Mar`)
     * - `dd`   — two-digit day of month (01–31)
     * - `d`    — day of month without leading zero (1–31)
     * - `HH`   — two-digit hour, 24h (00–23)
     * - `mm`   — two-digit minute (00–59)
     * - `ss`   — two-digit second (00–59)
     *
     * Unrecognised characters are passed through verbatim
     * (e.g. slashes, dashes, commas, spaces).
     *
     * Examples:
     * ```
     * formatWithPattern(epochMs, "dd/MM/yyyy")       // "14/03/2025"
     * formatWithPattern(epochMs, "MM/dd/yyyy")       // "03/14/2025"
     * formatWithPattern(epochMs, "yyyy-MM-dd")       // "2025-03-14"
     * formatWithPattern(epochMs, "d MMM yyyy")       // "14 Mar 2025"
     * formatWithPattern(epochMs, "dd/MM/yyyy HH:mm") // "14/03/2025 14:35"
     * ```
     */
    fun formatWithPattern(
        epochMs: Long,
        pattern: String,
        tz: TimeZone = AppTimezone.current,
    ): String {
        val ldt = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(tz)
        val day = ldt.dayOfMonth.toString().padStart(2, '0')
        val month = ldt.monthNumber.toString().padStart(2, '0')
        val monthAbbrev = MONTH_ABBREV[ldt.monthNumber - 1]
        val year = ldt.year.toString().padStart(4, '0')
        val hour = ldt.hour.toString().padStart(2, '0')
        val minute = ldt.minute.toString().padStart(2, '0')
        val second = ldt.second.toString().padStart(2, '0')

        // Replace longest tokens first to avoid partial matches (e.g. "MMM" before "MM").
        return pattern
            .replace("yyyy", year)
            .replace("MMM", monthAbbrev)
            .replace("MM", month)
            .replace("dd", day)
            .replace("d", ldt.dayOfMonth.toString())
            .replace("HH", hour)
            .replace("mm", minute)
            .replace("ss", second)
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private val MONTH_ABBREV = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )
}
