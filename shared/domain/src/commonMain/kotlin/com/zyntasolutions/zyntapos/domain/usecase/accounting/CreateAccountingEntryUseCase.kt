package com.zyntasolutions.zyntapos.domain.usecase.accounting

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.AccountingEntry
import com.zyntasolutions.zyntapos.domain.model.AccountingEntryType
import com.zyntasolutions.zyntapos.domain.repository.AccountingRepository
import kotlin.math.abs

/**
 * Inserts a balanced set of double-entry accounting records.
 *
 * **DEPRECATED** — Use [PostJournalEntryUseCase] with [SaveDraftJournalEntryUseCase] instead.
 * The new journal engine enforces balanced entries per the `journal_entries` /
 * `journal_entry_lines` schema and supports reversal, posting workflows, and full
 * General Ledger + financial statement computation.
 *
 * ### Business Rules
 * 1. [entries] must not be empty.
 * 2. Must contain at least one DEBIT and one CREDIT entry.
 * 3. Sum of DEBIT amounts must equal sum of CREDIT amounts (balanced books).
 * 4. All entry amounts must be positive.
 */
@Deprecated(
    message = "Use SaveDraftJournalEntryUseCase + PostJournalEntryUseCase instead. " +
        "Legacy accounting_entries table is superseded by journal_entries + journal_entry_lines.",
    level = DeprecationLevel.WARNING,
)
class CreateAccountingEntryUseCase(
    private val accountingRepository: AccountingRepository,
) {
    suspend operator fun invoke(entries: List<AccountingEntry>): Result<Unit> {
        if (entries.isEmpty()) {
            return Result.Error(
                ValidationException("Accounting entries must not be empty.", field = "entries", rule = "REQUIRED"),
            )
        }

        val debitTotal = entries.filter { it.entryType == AccountingEntryType.DEBIT }.sumOf { it.amount }
        val creditTotal = entries.filter { it.entryType == AccountingEntryType.CREDIT }.sumOf { it.amount }

        if (debitTotal == 0.0 || creditTotal == 0.0) {
            return Result.Error(
                ValidationException(
                    "Must have at least one DEBIT and one CREDIT entry.",
                    field = "entries",
                    rule = "DOUBLE_ENTRY",
                ),
            )
        }

        if (abs(debitTotal - creditTotal) > 0.005) {
            return Result.Error(
                ValidationException(
                    "Accounting entries are not balanced: DEBIT $debitTotal ≠ CREDIT $creditTotal.",
                    field = "entries",
                    rule = "UNBALANCED",
                ),
            )
        }

        return accountingRepository.insertEntries(entries)
    }
}
