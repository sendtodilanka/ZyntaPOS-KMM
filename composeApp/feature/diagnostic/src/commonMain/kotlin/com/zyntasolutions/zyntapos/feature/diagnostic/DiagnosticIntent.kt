package com.zyntasolutions.zyntapos.feature.diagnostic

sealed class DiagnosticIntent {
    /** Validate and load a JIT diagnostic token received via push or QR. */
    data class LoadToken(val rawToken: String) : DiagnosticIntent()
    /** Store operator accepts the pending diagnostic session request. */
    data object AcceptConsent : DiagnosticIntent()
    /** Store operator denies/revokes the diagnostic session request. */
    data object DenyConsent : DiagnosticIntent()
    /** Dismiss any error shown in the UI. */
    data object DismissError : DiagnosticIntent()
}
