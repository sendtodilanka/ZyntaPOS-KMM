package com.zyntasolutions.zyntapos.domain.model

/**
 * Lightweight projection of a [User] for the quick-switch user picker.
 *
 * Only includes the fields needed to render the selection list — avoids
 * exposing sensitive data (password hash, PIN hash) to the UI layer.
 */
data class QuickSwitchCandidate(
    val id: String,
    val name: String,
    val role: Role,
)
