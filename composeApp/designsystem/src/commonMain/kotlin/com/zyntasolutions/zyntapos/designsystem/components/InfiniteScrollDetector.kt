package com.zyntasolutions.zyntapos.designsystem.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/**
 * Detects when a [LazyListState] has scrolled near the end and triggers [onLoadMore].
 *
 * Use this alongside a paginated ViewModel to implement infinite scroll:
 * ```kotlin
 * val listState = rememberLazyListState()
 * LazyColumn(state = listState) { ... }
 * InfiniteScrollDetector(
 *     listState = listState,
 *     hasMore = state.hasMoreProducts,
 *     isLoading = state.isLoadingMore,
 *     onLoadMore = { viewModel.dispatch(Intent.LoadMore) }
 * )
 * ```
 *
 * @param listState The [LazyListState] of the scrollable list.
 * @param hasMore Whether more pages are available.
 * @param isLoading Whether a page load is currently in progress (prevents duplicate loads).
 * @param buffer Number of items before the end to trigger the load (default: 5).
 * @param onLoadMore Callback invoked when the user scrolls near the end.
 */
@Composable
fun InfiniteScrollDetector(
    listState: LazyListState,
    hasMore: Boolean,
    isLoading: Boolean,
    buffer: Int = 5,
    onLoadMore: () -> Unit,
) {
    val shouldLoadMore = remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleIndex >= (totalItems - buffer)
        }
    }

    LaunchedEffect(shouldLoadMore) {
        snapshotFlow { shouldLoadMore.value }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                if (hasMore && !isLoading) {
                    onLoadMore()
                }
            }
    }
}

/**
 * Grid variant of [InfiniteScrollDetector] for [LazyGridState].
 *
 * @param gridState The [LazyGridState] of the scrollable grid.
 * @param hasMore Whether more pages are available.
 * @param isLoading Whether a page load is currently in progress.
 * @param buffer Number of items before the end to trigger the load (default: 8).
 * @param onLoadMore Callback invoked when the user scrolls near the end.
 */
@Composable
fun InfiniteScrollDetector(
    gridState: LazyGridState,
    hasMore: Boolean,
    isLoading: Boolean,
    buffer: Int = 8,
    onLoadMore: () -> Unit,
) {
    val shouldLoadMore = remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleIndex >= (totalItems - buffer)
        }
    }

    LaunchedEffect(shouldLoadMore) {
        snapshotFlow { shouldLoadMore.value }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                if (hasMore && !isLoading) {
                    onLoadMore()
                }
            }
    }
}
