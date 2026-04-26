package com.zyntasolutions.zyntapos.feature.settings.screen

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.HistoryToggleOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.SupervisedUserCircle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.ExperimentalMaterial3Api
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaPageScaffold
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
 * @param isDebug     When `true`, adds a "Developer Tools" group with the Debug Console entry.
 *   Should reflect `AppInfoProvider.isDebug` at the call site.
 * @param onNavigate  Lambda invoked with the [SettingsRoute] the user selected.
 * @param onBack      Lambda invoked when the user presses the system back button.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsHomeScreen(
    isDebug: Boolean = false,
    onNavigate: (SettingsRoute) -> Unit,
    onBack: () -> Unit,
) {
    val s = LocalStrings.current
    val groups = if (isDebug) rememberSettingsGroups() + rememberDebugGroup() else rememberSettingsGroups()

    ZyntaPageScaffold(
        title = s[StringResource.SETTINGS_TITLE],
        onNavigateBack = onBack,
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
            groups.forEach { group ->
                item(key = group.title) {
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
    GENERAL, POS, TAX, PRINTER, USERS, SECURITY, RBAC_MANAGEMENT, BACKUP, APPEARANCE, SYSTEM_HEALTH, ABOUT,
    LABEL_PRINTER, SCANNER_SETTINGS, PRINTER_PROFILES, EDITION_MANAGEMENT,
    /** Multi-store user access management — grant/revoke staff access to stores (C3.2). */
    STORE_USER_ACCESS,
    /** Advanced security policy (session timeout, PIN, lockout, biometric) — Sprint 23. */
    SECURITY_POLICY,
    /** Audit log / sync queue / report data retention windows — Sprint 23. */
    DATA_RETENTION,
    /** Audit log category toggles — Sprint 23. */
    AUDIT_POLICY,
    /** Read-only system + custom role catalog (Sprint 23 task 23.4). */
    ROLE_LIST,
    /** Debug Console — only presented when [SettingsHomeScreen] receives `isDebug = true`. */
    DEBUG_CONSOLE,
}

@Composable
private fun rememberSettingsGroups(): List<SettingsGroup> {
    val s = LocalStrings.current
    return remember(s) {
        listOf(
            SettingsGroup(
                title = s[StringResource.SETTINGS_SECTION_STORE],
                entries = listOf(
                    SettingsEntry(s[StringResource.SETTINGS_GENERAL], s[StringResource.SETTINGS_GENERAL_SUBTITLE], Icons.Filled.Store, SettingsRoute.GENERAL),
                ),
            ),
            SettingsGroup(
                title = s[StringResource.SETTINGS_SECTION_POS],
                entries = listOf(
                    SettingsEntry(s[StringResource.SETTINGS_POS], s[StringResource.SETTINGS_POS_SUBTITLE], Icons.Filled.Receipt, SettingsRoute.POS),
                    SettingsEntry(s[StringResource.SETTINGS_TAX], s[StringResource.SETTINGS_TAX_SUBTITLE], Icons.Filled.Payment, SettingsRoute.TAX),
                    SettingsEntry(s[StringResource.SETTINGS_PRINTER], s[StringResource.SETTINGS_PRINTER_SUBTITLE], Icons.Filled.Print, SettingsRoute.PRINTER),
                    SettingsEntry(s[StringResource.SETTINGS_LABEL_PRINTER], s[StringResource.SETTINGS_LABEL_PRINTER_SUBTITLE], Icons.Filled.Label, SettingsRoute.LABEL_PRINTER),
                    SettingsEntry(s[StringResource.SETTINGS_PRINTER_PROFILES], s[StringResource.SETTINGS_PRINTER_PROFILES_SUBTITLE], Icons.Filled.Tune, SettingsRoute.PRINTER_PROFILES),
                    SettingsEntry(s[StringResource.SETTINGS_SCANNER_TITLE], s[StringResource.SETTINGS_SCANNER_SUBTITLE], Icons.Filled.QrCodeScanner, SettingsRoute.SCANNER_SETTINGS),
                ),
            ),
            SettingsGroup(
                title = s[StringResource.SETTINGS_SECTION_ADMINISTRATION],
                entries = listOf(
                    SettingsEntry(s[StringResource.SETTINGS_USER_MANAGEMENT], s[StringResource.SETTINGS_USER_MANAGEMENT_SUBTITLE], Icons.Filled.PersonOutline, SettingsRoute.USERS),
                    SettingsEntry(s[StringResource.SETTINGS_STORE_USER_ACCESS], s[StringResource.SETTINGS_STORE_USER_ACCESS_SUBTITLE], Icons.Filled.SupervisedUserCircle, SettingsRoute.STORE_USER_ACCESS),
                    SettingsEntry(s[StringResource.SETTINGS_SECURITY], s[StringResource.SETTINGS_SECURITY_SUBTITLE], Icons.Filled.Security, SettingsRoute.SECURITY),
                    SettingsEntry(s[StringResource.SETTINGS_SECURITY_POLICY], s[StringResource.SETTINGS_SECURITY_POLICY_SUBTITLE], Icons.Filled.Shield, SettingsRoute.SECURITY_POLICY),
                    SettingsEntry(s[StringResource.SETTINGS_DATA_RETENTION], s[StringResource.SETTINGS_DATA_RETENTION_SUBTITLE], Icons.Filled.HistoryToggleOff, SettingsRoute.DATA_RETENTION),
                    SettingsEntry(s[StringResource.SETTINGS_AUDIT_POLICY], s[StringResource.SETTINGS_AUDIT_POLICY_SUBTITLE], Icons.Filled.FactCheck, SettingsRoute.AUDIT_POLICY),
                    SettingsEntry(s[StringResource.SETTINGS_ROLES_PERMISSIONS], s[StringResource.SETTINGS_ROLES_PERMISSIONS_SUBTITLE], Icons.Filled.AdminPanelSettings, SettingsRoute.RBAC_MANAGEMENT),
                    SettingsEntry(s[StringResource.RBAC_SYSTEM_ROLES], s[StringResource.COMMON_READ_ONLY], Icons.Filled.Verified, SettingsRoute.ROLE_LIST),
                    SettingsEntry(s[StringResource.SETTINGS_EDITION_MANAGEMENT], s[StringResource.SETTINGS_EDITION_MANAGEMENT_SUBTITLE], Icons.Filled.WorkspacePremium, SettingsRoute.EDITION_MANAGEMENT),
                    SettingsEntry(s[StringResource.SETTINGS_BACKUP], s[StringResource.SETTINGS_BACKUP_SUBTITLE], Icons.Filled.Backup, SettingsRoute.BACKUP),
                ),
            ),
            SettingsGroup(
                title = s[StringResource.SETTINGS_SECTION_CUSTOMISATION],
                entries = listOf(
                    SettingsEntry(s[StringResource.SETTINGS_APPEARANCE], s[StringResource.SETTINGS_APPEARANCE_SUBTITLE], Icons.Filled.ColorLens, SettingsRoute.APPEARANCE),
                ),
            ),
            SettingsGroup(
                title = s[StringResource.SETTINGS_SECTION_SUPPORT],
                entries = listOf(
                    SettingsEntry(s[StringResource.SETTINGS_SYSTEM_HEALTH], s[StringResource.SETTINGS_SYSTEM_HEALTH_SUBTITLE], Icons.Filled.HealthAndSafety, SettingsRoute.SYSTEM_HEALTH),
                    SettingsEntry(s[StringResource.SETTINGS_ABOUT], s[StringResource.SETTINGS_ABOUT_SUBTITLE], Icons.Filled.Info, SettingsRoute.ABOUT),
                ),
            ),
        )
    }
}

/**
 * Debug-only settings group appended when [SettingsHomeScreen] receives `isDebug = true`.
 * Never shown in production builds.
 */
@Composable
private fun rememberDebugGroup(): SettingsGroup {
    val s = LocalStrings.current
    return remember(s) {
        SettingsGroup(
            title = s[StringResource.SETTINGS_SECTION_DEV_TOOLS],
            entries = listOf(
                SettingsEntry(
                    label = s[StringResource.SETTINGS_DEBUG_CONSOLE],
                    subtitle = s[StringResource.SETTINGS_DEBUG_CONSOLE_SUBTITLE],
                    icon = Icons.Filled.BugReport,
                    route = SettingsRoute.DEBUG_CONSOLE,
                ),
            ),
        )
    }
}

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
                            contentDescription = null,
                            modifier = Modifier.padding(ZyntaSpacing.xs),
                        )
                    },
                    modifier = Modifier.fillMaxWidth().clickable { onItemClick(entry) },
                )
                if (index < group.entries.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = ZyntaSpacing.md))
                }
            }
        }
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@androidx.compose.ui.tooling.preview.Preview
@androidx.compose.runtime.Composable
private fun SettingsHomeScreenPreview() {
    com.zyntasolutions.zyntapos.designsystem.theme.ZyntaTheme {
        SettingsHomeScreen(
            isDebug = false,
            onNavigate = {},
            onBack = {},
        )
    }
}
