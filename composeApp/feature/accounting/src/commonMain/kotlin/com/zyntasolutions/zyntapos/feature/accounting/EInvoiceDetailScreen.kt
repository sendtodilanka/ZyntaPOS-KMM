package com.zyntasolutions.zyntapos.feature.accounting

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.EInvoice
import com.zyntasolutions.zyntapos.domain.model.EInvoiceLineItem
import com.zyntasolutions.zyntapos.domain.model.EInvoiceStatus

/**
 * E-Invoice detail screen (Sprint 18-24).
 *
 * Shows the full invoice breakdown: customer details, line items, tax breakdown,
 * totals, IRD submission status, and reference number. For DRAFT invoices, a
 * "Submit to IRD" action button is available. For DRAFT/SUBMITTED invoices, a
 * cancel action is available.
 *
 * @param state    Current [EInvoiceState].
 * @param onIntent Dispatches intents to [EInvoiceViewModel].
 * @param modifier Optional modifier.
 */
@Composable
fun EInvoiceDetailScreen(
    state: EInvoiceState,
    onIntent: (EInvoiceIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val invoice = state.selectedInvoice

    if (invoice == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (state.isLoading) CircularProgressIndicator()
            else Text("Invoice not found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // ── Header ────────────────────────────────────────────────────────
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(ZyntaSpacing.md)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "#${invoice.invoiceNumber}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    InvoiceStatusBadge(invoice.status)
                }
                Spacer(Modifier.height(4.dp))
                Text("Date: ${invoice.invoiceDate}", style = MaterialTheme.typography.bodySmall)
                Text("Store: ${invoice.storeId}", style = MaterialTheme.typography.bodySmall)
                invoice.irdReferenceNumber?.let { ref ->
                    Text(
                        "IRD Ref: $ref",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Medium,
                    )
                }
                invoice.rejectionReason?.let { reason ->
                    Text(
                        "Rejection: $reason",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(ZyntaSpacing.md), verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md)) {

            // ── Customer ──────────────────────────────────────────────────
            InvoiceSection(title = "Customer") {
                InfoRow("Name", invoice.customerName)
                invoice.customerTaxId?.let { InfoRow("Tax ID / TIN", it) }
            }

            HorizontalDivider()

            // ── Line Items ────────────────────────────────────────────────
            InvoiceSection(title = "Line Items") {
                invoice.lineItems.forEachIndexed { idx, item ->
                    LineItemRow(index = idx + 1, item = item, currency = invoice.currency)
                    if (idx < invoice.lineItems.lastIndex) HorizontalDivider(thickness = 0.5.dp)
                }
            }

            HorizontalDivider()

            // ── Totals ────────────────────────────────────────────────────
            InvoiceSection(title = "Totals") {
                InfoRow("Subtotal", "${invoice.currency} ${"%.2f".format(invoice.subtotal)}")
                invoice.taxBreakdown.forEach { taxLine ->
                    InfoRow(
                        "${taxLine.taxRate}% Tax on ${"%.2f".format(taxLine.taxablAmount)}",
                        "${invoice.currency} ${"%.2f".format(taxLine.taxAmount)}",
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${invoice.currency} ${"%.2f".format(invoice.total)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            HorizontalDivider()

            // ── Actions ───────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm)) {
                if (invoice.status == EInvoiceStatus.DRAFT || invoice.status == EInvoiceStatus.REJECTED) {
                    Button(
                        onClick = { onIntent(EInvoiceIntent.SubmitToIrd(invoice.id)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isSubmitting,
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                                strokeWidth = ButtonDefaults.IconSize / 5,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text("Submitting…")
                        } else {
                            Icon(Icons.Default.CloudUpload, contentDescription = null)
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text(
                                if (invoice.status == EInvoiceStatus.REJECTED) "Resubmit to IRD"
                                else "Submit to IRD",
                            )
                        }
                    }
                }

                if (invoice.status == EInvoiceStatus.DRAFT || invoice.status == EInvoiceStatus.SUBMITTED) {
                    OutlinedButton(
                        onClick = { onIntent(EInvoiceIntent.RequestCancel(invoice)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = null)
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Cancel Invoice")
                    }
                }
            }
        }
    }

    // ── Cancel confirmation dialog ─────────────────────────────────────────
    state.showCancelConfirm?.let { inv ->
        AlertDialog(
            onDismissRequest = { onIntent(EInvoiceIntent.DismissCancel) },
            title = { Text("Cancel Invoice") },
            text = {
                Text(
                    "Cancel invoice #${inv.invoiceNumber}? " +
                        "Cancelled invoices cannot be resubmitted.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onIntent(EInvoiceIntent.ConfirmCancel) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Cancel Invoice")
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(EInvoiceIntent.DismissCancel) }) { Text("Keep") }
            },
        )
    }
}

// ── Private composables ────────────────────────────────────────────────────

@Composable
private fun InvoiceSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.xs)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun LineItemRow(index: Int, item: EInvoiceLineItem, currency: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = ZyntaSpacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "$index. ${item.description}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                "$currency ${"%.2f".format(item.lineTotal)}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
        }
        Text(
            "${item.quantity} × ${"%.2f".format(item.unitPrice)} · Tax ${item.taxRate}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InvoiceStatusBadge(status: EInvoiceStatus) {
    val (containerColor, contentColor) = when (status) {
        EInvoiceStatus.DRAFT -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        EInvoiceStatus.SUBMITTED -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        EInvoiceStatus.ACCEPTED -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        EInvoiceStatus.REJECTED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        EInvoiceStatus.CANCELLED -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.outline
    }
    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            status.name,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = FontWeight.Bold,
        )
    }
}
