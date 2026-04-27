package com.zyntasolutions.zyntapos.feature.settings

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.DataRetentionPolicy
import com.zyntasolutions.zyntapos.domain.usecase.settings.GetDataRetentionPolicyUseCase
import com.zyntasolutions.zyntapos.domain.usecase.settings.SaveDataRetentionPolicyUseCase
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel

/** MVI state for the data-retention screen. */
data class DataRetentionState(
    val policy: DataRetentionPolicy = DataRetentionPolicy(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

/** Intents accepted by [DataRetentionViewModel]. */
sealed interface DataRetentionIntent {
    /** Pull the current persisted policy. */
    data object Load : DataRetentionIntent

    /**
     * Apply a new [DataRetentionPolicy]. Same optimistic-then-rewind pattern
     * as the audit/security policy view-models.
     */
    data class Apply(val policy: DataRetentionPolicy) : DataRetentionIntent
}

/**
 * ViewModel for `DataRetentionSettingsScreen` (Sprint 23 task 23.9 — slice 3/3).
 *
 * Mirrors [SecurityPolicyViewModel] with a smaller surface (3 dropdowns,
 * no biometric switch). The "Run Purge Now" affordance from the spec is
 * deferred — the actual `PurgeExpiredDataUseCase` is not implemented yet,
 * so the screen renders the button disabled.
 */
class DataRetentionViewModel(
    private val getDataRetentionPolicyUseCase: GetDataRetentionPolicyUseCase,
    private val saveDataRetentionPolicyUseCase: SaveDataRetentionPolicyUseCase,
) : BaseViewModel<DataRetentionState, DataRetentionIntent, Nothing>(DataRetentionState()) {

    override suspend fun handleIntent(intent: DataRetentionIntent) {
        when (intent) {
            DataRetentionIntent.Load -> {
                updateState { copy(isLoading = true, error = null) }
                val policy = getDataRetentionPolicyUseCase()
                updateState { copy(policy = policy, isLoading = false) }
            }

            is DataRetentionIntent.Apply -> {
                val original = state.value.policy
                updateState { copy(policy = intent.policy, error = null) }
                when (val outcome = saveDataRetentionPolicyUseCase(intent.policy)) {
                    is Result.Error -> updateState {
                        copy(
                            policy = original,
                            error = outcome.exception.message ?: "Could not save retention policy.",
                        )
                    }
                    is Result.Success -> { /* optimistic apply stuck */ }
                    Result.Loading -> { /* never returned */ }
                }
            }
        }
    }
}
