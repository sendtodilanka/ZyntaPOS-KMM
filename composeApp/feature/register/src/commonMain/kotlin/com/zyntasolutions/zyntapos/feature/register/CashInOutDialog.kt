package com.zyntasolutions.zyntapos.feature.register

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.components.NumericPadMode
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaNumericPad
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.CashMovement

/**
 * Modal dialog for recording a Cash In or Cash Out movement against the active session.
 *
 * ## Layout
 * Full-screen dialog (compact) / centred dialog (expanded):
 * 1. **Type toggle** — segmented-button row: "Cash In" / "Cash Out".
 * 2. **ZyntaNumericPad (PRICE mode)** — amount entry with right-to-left formatting.
 * 3. **Reason text field** — mandatory audit-trail description.
 * 4. **Action row** — "Cancel" (outlined) + "Confirm" (primary).
 *
 * ## Validation (inline, below each field)
 * - Amount must be > 0 (`errors["amount"]`).
 * - Reason must not be blank (`errors["reason"]`).
 *
 * This composable is **stateless** — all state is driven by [CashInOutDialogState]
 * and all mutations are reported via callbacks to [RegisterViewModel].
 *
 * @param dialogState      Current immutable dialog state.
 * @param isLoading        True while the movement is being persisted.
 * @param onTypeChange     Called when the IN/OUT toggle is tapped.
 * @param onDigit          Called with the digit character tapped on the numeric pad.
 * @param onDoubleZero     Called when "00" is tapped.
 * @param onDecimal        Called when "." is tapped (no-op in PRICE mode).
 * @param onBackspace      Called when backspace is tapped.
 * @param onClear          Called when "C" is tapped.
 * @param onReasonChanged  Called when the reason text field changes.
 * @param onConfirm        Called when the "Confirm" button is tapped.
 * @param onDismiss        Called when the dialog is dismissed (cancel or outside tap).
 */
@Composable
fun CashInOutDialog(
    dialogState: CashInOutDialogState,
    isLoading: Boolean,
    onTypeChange: (CashMovement.Type) -> Unit,
    onDigit: (String) -> Unit,
    onDoubleZero: () -> Unit,
    onDecimal: () -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onReasonChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (dialogState.type == CashMovement.Type.IN) "Cash In" else "Cash Out",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            CashInOutDialogContent(
                dialogState = dialogState,
                onTypeChange = onTypeChange,
                onDigit = onDigit,
                onDoubleZero = onDoubleZero,
                onDecimal = onDecimal,
                onBackspace = onBackspace,
                onClear = onClear,
                onReasonChanged = onReasonChanged,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isLoading,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Confirm")
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Cancel")
            }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Dialog content (extracted for testability)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CashInOutDialogContent(
    dialogState: CashInOutDialogState,
    onTypeChange: (CashMovement.Type) -> Unit,
    onDigit: (String) -> Unit,
    onDoubleZero: () -> Unit,
    onDecimal: () -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onReasonChanged: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = ZyntaSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Type toggle ───────────────────────────────────────────────────
        CashTypeSegmentedButtons(
            selectedType = dialogState.type,
            onSelect = onTypeChange,
        )

        // ── Numeric Pad (PRICE mode) ───────────────────────────────────────
        val displayValue = buildString {
            val raw = dialogState.amountRaw.padStart(3, '0')
            append(raw.dropLast(2))
            append(".")
            append(raw.takeLast(2))
        }

        ZyntaNumericPad(
            displayValue = displayValue,
            onDigit = onDigit,
            onDoubleZero = onDoubleZero,
            onDecimal = onDecimal,
            onBackspace = onBackspace,
            onClear = onClear,
            mode = NumericPadMode.PRICE,
        )

        if (dialogState.validationErrors.containsKey("amount")) {
            Text(
                text = dialogState.validationErrors["amount"]!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        // ── Reason text field ─────────────────────────────────────────────
        OutlinedTextField(
            value = dialogState.reason,
            onValueChange = onReasonChanged,
            label = { Text("Reason *") },
            isError = dialogState.validationErrors.containsKey("reason"),
            supportingText = if (dialogState.validationErrors.containsKey("reason")) {
                { Text(dialogState.validationErrors["reason"]!!) }
            } else null,
            placeholder = { Text("e.g. Petty cash, bank drop…") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Segmented button toggle
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CashTypeSegmentedButtons(
    selectedType: CashMovement.Type,
    onSelect: (CashMovement.Type) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = selectedType == CashMovement.Type.IN,
            onClick = { onSelect(CashMovement.Type.IN) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            icon = {
                SegmentedButtonDefaults.Icon(active = selectedType == CashMovement.Type.IN) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = null,
                        modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                    )
                }
            },
        ) {
            Text("Cash In")
        }

        SegmentedButton(
            selected = selectedType == CashMovement.Type.OUT,
            onClick = { onSelect(CashMovement.Type.OUT) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            icon = {
                SegmentedButtonDefaults.Icon(active = selectedType == CashMovement.Type.OUT) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = null,
                        modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                    )
                }
            },
        ) {
            Text("Cash Out")
        }
    }
}
