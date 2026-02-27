package com.zyntasolutions.zyntapos.feature.accounting

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.JournalEntry
import com.zyntasolutions.zyntapos.domain.model.JournalEntryLine
import com.zyntasolutions.zyntapos.domain.model.JournalReferenceType
import com.zyntasolutions.zyntapos.domain.usecase.accounting.GetJournalEntriesUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.PostJournalEntryUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.ReverseJournalEntryUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.SaveDraftJournalEntryUseCase
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import kotlin.math.abs
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// ── State ─────────────────────────────────────────────────────────────────────

/**
 * Immutable UI state for the Journal Entry Detail (create / edit / view) screen.
 *
 * @property entry The entry loaded from the repository; null for a brand-new entry.
 * @property isLoading True while the entry is being fetched.
 * @property isSaving True while a draft-save operation is in progress.
 * @property isPosting True while a post (lock) operation is in progress.
 * @property isReversing True while a reversal entry is being created.
 * @property description Editable header description field.
 * @property entryDate Editable accounting date (ISO: YYYY-MM-DD).
 * @property referenceType Selected [JournalReferenceType] for this entry.
 * @property storeId The store scope for this entry.
 * @property createdBy The user ID attributed to this entry.
 * @property lines The current set of debit/credit lines shown in the UI.
 * @property isBalanced True when the sum of debits equals the sum of credits (within 0.005).
 * @property error Non-null when a use-case validation or repository error occurs.
 */
data class JournalEntryDetailState(
    val entry: JournalEntry? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isPosting: Boolean = false,
    val isReversing: Boolean = false,
    val description: String = "",
    val entryDate: String = "",
    val referenceType: JournalReferenceType = JournalReferenceType.MANUAL,
    val storeId: String = "default-store",
    val createdBy: String = "current-user",
    val lines: List<JournalEntryLine> = emptyList(),
    val isBalanced: Boolean = false,
    val error: String? = null,
)

// ── Intent ────────────────────────────────────────────────────────────────────

/** User-initiated events for the Journal Entry Detail screen. */
sealed class JournalEntryDetailIntent {
    /** Load an existing entry (with its lines) by [entryId]. */
    data class Load(val entryId: String) : JournalEntryDetailIntent()

    /** Initialise the form in "create new entry" mode for [storeId] and [createdBy]. */
    data class NewEntry(val storeId: String, val createdBy: String) : JournalEntryDetailIntent()

    /** Update the header description field. */
    data class UpdateDescription(val description: String) : JournalEntryDetailIntent()

    /** Update the accounting date field (ISO: YYYY-MM-DD). */
    data class UpdateDate(val date: String) : JournalEntryDetailIntent()

    /** Change the reference type classification. */
    data class UpdateReferenceType(val referenceType: JournalReferenceType) : JournalEntryDetailIntent()

    /** Append [line] to the current set of entry lines. */
    data class AddLine(val line: JournalEntryLine) : JournalEntryDetailIntent()

    /** Remove the line identified by [lineId] from the current set. */
    data class RemoveLine(val lineId: String) : JournalEntryDetailIntent()

    /** Validate and persist the entry as a draft (unposted). */
    data object Save : JournalEntryDetailIntent()

    /** Validate, balance-check, and post (lock) the entry identified by [entryId]. */
    data class Post(val entryId: String) : JournalEntryDetailIntent()

    /**
     * Create a reversal entry for the posted entry [entryId].
     * The reversal is created on [reversalDate] (ISO: YYYY-MM-DD).
     */
    data class Reverse(val entryId: String, val reversalDate: String) : JournalEntryDetailIntent()
}

// ── Effect ────────────────────────────────────────────────────────────────────

/** One-shot side-effects for the Journal Entry Detail screen. */
sealed class JournalEntryDetailEffect {
    /** Display a snackbar-style error message. */
    data class ShowError(val message: String) : JournalEntryDetailEffect()

    /** Display a snackbar-style success message. */
    data class ShowSuccess(val message: String) : JournalEntryDetailEffect()

