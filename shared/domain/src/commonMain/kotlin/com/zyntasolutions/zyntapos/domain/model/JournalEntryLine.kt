package com.zyntasolutions.zyntapos.domain.model

/**
 * A single debit or credit line within a [JournalEntry].
 *
 * Every [JournalEntry] contains at least two lines. For a balanced entry the sum
 * of all [debitAmount] values must equal the sum of all [creditAmount] values across
 * all lines sharing the same [journalEntryId].
 *
 * A line is a debit line when [debitAmount] > 0 and [creditAmount] == 0,
 * and a credit line when [creditAmount] > 0 and [debitAmount] == 0.
 *
 * @property id Unique identifier (UUID v4).
 * @property journalEntryId FK to the parent [JournalEntry].
 * @property accountId FK to the [Account] being debited or credited.
 * @property debitAmount Monetary amount debited to [accountId]. Zero for credit lines.
 * @property creditAmount Monetary amount credited to [accountId]. Zero for debit lines.
 * @property lineDescription Optional per-line narrative (supplements the entry-level description).
 * @property lineOrder Integer position within the entry for deterministic display ordering.
 * @property createdAt Epoch millis of record creation.
 * @property accountCode Denormalized [Account.accountCode] — populated when loaded with account details.
 * @property accountName Denormalized [Account.accountName] — populated when loaded with account details.
 */
data class JournalEntryLine(
    val id: String,
    val journalEntryId: String,
    val accountId: String,
    val debitAmount: Double,
    val creditAmount: Double,
    val lineDescription: String? = null,
    val lineOrder: Int,
    val createdAt: Long,
    val accountCode: String? = null,
    val accountName: String? = null,
)
