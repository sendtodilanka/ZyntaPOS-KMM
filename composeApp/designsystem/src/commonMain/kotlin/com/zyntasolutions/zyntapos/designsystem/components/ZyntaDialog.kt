package com.zyntasolutions.zyntapos.designsystem.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing

// ─────────────────────────────────────────────────────────────────────────────
// ZyntaDialog — Sealed variants: Confirm, Alert, Input
// All stateless; state hoisted to caller.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Sealed hierarchy of ZyntaPOS dialogs.
 * Render via [ZyntaDialogContent] by passing the desired variant.
 */
sealed class ZyntaDialogVariant {
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
    ) : ZyntaDialogVariant()

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
    ) : ZyntaDialogVariant()

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
    ) : ZyntaDialogVariant()
}

/**
 * Renders a [ZyntaDialogVariant] as a Material 3 [AlertDialog].
 * Show/hide is controlled externally — wrap in an `if (isVisible)` block.
 *
 * @param variant The dialog configuration to render.
 */
@Composable
fun ZyntaDialogContent(variant: ZyntaDialogVariant) {
    when (variant) {
        is ZyntaDialogVariant.Alert -> AlertDialog(
            onDismissRequest = variant.onOk,
            title = { Text(variant.title) },
            text = { Text(variant.message) },
            confirmButton = {
                TextButton(onClick = variant.onOk) { Text(variant.okLabel) }
            },
        )

        is ZyntaDialogVariant.Confirm -> AlertDialog(
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

        is ZyntaDialogVariant.Input -> {
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
                        Spacer(Modifier.height(ZyntaSpacing.xs))
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

// ── Preview ───────────────────────────────────────────────────────────────────

@org.jetbrains.compose.ui.tooling.preview.Preview
@androidx.compose.runtime.Composable
private fun ZyntaDialogPreview() {
    com.zyntasolutions.zyntapos.designsystem.theme.ZyntaTheme {
        ZyntaDialogContent(
            variant = ZyntaDialogVariant.Confirm(
                title = "Confirm Action",
                message = "Are you sure you want to continue?",
                onConfirm = {},
                onCancel = {},
            ),
        )
    }
}
