package com.zyntasolutions.zyntapos.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.material3.SwipeToDismissBoxValue.EndToStart
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.zyntasolutions.zyntapos.designsystem.tokens.ZentaSpacing

// ─────────────────────────────────────────────────────────────────────────────
// ZentaCartItemRow — Stateless; thumbnail, name, price, qty stepper, total.
// Swipe-to-remove via SwipeToDismissBox; all state hoisted to caller.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A single row in the shopping cart.
 *
 * @param name Product name snapshot.
 * @param unitPrice Formatted unit price string.
 * @param quantity Current quantity.
 * @param lineTotal Formatted line total string.
 * @param onIncrement Invoked when the [+] stepper button is tapped.
 * @param onDecrement Invoked when the [−] stepper button is tapped.
 * @param onRemove Invoked when the row is fully swiped away.
 * @param modifier Optional [Modifier].
 * @param imageUrl Optional product thumbnail URL.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZentaCartItemRow(
    name: String,
    unitPrice: String,
    quantity: Int,
    lineTotal: String,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == EndToStart) {
                onRemove()
                true
            } else false
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = "Remove",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(end = ZentaSpacing.md),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        },
        modifier = modifier,
        enableDismissFromStartToEnd = false,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(ZentaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Thumbnail
            AsyncImage(
                model = imageUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.width(ZentaSpacing.sm))

            // Name + unit price
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = unitPrice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Quantity stepper
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onDecrement,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = "Decrease quantity",
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = quantity.toString(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.widthIn(min = 24.dp),
                )
                IconButton(
                    onClick = onIncrement,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Increase quantity",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(Modifier.width(ZentaSpacing.sm))
            // Line total
            Text(
                text = lineTotal,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
