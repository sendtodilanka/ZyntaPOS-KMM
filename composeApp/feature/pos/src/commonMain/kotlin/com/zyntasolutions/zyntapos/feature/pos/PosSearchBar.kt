package com.zyntasolutions.zyntapos.feature.pos

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.zyntasolutions.zyntapos.designsystem.components.ZentaSearchBar
import com.zyntasolutions.zyntapos.designsystem.tokens.ZentaSpacing

/**
 * POS product search bar composable (Sprint 14, task 9.1.4).
 *
 * Wraps [ZentaSearchBar] with POS-specific wiring:
 * - Forwards keystroke changes as [PosIntent.SearchQueryChanged] — debounce (300 ms) is
 *   applied inside [PosViewModel], **not** here. The composable remains stateless.
 * - Focus changes dispatch [PosIntent.SearchFocusChanged] to drive [PosState.isSearchFocused]
 *   (used to show/hide the scanner FAB).
 * - The barcode scan icon toggles [PosState.scannerActive] via [PosIntent.SetScannerActive].
 *   When [scannerActive] is `true` the icon is highlighted (tinted primary).
 *
 * ### Keyboard shortcut integration
 * [KeyboardShortcutHandler] (Desktop, jvmMain) calls [focusRequester.requestFocus]` when
 * `F2` is pressed. The [focusRequester] is created here and should be hoisted to the parent
 * [PosScreen] scope if it needs to be shared with the keyboard handler.
 *
 * @param query        Current search text from [PosState.searchQuery].
 * @param scannerActive Whether scanner mode is active ([PosState.scannerActive]).
 * @param onQueryChange Called on every keystroke; dispatches [PosIntent.SearchQueryChanged].
 * @param onFocusChange Called on focus gain/loss; dispatches [PosIntent.SearchFocusChanged].
 * @param onScanToggle  Called when the barcode icon is tapped; dispatches [PosIntent.SetScannerActive].
 * @param focusRequester External [FocusRequester]; share with keyboard handler on Desktop.
 * @param modifier      Optional root [Modifier].
 */
@Composable
fun PosSearchBar(
    query: String,
    scannerActive: Boolean,
    onQueryChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onScanToggle: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    ZentaSearchBar(
        query = query,
        onQueryChange = onQueryChange,
        onClear = { onQueryChange("") },
        onScanToggle = onScanToggle,
        isScanActive = scannerActive,
        focusRequester = focusRequester,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ZentaSpacing.md, vertical = ZentaSpacing.sm),
    )
}
