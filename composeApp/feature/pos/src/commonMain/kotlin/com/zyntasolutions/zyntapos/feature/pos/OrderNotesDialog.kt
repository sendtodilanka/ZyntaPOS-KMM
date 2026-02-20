package com.zyntasolutions.zyntapos.feature.pos

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.tokens.ZentaSpacing

// ─────────────────────────────────────────────────────────────────────────────
// OrderNotesDialog — Free-text notes + reference number input.
// Both fields are optional. Confirm dispatches SetNotes intent.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Dialog for attaching free-text notes and an optional reference number to an order.
 *
 * Both fields are optional. The combined note string — `"[ref] notes"` when a
 * reference is present, or just the notes text otherwise — is passed to [onConfirm]
 * which should dispatch [PosIntent.SetNotes].
 *
 * @param currentNotes    Pre-populated note text (restored from [PosState.orderTotals]).
 * @param onConfirm       Invoked with the final concatenated note string.
 * @param onDismiss       Invoked when the dialog is dismissed without saving.
 */
@Composable
fun OrderNotesDialog(
    currentNotes: String = "",
    onConfirm: (notes: String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Split pre-populated notes back into ref + body if they follow "[ref] body" format
    val prefixPattern = Regex("""^\[([^\]]+)\]\s*(.*)$""", RegexOption.DOT_MATCHES_ALL)
    val match = prefixPattern.matchEntire(currentNotes)

    var referenceNumber by remember { mutableStateOf(match?.groupValues?.get(1) ?: "") }
    var noteText by remember { mutableStateOf(match?.groupValues?.get(2) ?: currentNotes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Order Notes",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(ZentaSpacing.md),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // ── Reference number ─────────────────────────────────────────
                OutlinedTextField(
                    value = referenceNumber,
                    onValueChange = { referenceNumber = it },
                    label = { Text("Reference Number (optional)") },
                    placeholder = { Text("e.g. PO-2026-001") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // ── Free-text notes ──────────────────────────────────────────
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Notes (optional)") },
                    placeholder = { Text("e.g. Customer requests gift wrapping") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp),
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val combined = buildCombinedNotes(referenceNumber.trim(), noteText.trim())
                onConfirm(combined)
            }) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * Builds the combined note string.
 *
 * Format: `"[<ref>] <notes>"` when reference is non-blank, otherwise just `<notes>`.
 */
private fun buildCombinedNotes(reference: String, notes: String): String = when {
    reference.isNotBlank() && notes.isNotBlank() -> "[$reference] $notes"
    reference.isNotBlank() -> "[$reference]"
    else -> notes
}
