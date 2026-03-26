package com.zyntasolutions.zyntapos.feature.register

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.CashMovement
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Scrollable list of [CashMovement] entries for the active register session.
 *
 * ## Layout
 * - Header row: "Cash Movements" title + movement count chip.
 * - [LazyColumn] of [CashMovementRow] items ordered by timestamp (most-recent first).
 * - Empty state: icon + message when no movements have been recorded yet.
 *
 * Each row displays:
 * - **Type badge** — green "IN" / red "OUT" chip.
 * - **Reason** — bold; truncated to 2 lines.
 * - **Amount** — right-aligned; prefixed with "+" or "−".
 * - **Timestamp** — secondary text below reason.
 *
 * This composable is **stateless** — it receives the movement list directly and
 * is embedded inside [RegisterDashboardScreen].
 *
 * @param movements Ordered list of [CashMovement] entries (ViewModel provides newest-first).
 * @param modifier  Optional [Modifier] applied to the root [Column].
 */
@Composable
fun CashMovementHistory(
    movements: List<CashMovement>,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    Column(modifier = modifier) {
        // ── Header ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.xs),
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = s[StringResource.REGISTER_CASH_MOVEMENTS_TITLE],
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (movements.isNotEmpty()) {
                Badge { Text(movements.size.toString()) }
            }
        }

        Spacer(Modifier.height(ZyntaSpacing.sm))
        HorizontalDivider()
        Spacer(Modifier.height(ZyntaSpacing.sm))

        // ── Content ───────────────────────────────────────────────────────
        if (movements.isEmpty()) {
            EmptyMovementsState(modifier = Modifier.fillMaxWidth())
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.xs),
            ) {
                // Show newest first
                items(
                    items = movements.sortedByDescending { it.timestamp },
                    key = { it.id },
                ) { movement ->
                    CashMovementRow(movement = movement)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Individual row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CashMovementRow(movement: CashMovement) {
    val isIn = movement.type == CashMovement.Type.IN
    val typeColor = if (isIn) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
    val typeIcon = if (isIn) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward
    val amountSign = if (isIn) "+" else "−"

    val formattedTime = remember(movement.timestamp) {
        val local = movement.timestamp.toLocalDateTime(TimeZone.currentSystemDefault())
        "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = ZyntaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
    ) {
        // Type badge
        Surface(
            color = typeColor.copy(alpha = 0.12f),
            shape = MaterialTheme.shapes.small,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = ZyntaSpacing.sm, vertical = ZyntaSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Icon(
                    imageVector = typeIcon,
                    contentDescription = null,
                    tint = typeColor,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = if (isIn) "IN" else "OUT",  // abbreviations not localized
                    style = MaterialTheme.typography.labelSmall,
                    color = typeColor,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Reason + time
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = movement.reason,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
            )
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Amount
        Text(
            text = "$amountSign%.2f".format(movement.amount),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = typeColor,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyMovementsState(modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    Column(
        modifier = modifier.padding(ZyntaSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
    ) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.outlineVariant,
        )
        Text(
            text = s[StringResource.REGISTER_NO_MOVEMENTS],
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = s[StringResource.REGISTER_NO_MOVEMENTS_HINT],
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
