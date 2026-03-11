package com.zyntasolutions.zyntapos.feature.diagnostic

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.domain.model.DiagnosticDataScope
import com.zyntasolutions.zyntapos.domain.model.DiagnosticSession

/**
 * Consent dialog shown to the store operator when a Zynta technician
 * requests remote diagnostic access.
 *
 * Displays session details (technician ID, data scope, expiry) and
 * offers ACCEPT / DENY actions. The decision is dispatched as a
 * [DiagnosticIntent] to the [DiagnosticViewModel].
 */
@Composable
fun DiagnosticConsentScreen(
    state: DiagnosticState,
    onIntent: (DiagnosticIntent) -> Unit,
) {
    val session = state.pendingSession

    if (session == null && !state.isLoading) {
        // No pending session — show placeholder
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No pending diagnostic session request.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state.isLoading) {
            CircularProgressIndicator()
            return@Column
        }

        if (session != null) {
            Text(
                text = "Remote Diagnostic Request",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SessionDetailRow("Session ID", session.id.take(8) + "…")
                    SessionDetailRow("Technician ID", session.technicianId.take(8) + "…")
                    SessionDetailRow(
                        "Access Scope",
                        when (session.dataScope) {
                            DiagnosticDataScope.READ_ONLY_DIAGNOSTICS -> "Read-only diagnostics"
                            DiagnosticDataScope.FULL_READ_ONLY        -> "Full read-only access"
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "A Zynta technician is requesting temporary read-only access to diagnose an issue. " +
                       "This session expires in 15 minutes and can be revoked at any time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { onIntent(DiagnosticIntent.DenyConsent) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Deny")
                }
                Button(
                    onClick = { onIntent(DiagnosticIntent.AcceptConsent) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Accept")
                }
            }
        }

        state.errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun SessionDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}
