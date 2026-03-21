package com.zyntasolutions.zyntapos.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing

// ─────────────────────────────────────────────────────────────────────────────
// ZyntaStoreSelector — Active store picker for multi-store environments.
//
// Displays the current store name with a dropdown to switch stores.
// Designed for use in navigation drawer footer, top app bar, or settings.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Minimal store item representation for the selector UI.
 *
 * @param id     Unique store identifier.
 * @param name   Human-readable store name (e.g., "Main Branch", "Outlet 2").
 * @param address Optional short address or location hint.
 */
data class StoreItem(
    val id: String,
    val name: String,
    val address: String? = null,
)

/**
 * A compact store selector that shows the current active store and
 * opens a dropdown to switch between available stores.
 *
 * @param currentStore     The currently active [StoreItem], or null if no store is selected.
 * @param availableStores  List of stores the user has access to.
 * @param onStoreSelected  Called when the user picks a different store.
 * @param modifier         Optional [Modifier].
 * @param enabled          Whether the selector is interactive (false when only one store).
 */
@Composable
fun ZyntaStoreSelector(
    currentStore: StoreItem?,
    availableStores: List<StoreItem>,
    onStoreSelected: (StoreItem) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = availableStores.size > 1,
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .clickable(enabled = enabled) { expanded = true },
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ZyntaSpacing.md, vertical = ZyntaSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Store,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(ZyntaSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentStore?.name ?: "No Store Selected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (currentStore?.address != null) {
                    Text(
                        text = currentStore.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (enabled) {
                Spacer(Modifier.width(ZyntaSpacing.xs))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Switch store",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }

    ZyntaDropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        availableStores.forEach { store ->
            val isSelected = store.id == currentStore?.id
            ZyntaDropdownMenuItem(
                text = {
                    Column {
                        Text(
                            text = store.name,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (store.address != null) {
                            Text(
                                text = store.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                onClick = {
                    expanded = false
                    onStoreSelected(store)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Store,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                },
                trailingIcon = if (isSelected) {
                    {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                } else null,
            )
        }
    }
}

/**
 * A minimal inline store selector suitable for top app bar or toolbar placement.
 *
 * Shows only the store name with a dropdown arrow. No icon or address.
 *
 * @param currentStoreName Name of the active store.
 * @param availableStores  Selectable stores.
 * @param onStoreSelected  Callback on selection.
 * @param modifier         Optional [Modifier].
 */
@Composable
fun ZyntaStoreSelectorCompact(
    currentStoreName: String,
    availableStores: List<StoreItem>,
    onStoreSelected: (StoreItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .clickable(enabled = availableStores.size > 1) { expanded = true }
            .padding(horizontal = ZyntaSpacing.sm, vertical = ZyntaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = currentStoreName,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (availableStores.size > 1) {
            Spacer(Modifier.width(ZyntaSpacing.xs))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Switch store",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }

    ZyntaDropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        availableStores.forEach { store ->
            ZyntaDropdownMenuItem(
                text = { Text(store.name) },
                onClick = {
                    expanded = false
                    onStoreSelected(store)
                },
            )
        }
    }
}
