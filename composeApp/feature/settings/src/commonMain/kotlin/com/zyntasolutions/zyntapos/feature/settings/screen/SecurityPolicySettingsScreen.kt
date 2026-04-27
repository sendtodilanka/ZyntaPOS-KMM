package com.zyntasolutions.zyntapos.feature.settings.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import com.zyntasolutions.zyntapos.designsystem.layouts.ZyntaPageScaffold
import com.zyntasolutions.zyntapos.designsystem.tokens.ZyntaSpacing
import com.zyntasolutions.zyntapos.domain.model.SecurityPolicy
import com.zyntasolutions.zyntapos.feature.settings.SecurityPolicyIntent
import com.zyntasolutions.zyntapos.feature.settings.SecurityPolicyState

/**
 * Security Policy settings screen (Sprint 23 task 23.9 — persistence slice 2/3).
 *
 * Renders five rows backed by [SecurityPolicyState]:
 *   1. Session timeout — int dropdown {5, 15, 30, 60} minutes
 *   2. PIN complexity — enum dropdown {4-digit, 6-digit, alphanumeric}
 *   3. Failed-login lockout threshold — int dropdown {3, 5, 10}
 *   4. Lockout duration — int dropdown {5, 15, 30} minutes
 *   5. Biometric — `Switch`
 *
 * Each tap dispatches a `SecurityPolicyIntent.Apply` carrying the next
 * snapshot computed via `state.policy.copy(...)`. The view-model
 * optimistically applies and rewinds on a validation or repo error.
 *
 * @param state    Current loaded policy from
 *                 [com.zyntasolutions.zyntapos.feature.settings.SecurityPolicyViewModel].
 * @param onIntent Pipe back to `viewModel.dispatch`.
 * @param onBack   Back navigation handler.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SecurityPolicySettingsScreen(
    state: SecurityPolicyState,
    onIntent: (SecurityPolicyIntent) -> Unit,
    onBack: () -> Unit,
) {
    val s = LocalStrings.current
    var openDialog by remember { mutableStateOf<DialogKind?>(null) }

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
                        value = "${state.policy.sessionTimeoutMinutes} min",
                        icon = Icons.Default.Timer,
                        enabled = !state.isLoading,
                        onClick = { openDialog = DialogKind.SESSION_TIMEOUT },
                    )
                    PolicyRow(
                        label = s[StringResource.SETTINGS_PIN_COMPLEXITY],
                        value = pinComplexityLabel(state.policy.pinComplexity),
                        icon = Icons.Default.Lock,
                        enabled = !state.isLoading,
                        onClick = { openDialog = DialogKind.PIN_COMPLEXITY },
                    )
                    PolicyRow(
                        label = s[StringResource.SETTINGS_FAILED_LOGIN_LOCKOUT],
                        value = "${state.policy.failedLoginLockoutAttempts} attempts",
                        icon = Icons.Default.Shield,
                        enabled = !state.isLoading,
                        onClick = { openDialog = DialogKind.LOCKOUT_ATTEMPTS },
                    )
                    PolicyRow(
                        label = s[StringResource.SETTINGS_LOCKOUT_DURATION],
                        value = "${state.policy.lockoutDurationMinutes} min",
                        icon = Icons.Default.LockClock,
                        enabled = !state.isLoading,
                        onClick = { openDialog = DialogKind.LOCKOUT_DURATION },
                    )
                    BiometricRow(
                        label = s[StringResource.SETTINGS_BIOMETRIC_AUTH],
                        checked = state.policy.biometricEnabled,
                        enabled = !state.isLoading,
                        onCheckedChange = { biometric ->
                            onIntent(
                                SecurityPolicyIntent.Apply(
                                    state.policy.copy(biometricEnabled = biometric),
                                ),
                            )
                        },
                    )
                }
            }
            state.error?.let { msg ->
                item {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    when (openDialog) {
        DialogKind.SESSION_TIMEOUT -> IntChoiceDialog(
            title = s[StringResource.SETTINGS_SESSION_TIMEOUT],
            options = SecurityPolicy.ALLOWED_SESSION_TIMEOUTS,
            current = state.policy.sessionTimeoutMinutes,
            unitSuffix = " min",
            onSelect = { value ->
                onIntent(SecurityPolicyIntent.Apply(state.policy.copy(sessionTimeoutMinutes = value)))
                openDialog = null
            },
            onDismiss = { openDialog = null },
        )
        DialogKind.LOCKOUT_ATTEMPTS -> IntChoiceDialog(
            title = s[StringResource.SETTINGS_FAILED_LOGIN_LOCKOUT],
            options = SecurityPolicy.ALLOWED_LOCKOUT_ATTEMPTS,
            current = state.policy.failedLoginLockoutAttempts,
            unitSuffix = " attempts",
            onSelect = { value ->
                onIntent(SecurityPolicyIntent.Apply(state.policy.copy(failedLoginLockoutAttempts = value)))
                openDialog = null
            },
            onDismiss = { openDialog = null },
        )
        DialogKind.LOCKOUT_DURATION -> IntChoiceDialog(
            title = s[StringResource.SETTINGS_LOCKOUT_DURATION],
            options = SecurityPolicy.ALLOWED_LOCKOUT_DURATIONS,
            current = state.policy.lockoutDurationMinutes,
            unitSuffix = " min",
            onSelect = { value ->
                onIntent(SecurityPolicyIntent.Apply(state.policy.copy(lockoutDurationMinutes = value)))
                openDialog = null
            },
            onDismiss = { openDialog = null },
        )
        DialogKind.PIN_COMPLEXITY -> PinComplexityDialog(
            title = s[StringResource.SETTINGS_PIN_COMPLEXITY],
            current = state.policy.pinComplexity,
            onSelect = { value ->
                onIntent(SecurityPolicyIntent.Apply(state.policy.copy(pinComplexity = value)))
                openDialog = null
            },
            onDismiss = { openDialog = null },
        )
        null -> Unit
    }
}

private enum class DialogKind { SESSION_TIMEOUT, PIN_COMPLEXITY, LOCKOUT_ATTEMPTS, LOCKOUT_DURATION }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PolicyRow(
    label: String,
    value: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
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
        modifier = Modifier
            .fillMaxWidth()
            .let { if (enabled) it.clickable(onClick = onClick) else it },
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun BiometricRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(label, style = MaterialTheme.typography.bodyLarge) },
        leadingContent = {
            Icon(
                Icons.Default.Fingerprint,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun IntChoiceDialog(
    title: String,
    options: List<Int>,
    current: Int,
    unitSuffix: String,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { value ->
                    ListItem(
                        headlineContent = {
                            Text(
                                "$value$unitSuffix",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (value == current)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s[StringResource.COMMON_CANCEL]) }
        },
    )
}

@Composable
private fun PinComplexityDialog(
    title: String,
    current: SecurityPolicy.PinComplexity,
    onSelect: (SecurityPolicy.PinComplexity) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                SecurityPolicy.PinComplexity.entries.forEach { value ->
                    ListItem(
                        headlineContent = {
                            Text(
                                pinComplexityLabel(value),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (value == current)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s[StringResource.COMMON_CANCEL]) }
        },
    )
}

private fun pinComplexityLabel(complexity: SecurityPolicy.PinComplexity): String = when (complexity) {
    SecurityPolicy.PinComplexity.FOUR_DIGIT -> "4 digits"
    SecurityPolicy.PinComplexity.SIX_DIGIT -> "6 digits"
    SecurityPolicy.PinComplexity.ALPHANUMERIC -> "Alphanumeric"
}
