package com.zyntasolutions.zyntapos.domain.usecase.accounting

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.repository.JournalRepository

/**
 * Deletes an unposted draft journal entry.
 *
 * Posted entries cannot be deleted; a reversal must be used instead.
 */
class DeleteDraftEntryUseCase(
    private val journalRepository: JournalRepository,
) {
    suspend fun execute(entryId: String): Result<Unit> {
        val entryResult = journalRepository.getById(entryId)
        if (entryResult is Result.Error) return entryResult
        val entry = (entryResult as Result.Success).data
            ?: return Result.Error(
                ValidationException(
                    "Journal entry not found: $entryId",
                    field = "entryId",
                    rule = "NOT_FOUND",
                ),
            )

        if (entry.isPosted) {
            return Result.Error(
                ValidationException(
                    "Cannot delete a posted journal entry #${entry.entryNumber}. Use a reversal entry instead.",
                    field = "isPosted",
                    rule = "ALREADY_POSTED",
                ),
            )
        }

        return journalRepository.deleteEntry(entryId)
    }
}
