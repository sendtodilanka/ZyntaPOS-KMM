package com.zyntasolutions.zyntapos.core.pagination

/**
 * A page of results from a paginated query.
 *
 * This is a lightweight, framework-agnostic pagination abstraction used across
 * the repository and ViewModel layers. It supports infinite-scroll UIs where
 * each page load appends to the existing list.
 *
 * ## Usage Pattern
 * ```kotlin
 * // Repository
 * suspend fun getPage(request: PageRequest): PaginatedResult<Product>
 *
 * // ViewModel
 * val nextPage = repository.getPage(PageRequest(offset = state.items.size))
 * updateState { copy(items = items + nextPage.items, hasMore = nextPage.hasMore) }
 * ```
 *
 * @param T The type of items in the page.
 * @property items The items in this page.
 * @property totalCount Total number of items across all pages (for progress indicators).
 * @property hasMore Whether more pages are available after this one.
 */
data class PaginatedResult<out T>(
    val items: List<T>,
    val totalCount: Long,
    val hasMore: Boolean,
)

/**
 * Parameters for a paginated query.
 *
 * @property limit Maximum number of items to return (page size).
 * @property offset Number of items to skip (for offset-based pagination).
 */
data class PageRequest(
    val limit: Int = DEFAULT_PAGE_SIZE,
    val offset: Int = 0,
) {
    companion object {
        const val DEFAULT_PAGE_SIZE = 30
    }
}
