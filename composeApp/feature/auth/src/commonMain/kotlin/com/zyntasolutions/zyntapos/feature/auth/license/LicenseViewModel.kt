package com.zyntasolutions.zyntapos.feature.auth.license

import com.zyntasolutions.zyntapos.domain.usecase.license.ActivateLicenseUseCase
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import kotlinx.coroutines.launch

data class LicenseState(
    val licenseKey: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
)

sealed class LicenseIntent {
    data class LicenseKeyChanged(val key: String) : LicenseIntent()
    data object ActivateClicked : LicenseIntent()
    data object DismissError : LicenseIntent()
}

sealed class LicenseEffect {
    data object NavigateToMain : LicenseEffect()
    data class ShowError(val message: String) : LicenseEffect()
}

/**
 * ViewModel for the license activation screen.
 *
 * Handles license key entry and delegates to [ActivateLicenseUseCase].
 * On success emits [LicenseEffect.NavigateToMain].
 *
 * @param activateLicenseUseCase Delegates to [LicenseRepository.activate].
 * @param deviceId               Platform-specific unique device identifier.
 * @param deviceName             Human-readable device name (optional).
 * @param appVersion             Current application version string.
 * @param osVersion              OS version string (optional).
 */
class LicenseViewModel(
    private val activateLicenseUseCase: ActivateLicenseUseCase,
    private val deviceId: String,
    private val deviceName: String?,
    private val appVersion: String,
    private val osVersion: String?,
) : BaseViewModel<LicenseState, LicenseIntent, LicenseEffect>(LicenseState()) {

    override suspend fun handleIntent(intent: LicenseIntent) {
        when (intent) {
            is LicenseIntent.LicenseKeyChanged -> onKeyChanged(intent.key)
            is LicenseIntent.ActivateClicked   -> onActivateClicked()
            is LicenseIntent.DismissError      -> updateState { copy(error = null) }
        }
    }

    private fun onKeyChanged(key: String) {
        updateState { copy(licenseKey = key, error = null) }
    }

    private fun onActivateClicked() {
        val key = currentState.licenseKey.trim()
        if (key.isBlank()) {
            updateState { copy(error = "License key is required") }
            return
        }
        // Basic format check: XXXX-XXXX-XXXX-XXXX
        val segments = key.split("-")
        if (segments.size != 4 || segments.any { it.length != 4 }) {
            updateState { copy(error = "Invalid format. Expected: XXXX-XXXX-XXXX-XXXX") }
            return
        }

        updateState { copy(isLoading = true, error = null) }
        viewModelScope.launch {
            activateLicenseUseCase(
                licenseKey = key,
                deviceId = deviceId,
                deviceName = deviceName,
                appVersion = appVersion,
                osVersion = osVersion,
            ).fold(
                onSuccess = {
                    updateState { copy(isLoading = false) }
                    sendEffect(LicenseEffect.NavigateToMain)
                },
                onFailure = { t ->
                    updateState { copy(isLoading = false, error = t.message ?: "Activation failed") }
                },
            )
        }
    }
}
