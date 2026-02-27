package com.zyntasolutions.zyntapos.domain.usecase.accounting

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.JournalEntry
import com.zyntasolutions.zyntapos.domain.model.PeriodStatus
import com.zyntasolutions.zyntapos.domain.repository.AccountingPeriodRepository
import com.zyntasolutions.zyntapos.domain.repository.JournalRepository

/**
 * Saves a new or updated draft journal entry.
 *
 * Business rules enforced:
 * - Entry must not be already posted (isPosted must be false).
 * - The entry's date must fall within an OPEN accounting period.
 * - Each line must have exactly one of debitAmount or creditAmount > 0 (not both zero, not both positive).
 */
class SaveDraftJournalEntryUseCase(
    private val journalRepository: JournalRepository,
    private val periodRepository: AccountingPeriodRepository,
) {
    suspend fun execute(entry: JournalEntry): Result<Unit> {
        if (entry.isPosted) {
            return Result.Error(
                ValidationException(
                    "Cannot save a posted entry as a draft.",
                    field = "isPosted",
                    rule = "ALREADY_POSTED",
                ),
            )
        }

        val periodResult = periodRepository.getPeriodForDate(entry.storeId, entry.entryDate)
        if (periodResult is Result.Error) return periodResult
        val period = (periodResult as Result.Success).data
        if (period == null || period.status != PeriodStatus.OPEN) {
            return Result.Error(
                ValidationException(
                    "No open accounting period found for entry date ${entry.entryDate}.",
                    field = "entryDate",
                    rule = "PERIOD_NOT_OPEN",
                ),
            )
        }

        for (line in entry.lines) {
            val hasDebit = line.debitAmount > 0.0
            val hasCredit = line.creditAmount > 0.0
            if (!hasDebit && !hasCredit) {
                return Result.Error(
                    ValidationException(
                        "Line ${line.id} must have exactly one of debitAmount or creditAmount > 0.",
                        field = "lines",
                        rule = "ZERO_LINE",
                    ),
                )
            }
            if (hasDebit && hasCredit) {
                return Result.Error(
                    ValidationException(
                        "Line ${line.id} cannot have both debitAmount and creditAmount > 0.",
                        field = "lines",
                        rule = "DUAL_SIDED_LINE",
                    ),
                )
            }
        }

        return journalRepository.saveDraftEntry(entry)
    }
}
