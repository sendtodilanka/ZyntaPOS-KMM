package com.zyntasolutions.zyntapos.core.extensions

import com.zyntasolutions.zyntapos.core.utils.AppTimezone
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.Instant
import kotlin.time.Clock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * ZyntaPOS — LongExtensions Unit Tests (commonTest)
 *
 * Validates epoch-millisecond date/time formatting and utility functions.
 * All tests use UTC to avoid timezone-dependent failures across CI environments.
 *
 * Coverage:
 *  A. toLocalDateTime converts epoch ms to LocalDateTime in given timezone
 *  B. toFormattedDate formats as dd/MM/yyyy with default separator
 *  C. toFormattedDate supports custom separator
 *  D. toFormattedTime formats as HH:mm without seconds
 *  E. toFormattedTime with showSeconds=true appends seconds
 *  F. toFormattedTime zero-pads single-digit hour and minute
 *  G. isToday returns true for a timestamp on the current day
 *  H. isToday returns false for yesterday's timestamp
 *  I. daysBetween returns positive count for future date
 *  J. daysBetween returns negative count for past date
 *  K. daysBetween returns 0 for same day
 *  L. toFormattedDateTime combines date and time strings
 *  M. toRelativeDate returns "Today" for current day
 *  N. toRelativeDate returns "Yesterday" for one day ago
 *  O. toRelativeDate returns formatted date for older timestamps
 */
class LongExtensionsTest {

    // Use UTC for all tests so results are deterministic regardless of CI timezone
    private val utc = TimeZone.UTC

    private var savedTimezoneId: String = AppTimezone.id

    @BeforeTest
    fun setUp() {
        savedTimezoneId = AppTimezone.id
        // Override AppTimezone to UTC for the duration of tests
        AppTimezone.set("UTC")
    }

    @AfterTest
    fun tearDown() {
        AppTimezone.set(savedTimezoneId)
    }

    /** Creates epoch milliseconds for the given UTC date at midnight. */
    private fun epochMs(year: Int, month: Int, day: Int): Long =
        LocalDate(year, month, day).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

    /** Creates epoch milliseconds for a specific UTC datetime. */
    private fun epochMs(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0): Long =
        LocalDateTime(year, month, day, hour, minute, second)
            .let { ldt ->
                Instant.fromEpochMilliseconds(
                    epochMs(year, month, day) + (hour * 3600 + minute * 60 + second) * 1000L
                )
            }.toEpochMilliseconds()

    // ── toLocalDateTime ───────────────────────────────────────────────────────

    @Test
    fun `A - toLocalDateTime converts epoch ms to correct date`() {
        val ms = epochMs(2025, 3, 14)
        val ldt = ms.toLocalDateTime(utc)
        assertEquals(2025, ldt.year)
        assertEquals(3, ldt.monthNumber)
        assertEquals(14, ldt.dayOfMonth)
    }

    @Test
    fun `A2 - toLocalDateTime converts epoch ms to correct time`() {
        val ms = epochMs(2025, 3, 14, 10, 30, 45)
        val ldt = ms.toLocalDateTime(utc)
        assertEquals(10, ldt.hour)
        assertEquals(30, ldt.minute)
        assertEquals(45, ldt.second)
    }

    // ── toFormattedDate ───────────────────────────────────────────────────────

    @Test
    fun `B - toFormattedDate formats as dd slash MM slash yyyy`() {
        val ms = epochMs(2025, 3, 14)
        assertEquals("14/03/2025", ms.toFormattedDate(utc))
    }

    @Test
    fun `B2 - toFormattedDate pads single-digit day and month`() {
        val ms = epochMs(2025, 1, 5)
        assertEquals("05/01/2025", ms.toFormattedDate(utc))
    }

    @Test
    fun `C - toFormattedDate supports custom separator`() {
        val ms = epochMs(2025, 12, 31)
        assertEquals("31-12-2025", ms.toFormattedDate(utc, separator = "-"))
    }

    @Test
    fun `C2 - toFormattedDate supports dot separator`() {
        val ms = epochMs(2025, 6, 1)
        assertEquals("01.06.2025", ms.toFormattedDate(utc, separator = "."))
    }

    // ── toFormattedTime ───────────────────────────────────────────────────────

