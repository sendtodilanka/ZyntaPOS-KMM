package com.zyntasolutions.zyntapos.domain.model

/**
 * A single row in the General Ledger report for a specific [Account].
 *
 * The General Ledger presents every [JournalEntryLine] that affected a given account,
 * sorted chronologically, with a [runningBalance] computed cumulatively in the application
 * layer. This model is read-only and is never persisted directly — it is derived from
 * [JournalEntry] and [JournalEntryLine] records by the reporting use-case.
 *
 * @property lineId FK to the source [JournalEntryLine].
 * @property journalEntryId FK to the parent [JournalEntry].
 * @property entryDate Accounting date of the parent entry (ISO: YYYY-MM-DD).
 * @property description Narrative from the parent [JournalEntry.description].
 * @property referenceType String representation of [JournalEntry.referenceType].
 * @property referenceId FK to the source document. Null for manual entries.
 * @property debit Debit amount for this line (0.0 for credit lines).
 * @property credit Credit amount for this line (0.0 for debit lines).
 * @property runningBalance Cumulative account balance after applying this line,
 *   computed in the application layer and not stored in the database.
 * @property isPosted Whether the parent [JournalEntry] has been posted.
 */
data class GeneralLedgerEntry(
    val lineId: String,
    val journalEntryId: String,
    val entryDate: String,
    val description: String,
    val referenceType: String,
    val referenceId: String? = null,
    val debit: Double,
    val credit: Double,
    val runningBalance: Double,
    val isPosted: Boolean,
)
