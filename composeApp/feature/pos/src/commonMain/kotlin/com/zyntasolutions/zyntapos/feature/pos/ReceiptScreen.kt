package com.zyntasolutions.zyntapos.feature.pos

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyntasolutions.zyntapos.designsystem.components.ZentaDialogContent
import com.zyntasolutions.zyntapos.designsystem.components.ZentaDialogVariant
import com.zyntasolutions.zyntapos.designsystem.tokens.ZentaSpacing
import com.zyntasolutions.zyntapos.domain.model.Order
import com.zyntasolutions.zyntapos.hal.printer.EscPosReceiptBuilder
import com.zyntasolutions.zyntapos.hal.printer.PrinterConfig

// ─────────────────────────────────────────────────────────────────────────────
// ReceiptScreen — Sprint 17, task 9.1.21
// Scrollable text-based receipt preview using EscPosReceiptBuilder output
// rendered as monospace text; action row: Print / Email / Skip buttons.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Full-screen receipt preview shown after a successful payment.
 *
 * Renders the receipt as a scrollable monospace text block constructed by
 * [EscPosReceiptBuilder.buildReceipt] and decoded to a UTF-8 string (ESC/POS
 * control bytes are stripped so the preview is human-readable).
 *
 * ### Layout
 * ```
 * ┌──────────────────────────────────────┐
 * │  ZentaTopAppBar — "Receipt"          │
 * ├──────────────────────────────────────┤
 * │  Receipt preview (scrollable)        │
 * │  ┌────────────────────────────────┐  │
 * │  │  Monospace receipt text        │  │
 * │  └────────────────────────────────┘  │
 * ├──────────────────────────────────────┤
 * │  [ 🖨 Print ]  [ ✉ Email ]  [ Skip ] │
 * └──────────────────────────────────────┘
 * ```
 *
 * ### Receipt Text Rendering
 * The raw ESC/POS bytes from [EscPosReceiptBuilder] contain binary control sequences
 * that are meaningless on screen. [receiptTextFrom] strips all bytes < 0x20 (except
 * `0x0A` line-feed) before decoding to a [String] so the preview matches the printed
 * layout faithfully.
 *
 * ### Action Row
 * - **Print** → invokes [onPrint]; caller dispatches [PosIntent] or use case.
 * - **Email** → invokes [onEmail]; opens email dialog or starts email intent.
 * - **Skip** → invokes [onSkip]; navigates back to the POS screen.
 *
 * @param order         The completed [Order] whose receipt is being previewed.
 * @param printerConfig Active [PrinterConfig] used for preview layout width.
 * @param isPrinting    When `true`, the Print button shows a loading indicator.
 * @param printError    Non-null error message triggers a retry [ZentaDialog].
 * @param onPrint       Invoked when the operator taps "Print".
 * @param onEmail       Invoked when the operator taps "Email".
 * @param onSkip        Invoked when the operator taps "Skip" to return to POS.
 * @param onDismissError Invoked to clear [printError] (typically after retry dialog).
 * @param modifier      Optional [Modifier].
 */
@Composable
fun ReceiptScreen(
    order: Order,
    printerConfig: PrinterConfig = PrinterConfig.DEFAULT,
    isPrinting: Boolean = false,
    printError: String? = null,
    onPrint: () -> Unit,
    onEmail: () -> Unit,
    onSkip: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // ── Build receipt preview text ────────────────────────────────────────────
    val receiptText: String = remember(order, printerConfig) {
        val bytes = EscPosReceiptBuilder(printerConfig).buildReceipt(order, printerConfig)
        receiptTextFrom(bytes)
    }

    // ── Retry error dialog ────────────────────────────────────────────────────
    if (printError != null) {
        ZentaDialogContent(
            variant = ZentaDialogVariant.Confirm(
                title = "Print Failed",
                message = "$printError\n\nWould you like to try again?",
                confirmLabel = "Retry",
                cancelLabel = "Cancel",
                onConfirm = { onPrint(); onDismissError() },
                onCancel = onDismissError,
            ),
        )
    }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(title = { Text("Receipt — Order #${order.orderNumber}") })
        },
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
                    .padding(ZentaSpacing.md),
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
                            .padding(ZentaSpacing.md),
                    ) {
                        Text(
                            text = receiptText,
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
                    .padding(horizontal = ZentaSpacing.md, vertical = ZentaSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(ZentaSpacing.sm),
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
                        Spacer(Modifier.width(ZentaSpacing.xs))
                        Text("Printing…")
                    } else {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(ZentaSpacing.xs))
                        Text("Print")
                    }
                }

                // Email button
                OutlinedButton(
                    onClick = onEmail,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(ZentaSpacing.xs))
                    Text("Email")
                }

                // Skip button
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(ZentaSpacing.xs))
                    Text("Skip")
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helper: strip ESC/POS control bytes for human-readable preview
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Converts raw ESC/POS [bytes] to a printable string suitable for on-screen preview.
 *
 * - Keeps printable ASCII (0x20–0x7E) and line-feed (0x0A).
 * - Replaces all other control bytes (ESC sequences, GS commands, etc.) with nothing.
 * - Decodes the remaining bytes as UTF-8.
 */
private fun receiptTextFrom(bytes: ByteArray): String {
    val cleaned = bytes.filter { b ->
        b == 0x0A.toByte() || (b >= 0x20 && b <= 0x7E)
    }.toByteArray()
    return cleaned.toString(Charsets.UTF_8)
}