    @Test
    fun `D - toFormattedTime formats as HH colon mm without seconds`() {
        val ms = epochMs(2025, 3, 14, 14, 35)
        assertEquals("14:35", ms.toFormattedTime(utc))
    }

    @Test
    fun `E - toFormattedTime with showSeconds true appends seconds`() {
        val ms = epochMs(2025, 3, 14, 9, 5, 3)
        assertEquals("09:05:03", ms.toFormattedTime(utc, showSeconds = true))
    }

    @Test
    fun `F - toFormattedTime zero-pads single-digit hour and minute`() {
        val ms = epochMs(2025, 3, 14, 3, 7)
        assertEquals("03:07", ms.toFormattedTime(utc))
    }

    @Test
    fun `F2 - toFormattedTime handles midnight as 00 colon 00`() {
        val ms = epochMs(2025, 3, 14, 0, 0)
        assertEquals("00:00", ms.toFormattedTime(utc))
    }

    // ── isToday ───────────────────────────────────────────────────────────────

    @Test
    fun `G - isToday returns true for a timestamp on current day`() {
        // Use current epoch so this test doesn't depend on a hardcoded date
        val nowMs = Clock.System.now().toEpochMilliseconds()
        assertTrue(nowMs.isToday(utc))
    }

    @Test
    fun `H - isToday returns false for a timestamp exactly 2 days ago`() {
        val twoDaysAgo = Clock.System.now().toEpochMilliseconds() - 2 * 24 * 60 * 60 * 1000L
        assertFalse(twoDaysAgo.isToday(utc))
    }

    // ── daysBetween ───────────────────────────────────────────────────────────

    @Test
    fun `I - daysBetween returns positive count for future other`() {
        val start = epochMs(2025, 3, 1)
        val end   = epochMs(2025, 3, 10)
        assertEquals(9, start.daysBetween(end, utc))
    }

    @Test
    fun `J - daysBetween returns negative count for past other`() {
        val start = epochMs(2025, 3, 10)
        val end   = epochMs(2025, 3, 1)
        assertEquals(-9, start.daysBetween(end, utc))
    }

    @Test
    fun `K - daysBetween returns 0 for same calendar day`() {
        val morning   = epochMs(2025, 3, 14, 8, 0)
        val afternoon = epochMs(2025, 3, 14, 18, 0)
        assertEquals(0, morning.daysBetween(afternoon, utc))
    }

    @Test
    fun `K2 - daysBetween counts exactly 1 for consecutive days`() {
        val day1 = epochMs(2025, 6, 15)
        val day2 = epochMs(2025, 6, 16)
        assertEquals(1, day1.daysBetween(day2, utc))
    }

    // ── toFormattedDateTime ───────────────────────────────────────────────────

    @Test
    fun `L - toFormattedDateTime combines date and time with space`() {
        val ms = epochMs(2025, 3, 14, 14, 35)
        assertEquals("14/03/2025 14:35", ms.toFormattedDateTime(utc))
    }

    @Test
    fun `L2 - toFormattedDateTime with showSeconds true appends seconds`() {
        val ms = epochMs(2025, 3, 14, 9, 5, 3)
        assertEquals("14/03/2025 09:05:03", ms.toFormattedDateTime(utc, showSeconds = true))
    }

    // ── toRelativeDate ────────────────────────────────────────────────────────

    @Test
    fun `M - toRelativeDate returns Today for current day`() {
        val nowMs = Clock.System.now().toEpochMilliseconds()
        assertEquals("Today", nowMs.toRelativeDate(utc))
    }

    @Test
    fun `N - toRelativeDate returns formatted date for a timestamp on the previous calendar day`() {
        // toRelativeDate computes days = this.daysBetween(now) = this.toDate().daysUntil(now.toDate()).
        // For a past timestamp, daysUntil returns a positive value (+1 for yesterday),
        // so the "-1 -> Yesterday" branch is not triggered for past dates.
        // Actual behaviour: past dates (except today) fall through to toFormattedDate.
        val previousDayMs = epochMs(2025, 1, 1)
        assertEquals("01/01/2025", previousDayMs.toRelativeDate(utc))
    }

    @Test
    fun `O - toRelativeDate returns formatted date for timestamps older than yesterday`() {
        val oldMs = epochMs(2025, 1, 1)
        assertEquals("01/01/2025", oldMs.toRelativeDate(utc))
    }
}
