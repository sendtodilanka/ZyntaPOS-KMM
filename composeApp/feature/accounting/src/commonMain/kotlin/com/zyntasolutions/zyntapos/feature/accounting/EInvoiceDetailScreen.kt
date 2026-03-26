package com.zyntasolutions.zyntapos.feature.accounting

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
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
    val s = LocalStrings.current
    val invoice = state.selectedInvoice

    if (invoice == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (state.isLoading) CircularProgressIndicator()
            else Text(s[StringResource.EINVOICE_NOT_FOUND], color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Text("${s[StringResource.COMMON_DATE]}: ${invoice.invoiceDate}", style = MaterialTheme.typography.bodySmall)
                Text("${s[StringResource.EINVOICE_STORE]}: ${invoice.storeId}", style = MaterialTheme.typography.bodySmall)
                invoice.irdReferenceNumber?.let { ref ->
                    Text(
                        "${s[StringResource.EINVOICE_IRD_REF]}: $ref",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Medium,
                    )
                }
                invoice.rejectionReason?.let { reason ->
                    Text(
                        "${s[StringResource.EINVOICE_REJECTION]}: $reason",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(ZyntaSpacing.md), verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md)) {

            // ── Customer ──────────────────────────────────────────────────
            InvoiceSection(title = s[StringResource.EINVOICE_CUSTOMER]) {
                InfoRow(s[StringResource.EINVOICE_NAME], invoice.customerName)
                invoice.customerTaxId?.let { InfoRow(s[StringResource.EINVOICE_TAX_ID], it) }
            }

            HorizontalDivider()

            // ── Line Items ────────────────────────────────────────────────
            InvoiceSection(title = s[StringResource.EINVOICE_LINE_ITEMS]) {
                invoice.lineItems.forEachIndexed { idx, item ->
                    LineItemRow(index = idx + 1, item = item, currency = invoice.currency)
                    if (idx < invoice.lineItems.lastIndex) HorizontalDivider(thickness = 0.5.dp)
                }
            }

            HorizontalDivider()

            // ── Totals ────────────────────────────────────────────────────
            InvoiceSection(title = s[StringResource.EINVOICE_TOTALS]) {
                InfoRow(s[StringResource.EINVOICE_SUBTOTAL], "${invoice.currency} ${"%.2f".format(invoice.subtotal)}")
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
                    Text(s[StringResource.EINVOICE_TOTAL], style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                            Text(s[StringResource.EINVOICE_SUBMITTING])
                        } else {
                            Icon(Icons.Default.CloudUpload, contentDescription = null)
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text(
                                if (invoice.status == EInvoiceStatus.REJECTED) s[StringResource.EINVOICE_RESUBMIT_IRD]
                                else s[StringResource.EINVOICE_SUBMIT_IRD],
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
                        Text(s[StringResource.EINVOICE_CANCEL_INVOICE])
                    }
                }
            }
        }
    }

    // ── Cancel confirmation dialog ─────────────────────────────────────────
    state.showCancelConfirm?.let { inv ->
        AlertDialog(
            onDismissRequest = { onIntent(EInvoiceIntent.DismissCancel) },
            title = { Text(s[StringResource.EINVOICE_CANCEL_INVOICE]) },
            text = {
                Text(
                    "${s[StringResource.EINVOICE_CANCEL_CONFIRM]} #${inv.invoiceNumber}? " +
                        s[StringResource.EINVOICE_CANCEL_WARNING],
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onIntent(EInvoiceIntent.ConfirmCancel) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(s[StringResource.EINVOICE_CANCEL_INVOICE])
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(EInvoiceIntent.DismissCancel) }) { Text(s[StringResource.EINVOICE_KEEP]) }
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
    val icon = when (status) {
        EInvoiceStatus.DRAFT -> Icons.Default.Receipt
        EInvoiceStatus.SUBMITTED -> Icons.Default.HourglassEmpty
        EInvoiceStatus.ACCEPTED -> Icons.Default.CheckCircle
        EInvoiceStatus.REJECTED -> Icons.Default.Error
        EInvoiceStatus.CANCELLED -> Icons.Default.Cancel
    }
    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp),
            )
            Text(
                status.name,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
