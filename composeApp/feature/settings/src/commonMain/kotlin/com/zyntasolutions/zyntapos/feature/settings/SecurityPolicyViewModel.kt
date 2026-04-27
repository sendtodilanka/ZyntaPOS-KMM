package com.zyntasolutions.zyntapos.feature.settings

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.SecurityPolicy
import com.zyntasolutions.zyntapos.domain.usecase.settings.GetSecurityPolicyUseCase
import com.zyntasolutions.zyntapos.domain.usecase.settings.SaveSecurityPolicyUseCase
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel

/**
 * MVI state for the security-policy screen.
 *
 * @property policy    Current loaded policy. Defaults to the canonical
 *                     [SecurityPolicy]() snapshot until the on-disk values
 *                     resolve.
 * @property isLoading `true` while [SecurityPolicyIntent.Load] is in flight.
 * @property error     Latest user-facing error from a save attempt; cleared
 *                     on the next successful action.
 */
data class SecurityPolicyState(
    val policy: SecurityPolicy = SecurityPolicy(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

/** Intents accepted by [SecurityPolicyViewModel]. */
sealed interface SecurityPolicyIntent {
    /** Pull the current persisted policy from the settings store. */
    data object Load : SecurityPolicyIntent

    /**
     * Apply a new [SecurityPolicy] snapshot. The UI is expected to compute
     * the next snapshot via `state.policy.copy(...)` and dispatch it as a
     * single intent — the VM persists optimistically and rewinds on a
     * validation or repo error.
     */
    data class Apply(val policy: SecurityPolicy) : SecurityPolicyIntent
}

/**
 * ViewModel for `SecurityPolicySettingsScreen` (Sprint 23 task 23.9 — slice 2/3).
 *
 * Same optimistic-then-revert pattern as [AuditPolicyViewModel]: every
 * `Apply` updates state immediately and triggers a save; failures rewind
 * the state to the last-known-good snapshot and surface the error message
 * for an inline banner.
 */
class SecurityPolicyViewModel(
    private val getSecurityPolicyUseCase: GetSecurityPolicyUseCase,
    private val saveSecurityPolicyUseCase: SaveSecurityPolicyUseCase,
) : BaseViewModel<SecurityPolicyState, SecurityPolicyIntent, Nothing>(SecurityPolicyState()) {

    override suspend fun handleIntent(intent: SecurityPolicyIntent) {
        when (intent) {
            SecurityPolicyIntent.Load -> {
                updateState { copy(isLoading = true, error = null) }
                val policy = getSecurityPolicyUseCase()
                updateState { copy(policy = policy, isLoading = false) }
            }

            is SecurityPolicyIntent.Apply -> {
                val original = state.value.policy
                updateState { copy(policy = intent.policy, error = null) }
                when (val outcome = saveSecurityPolicyUseCase(intent.policy)) {
                    is Result.Error -> updateState {
                        copy(
                            policy = original,
                            error = outcome.exception.message ?: "Could not save security policy.",
                        )
                    }
                    is Result.Success -> { /* optimistic apply stuck */ }
                    Result.Loading -> { /* never returned */ }
                }
            }
        }
    }
}
