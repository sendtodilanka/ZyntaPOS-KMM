package com.zyntasolutions.zyntapos.designsystem.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────────────────────
// ZyntaDropdownMenu — Theme-aware DropdownMenu wrapper for ZyntaPOS.
//
// Wraps Material 3 DropdownMenu and DropdownMenuItem so that ZyntaPOS can
// apply custom theming, shapes, and colors globally in a single place.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A ZyntaPOS-themed dropdown menu.
 *
 * Wraps [DropdownMenu] with consistent ZyntaPOS surface colors and shapes,
 * enabling centralised theme customisation in future design-system updates.
 *
 * @param expanded Whether the menu is currently shown.
 * @param onDismissRequest Called when the menu should be dismissed (back press, outside tap).
 * @param modifier Optional [Modifier].
 * @param offset Optional positional offset from the anchor.
 * @param content Menu content — typically [ZyntaDropdownMenuItem] composables.
 */
@Composable
fun ZyntaDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        shape = MaterialTheme.shapes.medium,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        content = content,
    )
}

/**
 * A ZyntaPOS-themed dropdown menu item.
 *
 * Wraps [DropdownMenuItem] with consistent ZyntaPOS typography and icon styling.
 *
 * @param text Primary text content.
 * @param onClick Called when the item is tapped.
 * @param modifier Optional [Modifier].
 * @param leadingIcon Optional leading icon composable.
 * @param trailingIcon Optional trailing icon composable.
 * @param enabled Whether the item is interactive.
 * @param colors Color overrides (defaults to theme-aware colors).
 * @param contentPadding Padding inside the menu item.
 */
@Composable
fun ZyntaDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    colors: MenuItemColors = MenuDefaults.itemColors(),
    contentPadding: PaddingValues = MenuDefaults.DropdownMenuItemContentPadding,
) {
    DropdownMenuItem(
        text = text,
        onClick = onClick,
        modifier = modifier,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled,
        colors = colors,
        contentPadding = contentPadding,
    )
}
