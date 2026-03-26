package com.zyntasolutions.zyntapos.feature.diagnostic

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
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
    val s = LocalStrings.current
    val session = state.pendingSession

    if (session == null && !state.isLoading) {
        // No pending session — show placeholder
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = s[StringResource.DIAGNOSTIC_NO_PENDING],
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
                text = s[StringResource.DIAGNOSTIC_REQUEST_TITLE],
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
                    SessionDetailRow(s[StringResource.DIAGNOSTIC_SESSION_ID], session.id.take(8) + "\u2026")
                    SessionDetailRow(s[StringResource.DIAGNOSTIC_TECHNICIAN_ID], session.technicianId.take(8) + "\u2026")
                    SessionDetailRow(
                        s[StringResource.DIAGNOSTIC_ACCESS_SCOPE],
                        when (session.dataScope) {
                            DiagnosticDataScope.READ_ONLY_DIAGNOSTICS -> s[StringResource.DIAGNOSTIC_SCOPE_READ_ONLY]
                            DiagnosticDataScope.FULL_READ_ONLY        -> s[StringResource.DIAGNOSTIC_SCOPE_FULL_READ_ONLY]
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = s[StringResource.DIAGNOSTIC_CONSENT_TEXT],
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
                    Text(s[StringResource.DIAGNOSTIC_DENY])
                }
                Button(
                    onClick = { onIntent(DiagnosticIntent.AcceptConsent) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(s[StringResource.DIAGNOSTIC_ACCEPT])
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
