package com.zyntasolutions.zyntapos.feature.accounting

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.EInvoice
import com.zyntasolutions.zyntapos.domain.model.EInvoiceStatus

/**
 * E-Invoice list screen (Sprint 18-24).
 *
 * Displays all e-invoices for the current store with status chip filters and a
 * per-row action to navigate to the invoice detail.
 *
 * @param state    Current [EInvoiceState].
 * @param onIntent Dispatches intents to [EInvoiceViewModel].
 * @param onNavigateToDetail Callback to navigate to invoice detail by ID.
 * @param modifier Optional modifier.
 */
@Composable
fun EInvoiceListScreen(
    state: EInvoiceState,
    onIntent: (EInvoiceIntent) -> Unit,
    onNavigateToDetail: (invoiceId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filtered = if (state.statusFilter == null) state.invoices
                   else state.invoices.filter { it.status == state.statusFilter }

    Column(modifier = modifier.fillMaxSize()) {
        // ── Status filter chips ────────────────────────────────────────────
        LazyRow(
            contentPadding = PaddingValues(horizontal = ZyntaSpacing.md, vertical = ZyntaSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
        ) {
            item {
                FilterChip(
                    selected = state.statusFilter == null,
                    onClick = { onIntent(EInvoiceIntent.FilterByStatus(null)) },
                    label = { Text("All (${state.invoices.size})") },
                )
            }
            items(EInvoiceStatus.values()) { status ->
                val count = state.invoices.count { it.status == status }
                FilterChip(
                    selected = state.statusFilter == status,
                    onClick = { onIntent(EInvoiceIntent.FilterByStatus(status)) },
                    label = { Text("${status.label()} ($count)") },
                )
            }
        }

        HorizontalDivider()

        if (state.isLoading && state.invoices.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Receipt,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No invoices found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(ZyntaSpacing.md),
                verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
            ) {
                items(filtered, key = { it.id }) { invoice ->
                    EInvoiceListItem(
                        invoice = invoice,
                        onClick = {
                            onIntent(EInvoiceIntent.LoadInvoice(invoice.id))
                            onNavigateToDetail(invoice.id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EInvoiceListItem(
    invoice: EInvoice,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(ZyntaSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
        ) {
            Icon(
                imageVector = invoice.status.icon(),
                contentDescription = invoice.status.name,
                tint = invoice.status.color(),
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "#${invoice.invoiceNumber}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    invoice.customerName,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    invoice.invoiceDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${invoice.currency} ${"%.2f".format(invoice.total)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                EInvoiceStatusChip(status = invoice.status)
            }
        }
    }
}

@Composable
private fun EInvoiceStatusChip(status: EInvoiceStatus) {
    SuggestionChip(
        onClick = {},
        label = { Text(status.label(), style = MaterialTheme.typography.labelSmall) },
        icon = {
            Icon(
                imageVector = status.icon(),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
        },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = status.color().copy(alpha = 0.12f),
            labelColor = status.color(),
            iconContentColor = status.color(),
        ),
    )
}

// ── Extension helpers ──────────────────────────────────────────────────────

private fun EInvoiceStatus.label(): String = when (this) {
    EInvoiceStatus.DRAFT -> "Draft"
    EInvoiceStatus.SUBMITTED -> "Submitted"
    EInvoiceStatus.ACCEPTED -> "Accepted"
    EInvoiceStatus.REJECTED -> "Rejected"
    EInvoiceStatus.CANCELLED -> "Cancelled"
}

private fun EInvoiceStatus.icon(): ImageVector = when (this) {
    EInvoiceStatus.DRAFT -> Icons.Default.Receipt
    EInvoiceStatus.SUBMITTED -> Icons.Default.HourglassEmpty
    EInvoiceStatus.ACCEPTED -> Icons.Default.CheckCircle
    EInvoiceStatus.REJECTED -> Icons.Default.Error
    EInvoiceStatus.CANCELLED -> Icons.Default.Error
}

@Composable
private fun EInvoiceStatus.color() = when (this) {
    EInvoiceStatus.DRAFT -> MaterialTheme.colorScheme.onSurfaceVariant
    EInvoiceStatus.SUBMITTED -> MaterialTheme.colorScheme.primary
    EInvoiceStatus.ACCEPTED -> MaterialTheme.colorScheme.tertiary
    EInvoiceStatus.REJECTED -> MaterialTheme.colorScheme.error
    EInvoiceStatus.CANCELLED -> MaterialTheme.colorScheme.outline
}
