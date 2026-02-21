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
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaDialogContent
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaDialogVariant
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
 * ┌──────────────────────────────────────┐
 * │  TopAppBar — "Receipt #<orderNumber>"│
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
 * ### Action Row
 * - **Print** → invokes [onPrint]; ViewModel dispatches [PosIntent.PrintCurrentReceipt].
 * - **Email** → invokes [onEmail].
 * - **Skip** → invokes [onSkip]; returns to POS screen without printing.
 *
 * @param receiptPreviewText Pre-formatted receipt string from [PosState.receiptPreviewText].
 *   Typically produced by [com.zyntasolutions.zyntapos.domain.formatter.ReceiptFormatter].
 * @param orderNumber        The order number shown in the top app bar title.
 * @param isPrinting         When `true`, the Print button shows a loading indicator.
 * @param printError         Non-null error message triggers a retry [ZyntaDialog].
 * @param onPrint            Invoked when the cashier taps "Print".
 * @param onEmail            Invoked when the cashier taps "Email".
 * @param onSkip             Invoked when the cashier taps "Skip" to return to POS.
 * @param onDismissError     Invoked to clear [printError] after the retry dialog is handled.
 * @param modifier           Optional [Modifier].
 */
@Composable
fun ReceiptScreen(
    receiptPreviewText: String,
    orderNumber: String,
    isPrinting: Boolean = false,
    printError: String? = null,
    onPrint: () -> Unit,
    onEmail: () -> Unit,
    onSkip: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // ── Retry error dialog ────────────────────────────────────────────────────
    if (printError != null) {
        ZyntaDialogContent(
            variant = ZyntaDialogVariant.Confirm(
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
            TopAppBar(title = { Text("Receipt — Order #$orderNumber") })
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
                        Text("Printing…")
                    } else {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(ZyntaSpacing.xs))
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
                    Spacer(Modifier.width(ZyntaSpacing.xs))
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
                    Spacer(Modifier.width(ZyntaSpacing.xs))
                    Text("Skip")
                }
            }
        }
    }
}
