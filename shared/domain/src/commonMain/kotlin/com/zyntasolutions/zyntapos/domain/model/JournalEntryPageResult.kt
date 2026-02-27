package com.zyntasolutions.zyntapos.domain.model

/**
 * A single page of [JournalEntry] records returned by a paginated query.
 *
 * Used by the journal listing use-case and its corresponding ViewModel to support
 * incremental loading in the accounting module UI.
 *
 * @property entries The [JournalEntry] records on this page.
 * @property totalCount Total number of [JournalEntry] records matching the query (across all pages).
 * @property pageNumber Current page index (zero-based).
 * @property pageSize Maximum number of entries per page.
 */
data class JournalEntryPageResult(
    val entries: List<JournalEntry>,
    val totalCount: Int,
    val pageNumber: Int,
    val pageSize: Int,
)
