package com.zyntasolutions.zyntapos.domain.usecase.accounting

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.PeriodStatus
import com.zyntasolutions.zyntapos.domain.repository.AccountingPeriodRepository
import com.zyntasolutions.zyntapos.domain.repository.JournalRepository
import kotlin.math.abs

/**
 * Validates and posts a draft journal entry.
 *
 * Business rules enforced:
 * - Entry must not already be posted.
 * - Entry must have at least one line.
 * - sum(debit) must equal sum(credit) within a tolerance of 0.005.
 * - Each line must have exactly one of debitAmount or creditAmount > 0 (not both zero).
 * - The entry's accounting period (based on entryDate) must be OPEN.
 */
class PostJournalEntryUseCase(
    private val journalRepository: JournalRepository,
    private val periodRepository: AccountingPeriodRepository,
) {
    suspend fun execute(entryId: String, postedAt: Long): Result<Unit> {
        val entryResult = journalRepository.getById(entryId)
        if (entryResult is Result.Error) return entryResult
        val entry = (entryResult as Result.Success).data
            ?: return Result.Error(ValidationException("Journal entry not found: $entryId", field = "entryId", rule = "NOT_FOUND"))

        if (entry.isPosted) {
            return Result.Error(
                ValidationException(
                    "Journal entry #${entry.entryNumber} is already posted.",
                    field = "isPosted",
                    rule = "ALREADY_POSTED",
                ),
            )
        }

        if (entry.lines.isEmpty()) {
            return Result.Error(
                ValidationException(
                    "Journal entry #${entry.entryNumber} has no lines.",
                    field = "lines",
                    rule = "NO_LINES",
                ),
            )
        }

        for (line in entry.lines) {
            if (line.debitAmount == 0.0 && line.creditAmount == 0.0) {
                return Result.Error(
                    ValidationException(
                        "Line ${line.id} has both debit and credit equal to zero.",
                        field = "lines",
                        rule = "ZERO_LINE",
                    ),
                )
            }
        }

        val debitTotal = entry.lines.sumOf { it.debitAmount }
        val creditTotal = entry.lines.sumOf { it.creditAmount }
        if (abs(debitTotal - creditTotal) > 0.005) {
            return Result.Error(
                ValidationException(
                    "Journal entry is not balanced: DEBIT $debitTotal ≠ CREDIT $creditTotal.",
                    field = "lines",
                    rule = "UNBALANCED",
                ),
            )
        }

        val periodResult = periodRepository.getPeriodForDate(entry.storeId, entry.entryDate)
        if (periodResult is Result.Error) return periodResult
        val period = (periodResult as Result.Success).data
        if (period == null || period.status == PeriodStatus.CLOSED || period.status == PeriodStatus.LOCKED) {
            return Result.Error(
                ValidationException(
                    "No open accounting period found for date ${entry.entryDate}.",
                    field = "entryDate",
                    rule = "PERIOD_NOT_OPEN",
                ),
            )
        }

        return journalRepository.postEntry(entryId, postedAt)
    }
}
