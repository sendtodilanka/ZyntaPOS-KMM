package com.zyntasolutions.zyntapos.feature.pos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.tokens.ZentaSpacing
import com.zyntasolutions.zyntapos.domain.model.Category

/**
 * Horizontally scrollable category filter chip row for the POS product grid.
 *
 * Layout contract (per UI/UX plan §7.1):
 * - "**All**" chip is always rendered first and is selected when [selectedCategoryId] is `null`.
 * - Each [Category] in [categories] gets a [FilterChip].
 * - Tapping the currently-selected category chip dispatches `null` (deselects), returning to
 *   the "All" view. This is consistent with Material 3 chip toggle semantics.
 * - Chips scroll horizontally without pagination — fine up to ~40 categories (typical retail).
 *
 * ### Performance
 * Stable [Category.id] keys prevent full row recomposition when only the selected item changes.
 *
 * @param categories        Live list of active categories from [PosState.categories].
 * @param selectedCategoryId Currently selected category ID, or `null` for "All".
 * @param onSelectCategory  Intent callback; receives the tapped category's ID or `null` for "All".
 * @param modifier          Optional root [Modifier].
 */
@Composable
fun CategoryFilterRow(
    categories: List<Category>,
    selectedCategoryId: String?,
    onSelectCategory: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = ZentaSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(ZentaSpacing.sm),
    ) {
        // "All" chip — always first
        item(key = "__all__") {
            FilterChip(
                selected = selectedCategoryId == null,
                onClick = { onSelectCategory(null) },
                label = { Text("All") },
            )
        }

        // Per-category chips
        items(
            items = categories,
            key = { it.id },
        ) { category ->
            val isSelected = category.id == selectedCategoryId
            FilterChip(
                selected = isSelected,
                onClick = {
                    // Tapping the already-selected chip resets to "All"
                    onSelectCategory(if (isSelected) null else category.id)
                },
                label = { Text(category.name) },
            )
        }
    }
}
