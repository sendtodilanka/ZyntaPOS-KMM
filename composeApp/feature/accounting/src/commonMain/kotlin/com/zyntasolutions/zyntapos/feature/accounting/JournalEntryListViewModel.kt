package com.zyntasolutions.zyntapos.feature.accounting

import androidx.lifecycle.viewModelScope
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.JournalEntry
import com.zyntasolutions.zyntapos.domain.usecase.accounting.DeleteDraftEntryUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.GetJournalEntriesUseCase
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

// ── State ─────────────────────────────────────────────────────────────────────

/**
 * Immutable UI state for the Journal Entry List screen.
 *
 * @property entries The currently loaded journal entry headers.
 * @property isLoading True while the reactive subscription is initialising or a
 *   delete operation is in progress.
 * @property storeId The store whose entries are being viewed.
 * @property fromDate ISO date (YYYY-MM-DD) for the start of the current filter range.
 * @property toDate ISO date (YYYY-MM-DD) for the end of the current filter range.
 * @property showUnpostedOnly When true only draft (unposted) entries are displayed.
 * @property error Non-null when a use-case or repository error occurs.
 */
data class JournalEntryListState(
    val entries: List<JournalEntry> = emptyList(),
    val isLoading: Boolean = false,
    val storeId: String = "default-store",
    val fromDate: String = "",
    val toDate: String = "",
    val showUnpostedOnly: Boolean = false,
    val error: String? = null,
)

// ── Intent ────────────────────────────────────────────────────────────────────

/** User-initiated events for the Journal Entry List screen. */
sealed class JournalEntryListIntent {
    /**
     * Load journal entries for [storeId] within the given date range (ISO: YYYY-MM-DD).
     * Replaces any active subscription with a new one for the provided parameters.
     */
    data class Load(
        val storeId: String,
        val fromDate: String,
        val toDate: String,
    ) : JournalEntryListIntent()

    /** Switch to observing only unposted (draft) entries for the current store. */
    data class LoadUnposted(val storeId: String) : JournalEntryListIntent()

    /**
     * Apply a new date range filter.
     * Re-subscribes the reactive source with the updated range.
     */
    data class SetDateRange(val fromDate: String, val toDate: String) : JournalEntryListIntent()

    /** Toggle whether the list shows all entries or only drafts. */
    data class ToggleUnpostedFilter(val showUnpostedOnly: Boolean) : JournalEntryListIntent()

    /** Permanently delete the draft (unposted) entry identified by [entryId]. */
    data class DeleteDraft(val entryId: String) : JournalEntryListIntent()
}

// ── Effect ────────────────────────────────────────────────────────────────────

/** One-shot side-effects for the Journal Entry List screen. */
sealed class JournalEntryListEffect {
    /** Display a snackbar-style error message. */
    data class ShowError(val message: String) : JournalEntryListEffect()

    /** Display a snackbar-style success message. */
    data class ShowSuccess(val message: String) : JournalEntryListEffect()

    /**
     * Navigate to the Journal Entry Detail screen.
     * A null [entryId] opens the "create new entry" form.
     */
    data class NavigateToEntry(val entryId: String?) : JournalEntryListEffect()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

/**
 * ViewModel for the Journal Entry List screen.
 *
 * Observes journal entries reactively via [GetJournalEntriesUseCase].  When the
 * [showUnpostedOnly][JournalEntryListState.showUnpostedOnly] flag changes, the
 * subscription is replaced by the appropriate Flow variant.  Draft deletion is a
 * one-shot suspend operation; the reactive list updates automatically afterwards.
 *
 * @param getJournalEntriesUseCase Provides date-range and unposted Flow variants.
 * @param deleteDraftEntryUseCase Validates and deletes an unposted draft entry.
 */
class JournalEntryListViewModel(
    private val getJournalEntriesUseCase: GetJournalEntriesUseCase,
    private val deleteDraftEntryUseCase: DeleteDraftEntryUseCase,
) : BaseViewModel<JournalEntryListState, JournalEntryListIntent, JournalEntryListEffect>(
    initialState = JournalEntryListState(),
) {
    override suspend fun handleIntent(intent: JournalEntryListIntent) {
        when (intent) {
            is JournalEntryListIntent.Load -> {
                updateState {
                    copy(
                        storeId = intent.storeId,
                        fromDate = intent.fromDate,
                        toDate = intent.toDate,
                        showUnpostedOnly = false,
                    )
                }
                observeByDateRange(intent.storeId, intent.fromDate, intent.toDate)
            }

            is JournalEntryListIntent.LoadUnposted -> {
                updateState { copy(storeId = intent.storeId, showUnpostedOnly = true) }
                observeUnposted(intent.storeId)
            }

            is JournalEntryListIntent.SetDateRange -> {
                updateState { copy(fromDate = intent.fromDate, toDate = intent.toDate) }
                val storeId = currentState.storeId
                if (currentState.showUnpostedOnly) {
                    observeUnposted(storeId)
                } else {
                    observeByDateRange(storeId, intent.fromDate, intent.toDate)
                }
            }

            is JournalEntryListIntent.ToggleUnpostedFilter -> {
                updateState { copy(showUnpostedOnly = intent.showUnpostedOnly) }
                val storeId = currentState.storeId
                if (intent.showUnpostedOnly) {
                    observeUnposted(storeId)
                } else {
                    observeByDateRange(storeId, currentState.fromDate, currentState.toDate)
                }
            }

            is JournalEntryListIntent.DeleteDraft -> deleteDraft(intent.entryId)
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun observeByDateRange(storeId: String, fromDate: String, toDate: String) {
        updateState { copy(isLoading = true) }
        getJournalEntriesUseCase.execute(storeId, fromDate, toDate)
            .onEach { entries -> updateState { copy(entries = entries, isLoading = false) } }
            .catch { e ->
                updateState { copy(isLoading = false) }
                sendEffect(JournalEntryListEffect.ShowError(e.message ?: "Failed to load journal entries"))
            }
            .launchIn(viewModelScope)
    }

    private fun observeUnposted(storeId: String) {
        updateState { copy(isLoading = true) }
        getJournalEntriesUseCase.executeUnposted(storeId)
            .onEach { entries -> updateState { copy(entries = entries, isLoading = false) } }
            .catch { e ->
                updateState { copy(isLoading = false) }
                sendEffect(JournalEntryListEffect.ShowError(e.message ?: "Failed to load draft entries"))
            }
            .launchIn(viewModelScope)
    }

    private suspend fun deleteDraft(entryId: String) {
        updateState { copy(isLoading = true) }
        when (val result = deleteDraftEntryUseCase.execute(entryId)) {
            is Result.Success -> {
                updateState { copy(isLoading = false) }
                sendEffect(JournalEntryListEffect.ShowSuccess("Draft entry deleted"))
            }
            is Result.Error -> {
                updateState { copy(isLoading = false) }
                sendEffect(JournalEntryListEffect.ShowError(result.exception.message ?: "Failed to delete draft entry"))
            }
            is Result.Loading -> Unit
        }
    }
}
