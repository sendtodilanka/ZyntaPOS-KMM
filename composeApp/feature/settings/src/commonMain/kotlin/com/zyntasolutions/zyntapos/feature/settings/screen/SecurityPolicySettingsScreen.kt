package com.zyntasolutions.zyntapos.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaPageScaffold
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing

/**
 * Security Policy read-only shell (Phase 3 Sprint 23).
 *
 * Displays the currently-enforced platform security policy as five labelled
 * rows. Values are the compiled-in defaults — making them editable is tracked
 * as Sprint 24 work (persistence to the `settings` table + form validation).
 *
 * Shown under Settings → Administration → Security Policy, distinct from the
 * existing Security screen which owns session auto-lock.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SecurityPolicySettingsScreen(onBack: () -> Unit) {
    val s = LocalStrings.current
    ZyntaPageScaffold(
        title = s[StringResource.SETTINGS_SECURITY_POLICY],
        onNavigateBack = onBack,
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = ZyntaSpacing.md,
                end = ZyntaSpacing.md,
                top = innerPadding.calculateTopPadding() + ZyntaSpacing.md,
                bottom = innerPadding.calculateBottomPadding() + ZyntaSpacing.md,
            ),
            verticalArrangement = Arrangement.spacedBy(ZyntaSpacing.sm),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    PolicyRow(
                        label = s[StringResource.SETTINGS_SESSION_TIMEOUT],
                        value = "15 min",
                        icon = Icons.Default.Timer,
                    )
                    PolicyRow(
                        label = s[StringResource.SETTINGS_PIN_COMPLEXITY],
                        value = "6 digits",
                        icon = Icons.Default.Lock,
                    )
                    PolicyRow(
                        label = s[StringResource.SETTINGS_FAILED_LOGIN_LOCKOUT],
                        value = "5 attempts",
                        icon = Icons.Default.Shield,
                    )
                    PolicyRow(
                        label = s[StringResource.SETTINGS_LOCKOUT_DURATION],
                        value = "15 min",
                        icon = Icons.Default.LockClock,
                    )
                    PolicyRow(
                        label = s[StringResource.SETTINGS_BIOMETRIC_AUTH],
                        value = s[StringResource.COMMON_ON],
                        icon = Icons.Default.Fingerprint,
                    )
                }
            }
            item {
                Text(
                    text = s[StringResource.COMMON_READ_ONLY],
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = ZyntaSpacing.xs, top = ZyntaSpacing.sm),
                )
            }
        }
    }
}

@Composable
private fun PolicyRow(label: String, value: String, icon: ImageVector) {
    ListItem(
        headlineContent = { Text(label, style = MaterialTheme.typography.bodyLarge) },
        trailingContent = {
            Text(
                value,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
