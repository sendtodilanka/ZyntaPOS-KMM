package com.zyntasolutions.zyntapos.common

import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimestampUtilsTest {

    @Test
    fun `nowEpochMs returns current time in millis`() {
        val before = System.currentTimeMillis()
        val result = TimestampUtils.nowEpochMs()
        val after = System.currentTimeMillis()
        assertTrue(result in before..after)
    }

    @Test
    fun `toEpochMs converts OffsetDateTime correctly`() {
        val odt = OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
        val expected = odt.toInstant().toEpochMilli()
        assertEquals(expected, TimestampUtils.toEpochMs(odt))
    }

    @Test
    fun `fromEpochMs converts epoch to OffsetDateTime UTC`() {
        val epochMs = 1704067200000L // 2024-01-01T00:00:00Z
        val result = TimestampUtils.fromEpochMs(epochMs)
        assertEquals(ZoneOffset.UTC, result.offset)
        assertEquals(2024, result.year)
        assertEquals(1, result.monthValue)
        assertEquals(1, result.dayOfMonth)
    }

    @Test
    fun `toIso8601 returns ISO format string`() {
        val odt = OffsetDateTime.of(2025, 6, 15, 12, 30, 0, 0, ZoneOffset.UTC)
        val result = TimestampUtils.toIso8601(odt)
        assertEquals("2025-06-15T12:30:00Z", result)
    }

    @Test
    fun `epochMsToIso8601 converts epoch to ISO string`() {
        val result = TimestampUtils.epochMsToIso8601(0L)
        assertEquals("1970-01-01T00:00:00Z", result)
    }

    @Test
    fun `round-trip toEpochMs then fromEpochMs preserves value`() {
        val odt = OffsetDateTime.now(ZoneOffset.UTC)
        val roundTrip = TimestampUtils.fromEpochMs(TimestampUtils.toEpochMs(odt))
        // OffsetDateTime has nanos, epoch ms only has millis — truncate for comparison
        assertEquals(
            odt.toInstant().toEpochMilli(),
            roundTrip.toInstant().toEpochMilli()
        )
    }

    // ── validateEpochMs (non-strict) ─────────────────────────────────────

    @Test
    fun `validateEpochMs accepts current time`() {
        assertNull(TimestampUtils.validateEpochMs(System.currentTimeMillis()))
    }

    @Test
    fun `validateEpochMs accepts zero in non-strict mode`() {
        assertNull(TimestampUtils.validateEpochMs(0L))
    }

    @Test
    fun `validateEpochMs rejects negative`() {
        val err = TimestampUtils.validateEpochMs(-1L, "ts")
        assertNotNull(err)
        assertTrue(err.contains("non-negative"))
    }

    @Test
    fun `validateEpochMs rejects far future`() {
        val future = System.currentTimeMillis() + 120_000
        val err = TimestampUtils.validateEpochMs(future, "ts")
        assertNotNull(err)
        assertTrue(err.contains("future"))
    }

    @Test
    fun `validateEpochMs accepts within 60s skew`() {
        val within = System.currentTimeMillis() + 59_000
        assertNull(TimestampUtils.validateEpochMs(within))
    }

    // ── validateEpochMs (strict) ─────────────────────────────────────────

    @Test
    fun `validateEpochMs strict rejects pre-2020 timestamp`() {
        val pre2020 = 1_000_000_000L // ~2001
        val err = TimestampUtils.validateEpochMs(pre2020, "ts", strict = true)
        assertNotNull(err)
        assertTrue(err.contains("before 2020"))
    }

    @Test
    fun `validateEpochMs strict accepts zero`() {
        // 0 is outside the range 1..MIN_VALID so it's not rejected by the strict check
        assertNull(TimestampUtils.validateEpochMs(0L, "ts", strict = true))
    }

    @Test
    fun `validateEpochMs strict accepts post-2020 timestamp`() {
        val post2020 = TimestampUtils.MIN_VALID_EPOCH_MS + 1
        assertNull(TimestampUtils.validateEpochMs(post2020, "ts", strict = true))
    }

    @Test
    fun `validateEpochMs uses field name in error message`() {
        val err = TimestampUtils.validateEpochMs(-1L, "ORDER.created_at")
        assertNotNull(err)
        assertTrue(err.contains("ORDER.created_at"))
    }
}
