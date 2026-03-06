package com.zyntasolutions.zyntapos.domain.usecase.admin

import com.zyntasolutions.zyntapos.domain.model.AuditEntry
import com.zyntasolutions.zyntapos.domain.model.IntegrityReport
import com.zyntasolutions.zyntapos.domain.repository.AuditRepository
import kotlin.time.Clock

/**
 * Reads all audit entries in chronological order and verifies the SHA-256 hash chain.
 *
 * Each entry's hash is recomputed from its fields + the previous entry's hash.
 * Any mismatch indicates potential tampering and is counted as a violation.
 *
 * The hash computation must match [SecurityAuditLogger.computeExpectedHash] exactly.
 *
 * @param auditRepository Source of audit entries.
 * @param hashComputer    Function that recomputes the expected hash for an entry given
 *                        the previous hash. Injected to avoid a domain→security dependency.
 */
class VerifyAuditIntegrityUseCase(
    private val auditRepository: AuditRepository,
    private val hashComputer: (entry: AuditEntry, previousHash: String) -> String,
) {
    suspend operator fun invoke(): IntegrityReport {
        return runCatching {
            val entries = auditRepository.getAllChronological()
            var violations = 0
            var previousHash = "GENESIS"

            for (entry in entries) {
                // Skip entries with empty hashes (legacy Phase 1 data)
                if (entry.hash.isEmpty()) {
                    previousHash = entry.hash
                    continue
                }

                // Verify chain link
                if (entry.previousHash != previousHash) {
                    violations++
                }

                // Recompute and verify hash
                val expectedHash = hashComputer(entry, entry.previousHash)
                if (entry.hash != expectedHash) {
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
