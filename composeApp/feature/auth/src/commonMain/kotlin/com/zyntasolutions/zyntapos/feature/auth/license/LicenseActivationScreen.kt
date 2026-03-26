package com.zyntasolutions.zyntapos.feature.auth.license

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import org.koin.compose.viewmodel.koinViewModel

/**
 * License activation screen.
 *
 * Displayed when the device has not yet been activated (UNACTIVATED status).
 * The user enters their license key in XXXX-XXXX-XXXX-XXXX format and taps Activate.
 *
 * @param onActivated Called when activation succeeds; triggers navigation to the main graph.
 */
@Composable
fun LicenseActivationScreen(
    onActivated: () -> Unit,
    viewModel: LicenseViewModel = koinViewModel(),
) {
    val s = LocalStrings.current
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is LicenseEffect.NavigateToMain -> onActivated()
                is LicenseEffect.ShowError      -> { /* handled inline via state.error */ }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.widthIn(max = 400.dp),
        ) {
            Text(
                text = s[StringResource.AUTH_ACTIVATE_TITLE],
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = s[StringResource.AUTH_ACTIVATE_SUBTITLE],
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.licenseKey,
                onValueChange = { viewModel.dispatch(LicenseIntent.LicenseKeyChanged(it.uppercase())) },
                label = { Text(s[StringResource.AUTH_LICENSE_KEY_LABEL]) },
                placeholder = { Text("XXXX-XXXX-XXXX-XXXX") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                ),
                isError = state.error != null,
                supportingText = state.error?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { viewModel.dispatch(LicenseIntent.ActivateClicked) },
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(s[StringResource.AUTH_ACTIVATE_BUTTON])
                }
            }
            Text(
                text = s[StringResource.AUTH_LICENSE_SUPPORT_HINT],
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
