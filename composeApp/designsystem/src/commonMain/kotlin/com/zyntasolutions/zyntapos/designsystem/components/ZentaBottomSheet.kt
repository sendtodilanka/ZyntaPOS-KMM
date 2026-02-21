package com.zyntasolutions.zyntapos.designsystem.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// ─────────────────────────────────────────────────────────────────────────────
// ZentaBottomSheet — M3 ModalBottomSheet wrapper; sheetState hoisted.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A Material 3 [ModalBottomSheet] wrapper standardised for ZyntaPOS.
 *
 * The caller owns [sheetState] and is responsible for showing/hiding the sheet.
 * Typical usage:
 * ```
 * val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
 * var show by remember { mutableStateOf(false) }
 * if (show) {
 *     ZentaBottomSheet(sheetState = sheetState, onDismiss = { show = false }) {
 *         // content
 *     }
 * }
 * ```
 *
 * @param sheetState Hoisted [SheetState] from [rememberModalBottomSheetState].
 * @param onDismiss Invoked when the sheet is dismissed by drag or tap-outside.
 * @param modifier Optional [Modifier] for the sheet container.
 * @param dragHandleVisible Whether to show the M3 drag handle indicator.
 * @param content Slot for sheet body composable content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZentaBottomSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandleVisible: Boolean = true,
    content: @Composable () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        dragHandle = if (dragHandleVisible) ({ BottomSheetDefaults.DragHandle() }) else null,
    ) {
        content()
    }
}
