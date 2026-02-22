package com.zyntasolutions.zyntapos.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaTopAppBar
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing

// ─────────────────────────────────────────────────────────────────────────────
// SettingsHomeScreen — grouped card layout with all settings categories.
// Sprint 23 — Step 13.1.1
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Top-level settings screen displaying grouped categories.
 *
 * Each category is a clickable [ListItem] inside a [Card].
 * Navigation is handled by the caller via [onNavigate].
 *
 * @param onNavigate  Lambda invoked with the [SettingsRoute] the user selected.
 * @param onBack      Lambda invoked when the user presses the system back button.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsHomeScreen(
    onNavigate: (SettingsRoute) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            ZyntaTopAppBar(
                title = "Settings",
                onNavigateBack = onBack,
            )
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = ZyntaSpacing.md,
                end = ZyntaSpacing.md,
                top = innerPadding.calculateTopPadding() + ZyntaSpacing.md,
                bottom = innerPadding.calculateBottomPadding() + ZyntaSpacing.md,
            ),
            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.md),
        ) {
            settingsGroups.forEach { group ->
                item {
                    SettingsCategoryCard(
                        group = group,
                        onItemClick = { onNavigate(it.route) },
                    )
                }
            }
        }
    }
}

// ─── Data model ───────────────────────────────────────────────────────────────

/** Represents a single navigable settings entry. */
data class SettingsEntry(
    val label: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: SettingsRoute,
)

/** A named group of [SettingsEntry] items displayed in one card. */
data class SettingsGroup(
    val title: String,
    val entries: List<SettingsEntry>,
)

/** All routes the settings home screen can navigate to. */
enum class SettingsRoute {
    GENERAL, POS, TAX, PRINTER, USERS, SECURITY, BACKUP, APPEARANCE, ABOUT,
}

private val settingsGroups: List<SettingsGroup> = listOf(
    SettingsGroup(
        title = "Store",
        entries = listOf(
            SettingsEntry("General", "Store name, address, logo & currency", Icons.Filled.Store, SettingsRoute.GENERAL),
        ),
    ),
    SettingsGroup(
        title = "Point of Sale",
        entries = listOf(
            SettingsEntry("POS", "Order type, receipts & discounts", Icons.Filled.Receipt, SettingsRoute.POS),
            SettingsEntry("Tax", "Tax groups & rates", Icons.Filled.Payment, SettingsRoute.TAX),
            SettingsEntry("Printer", "Printer type, connection & receipt format", Icons.Filled.Print, SettingsRoute.PRINTER),
        ),
    ),
    SettingsGroup(
        title = "Administration",
        entries = listOf(
            SettingsEntry("Users", "Create and manage staff accounts", Icons.Filled.PersonOutline, SettingsRoute.USERS),
            SettingsEntry("Backup", "Manual backup & restore", Icons.Filled.Backup, SettingsRoute.BACKUP),
        ),
    ),
    SettingsGroup(
        title = "Customisation",
        entries = listOf(
            SettingsEntry("Appearance", "Light, dark or system theme", Icons.Filled.ColorLens, SettingsRoute.APPEARANCE),
        ),
    ),
    SettingsGroup(
        title = "Support",
        entries = listOf(
            SettingsEntry("About", "Version, build info & licences", Icons.Filled.Info, SettingsRoute.ABOUT),
        ),
    ),
)

// ─── Sub-composables ──────────────────────────────────────────────────────────

@Composable
private fun SettingsCategoryCard(
    group: SettingsGroup,
    onItemClick: (SettingsEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = group.title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = ZyntaSpacing.xs, bottom = ZyntaSpacing.xs),
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth(),
        ) {
            group.entries.forEachIndexed { index, entry ->
                ListItem(
                    headlineContent = { Text(entry.label, style = MaterialTheme.typography.bodyLarge) },
                    supportingContent = { Text(entry.subtitle, style = MaterialTheme.typography.bodySmall) },
                    leadingContent = {
                        Icon(
                            imageVector = entry.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = "Navigate",
                            modifier = Modifier.padding(4.dp),
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            Modifier.let { m ->
                                if (index < group.entries.lastIndex)
                                    m // divider added below
                                else m
                            }
                        )
                        .also { },
                )
                if (index < group.entries.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = ZyntaSpacing.md))
                }
            }
        }
    }
}
