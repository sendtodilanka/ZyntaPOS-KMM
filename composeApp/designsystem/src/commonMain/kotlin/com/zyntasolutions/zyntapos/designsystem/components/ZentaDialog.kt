package com.zyntasolutions.zyntapos.designsystem.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.zyntasolutions.zyntapos.designsystem.tokens.ZentaSpacing

// ─────────────────────────────────────────────────────────────────────────────
// ZentaDialog — Sealed variants: Confirm, Alert, Input
// All stateless; state hoisted to caller.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Sealed hierarchy of ZentaPOS dialogs.
 * Render via [ZentaDialogContent] by passing the desired variant.
 */
sealed class ZentaDialogVariant {
    /**
     * Two-action confirmation dialog.
     * @param title Dialog heading.
     * @param message Body message.
     * @param confirmLabel Label for the positive action button (default "Confirm").
     * @param cancelLabel Label for the dismissal button (default "Cancel").
     * @param onConfirm Invoked when confirm is tapped.
     * @param onCancel Invoked when cancel is tapped or dialog is dismissed.
     * @param isDangerous When true, the confirm button uses error/danger colors.
     */
    data class Confirm(
        val title: String,
        val message: String,
        val confirmLabel: String = "Confirm",
        val cancelLabel: String = "Cancel",
        val onConfirm: () -> Unit,
        val onCancel: () -> Unit,
        val isDangerous: Boolean = false,
    ) : ZentaDialogVariant()

    /**
     * Single-action informational alert dialog.
     * @param title Dialog heading.
     * @param message Body message.
     * @param okLabel Label for the acknowledgement button (default "OK").
     * @param onOk Invoked when OK is tapped or dialog is dismissed.
     */
    data class Alert(
        val title: String,
        val message: String,
        val okLabel: String = "OK",
        val onOk: () -> Unit,
    ) : ZentaDialogVariant()

    /**
     * Text input dialog.
     * @param title Dialog heading.
     * @param hint Placeholder hint text in the text field.
     * @param initialValue Pre-filled text value.
     * @param confirmLabel Label for the confirm button (default "OK").
     * @param cancelLabel Label for the cancel button (default "Cancel").
     * @param inputType Keyboard type for the text field.
     * @param onConfirm Invoked with the entered text when confirmed.
     * @param onCancel Invoked when the dialog is dismissed without confirming.
     */
    data class Input(
        val title: String,
        val hint: String = "",
        val initialValue: String = "",
        val confirmLabel: String = "OK",
        val cancelLabel: String = "Cancel",
        val inputType: KeyboardType = KeyboardType.Text,
        val onConfirm: (text: String) -> Unit,
        val onCancel: () -> Unit,
    ) : ZentaDialogVariant()
}

/**
 * Renders a [ZentaDialogVariant] as a Material 3 [AlertDialog].
 * Show/hide is controlled externally — wrap in an `if (isVisible)` block.
 *
 * @param variant The dialog configuration to render.
 */
@Composable
fun ZentaDialogContent(variant: ZentaDialogVariant) {
    when (variant) {
        is ZentaDialogVariant.Alert -> AlertDialog(
            onDismissRequest = variant.onOk,
            title = { Text(variant.title) },
            text = { Text(variant.message) },
            confirmButton = {
                TextButton(onClick = variant.onOk) { Text(variant.okLabel) }
            },
        )

        is ZentaDialogVariant.Confirm -> AlertDialog(
            onDismissRequest = variant.onCancel,
            title = { Text(variant.title) },
            text = { Text(variant.message) },
            confirmButton = {
                if (variant.isDangerous) {
                    Button(
                        onClick = variant.onConfirm,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) { Text(variant.confirmLabel) }
                } else {
                    Button(onClick = variant.onConfirm) { Text(variant.confirmLabel) }
                }
            },
            dismissButton = {
                TextButton(onClick = variant.onCancel) { Text(variant.cancelLabel) }
            },
        )

        is ZentaDialogVariant.Input -> {
            var text by remember { mutableStateOf(variant.initialValue) }
            AlertDialog(
                onDismissRequest = variant.onCancel,
                title = { Text(variant.title) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            placeholder = { Text(variant.hint) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = variant.inputType),
                        )
                        Spacer(Modifier.height(ZentaSpacing.xs))
                    }
                },
                confirmButton = {
                    Button(onClick = { variant.onConfirm(text) }) { Text(variant.confirmLabel) }
                },
                dismissButton = {
                    TextButton(onClick = variant.onCancel) { Text(variant.cancelLabel) }
                },
            )
        }
    }
}
