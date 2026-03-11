package com.zyntasolutions.zyntapos.feature.diagnostic

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
 * posts the consent decision back to the server.
 *
 * The VM is intentionally lightweight — actual network calls are stub placeholders
 * because the ApiService interface does not yet have diagnostic endpoints (added
 * as backend-only in this sprint; KMM API client extension is Phase 2 work).
 */
class DiagnosticViewModel(
    private val tokenValidator: DiagnosticTokenValidator,
) : BaseViewModel<DiagnosticState, DiagnosticIntent, DiagnosticEffect>(DiagnosticState()) {

    /**
     * Validates and loads a JIT diagnostic session token into the UI state.
     * Call this when the device receives the token (push or QR).
     */
    fun loadToken(rawToken: String) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true, errorMessage = null) }
            val result = tokenValidator.validateToken(rawToken)
            result.fold(
                onSuccess = { claims ->
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
                    updateState { it.copy(pendingSession = session, isLoading = false) }
                },
                onFailure = { error ->
                    updateState { it.copy(isLoading = false, errorMessage = error.message) }
                    sendEffect(DiagnosticEffect.ShowError(error.message ?: "Invalid diagnostic token"))
                },
            )
        }
    }

    override suspend fun handleIntent(intent: DiagnosticIntent) {
        when (intent) {
            is DiagnosticIntent.AcceptConsent -> acceptConsent()
            is DiagnosticIntent.DenyConsent   -> denyConsent()
            is DiagnosticIntent.DismissError  -> updateState { it.copy(errorMessage = null) }
        }
    }

    private suspend fun acceptConsent() {
        val session = state.value.pendingSession ?: return
        updateState { it.copy(isLoading = true) }
        // Phase 2: call ApiService.grantDiagnosticConsent(session.id, Clock.System.now().toEpochMilliseconds())
        updateState { it.copy(isLoading = false, pendingSession = null) }
        sendEffect(DiagnosticEffect.ConsentAccepted)
    }

    private suspend fun denyConsent() {
        val session = state.value.pendingSession ?: return
        updateState { it.copy(isLoading = true) }
        // Phase 2: call ApiService.revokeDiagnosticConsent(session.id)
        updateState { it.copy(isLoading = false, pendingSession = null) }
        sendEffect(DiagnosticEffect.ConsentDenied)
    }
}
