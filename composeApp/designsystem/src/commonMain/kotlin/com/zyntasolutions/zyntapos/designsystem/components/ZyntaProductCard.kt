package com.zyntasolutions.zyntapos.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing

// ─────────────────────────────────────────────────────────────────────────────
// ZyntaProductCard — Async image via Coil; stock indicator badge.
// Variants: Grid (square), List (horizontal), Compact (dense list)
// ─────────────────────────────────────────────────────────────────────────────

/** Stock availability state for [ZyntaProductCard]. */
enum class StockIndicator {
    /** Quantity is above minimum threshold. */
    InStock,
    /** Quantity is at or below minimum threshold. */
    LowStock,
    /** Quantity is zero. */
    OutOfStock,
}

/** Display variant for [ZyntaProductCard]. */
enum class ProductCardVariant { Grid, List, Compact }

/**
 * Product display card supporting Grid, List, and Compact layouts.
 *
 * @param name Product display name.
 * @param price Formatted price string (e.g. "LKR 450.00").
 * @param imageUrl Remote or local image URL. Null shows a placeholder.
 * @param stockIndicator Current stock state drives the badge color.
 * @param onClick Invoked when the card is tapped.
 * @param modifier Optional [Modifier].
 * @param variant Layout mode — Grid/List/Compact.
 */
@Composable
fun ZyntaProductCard(
    name: String,
    price: String,
    imageUrl: String?,
    stockIndicator: StockIndicator,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ProductCardVariant = ProductCardVariant.Grid,
) {
    when (variant) {
        ProductCardVariant.Grid -> GridCard(name, price, imageUrl, stockIndicator, onClick, modifier)
        ProductCardVariant.List -> ListCard(name, price, imageUrl, stockIndicator, onClick, modifier)
        ProductCardVariant.Compact -> CompactCard(name, price, stockIndicator, onClick, modifier)
    }
}

// ── Grid variant ─────────────────────────────────────────────────────────────
@Composable
private fun GridCard(
    name: String,
    price: String,
    imageUrl: String?,
    stock: StockIndicator,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Card(
        modifier = modifier
            .aspectRatio(0.85f)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Product image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // Stock badge overlay
                StockBadge(
                    stock = stock,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(ZyntaSpacing.xs),
                )
            }
            // Name + price footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ZyntaSpacing.sm, vertical = ZyntaSpacing.xs),
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = price,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// ── List variant ─────────────────────────────────────────────────────────────
@Composable
private fun ListCard(
    name: String,
    price: String,
    imageUrl: String?,
    stock: StockIndicator,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = ZyntaSpacing.md),
            ) {
                Text(text = name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = price, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            StockBadge(stock = stock, modifier = Modifier.padding(end = ZyntaSpacing.sm))
        }
    }
}

// ── Compact variant ───────────────────────────────────────────────────────────
@Composable
private fun CompactCard(
    name: String,
    price: String,
    stock: StockIndicator,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(price, color = MaterialTheme.colorScheme.primary) },
        trailingContent = { StockBadge(stock = stock) },
    )
}

// ── Stock badge ────────────────────────────────────────────────────────────────
@Composable
private fun StockBadge(stock: StockIndicator, modifier: Modifier = Modifier) {
    val (label, color) = when (stock) {
        StockIndicator.InStock -> "In Stock" to MaterialTheme.colorScheme.tertiary
        StockIndicator.LowStock -> "Low Stock" to MaterialTheme.colorScheme.secondary
        StockIndicator.OutOfStock -> "Out" to MaterialTheme.colorScheme.error
    }
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = ZyntaSpacing.xs, vertical = 2.dp),
        )
    }
}
