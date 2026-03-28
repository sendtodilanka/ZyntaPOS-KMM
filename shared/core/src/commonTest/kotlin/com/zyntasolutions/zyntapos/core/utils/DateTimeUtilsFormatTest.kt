package com.zyntasolutions.zyntapos.core.utils

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ZyntaPOS — DateTimeUtils.formatWithPattern Tests (commonTest)
 *
 * Validates the custom date/time pattern formatter in [DateTimeUtils.formatWithPattern].
 *
 * Coverage:
 *  A. dd/MM/yyyy produces zero-padded day and month
 *  B. MM/dd/yyyy US format
 *  C. yyyy-MM-dd ISO date only
 *  D. d MMM yyyy produces abbreviated month name without leading zero on day
 *  E. HH:mm time-only pattern
 *  F. dd/MM/yyyy HH:mm datetime pattern
 *  G. ss seconds token
 *  H. MMM produces correct month abbreviations for all 12 months
 *  I. verbatim characters (slashes, dashes, spaces) pass through unchanged
 */
class DateTimeUtilsFormatTest {

    private val utc = TimeZone.UTC

    // 2025-03-14T14:35:07Z = Friday 14 March 2025, 14:35:07 UTC
    private val epochMs = DateTimeUtils.fromIso("2025-03-14T14:35:07Z")

    @Test
    fun `A - dd slash MM slash yyyy produces zero-padded day and month`() {
        val result = DateTimeUtils.formatWithPattern(epochMs, "dd/MM/yyyy", utc)
        assertEquals("14/03/2025", result)
    }

    @Test
    fun `B - MM slash dd slash yyyy US format`() {
        val result = DateTimeUtils.formatWithPattern(epochMs, "MM/dd/yyyy", utc)
        assertEquals("03/14/2025", result)
    }

    @Test
    fun `C - yyyy-MM-dd ISO date only`() {
        val result = DateTimeUtils.formatWithPattern(epochMs, "yyyy-MM-dd", utc)
        assertEquals("2025-03-14", result)
    }

    @Test
    fun `D - d MMM yyyy uses abbreviated month without leading zero on day`() {
        val result = DateTimeUtils.formatWithPattern(epochMs, "d MMM yyyy", utc)
        assertEquals("14 Mar 2025", result)
    }

    @Test
    fun `E - HH colon mm time-only pattern`() {
        val result = DateTimeUtils.formatWithPattern(epochMs, "HH:mm", utc)
        assertEquals("14:35", result)
    }

    @Test
    fun `F - dd slash MM slash yyyy HH colon mm full datetime`() {
        val result = DateTimeUtils.formatWithPattern(epochMs, "dd/MM/yyyy HH:mm", utc)
        assertEquals("14/03/2025 14:35", result)
    }

    @Test
    fun `G - ss seconds token`() {
        val result = DateTimeUtils.formatWithPattern(epochMs, "HH:mm:ss", utc)
        assertEquals("14:35:07", result)
    }

    @Test
    fun `H - MMM produces correct abbreviation for all 12 months`() {
        val expected = listOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
        )
        for ((index, abbrev) in expected.withIndex()) {
            val monthNum = (index + 1).toString().padStart(2, '0')
            val sampleMs = DateTimeUtils.fromIso("2025-$monthNum-01T00:00:00Z")
            val result = DateTimeUtils.formatWithPattern(sampleMs, "MMM", utc)
            assertEquals(abbrev, result, "Month $monthNum should abbreviate to $abbrev but got $result")
        }
    }

    @Test
    fun `I - verbatim characters pass through unchanged`() {
        // Commas, spaces, and T separator pass through
        val result = DateTimeUtils.formatWithPattern(epochMs, "dd-MM-yyyy", utc)
        assertEquals("14-03-2025", result)
    }

    @Test
    fun `single-digit day uses d token without leading zero`() {
        // 2025-03-05 → day = 5 (no leading zero for 'd' token)
        val ms = DateTimeUtils.fromIso("2025-03-05T00:00:00Z")
        val result = DateTimeUtils.formatWithPattern(ms, "d", utc)
        assertEquals("5", result)
    }
}
