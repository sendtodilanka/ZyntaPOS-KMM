package com.zyntasolutions.zyntapos.feature.settings.edition

import androidx.lifecycle.viewModelScope
import com.zyntasolutions.zyntapos.core.result.onError
import com.zyntasolutions.zyntapos.core.result.onSuccess
import com.zyntasolutions.zyntapos.domain.model.FeatureConfig
import com.zyntasolutions.zyntapos.domain.model.ZyntaFeature
import com.zyntasolutions.zyntapos.domain.usecase.feature.GetAllFeatureConfigsUseCase
import com.zyntasolutions.zyntapos.domain.usecase.feature.SetFeatureEnabledUseCase
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlin.time.Clock

// ─────────────────────────────────────────────────────────────────────────────
// EditionManagementViewModel — MVI ViewModel for the Edition Management screen.
//
// Loads all 23 FeatureConfig rows reactively and exposes toggle intent to
// enable or disable PREMIUM/ENTERPRISE features. STANDARD features are
// protected by SetFeatureEnabledUseCase's business rule.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Immutable state for the Edition Management screen.
 *
 * @param featureConfigs Live list of all 23 [FeatureConfig] rows, re-emitted on any change.
 * @param isLoading      True while the initial load is in progress.
 * @param errorMessage   Non-null when a load or toggle operation fails.
 */
data class EditionManagementState(
    val featureConfigs: List<FeatureConfig> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

/**
 * All user actions on the Edition Management screen.
 */
sealed class EditionManagementIntent {
    /** Trigger (or re-trigger) the reactive feature-config stream. */
    data object Load : EditionManagementIntent()

    /**
     * Toggle [feature] to [enabled].
     * Ignored for STANDARD features (use case returns [Result.Error]).
     */
    data class ToggleFeature(
        val feature: ZyntaFeature,
        val enabled: Boolean,
    ) : EditionManagementIntent()
}

/**
 * One-shot effects emitted by [EditionManagementViewModel].
 */
sealed class EditionManagementEffect {
    /** Display a transient error (snackbar / toast). */
    data class ShowError(val message: String) : EditionManagementEffect()

    /** Display a transient success confirmation. */
    data class ShowSuccess(val message: String) : EditionManagementEffect()
}

/**
 * MVI ViewModel for the Edition Management settings screen.
 *
 * Provides a reactive view over all feature-flag rows via [GetAllFeatureConfigsUseCase]
 * and delegates toggle operations to [SetFeatureEnabledUseCase], which enforces the
 * business rule that STANDARD features can never be disabled.
 *
 * @param getAllFeatureConfigs Reactive use case returning [Flow<List<FeatureConfig>>].
 * @param setFeatureEnabled   Transactional toggle use case with STANDARD-feature guard.
 */
class EditionManagementViewModel(
    private val getAllFeatureConfigs: GetAllFeatureConfigsUseCase,
    private val setFeatureEnabled: SetFeatureEnabledUseCase,
) : BaseViewModel<EditionManagementState, EditionManagementIntent, EditionManagementEffect>(
    EditionManagementState(),
) {

    private var loadJob: Job? = null

    init {
        dispatch(EditionManagementIntent.Load)
    }

    override suspend fun handleIntent(intent: EditionManagementIntent) {
        when (intent) {
            EditionManagementIntent.Load            -> load()
            is EditionManagementIntent.ToggleFeature -> toggle(intent.feature, intent.enabled)
        }
    }

    // ── Load ─────────────────────────────────────────────────────────────────

    private fun load() {
        loadJob?.cancel()
        updateState { copy(isLoading = true, errorMessage = null) }
        loadJob = viewModelScope.launch {
            getAllFeatureConfigs()
                .catch { e ->
                    updateState { copy(isLoading = false, errorMessage = e.message) }
                }
                .collect { configs ->
                    updateState { copy(featureConfigs = configs, isLoading = false) }
                }
        }
    }

    // ── Toggle ───────────────────────────────────────────────────────────────

    private suspend fun toggle(feature: ZyntaFeature, enabled: Boolean) {
        val now = Clock.System.now().toEpochMilliseconds()
        setFeatureEnabled(feature, enabled, now, null)
            .onSuccess {
                val verb = if (enabled) "enabled" else "disabled"
                sendEffect(EditionManagementEffect.ShowSuccess("${feature.name} $verb"))
            }
            .onError { exception ->
                sendEffect(EditionManagementEffect.ShowError(exception.message ?: "Failed to update feature"))
            }
    }
}
