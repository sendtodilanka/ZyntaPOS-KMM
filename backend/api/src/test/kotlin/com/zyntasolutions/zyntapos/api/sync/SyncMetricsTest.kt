package com.zyntasolutions.zyntapos.api.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [SyncMetrics] - pure in-memory counters and statistics.
 */
class SyncMetricsTest {

    @Test
    fun `initial counters are all zero`() {
        val metrics = SyncMetrics()
        assertEquals(0L, metrics.opsAccepted.get())
        assertEquals(0L, metrics.opsRejected.get())
        assertEquals(0L, metrics.conflictsTotal.get())
        assertEquals(0L, metrics.deadLettersTotal.get())
    }

    @Test
    fun `atomic counters increment correctly`() {
        val metrics = SyncMetrics()
        metrics.opsAccepted.addAndGet(5)
        metrics.opsRejected.addAndGet(2)
        metrics.conflictsTotal.addAndGet(1)
        metrics.deadLettersTotal.addAndGet(3)

        assertEquals(5L, metrics.opsAccepted.get())
        assertEquals(2L, metrics.opsRejected.get())
        assertEquals(1L, metrics.conflictsTotal.get())
        assertEquals(3L, metrics.deadLettersTotal.get())
    }

    // ── Push/Pull duration recording ────────────────────────────────────

    @Test
    fun `push P95 returns 0 when no samples recorded`() {
        val metrics = SyncMetrics()
        assertEquals(0L, metrics.pushP95Ms())
    }

    @Test
    fun `pull P95 returns 0 when no samples recorded`() {
        val metrics = SyncMetrics()
        assertEquals(0L, metrics.pullP95Ms())
    }

    @Test
    fun `push P95 with single sample returns that sample`() {
        val metrics = SyncMetrics()
        metrics.recordPushDuration(42L)
        assertEquals(42L, metrics.pushP95Ms())
    }

    @Test
    fun `pull P95 with single sample returns that sample`() {
        val metrics = SyncMetrics()
        metrics.recordPullDuration(100L)
        assertEquals(100L, metrics.pullP95Ms())
    }

    @Test
    fun `push P95 with 100 sequential samples returns correct percentile`() {
        val metrics = SyncMetrics()
        // Record values 1..100
        for (i in 1L..100L) {
            metrics.recordPushDuration(i)
        }
        val p95 = metrics.pushP95Ms()
        // P95 of 1..100 should be >= 95
        assertTrue(p95 >= 95L, "Expected P95 >= 95 but got $p95")
    }

    @Test
    fun `rolling window evicts oldest entries beyond 1000`() {
        val metrics = SyncMetrics()
        // Record 1100 samples - first 100 should be evicted
        for (i in 1L..1100L) {
            metrics.recordPushDuration(i)
        }
        // Window should contain 101..1100 (1000 samples)
        val p95 = metrics.pushP95Ms()
        // P95 should be from the most recent 1000 (101..1100)
        assertTrue(p95 >= 1050L, "Expected P95 >= 1050 but got $p95")
    }

    // ── Conflict rate ───────────────────────────────────────────────────

    @Test
    fun `conflict rate is 0 when no operations accepted`() {
        val metrics = SyncMetrics()
        assertEquals(0.0, metrics.conflictRate())
    }

    @Test
    fun `conflict rate is 0 when no conflicts`() {
        val metrics = SyncMetrics()
        metrics.opsAccepted.set(100)
        assertEquals(0.0, metrics.conflictRate())
    }

    @Test
    fun `conflict rate is ratio of conflicts to accepted`() {
        val metrics = SyncMetrics()
        metrics.opsAccepted.set(100)
        metrics.conflictsTotal.set(5)
        assertEquals(0.05, metrics.conflictRate(), 0.001)
    }

    @Test
    fun `conflict rate is 1 when all ops are conflicts`() {
        val metrics = SyncMetrics()
        metrics.opsAccepted.set(10)
        metrics.conflictsTotal.set(10)
        assertEquals(1.0, metrics.conflictRate(), 0.001)
    }

    // ── Snapshot ─────────────────────────────────────────────────────────

    @Test
    fun `snapshot contains all expected keys`() {
        val metrics = SyncMetrics()
        val snap = metrics.snapshot()
        assertTrue(snap.containsKey("ops_accepted_total"))
        assertTrue(snap.containsKey("ops_rejected_total"))
        assertTrue(snap.containsKey("conflicts_total"))
        assertTrue(snap.containsKey("dead_letters_total"))
        assertTrue(snap.containsKey("conflict_rate"))
        assertTrue(snap.containsKey("push_p95_ms"))
        assertTrue(snap.containsKey("pull_p95_ms"))
    }

    @Test
    fun `snapshot reflects current counter values`() {
        val metrics = SyncMetrics()
        metrics.opsAccepted.set(42)
        metrics.opsRejected.set(7)
        val snap = metrics.snapshot()
        assertEquals(42L, snap["ops_accepted_total"])
        assertEquals(7L, snap["ops_rejected_total"])
    }
}
