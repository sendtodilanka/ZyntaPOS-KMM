package com.zyntasolutions.zyntapos.domain.model

import kotlinx.datetime.Instant

/**
 * Result of an audit entry hash chain integrity verification run.
 *
 * Produced by [VerifyAuditIntegrityUseCase] and consumed by [AdminViewModel].
 *
 * @param totalEntries  Total audit entries scanned.
 * @param violations    Number of hash chain violations detected (0 = intact).
 *                      -1 indicates the check itself failed with an error.
 * @param isIntact      `true` when [violations] == 0.
 * @param verifiedAt    Timestamp when the check completed.
 */
data class IntegrityReport(
    val totalEntries: Long,
    val violations: Int,
    val isIntact: Boolean,
    val verifiedAt: Instant,
)
