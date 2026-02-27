package com.zyntasolutions.zyntapos.domain.usecase.accounting

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.JournalEntry
import com.zyntasolutions.zyntapos.domain.repository.JournalRepository
import kotlinx.coroutines.flow.Flow

/**
 * Provides reactive and one-shot access to journal entries.
 */
class GetJournalEntriesUseCase(
    private val journalRepository: JournalRepository,
) {
    /**
     * Returns a reactive [Flow] of journal entries for a given date range (both inclusive, ISO: YYYY-MM-DD).
     * The emitted list contains header records only; lines are not populated.
     */
    fun execute(storeId: String, fromDate: String, toDate: String): Flow<List<JournalEntry>> =
        journalRepository.getEntriesByDateRange(storeId, fromDate, toDate)

    /**
     * Returns a reactive [Flow] of unposted (draft) entries for the given store.
     */
    fun executeUnposted(storeId: String): Flow<List<JournalEntry>> =
        journalRepository.getUnpostedEntries(storeId)

    /**
     * Loads a single entry with all its lines. Returns null if not found.
     */
    suspend fun executeById(entryId: String): Result<JournalEntry?> =
        journalRepository.getById(entryId)
}
