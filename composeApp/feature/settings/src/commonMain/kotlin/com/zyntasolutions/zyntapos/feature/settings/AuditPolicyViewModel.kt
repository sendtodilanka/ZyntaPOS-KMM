package com.zyntasolutions.zyntapos.feature.settings

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.AuditPolicy
import com.zyntasolutions.zyntapos.domain.usecase.audit.GetAuditPolicyUseCase
import com.zyntasolutions.zyntapos.domain.usecase.audit.SetAuditPolicyEnabledUseCase
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel

/**
 * MVI state for the audit-policy screen.
 *
 * @property policy    Current toggle map. Defaults to all-enabled until
 *                     [AuditPolicyIntent.Load] resolves the on-disk values.
 * @property isLoading `true` while the initial load is in flight.
 * @property error     Latest user-facing error from a toggle write (e.g.
 *                     persistence failure or attempting to disable
 *                     `ROLE_CHANGES`). Cleared on the next successful action.
 */
data class AuditPolicyState(
    val policy: AuditPolicy = AuditPolicy(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

/** Intents accepted by [AuditPolicyViewModel]. */
sealed interface AuditPolicyIntent {
    /** Pull the current persisted policy from the settings store. */
    data object Load : AuditPolicyIntent

    /**
     * Flip the enabled state for [category]. Optimistically updates the
     * UI; reverts and surfaces an error if the underlying write fails or
     * is rejected (e.g. attempting to disable ROLE_CHANGES).
     */
    data class Toggle(val category: AuditPolicy.Category) : AuditPolicyIntent
}

/**
 * ViewModel for `AuditPolicySettingsScreen` (Sprint 23 task 23.9 — audit
 * policy persistence slice).
 *
 * Follows the optimistic-then-revert pattern for toggle writes — the UI
 * reflects the new state immediately, then if the use case fails the
 * state rewinds and an error is surfaced. This avoids a "loading
 * spinner" on every Switch tap while still keeping persistence
 * authoritative.
 */
class AuditPolicyViewModel(
    private val getAuditPolicyUseCase: GetAuditPolicyUseCase,
    private val setAuditPolicyEnabledUseCase: SetAuditPolicyEnabledUseCase,
) : BaseViewModel<AuditPolicyState, AuditPolicyIntent, Nothing>(AuditPolicyState()) {

    override suspend fun handleIntent(intent: AuditPolicyIntent) {
        when (intent) {
            AuditPolicyIntent.Load -> {
                updateState { copy(isLoading = true, error = null) }
                val policy = getAuditPolicyUseCase()
                updateState { copy(policy = policy, isLoading = false) }
            }

            is AuditPolicyIntent.Toggle -> {
                val original = state.value.policy
                val next = !original.isEnabled(intent.category)
                // Optimistic apply; if the write fails the state rewinds.
                updateState { copy(policy = original.with(intent.category, next), error = null) }
                when (val outcome = setAuditPolicyEnabledUseCase(intent.category, next)) {
                    is Result.Error -> updateState {
                        copy(policy = original, error = outcome.exception.message ?: "Could not save audit policy.")
                    }
                    is Result.Success -> { /* optimistic apply stuck */ }
                    Result.Loading -> { /* never returned by this use case */ }
                }
            }
        }
    }
}
