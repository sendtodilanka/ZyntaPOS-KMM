package com.zyntasolutions.zyntapos.debug.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.debug.model.SeedProfile
import com.zyntasolutions.zyntapos.debug.mvi.DebugIntent
import com.zyntasolutions.zyntapos.debug.mvi.DebugState
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButton
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButtonVariant
import com.zyntasolutions.zyntapos.designsystem.components.ZyntaTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton

/**
 * Seeds tab — run seed profiles, create admin account, clear seed data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeedsTab(
    state: DebugState,
    onIntent: (DebugIntent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {

        // ── Seed profile selector ──────────────────────────────────────────────
        Text("Seed Profile", style = MaterialTheme.typography.titleSmall)

        var expanded by remember { mutableStateOf(false) }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = state.selectedProfile.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Profile") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                SeedProfile.entries.forEach { profile ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(profile.displayName, style = MaterialTheme.typography.bodyMedium)
                                Text(profile.description, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        onClick = {
                            onIntent(DebugIntent.SelectSeedProfile(profile))
                            expanded = false
                        },
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ZyntaButton(
                text = "Run Seed",
                onClick = { onIntent(DebugIntent.RunSeedProfile) },
                isLoading = state.isLoading,
                modifier = Modifier.weight(1f),
            )
            ZyntaButton(
                text = "Clear Seeds",
                onClick = { onIntent(DebugIntent.RequestClearSeedData) },
                variant = ZyntaButtonVariant.Secondary,
                modifier = Modifier.weight(1f),
            )
        }

        // ── Seed result card ───────────────────────────────────────────────────
        state.seedSummary?.let { summary ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Last Seed Result", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    summary.results.forEach { result ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(result.entityType, style = MaterialTheme.typography.bodySmall)
                            Text(
                                "${result.inserted} inserted  ${result.skipped} skipped  ${result.failed} failed",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (result.failed > 0) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Total: ${summary.totalInserted} inserted, ${summary.totalSkipped} skipped, ${summary.totalFailed} failed",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (summary.isSuccess) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        // ── Admin account setup ────────────────────────────────────────────────
        Text("Admin Account", style = MaterialTheme.typography.titleSmall)
        Text(
            "Create or overwrite the ADMIN account. Password is collected at runtime and never stored.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ZyntaButton(
            text = "Setup Admin Account",
            onClick = { onIntent(DebugIntent.ShowAdminSetupDialog) },
            variant = ZyntaButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    // ── Admin setup dialog ─────────────────────────────────────────────────────
    if (state.showAdminSetupDialog) {
        AdminSetupDialog(
            onConfirm = { email, password ->
                onIntent(DebugIntent.SetAdminCredentials(email, password))
            },
            onDismiss = { onIntent(DebugIntent.DismissAdminSetupDialog) },
        )
    }
}

@Composable
private fun AdminSetupDialog(
    onConfirm: (email: String, password: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var email by remember { mutableStateOf("admin@debug.local") }
    var password by remember { mutableStateOf("") }
    val isValid = email.isNotBlank() && password.length >= 8

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Admin Account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Password is passed directly to UserRepository.create() and is never stored.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ZyntaTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = "Admin Email",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                ZyntaTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password (min 8 chars)",
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    error = if (password.isNotEmpty() && password.length < 8) "Min 8 characters" else null,
                )
            }
        },
        confirmButton = {
            ZyntaButton(
                text = "Create",
                onClick = { onConfirm(email, password) },
                enabled = isValid,
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
