package com.zyntasolutions.zyntapos.feature.diagnostic

sealed class DiagnosticIntent {
    /** Store operator accepts the pending diagnostic session request. */
    data object AcceptConsent : DiagnosticIntent()
    /** Store operator denies/revokes the diagnostic session request. */
    data object DenyConsent : DiagnosticIntent()
    /** Dismiss any error shown in the UI. */
    data object DismissError : DiagnosticIntent()
}
