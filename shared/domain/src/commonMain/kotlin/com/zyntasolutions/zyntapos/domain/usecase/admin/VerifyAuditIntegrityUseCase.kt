package com.zyntasolutions.zyntapos.domain.usecase.admin

import com.zyntasolutions.zyntapos.domain.model.IntegrityReport
import com.zyntasolutions.zyntapos.domain.repository.AuditRepository
import kotlin.time.Clock

/**
 * Reads all audit entries in chronological order and verifies the hash chain.
 *
 * **Phase 1 behaviour:** Hash fields are stored as empty strings (SHA-256
 * computation is a Phase 2 backlog item). This use case detects inconsistencies
 * only when non-empty hashes do not chain correctly — trivially intact in Phase 1.
 *
 * **Phase 2:** Replace the loop body with a full SHA-256 recomputation.
 */
class VerifyAuditIntegrityUseCase(
    private val auditRepository: AuditRepository,
) {
    suspend operator fun invoke(): IntegrityReport {
        return runCatching {
            val entries = auditRepository.getAllChronological()
            var violations = 0
            var previousHash = ""
            for (entry in entries) {
                // Phase 2: recompute SHA-256(previousHash + entry fields) == entry.hash
                // Phase 1: only flag if a non-empty hash breaks the chain
                if (entry.hash.isNotEmpty() && entry.previousHash != previousHash) {
                    violations++
                }
                previousHash = entry.hash
            }
            IntegrityReport(
                totalEntries = entries.size.toLong(),
                violations = violations,
                isIntact = violations == 0,
                verifiedAt = Clock.System.now(),
            )
        }.getOrElse {
            IntegrityReport(
                totalEntries = 0L,
                violations = -1,
                isIntact = false,
                verifiedAt = Clock.System.now(),
            )
        }
    }
}
