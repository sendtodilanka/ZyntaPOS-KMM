package com.zyntasolutions.zyntapos.feature.settings.edition

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zyntasolutions.zyntapos.domain.model.FeatureConfig
import com.zyntasolutions.zyntapos.domain.model.ZyntaEdition
import com.zyntasolutions.zyntapos.domain.model.ZyntaFeature
import com.zyntasolutions.zyntapos.core.i18n.StringResource
import com.zyntasolutions.zyntapos.designsystem.components.LocalStrings
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.viewmodel.koinViewModel

// ─────────────────────────────────────────────────────────────────────────────
// EditionManagementScreen — Feature-flag toggle UI grouped by edition tier.
//
// Displays all 23 ZyntaFeature rows in three sections (STANDARD, PREMIUM,
// ENTERPRISE). STANDARD features show a disabled switch (always on). PREMIUM
// and ENTERPRISE features are toggled via EditionManagementIntent.ToggleFeature.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Settings screen for managing edition-gated feature flags.
 *
 * Features are grouped into three sections — Standard, Premium, and Enterprise —
 * matching [ZyntaEdition] tiers. STANDARD features always display as enabled and
 * cannot be toggled (enforced in [SetFeatureEnabledUseCase]).
 *
 * @param onNavigateBack Navigation callback for the back action.
 * @param viewModel      Provided by Koin via [koinViewModel]; not normally overridden.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditionManagementScreen(
    onNavigateBack: () -> Unit,
    viewModel: EditionManagementViewModel = koinViewModel(),
) {
    val s = LocalStrings.current
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // ── Collect one-shot effects ──────────────────────────────────────────────
    LaunchedEffect(viewModel.effects) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is EditionManagementEffect.ShowError   -> snackbarHostState.showSnackbar(effect.message)
                is EditionManagementEffect.ShowSuccess -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    // ── Group by edition ──────────────────────────────────────────────────────
    val standardFeatures   = state.featureConfigs.filter { it.feature.edition == ZyntaEdition.STANDARD }
    val premiumFeatures    = state.featureConfigs.filter { it.feature.edition == ZyntaEdition.PREMIUM }
    val enterpriseFeatures = state.featureConfigs.filter { it.feature.edition == ZyntaEdition.ENTERPRISE }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s[StringResource.SETTINGS_EDITION_MANAGEMENT]) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = s[StringResource.COMMON_BACK],
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding(),
                ),
            ) {
                // ── Standard ─────────────────────────────────────────────────
                item { EditionSectionHeader(title = s[StringResource.SETTINGS_EDITION_STANDARD]) }
                items(standardFeatures, key = { it.feature.name }) { config ->
                    // STANDARD features have their switch disabled (always enabled)
                    FeatureToggleRow(config = config, onToggle = { _, _ -> })
                }

                // ── Premium ──────────────────────────────────────────────────
                item { EditionSectionHeader(title = s[StringResource.SETTINGS_EDITION_PREMIUM]) }
                items(premiumFeatures, key = { it.feature.name }) { config ->
                    FeatureToggleRow(config = config) { feature, enabled ->
                        viewModel.dispatch(EditionManagementIntent.ToggleFeature(feature, enabled))
                    }
                }

                // ── Enterprise ───────────────────────────────────────────────
                item { EditionSectionHeader(title = s[StringResource.SETTINGS_EDITION_ENTERPRISE]) }
                items(enterpriseFeatures, key = { it.feature.name }) { config ->
                    FeatureToggleRow(config = config) { feature, enabled ->
                        viewModel.dispatch(EditionManagementIntent.ToggleFeature(feature, enabled))
                    }
                }
            }
        }
    }
}

// ── Section header ────────────────────────────────────────────────────────────

/**
 * Bold section label displayed above each edition group in the [LazyColumn].
 */
@Composable
private fun EditionSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

// ── Feature row ───────────────────────────────────────────────────────────────

/**
 * A single [ListItem] row representing a [FeatureConfig].
 *
 * Shows the human-readable feature name in the headline and the edition tier
 * as supporting text. The trailing [Switch] is disabled for STANDARD features.
 *
 * @param config   The feature config to display.
 * @param onToggle Callback invoked when the switch is changed.
 */
@Composable
private fun FeatureToggleRow(
    config: FeatureConfig,
    onToggle: (ZyntaFeature, Boolean) -> Unit,
) {
    val isStandard = config.feature.edition == ZyntaEdition.STANDARD
    val displayName = config.feature.name
        .replace("_", " ")
        .lowercase()
        .replaceFirstChar { it.titlecase() }

    ListItem(
        headlineContent = {
            Text(displayName)
        },
        supportingContent = {
            Text(config.feature.edition.name)
        },
        trailingContent = {
            Switch(
                checked = config.isEnabled,
                onCheckedChange = { enabled -> onToggle(config.feature, enabled) },
                // STANDARD features are always enabled and cannot be toggled via the UI.
                enabled = !isStandard,
            )
        },
    )
}
