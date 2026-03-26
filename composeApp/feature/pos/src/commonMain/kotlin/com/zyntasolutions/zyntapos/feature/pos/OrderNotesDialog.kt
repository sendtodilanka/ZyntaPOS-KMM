package com.zyntasolutions.zyntapos.feature.pos

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing

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

    val s = LocalStrings.current
    var referenceNumber by remember { mutableStateOf(match?.groupValues?.get(1) ?: "") }
    var noteText by remember { mutableStateOf(match?.groupValues?.get(2) ?: currentNotes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = s[StringResource.POS_ORDER_NOTES_TITLE],
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // ── Reference number ─────────────────────────────────────────
                OutlinedTextField(
                    value = referenceNumber,
                    onValueChange = { referenceNumber = it },
                    label = { Text(s[StringResource.POS_REFERENCE_NUMBER_LABEL]) },
                    placeholder = { Text(s[StringResource.POS_REFERENCE_NUMBER_PLACEHOLDER]) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // ── Free-text notes ──────────────────────────────────────────
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text(s[StringResource.POS_NOTES_LABEL]) },
                    placeholder = { Text(s[StringResource.POS_NOTES_PLACEHOLDER]) },
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
                Text(s[StringResource.COMMON_CONFIRM])
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s[StringResource.COMMON_CANCEL]) }
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
