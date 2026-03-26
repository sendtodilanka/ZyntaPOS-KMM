package com.zyntasolutions.zyntapos.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing

// ─────────────────────────────────────────────────────────────────────────────
// ZyntaTable — Header row (sortable), LazyColumn data rows, slots for
// empty state, loading state, and pagination footer.
// ─────────────────────────────────────────────────────────────────────────────

/** Sort direction for a table column. */
enum class SortDirection { Ascending, Descending, None }

/**
 * Column descriptor for [ZyntaTable].
 *
 * @param key Unique identifier used to trigger sort callbacks.
 * @param header Display label for the column header.
 * @param weight Column width relative weight (default 1f = equal distribution).
 * @param sortable Whether the column supports sorting.
 */
data class ZyntaTableColumn(
    val key: String,
    val header: String,
    val weight: Float = 1f,
    val sortable: Boolean = true,
)

/**
 * A generic data table with sortable headers, lazy row rendering, and state slots.
 *
 * @param columns Column definitions.
 * @param sortColumnKey Currently sorted column key; null = no active sort.
 * @param sortDirection Current sort direction for [sortColumnKey].
 * @param onSort Invoked when a sortable header is tapped. Passes the column key.
 * @param isLoading When true, renders the [loadingContent] slot instead of rows.
 * @param isEmpty When true and not loading, renders the [emptyContent] slot.
 * @param modifier Optional [Modifier].
 * @param rowContent Slot returning a row composable for each item [T].
 * @param items Data items to display; ignored when loading or empty.
 * @param rowKey Stable key extractor for LazyColumn performance.
 * @param loadingContent Custom loading slot (default: [CircularProgressIndicator]).
 * @param emptyContent Custom empty-state slot (default: "No data" text).
 * @param paginationFooter Optional pagination footer composable.
 */
@Composable
fun <T> ZyntaTable(
    columns: List<ZyntaTableColumn>,
    items: List<T>,
    sortColumnKey: String? = null,
    sortDirection: SortDirection = SortDirection.None,
    onSort: (columnKey: String) -> Unit = {},
    isLoading: Boolean = false,
    isEmpty: Boolean = items.isEmpty(),
    modifier: Modifier = Modifier,
    rowKey: ((T) -> Any)? = null,
    rowContent: @Composable RowScope.(item: T) -> Unit,
    loadingContent: @Composable ColumnScope.() -> Unit = {
        Box(Modifier.fillMaxWidth().padding(ZyntaSpacing.xl), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    },
    emptyContent: @Composable ColumnScope.() -> Unit = {
        val s = LocalStrings.current
        Box(Modifier.fillMaxWidth().padding(ZyntaSpacing.xl), contentAlignment = Alignment.Center) {
            Text(s[StringResource.COMMON_NO_DATA], style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    },
    paginationFooter: @Composable (ColumnScope.() -> Unit)? = null,
) {
    Column(modifier = modifier) {
        // ── Header row ──────────────────────────────────────────────────────
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ZyntaSpacing.md, vertical = ZyntaSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                columns.forEach { col ->
                    Row(
                        modifier = Modifier
                            .weight(col.weight)
                            .then(
                                if (col.sortable) Modifier.clickable { onSort(col.key) }
                                else Modifier
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = col.header,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(1f),
                        )
                        if (col.sortable) {
                            val icon = when {
                                col.key == sortColumnKey && sortDirection == SortDirection.Ascending -> Icons.Default.ArrowUpward
                                col.key == sortColumnKey && sortDirection == SortDirection.Descending -> Icons.Default.ArrowDownward
                                else -> Icons.Default.UnfoldMore
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = LocalStrings.current[StringResource.COMMON_SORT_BY_FORMAT, col.header],
                                modifier = Modifier.size(16.dp),
                                tint = if (col.key == sortColumnKey)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider()

        // ── Body ─────────────────────────────────────────────────────────────
        when {
            isLoading -> Column { loadingContent() }
            isEmpty -> Column { emptyContent() }
            else -> {
                LazyColumn {
                    items(
                        items = items,
                        key = rowKey?.let { keyFn -> { item: T -> keyFn(item) } },
                    ) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = ZyntaSpacing.md, vertical = ZyntaSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) { rowContent(item) }
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }
        }

        // ── Pagination footer ─────────────────────────────────────────────────
        if (paginationFooter != null) {
            Column { paginationFooter() }
        }
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@androidx.compose.ui.tooling.preview.Preview
@androidx.compose.runtime.Composable
private fun ZyntaTablePreview() {
    com.zyntasolutions.zyntapos.designsystem.theme.ZyntaTheme {
        ZyntaTable(
            columns = listOf(
                ZyntaTableColumn(key = "name",  header = "Name",  weight = 2f),
                ZyntaTableColumn(key = "price", header = "Price", weight = 1f),
                ZyntaTableColumn(key = "stock", header = "Stock", weight = 1f),
            ),
            items = listOf(
                listOf("Espresso", "Rs 250", "12"),
                listOf("Cappuccino", "Rs 350", "8"),
                listOf("Latte", "Rs 400", "5"),
            ),
            rowKey = { it.hashCode() },
            rowContent = { row ->
                row.forEachIndexed { i, cell ->
                    val weight = if (i == 0) 2f else 1f
                    androidx.compose.material3.Text(
                        cell,
                        modifier = androidx.compose.ui.Modifier.weight(weight),
                    )
                }
            },
        )
    }
}
