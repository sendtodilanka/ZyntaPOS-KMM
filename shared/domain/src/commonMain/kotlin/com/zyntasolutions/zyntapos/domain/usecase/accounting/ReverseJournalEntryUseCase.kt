package com.zyntasolutions.zyntapos.domain.usecase.accounting

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.JournalEntry
import com.zyntasolutions.zyntapos.domain.repository.JournalRepository

/**
 * Creates a reversal of a posted journal entry by swapping debits and credits.
 *
 * The original entry must be posted. A new draft reversal entry is returned
 * for review before it is committed.
 */
class ReverseJournalEntryUseCase(
    private val journalRepository: JournalRepository,
) {
    suspend fun execute(
        originalEntryId: String,
        reversalDate: String,
        createdBy: String,
        now: Long,
    ): Result<JournalEntry> {
        val entryResult = journalRepository.getById(originalEntryId)
        if (entryResult is Result.Error) return entryResult
        val entry = (entryResult as Result.Success).data
            ?: return Result.Error(
                ValidationException(
                    "Journal entry not found: $originalEntryId",
                    field = "originalEntryId",
                    rule = "NOT_FOUND",
                ),
            )

        if (!entry.isPosted) {
            return Result.Error(
                ValidationException(
                    "Only posted entries can be reversed. Entry #${entry.entryNumber} is a draft.",
                    field = "isPosted",
                    rule = "NOT_POSTED",
                ),
            )
        }

        return journalRepository.reverseEntry(originalEntryId, reversalDate, createdBy, now)
    }
}
