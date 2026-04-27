package com.zyntasolutions.zyntapos.domain.model

/**
 * Retention windows for the three pruneable data classes (Sprint 23 task 23.9).
 *
 * Persisted as three int-encoded keys in the generic `settings` table —
 * see `domain.usecase.settings.DataRetentionPolicyKeys`. Defaults match the
 * Sprint 23 spec.
 *
 * @property auditLogRetentionDays   How long audit log rows stay before
 *                                   they're eligible for purge.
 * @property syncQueueRetentionDays  How long completed sync-queue rows are
 *                                   kept before pruning.
 * @property reportRetentionMonths   How long aggregated report data is
 *                                   retained.
 */
data class DataRetentionPolicy(
    val auditLogRetentionDays: Int = 90,
    val syncQueueRetentionDays: Int = 14,
    val reportRetentionMonths: Int = 12,
) {
    companion object {
        /** Allowed values for [auditLogRetentionDays]. */
        val ALLOWED_AUDIT_LOG_DAYS: List<Int> = listOf(30, 90, 180, 365)

        /** Allowed values for [syncQueueRetentionDays]. */
        val ALLOWED_SYNC_QUEUE_DAYS: List<Int> = listOf(7, 14, 30)

        /** Allowed values for [reportRetentionMonths]. */
        val ALLOWED_REPORT_MONTHS: List<Int> = listOf(6, 12, 24)
    }
}
