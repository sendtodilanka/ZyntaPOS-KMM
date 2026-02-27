package com.zyntasolutions.zyntapos.domain.model

/**
 * A fiscal accounting period (typically one calendar month).
 *
 * [JournalEntry] records are allocated to a period by their [JournalEntry.entryDate].
 * A period must be [PeriodStatus.OPEN] to accept new postings. Closing a period prevents
 * further edits; locking it makes the restriction permanent and audit-safe.
 *
 * @property id Unique identifier (UUID v4).
 * @property periodName Human-readable label shown in reports (e.g., "January 2026").
 * @property startDate First day of the period in ISO format: YYYY-MM-DD.
 * @property endDate Last day of the period in ISO format: YYYY-MM-DD.
 * @property status Current lifecycle state of the period. See [PeriodStatus].
 * @property fiscalYearStart Start date of the fiscal year that contains this period (e.g., "2026-01-01").
 * @property isAdjustment True for adjustment periods (e.g., year-end journal adjustments outside the normal calendar).
 * @property createdAt Epoch millis of record creation.
 * @property updatedAt Epoch millis of the last modification.
 * @property lockedAt Epoch millis when the period was locked. Null if not yet locked.
 * @property lockedBy FK to the [User] who locked the period. Null if not yet locked.
 */
data class AccountingPeriod(
    val id: String,
    val periodName: String,
    val startDate: String,
    val endDate: String,
    val status: PeriodStatus,
    val fiscalYearStart: String,
    val isAdjustment: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val lockedAt: Long? = null,
    val lockedBy: String? = null,
)

/** Lifecycle state of an [AccountingPeriod]. */
enum class PeriodStatus {
    /** The period accepts new [JournalEntry] postings. */
    OPEN,
    /** The period is closed — no new postings, but the lock is reversible by an administrator. */
    CLOSED,
    /** The period is permanently locked — no changes are permitted under any circumstance. */
    LOCKED,
}
