package com.zyntasolutions.zyntapos.core.health

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for [JvmSystemHealthTracker].
 *
 * Coverage:
 *  A. Initial snapshot has UNKNOWN overall status before first refresh
 *  B. After refresh(), snapshot contains non-negative heap values
 *  C. After refresh(), heapUsedBytes <= heapMaxBytes
 *  D. After refresh(), heapUsagePercent is in [0, 100]
 *  E. After refresh(), diskTotalBytes > 0
 *  F. After refresh(), diskFreeBytes <= diskTotalBytes
 *  G. After refresh(), availableProcessors > 0
 *  H. After refresh(), platformDescription contains "Java"
 *  I. After refresh(), lastRefreshedAtMillis is recent (< 5s ago)
 *  J. After refresh(), overallStatus is not UNKNOWN
 *  K. createSystemHealthTracker factory returns non-null instance
 *  L. stopAutoRefresh cancels without throwing
 */
class JvmSystemHealthTrackerTest {

    private fun createTracker(): JvmSystemHealthTracker = JvmSystemHealthTracker()

    // ── A: Initial state ──────────────────────────────────────────────────────

    @Test
    fun `A - initial snapshot overall status is UNKNOWN before refresh`() {
        val tracker = createTracker()
        assertEquals(OverallStatus.UNKNOWN, tracker.snapshot.value.overallStatus)
    }

    // ── B: Heap values after refresh ─────────────────────────────────────────

    @Test
    fun `B - after refresh heapUsedBytes is non-negative`() = runBlocking {
        val tracker = createTracker()
        tracker.refresh()
        assertTrue(tracker.snapshot.value.heapUsedBytes >= 0L)
    }

    @Test
    fun `B2 - after refresh heapMaxBytes is positive`() = runBlocking {
        val tracker = createTracker()
        tracker.refresh()
        assertTrue(tracker.snapshot.value.heapMaxBytes > 0L)
    }

    // ── C: Heap constraint ────────────────────────────────────────────────────

    @Test
    fun `C - after refresh heapUsedBytes is at most heapMaxBytes`() = runBlocking {
        val tracker = createTracker()
        tracker.refresh()
        val snap = tracker.snapshot.value
        assertTrue(
            snap.heapUsedBytes <= snap.heapMaxBytes,
            "heapUsed (${snap.heapUsedBytes}) must be <= heapMax (${snap.heapMaxBytes})"
        )
    }

    // ── D: Heap percent ───────────────────────────────────────────────────────

    @Test
    fun `D - after refresh heapUsagePercent is between 0 and 100`() = runBlocking {
        val tracker = createTracker()
        tracker.refresh()
        val pct = tracker.snapshot.value.heapUsagePercent
        assertTrue(pct >= 0f && pct <= 100f, "heapUsagePercent $pct must be in [0, 100]")
    }

    // ── E: Disk total ─────────────────────────────────────────────────────────

    @Test
    fun `E - after refresh diskTotalBytes is positive`() = runBlocking {
        val tracker = createTracker()
        tracker.refresh()
        assertTrue(tracker.snapshot.value.diskTotalBytes > 0L)
    }

    // ── F: Disk free constraint ───────────────────────────────────────────────

    @Test
    fun `F - after refresh diskFreeBytes is at most diskTotalBytes`() = runBlocking {
        val tracker = createTracker()
        tracker.refresh()
        val snap = tracker.snapshot.value
        assertTrue(
            snap.diskFreeBytes <= snap.diskTotalBytes,
            "diskFree (${snap.diskFreeBytes}) must be <= diskTotal (${snap.diskTotalBytes})"
        )
    }

    // ── G: CPU count ──────────────────────────────────────────────────────────

    @Test
    fun `G - after refresh availableProcessors is at least 1`() = runBlocking {
        val tracker = createTracker()
        tracker.refresh()
        assertTrue(tracker.snapshot.value.availableProcessors >= 1)
    }

    // ── H: Platform description ───────────────────────────────────────────────

    @Test
    fun `H - after refresh platformDescription contains Java`() = runBlocking {
        val tracker = createTracker()
        tracker.refresh()
        assertTrue(
            tracker.snapshot.value.platformDescription.contains("Java"),
            "platformDescription must contain 'Java'"
        )
    }

    // ── I: Last refreshed timestamp ───────────────────────────────────────────

    @Test
    fun `I - after refresh lastRefreshedAtMillis is within 5 seconds of now`() = runBlocking {
        val tracker = createTracker()
        val before = System.currentTimeMillis()
        tracker.refresh()
        val after = System.currentTimeMillis()
        val ts = tracker.snapshot.value.lastRefreshedAtMillis
        assertTrue(ts in before..after + 1000L, "lastRefreshedAtMillis $ts must be recent")
    }

    // ── J: Overall status after refresh ──────────────────────────────────────

    @Test
    fun `J - after refresh overallStatus is not UNKNOWN`() = runBlocking {
        val tracker = createTracker()
        tracker.refresh()
        assertTrue(
            tracker.snapshot.value.overallStatus != OverallStatus.UNKNOWN,
            "overallStatus must be resolved after refresh"
        )
    }

    // ── K: Factory function ───────────────────────────────────────────────────

    @Test
    fun `K - createSystemHealthTracker returns non-null JvmSystemHealthTracker`() {
        val tracker = createSystemHealthTracker()
        assertNotNull(tracker)
        assertTrue(tracker is JvmSystemHealthTracker)
    }

    // ── L: stopAutoRefresh ────────────────────────────────────────────────────

    @Test
    fun `L - stopAutoRefresh does not throw when auto refresh was never started`() {
        val tracker = createTracker()
        tracker.stopAutoRefresh() // must not throw
    }
}
