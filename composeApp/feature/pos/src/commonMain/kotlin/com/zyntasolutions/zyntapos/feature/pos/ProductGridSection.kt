package com.zyntasolutions.zyntapos.feature.pos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zyntasolutions.zyntapos.designsystem.components.StockIndicator
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaProductCard
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaGrid
import com.zyntasolutions.zyntapos.domain.model.Product

/**
 * Displays the product catalogue as a responsive [ZyntaGrid] driven by [WindowSizeClass].
 *
 * ### Column rules (per UI/UX plan §7.1 and PLAN_PHASE1.md §6.3.3):
 * - **COMPACT**  → 2 columns (phone portrait)
 * - **MEDIUM**   → 3–4 adaptive columns (tablet portrait / landscape phone)
 * - **EXPANDED** → 4–6 adaptive columns (desktop / tablet landscape)
 *
 * ### Performance
 * - `key = { it.id }` ensures only changed product cards are recomposed,
 *   satisfying the sub-200 ms barcode scan SLA (adding one item to cart must not
 *   trigger a full grid redraw).
 * - Products are pre-filtered by [PosViewModel] (search + category) before reaching
 *   this composable — no business logic here.
 *
 * @param products   Pre-filtered list of [Product]s to display.
 * @param onAddToCart Dispatched when a card is tapped; carries the selected [Product].
 * @param modifier   Optional root [Modifier].
 */
@Composable
fun ProductGridSection(
    products: List<Product>,
    onAddToCart: (Product) -> Unit,
    modifier: Modifier = Modifier,
) {
    ZyntaGrid(
        items = products,
        key = { it.id },
        modifier = modifier.fillMaxSize(),
    ) { product ->
        ZyntaProductCard(
            name = product.name,
            price = formatPrice(product.price),
            imageUrl = product.imageUrl,
            stockIndicator = product.toStockIndicator(),
            onClick = { onAddToCart(product) },
        )
    }
}

// ── Private helpers ───────────────────────────────────────────────────────────

/**
 * Formats a raw [Double] price to a display string.
 * Locale-specific formatting is deferred to a future l10n sprint;
 * for Phase 1 the currency prefix is hardcoded to "LKR".
 */
private fun formatPrice(price: Double): String {
    val formatted = (price * 100.0).toLong().let { cents ->
        val lkr = cents / 100
        val sen = cents % 100
        "$lkr.${sen.toString().padStart(2, '0')}"
    }
    return "LKR $formatted"
}

/**
 * Maps [Product.stockQty] to a [StockIndicator] for the card badge.
 *
 * Thresholds (Phase 1 defaults — configurable per-product in Phase 2):
 * - `stockQty == 0` → [StockIndicator.OutOfStock]
 * - `stockQty <= lowStockThreshold` → [StockIndicator.LowStock]
 * - otherwise → [StockIndicator.InStock]
 */
private fun Product.toStockIndicator(): StockIndicator = when {
    stockQty <= 0.0 -> StockIndicator.OutOfStock
    stockQty <= minStockQty.coerceAtLeast(1.0) -> StockIndicator.LowStock
    else -> StockIndicator.InStock
}