    /** Pop this screen from the back-stack. */
    data object NavigateBack : JournalEntryDetailEffect()

    /** Navigate to the newly created reversal entry for review. */
    data class NavigateToEntry(val entryId: String) : JournalEntryDetailEffect()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

/**
 * ViewModel for the Journal Entry Detail screen (create, edit, view, post, reverse).
 *
 * Workflow:
 * 1. **New entry** — [JournalEntryDetailIntent.NewEntry] resets the form.
 * 2. **Editing** — field intents accumulate changes in state; [JournalEntryDetailIntent.Save]
 *    persists the draft via [SaveDraftJournalEntryUseCase].
 * 3. **Posting** — [JournalEntryDetailIntent.Post] validates balance and period status via
 *    [PostJournalEntryUseCase].
 * 4. **Reversal** — [JournalEntryDetailIntent.Reverse] creates a new draft reversal via
 *    [ReverseJournalEntryUseCase] and navigates to it.
 *
 * @param getJournalEntriesUseCase One-shot fetch of a single entry with its lines.
 * @param saveDraftJournalEntryUseCase Validates and persists a draft entry.
 * @param postJournalEntryUseCase Validates balance / period and marks the entry posted.
 * @param reverseJournalEntryUseCase Creates a draft reversal of a posted entry.
 */
class JournalEntryDetailViewModel(
    private val getJournalEntriesUseCase: GetJournalEntriesUseCase,
    private val saveDraftJournalEntryUseCase: SaveDraftJournalEntryUseCase,
    private val postJournalEntryUseCase: PostJournalEntryUseCase,
    private val reverseJournalEntryUseCase: ReverseJournalEntryUseCase,
) : BaseViewModel<JournalEntryDetailState, JournalEntryDetailIntent, JournalEntryDetailEffect>(
    initialState = JournalEntryDetailState(),
) {
    override suspend fun handleIntent(intent: JournalEntryDetailIntent) {
        when (intent) {
            is JournalEntryDetailIntent.Load -> loadEntry(intent.entryId)
            is JournalEntryDetailIntent.NewEntry -> initNewEntry(intent.storeId, intent.createdBy)
            is JournalEntryDetailIntent.UpdateDescription -> updateState {
                copy(description = intent.description, error = null)
            }
            is JournalEntryDetailIntent.UpdateDate -> updateState {
                copy(entryDate = intent.date, error = null)
            }
            is JournalEntryDetailIntent.UpdateReferenceType -> updateState {
                copy(referenceType = intent.referenceType)
            }
            is JournalEntryDetailIntent.AddLine -> addLine(intent.line)
            is JournalEntryDetailIntent.RemoveLine -> removeLine(intent.lineId)
            is JournalEntryDetailIntent.Save -> saveDraft()
            is JournalEntryDetailIntent.Post -> postEntry(intent.entryId)
            is JournalEntryDetailIntent.Reverse -> reverseEntry(intent.entryId, intent.reversalDate)
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private suspend fun loadEntry(entryId: String) {
        updateState { copy(isLoading = true, error = null) }
        when (val result = getJournalEntriesUseCase.executeById(entryId)) {
            is Result.Success -> {
                val entry = result.data
                if (entry == null) {
                    updateState { copy(isLoading = false, error = "Journal entry not found") }
                    sendEffect(JournalEntryDetailEffect.ShowError("Journal entry not found"))
                } else {
                    updateState {
                        copy(
                            isLoading = false,
                            entry = entry,
                            description = entry.description,
                            entryDate = entry.entryDate,
                            referenceType = entry.referenceType,
                            storeId = entry.storeId,
                            createdBy = entry.createdBy,
                            lines = entry.lines,
                            isBalanced = isBalanced(entry.lines),
                        )
                    }
                }
            }
            is Result.Error -> {
                updateState { copy(isLoading = false, error = result.exception.message) }
                sendEffect(JournalEntryDetailEffect.ShowError(result.exception.message ?: "Failed to load entry"))
            }
            is Result.Loading -> Unit
        }
    }

    private fun initNewEntry(storeId: String, createdBy: String) {
        val today = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date.toString()
        updateState {
            JournalEntryDetailState(
                storeId = storeId,
                createdBy = createdBy,
                entryDate = today,
                referenceType = JournalReferenceType.MANUAL,
            )
        }
    }

    private fun addLine(line: JournalEntryLine) {
        updateState {
            val updatedLines = lines + line
            copy(
                lines = updatedLines,
                isBalanced = isBalanced(updatedLines),
                error = null,
            )
        }
    }

    private fun removeLine(lineId: String) {
        updateState {
            val updatedLines = lines.filterNot { it.id == lineId }
            copy(
                lines = updatedLines,
                isBalanced = isBalanced(updatedLines),
            )
        }
    }

    private suspend fun saveDraft() {
        val state = currentState
        updateState { copy(isSaving = true, error = null) }

        val now = Clock.System.now().toEpochMilliseconds()
        val existing = state.entry
        val entryToSave = JournalEntry(
            id = existing?.id ?: "jnl-${now}-${(0..9999).random()}",
            entryNumber = existing?.entryNumber ?: 0,
            storeId = state.storeId,
            entryDate = state.entryDate,
            entryTime = now,
            description = state.description.trim(),
            referenceType = state.referenceType,
            referenceId = existing?.referenceId,
            isPosted = false,
            createdBy = state.createdBy,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            memo = existing?.memo,
            lines = state.lines,
        )

        when (val result = saveDraftJournalEntryUseCase.execute(entryToSave)) {
            is Result.Success -> {
                updateState { copy(isSaving = false) }
                sendEffect(JournalEntryDetailEffect.ShowSuccess("Draft saved"))
            }
            is Result.Error -> {
                updateState { copy(isSaving = false, error = result.exception.message) }
                sendEffect(JournalEntryDetailEffect.ShowError(result.exception.message ?: "Failed to save draft"))
            }
            is Result.Loading -> Unit
        }
    }

    private suspend fun postEntry(entryId: String) {
        updateState { copy(isPosting = true, error = null) }
        val now = Clock.System.now().toEpochMilliseconds()
        when (val result = postJournalEntryUseCase.execute(entryId, now)) {
            is Result.Success -> {
                updateState { copy(isPosting = false) }
                sendEffect(JournalEntryDetailEffect.ShowSuccess("Entry posted successfully"))
                sendEffect(JournalEntryDetailEffect.NavigateBack)
            }
            is Result.Error -> {
                updateState { copy(isPosting = false, error = result.exception.message) }
                sendEffect(JournalEntryDetailEffect.ShowError(result.exception.message ?: "Failed to post entry"))
            }
            is Result.Loading -> Unit
        }
    }

    private suspend fun reverseEntry(entryId: String, reversalDate: String) {
        updateState { copy(isReversing = true, error = null) }
        val now = Clock.System.now().toEpochMilliseconds()
        when (val result = reverseJournalEntryUseCase.execute(entryId, reversalDate, currentState.createdBy, now)) {
            is Result.Success -> {
                val reversalEntry = result.data
                updateState { copy(isReversing = false) }
                sendEffect(JournalEntryDetailEffect.ShowSuccess("Reversal entry created"))
                sendEffect(JournalEntryDetailEffect.NavigateToEntry(reversalEntry.id))
            }
            is Result.Error -> {
                updateState { copy(isReversing = false, error = result.exception.message) }
                sendEffect(JournalEntryDetailEffect.ShowError(result.exception.message ?: "Failed to reverse entry"))
            }
            is Result.Loading -> Unit
        }
    }

    /** Returns true when the sum of all debit amounts equals the sum of all credit amounts (epsilon 0.005). */
    private fun isBalanced(lines: List<JournalEntryLine>): Boolean {
        if (lines.isEmpty()) return false
        val debitTotal = lines.sumOf { it.debitAmount }
        val creditTotal = lines.sumOf { it.creditAmount }
        return abs(debitTotal - creditTotal) <= 0.005
    }
}
