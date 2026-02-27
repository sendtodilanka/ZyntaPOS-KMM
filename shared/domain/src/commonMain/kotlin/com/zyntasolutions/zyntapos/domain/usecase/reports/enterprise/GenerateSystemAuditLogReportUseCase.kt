package com.zyntasolutions.zyntapos.domain.usecase.reports.enterprise

import com.zyntasolutions.zyntapos.domain.model.AuditEntry
import com.zyntasolutions.zyntapos.domain.repository.AuditRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

/**
 * Generates a system audit log report filtered to a date range with an optional record limit.
 *
 * Retrieves all audit entries ordered by creation time descending and filters them to
 * the specified window. A [limit] cap is applied to prevent unbounded data loads for
 * large audit logs; the default of 500 covers typical on-screen review scenarios.
 *
 * @param auditRepository Source for security audit log entries.
 */
class GenerateSystemAuditLogReportUseCase(
    private val auditRepository: AuditRepository,
) {
    /**
     * @param from  Start of the reporting window (inclusive).
     * @param to    End of the reporting window (inclusive).
     * @param limit Maximum number of entries to return. Defaults to 500.
     * @return A [Flow] emitting the filtered list of [AuditEntry] records, newest first.
     */
    operator fun invoke(
        from: Instant,
        to: Instant,
        limit: Int = 500,
    ): Flow<List<AuditEntry>> =
        auditRepository.observeAll().map { entries ->
            entries
                .filter { it.createdAt >= from && it.createdAt <= to }
                .take(limit)
        }
}
