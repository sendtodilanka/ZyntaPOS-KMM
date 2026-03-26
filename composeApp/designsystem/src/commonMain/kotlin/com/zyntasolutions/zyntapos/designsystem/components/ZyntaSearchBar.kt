package com.zyntasolutions.zyntapos.designsystem.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.zyntasolutions.zyntapos.core.i18n.StringResource

// ─────────────────────────────────────────────────────────────────────────────
// ZyntaSearchBar — Stateless; debounce handled by caller.
// Barcode scan icon toggles scan mode; caller owns the state.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Product/customer search bar with integrated barcode scan mode toggle.
 *
 * @param query Current search query text.
 * @param onQueryChange Emitted on every keystroke; caller handles debounce.
 * @param onClear Invoked when the clear (X) button is tapped.
 * @param onScanToggle Invoked when the barcode scan icon is tapped; caller toggles [isScanActive].
 * @param modifier Optional [Modifier].
 * @param placeholder Hint text shown when query is empty.
 * @param isScanActive When true, scan icon is highlighted to indicate active scan mode.
 * @param focusRequester External [FocusRequester] allowing callers to programmatically focus the field.
 * @param onSearch Invoked when the keyboard search/done action is triggered.
 */
@Composable
fun ZyntaSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onScanToggle: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    placeholder: String = "Search products or scan barcode…",
    isScanActive: Boolean = false,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onSearch: (() -> Unit)? = null,
) {
    val s = LocalStrings.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.focusRequester(focusRequester),
        placeholder = { Text(placeholder) },
        singleLine = true,
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = s[StringResource.COMMON_SEARCH],
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            Row {
                // Barcode scan toggle
                IconButton(onClick = onScanToggle) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = if (isScanActive) s[StringResource.COMMON_DISABLE_SCAN_CD] else s[StringResource.COMMON_ENABLE_SCAN_CD],
                        tint = if (isScanActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Clear button — only visible when query is non-empty
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = s[StringResource.COMMON_CLEAR_SEARCH_CD],
                        )
                    }
                }
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = if (onSearch != null) ImeAction.Search else ImeAction.Default,
        ),
        keyboardActions = KeyboardActions(
            onSearch = { onSearch?.invoke() },
        ),
    )
}

// ── Preview ───────────────────────────────────────────────────────────────────

@androidx.compose.ui.tooling.preview.Preview
@androidx.compose.runtime.Composable
private fun ZyntaSearchBarPreview() {
    com.zyntasolutions.zyntapos.designsystem.theme.ZyntaTheme {
        ZyntaSearchBar(
            query = "",
            onQueryChange = {},
            onClear = {},
            onScanToggle = {},
        )
    }
}
