package com.zyntasolutions.zyntapos.core.utils

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for [DateTimeUtils].
 * Target: ≥ 90% coverage of utils/DateTimeUtils.kt
 */
class DateTimeUtilsTest {

    private val utc = TimeZone.UTC

    // ── nowMillis ─────────────────────────────────────────────────────────────

    @Test
    fun `nowMillis returns positive epoch value`() {
        val now = DateTimeUtils.nowMillis()
        assertTrue(now > 0L)
    }

    @Test
    fun `nowMillis two consecutive calls are ordered`() {
        val first = DateTimeUtils.nowMillis()
        val second = DateTimeUtils.nowMillis()
        // second >= first (monotonically non-decreasing)
        assertTrue(second >= first)
    }

    // ── toIso / fromIso ───────────────────────────────────────────────────────

    @Test
    fun `toIso produces valid ISO 8601 string`() {
        val epochMs = 1_741_600_000_000L // ~ 2025-03-10
        val iso = DateTimeUtils.toIso(epochMs)
        assertTrue(iso.contains("T"), "Expected ISO 8601 with T separator: $iso")
        assertTrue(iso.endsWith("Z"), "Expected UTC Z suffix: $iso")
    }

    @Test
    fun `fromIso parses ISO string back to correct epoch`() {
        val original = 1_741_600_000_000L
        val iso = DateTimeUtils.toIso(original)
        val parsed = DateTimeUtils.fromIso(iso)
        assertEquals(original, parsed)
    }

    @Test
    fun `fromIso throws on invalid string`() {
        assertFailsWith<IllegalArgumentException> {
            DateTimeUtils.fromIso("not-a-date")
        }
    }

    // ── startOfDay / endOfDay ─────────────────────────────────────────────────

    @Test
    fun `startOfDay is before or equal to input`() {
        val now = DateTimeUtils.nowMillis()
        val start = DateTimeUtils.startOfDay(now, utc)
        assertTrue(start <= now)
    }

    @Test
    fun `endOfDay is after or equal to input`() {
        val now = DateTimeUtils.nowMillis()
        val end = DateTimeUtils.endOfDay(now, utc)
        assertTrue(end >= now)
    }

    @Test
    fun `startOfDay and endOfDay span exactly one day`() {
        val now = DateTimeUtils.nowMillis()
        val start = DateTimeUtils.startOfDay(now, utc)
        val end   = DateTimeUtils.endOfDay(now, utc)
        // 86400000 ms in a day, endOfDay = startOfNextDay - 1
        assertEquals(86_400_000L - 1L, end - start)
    }

    @Test
    fun `startOfDay returns midnight 00h00m for known timestamp`() {
        // 2025-03-14 12:00:00 UTC → startOfDay should be 2025-03-14 00:00:00 UTC
        val noon = DateTimeUtils.fromIso("2025-03-14T12:00:00Z")
        val start = DateTimeUtils.startOfDay(noon, utc)
        val midnight = DateTimeUtils.fromIso("2025-03-14T00:00:00Z")
        assertEquals(midnight, start)
    }

    // ── formatForDisplay ──────────────────────────────────────────────────────

    @Test
    fun `formatForDisplay returns non-empty string`() {
        val result = DateTimeUtils.formatForDisplay(DateTimeUtils.nowMillis())
        assertTrue(result.isNotBlank())
    }

    @Test
    fun `formatForDisplay contains comma separator`() {
        val epochMs = DateTimeUtils.fromIso("2025-03-14T10:30:00Z")
        val result = DateTimeUtils.formatForDisplay(epochMs, utc)
        assertTrue(result.contains(","), "Expected comma in: $result")
    }

    // ── daysAgo ───────────────────────────────────────────────────────────────

    @Test
    fun `daysAgo 0 returns start of current day`() {
        val now = DateTimeUtils.nowMillis()
        val todayStart = DateTimeUtils.startOfDay(now, utc)
        val ago = DateTimeUtils.daysAgo(0, now, utc)
        assertEquals(todayStart, ago)
    }

    @Test
    fun `daysAgo 1 returns start of yesterday`() {
        val now = DateTimeUtils.nowMillis()
        val todayStart = DateTimeUtils.startOfDay(now, utc)
        val yesterdayStart = DateTimeUtils.daysAgo(1, now, utc)
        assertEquals(86_400_000L, todayStart - yesterdayStart)
    }

    // ── nowLocal ──────────────────────────────────────────────────────────────

    @Test
    fun `nowLocal returns non-null LocalDateTime`() {
        val ldt = DateTimeUtils.nowLocal(utc)
        assertNotNull(ldt)
        assertTrue(ldt.year >= 2025)
    }
}
