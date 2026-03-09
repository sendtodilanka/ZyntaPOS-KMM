package com.zyntasolutions.zyntapos.debug.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButton
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButtonVariant

/**
 * Confirmation dialog for destructive debug actions.
 *
 * The confirm button remains disabled until the user types [confirmWord] exactly.
 * This prevents accidental execution of irreversible operations.
 *
 * @param title         Dialog title, e.g. "Reset Database".
 * @param message       Consequence description shown below the title.
 * @param confirmWord   Exact string the user must type to enable the confirm button.
 * @param onConfirm     Invoked when the user types [confirmWord] and taps Confirm.
 * @param onDismiss     Invoked when the user taps Cancel or dismisses the dialog.
 */
@Composable
fun ConfirmDestructiveDialog(
    title: String,
    message: String,
    confirmWord: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var typedWord by remember { mutableStateOf("") }
    val isConfirmEnabled = typedWord.trim() == confirmWord

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Type \"$confirmWord\" to confirm:",
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = typedWord,
                    onValueChange = { typedWord = it },
                    singleLine = true,
                    isError = typedWord.isNotEmpty() && !isConfirmEnabled,
                )
            }
        },
        confirmButton = {
            ZyntaButton(
                text = "Confirm",
                onClick = onConfirm,
                variant = ZyntaButtonVariant.Danger,
                enabled = isConfirmEnabled,
            )
        },
        dismissButton = {
            ZyntaButton(
                text = "Cancel",
                onClick = onDismiss,
                variant = ZyntaButtonVariant.Ghost,
            )
        },
    )
}
