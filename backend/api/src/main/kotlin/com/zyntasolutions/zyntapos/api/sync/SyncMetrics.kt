package com.zyntasolutions.zyntapos.api.sync

import java.util.concurrent.atomic.AtomicLong

/**
 * Simple in-memory sync metrics counters.
 * Exposed via the `/health/sync` endpoint.
 *
 * Note: Counters reset on service restart — for persistent metrics use
 * Prometheus/Micrometer (Phase 3 observability milestone).
 */
class SyncMetrics {
    val opsAccepted   = AtomicLong(0)
    val opsRejected   = AtomicLong(0)
    val conflictsTotal = AtomicLong(0)
    val deadLettersTotal = AtomicLong(0)

    private val pushDurationsMs = ArrayDeque<Long>(1000)
    private val pullDurationsMs = ArrayDeque<Long>(1000)

    fun recordPushDuration(ms: Long) = synchronized(pushDurationsMs) {
        if (pushDurationsMs.size >= 1000) pushDurationsMs.removeFirst()
        pushDurationsMs.addLast(ms)
    }

    fun recordPullDuration(ms: Long) = synchronized(pullDurationsMs) {
        if (pullDurationsMs.size >= 1000) pullDurationsMs.removeFirst()
        pullDurationsMs.addLast(ms)
    }

    fun pushP95Ms(): Long = synchronized(pushDurationsMs) { percentile(pushDurationsMs, 95) }
    fun pullP95Ms(): Long = synchronized(pullDurationsMs) { percentile(pullDurationsMs, 95) }

    fun conflictRate(): Double {
        val accepted = opsAccepted.get().toDouble()
        val conflicts = conflictsTotal.get().toDouble()
        return if (accepted == 0.0) 0.0 else conflicts / accepted
    }

    private fun percentile(data: Collection<Long>, p: Int): Long {
        if (data.isEmpty()) return 0L
        val sorted = data.sorted()
        val index = ((p / 100.0) * sorted.size).toInt().coerceIn(0, sorted.size - 1)
        return sorted[index]
    }

    fun snapshot(): Map<String, Any> = mapOf(
        "ops_accepted_total"   to opsAccepted.get(),
        "ops_rejected_total"   to opsRejected.get(),
        "conflicts_total"      to conflictsTotal.get(),
        "dead_letters_total"   to deadLettersTotal.get(),
        "conflict_rate"        to "%.4f".format(conflictRate()),
        "push_p95_ms"          to pushP95Ms(),
        "pull_p95_ms"          to pullP95Ms(),
    )
}
