package com.zyntasolutions.zyntapos.feature.diagnostic

sealed class DiagnosticEffect {
    /** Session accepted — navigate away and show success confirmation. */
    data object ConsentAccepted : DiagnosticEffect()
    /** Session denied/revoked — navigate away. */
    data object ConsentDenied : DiagnosticEffect()
    /** Non-fatal error that should be surfaced as a snackbar. */
    data class ShowError(val message: String) : DiagnosticEffect()
}
