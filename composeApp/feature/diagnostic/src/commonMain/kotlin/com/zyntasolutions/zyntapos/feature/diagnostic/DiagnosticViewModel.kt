package com.zyntasolutions.zyntapos.feature.diagnostic

import androidx.lifecycle.viewModelScope
import com.zyntasolutions.zyntapos.core.result.onError
import com.zyntasolutions.zyntapos.core.result.onSuccess
import com.zyntasolutions.zyntapos.data.remote.api.ApiService
import com.zyntasolutions.zyntapos.domain.model.DiagnosticSession
import com.zyntasolutions.zyntapos.security.auth.DiagnosticTokenValidator
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * ViewModel for the remote diagnostic consent flow.
 *
 * When a Zynta technician requests remote access, the backend issues a 15-minute
 * JIT token and delivers it to the store device (via push notification or QR code).
 * This VM decodes the token claims, presents the [DiagnosticConsentScreen], and
 * posts the consent decision back to the server via [ApiService].
 */
class DiagnosticViewModel(
    private val tokenValidator: DiagnosticTokenValidator,
    private val apiService: ApiService,
) : BaseViewModel<DiagnosticState, DiagnosticIntent, DiagnosticEffect>(DiagnosticState()) {

    /**
     * Validates and loads a JIT diagnostic session token into the UI state.
     * Call this when the device receives the token (push or QR).
     */
    fun loadToken(rawToken: String) {
        viewModelScope.launch {
            updateState { copy(isLoading = true, errorMessage = null) }
            val result = tokenValidator.validateToken(rawToken)
            result
                .onSuccess { claims ->
                    val session = DiagnosticSession(
                        id               = claims.sessionId,
                        storeId          = claims.storeId,
                        technicianId     = claims.technicianId,
                        requestedBy      = claims.technicianId,
                        consentGrantedAt = null,
                        expiresAt        = claims.exp * 1000L,
                        status           = com.zyntasolutions.zyntapos.domain.model.DiagnosticSessionStatus.PENDING_CONSENT,
                        dataScope        = when (claims.scope) {
                            "FULL_READ_ONLY" -> com.zyntasolutions.zyntapos.domain.model.DiagnosticDataScope.FULL_READ_ONLY
                            else             -> com.zyntasolutions.zyntapos.domain.model.DiagnosticDataScope.READ_ONLY_DIAGNOSTICS
                        },
                    )
                    updateState { copy(pendingSession = session, isLoading = false) }
                }
                .onError { error ->
                    updateState { copy(isLoading = false, errorMessage = error.message) }
                    sendEffect(DiagnosticEffect.ShowError(error.message))
                }
        }
    }

    override suspend fun handleIntent(intent: DiagnosticIntent) {
        when (intent) {
            is DiagnosticIntent.AcceptConsent -> acceptConsent()
            is DiagnosticIntent.DenyConsent   -> denyConsent()
            is DiagnosticIntent.DismissError  -> updateState { copy(errorMessage = null) }
        }
    }

    private suspend fun acceptConsent() {
        val session = state.value.pendingSession ?: return
        updateState { copy(isLoading = true) }
        try {
            apiService.grantDiagnosticConsent(session.id, Clock.System.now().toEpochMilliseconds())
            updateState { copy(isLoading = false, pendingSession = null) }
            sendEffect(DiagnosticEffect.ConsentAccepted)
        } catch (e: Exception) {
            updateState { copy(isLoading = false, errorMessage = e.message) }
            sendEffect(DiagnosticEffect.ShowError(e.message ?: "Failed to grant consent"))
        }
    }

    private suspend fun denyConsent() {
        val session = state.value.pendingSession ?: return
        updateState { copy(isLoading = true) }
        try {
            apiService.revokeDiagnosticConsent(session.id)
            updateState { copy(isLoading = false, pendingSession = null) }
            sendEffect(DiagnosticEffect.ConsentDenied)
        } catch (e: Exception) {
            updateState { copy(isLoading = false, errorMessage = e.message) }
            sendEffect(DiagnosticEffect.ShowError(e.message ?: "Failed to revoke consent"))
        }
    }
}
