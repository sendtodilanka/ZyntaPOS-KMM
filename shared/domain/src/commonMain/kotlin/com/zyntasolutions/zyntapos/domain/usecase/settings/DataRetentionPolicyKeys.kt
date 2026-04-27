package com.zyntasolutions.zyntapos.domain.usecase.settings

/**
 * Key namespace for `DataRetentionPolicy` entries in the generic `settings`
 * table. Per-field rows for the same reasons as the audit/security slices.
 */
internal object DataRetentionPolicyKeys {
    const val AUDIT_LOG_DAYS = "data_retention.audit_log_days"
    const val SYNC_QUEUE_DAYS = "data_retention.sync_queue_days"
    const val REPORT_MONTHS = "data_retention.report_months"
}
