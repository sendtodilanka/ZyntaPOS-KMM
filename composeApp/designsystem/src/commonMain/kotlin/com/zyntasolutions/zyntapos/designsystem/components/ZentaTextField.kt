package com.zyntasolutions.zyntapos.designsystem.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.VisualTransformation

// ─────────────────────────────────────────────────────────────────────────────
// ZentaTextField — Stateless outlined text field; all state hoisted to caller
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Standard outlined text input for ZentaPOS forms.
 *
 * @param value Current text value.
 * @param onValueChange Called on each keystroke with the new value.
 * @param label Floating label text shown inside the field.
 * @param modifier Optional [Modifier]; defaults to [fillMaxWidth].
 * @param placeholder Optional hint text shown when field is empty.
 * @param error Optional error message — renders below the field in error style when non-null.
 * @param leadingIcon Optional icon displayed at the start of the field.
 * @param trailingIcon Optional icon displayed at the end of the field (e.g. visibility toggle).
 * @param keyboardOptions Keyboard type, IME action, capitalisation, etc.
 * @param visualTransformation Transforms visible text (e.g. [PasswordVisualTransformation]).
 * @param enabled When false, field is not editable.
 * @param singleLine When true, field collapses to a single scrollable line.
 * @param readOnly When true, value is displayed but cannot be edited.
 * @param maxLines Maximum number of visible lines (ignored when [singleLine] is true).
 */
@Composable
fun ZentaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    placeholder: String? = null,
    error: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    readOnly: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        placeholder = if (placeholder != null) ({ Text(placeholder) }) else null,
        isError = error != null,
        supportingText = if (error != null) ({ Text(text = error, color = MaterialTheme.colorScheme.error) }) else null,
        leadingIcon = if (leadingIcon != null) ({
            Icon(imageVector = leadingIcon, contentDescription = null)
        }) else null,
        trailingIcon = trailingIcon,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        enabled = enabled,
        singleLine = singleLine,
        readOnly = readOnly,
        maxLines = maxLines,
    )
}
