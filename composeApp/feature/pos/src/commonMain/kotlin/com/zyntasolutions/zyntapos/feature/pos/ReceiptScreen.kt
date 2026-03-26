package com.zyntasolutions.zyntapos.feature.pos

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaDialogContent
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaDialogVariant
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaPageScaffold
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing

// ─────────────────────────────────────────────────────────────────────────────
// ReceiptScreen — Sprint 17, task 9.1.21 (refactored: RCV-7)
//
// BEFORE: received Order + PrinterConfig, built ESC/POS bytes inline via
//   EscPosReceiptBuilder, stripped control bytes in receiptTextFrom().
//
// AFTER: receives pre-computed receiptPreviewText: String from PosState.
//   The text is built in PosViewModel via ReceiptFormatter (domain layer).
//   No HAL imports remain in this file — presentation layer is clean.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Full-screen receipt preview shown after a successful payment.
 *
 * Renders [receiptPreviewText] — a plain monospace string pre-computed by
 * [com.zyntasolutions.zyntapos.domain.formatter.ReceiptFormatter] and stored in
 * [PosState.receiptPreviewText] — as a scrollable text block. This composable
 * performs **no receipt formatting** itself; it is a pure display component.
 *
 * ### Layout
 * ```
 * ┌──────────────────────────────────────────────────────────┐
 * │  TopAppBar — "Receipt #<orderNumber>"                    │
 * ├──────────────────────────────────────────────────────────┤
 * │  Receipt preview (scrollable)                            │
 * ├──────────────────────────────────────────────────────────┤
 * │  [ 🖨 Print ]  [ ↩ Reprint ]  [ 📄 A4 ]  [ ✉ ]  [ Skip ]│
 * └──────────────────────────────────────────────────────────┘
 * ```
 *
 * ### Action Row
 * - **Print**   → invokes [onPrint]; ViewModel dispatches [PosIntent.PrintCurrentReceipt].
 * - **Reprint** → invokes [onReprint]; ViewModel dispatches [PosIntent.ReprintReceipt].
 * - **A4**      → invokes [onPrintA4Invoice]; ViewModel dispatches [PosIntent.PrintA4Invoice].
 * - **Email**   → invokes [onEmail]; opens email dialog.
 * - **Skip**    → invokes [onSkip]; returns to POS screen without printing.
 */
@Composable
fun ReceiptScreen(
    receiptPreviewText: String,
    orderNumber: String,
    orderId: String,
    isPrinting: Boolean = false,
    isReprintingReceipt: Boolean = false,
    isPrintingA4: Boolean = false,
    printError: String? = null,
    onPrint: () -> Unit,
    onReprint: (orderId: String) -> Unit,
    onPrintA4Invoice: (orderId: String) -> Unit,
    onEmail: (orderId: String) -> Unit,
    onSkip: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    // ── Retry error dialog ────────────────────────────────────────────────────
    if (printError != null) {
        ZyntaDialogContent(
            variant = ZyntaDialogVariant.Confirm(
                title = s[StringResource.POS_PRINT_FAILED],
                message = "$printError\n\n${s[StringResource.POS_RETRY_PROMPT]}",
                confirmLabel = s[StringResource.COMMON_RETRY],
                cancelLabel = s[StringResource.COMMON_CANCEL],
                onConfirm = { onPrint(); onDismissError() },
                onCancel = onDismissError,
            ),
        )
    }

    ZyntaPageScaffold(
        title = "${s[StringResource.POS_RECEIPT]} — ${s[StringResource.POS_ORDER_NUMBER_PREFIX]}$orderNumber",
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // ── Receipt preview ────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(ZyntaSpacing.md),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface)
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                            .padding(ZyntaSpacing.md),
                    ) {
                        Text(
                            text = receiptPreviewText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // ── Action row ─────────────────────────────────────────────────────
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ZyntaSpacing.md, vertical = ZyntaSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Print button
                Button(
                    onClick = onPrint,
                    enabled = !isPrinting,
                    modifier = Modifier.weight(1f),
                ) {
                    if (isPrinting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(ZyntaSpacing.xs))
                        Text(s[StringResource.POS_PRINTING])
                    } else {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(ZyntaSpacing.xs))
                        Text(s[StringResource.COMMON_PRINT])
                    }
                }

                // Reprint button (for past reprints)
                OutlinedButton(
                    onClick = { onReprint(orderId) },
                    enabled = !isReprintingReceipt,
                    modifier = Modifier.weight(1f),
                ) {
                    if (isReprintingReceipt) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(ZyntaSpacing.xs))
                    Text(s[StringResource.POS_REPRINT])
                }

                // A4 Invoice button
                OutlinedButton(
                    onClick = { onPrintA4Invoice(orderId) },
                    enabled = !isPrintingA4,
                    modifier = Modifier.weight(1f),
                ) {
                    if (isPrintingA4) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(ZyntaSpacing.xs))
                    Text(s[StringResource.POS_A4])
                }

                // Email button
                OutlinedButton(
                    onClick = { onEmail(orderId) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(ZyntaSpacing.xs))
                    Text(s[StringResource.POS_EMAIL])
                }

                // Skip button
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(ZyntaSpacing.xs))
                    Text(s[StringResource.POS_SKIP])
                }
            }
        }
    }
}
