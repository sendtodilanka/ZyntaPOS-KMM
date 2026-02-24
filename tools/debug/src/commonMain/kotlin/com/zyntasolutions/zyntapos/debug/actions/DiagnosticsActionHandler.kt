package com.zyntasolutions.zyntapos.debug.actions

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.AuditEntry
import com.zyntasolutions.zyntapos.domain.repository.AuditRepository
import kotlinx.coroutines.flow.firstOrNull

/**
 * Abstracts diagnostics operations for the Diagnostics tab.
 */
interface DiagnosticsActionHandler {
    /** Returns the full audit log ordered by most-recent first. */
    suspend fun getAuditLog(): Result<List<AuditEntry>>

    /**
     * Returns a simple snapshot of in-memory log lines.
     *
     * In Phase 1 this is sourced from a static ring-buffer logger.
     * Kermit integration (log tree forwarding) is deferred to Phase 2.
     */
    fun getLogLines(): List<String>
}

/**
 * Default implementation backed by [AuditRepository].
 */
class DiagnosticsActionHandlerImpl(
    private val auditRepository: AuditRepository,
) : DiagnosticsActionHandler {

    // In-memory log ring buffer — populated by debug session events (max 500 entries)
    private val logBuffer = ArrayDeque<String>()

    override suspend fun getAuditLog(): Result<List<AuditEntry>> {
        return try {
            val entries = auditRepository.observeAll().firstOrNull() ?: emptyList()
            Result.Success(entries)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to load audit log: ${e.message}"))
        }
    }

    override fun getLogLines(): List<String> = logBuffer.toList()

    fun appendLog(line: String) {
        if (logBuffer.size >= 500) logBuffer.removeFirst()
        logBuffer.addLast(line)
    }
}
