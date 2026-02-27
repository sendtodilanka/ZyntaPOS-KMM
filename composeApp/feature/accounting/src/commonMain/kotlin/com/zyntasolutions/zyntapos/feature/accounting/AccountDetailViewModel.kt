package com.zyntasolutions.zyntapos.feature.accounting

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Account
import com.zyntasolutions.zyntapos.domain.model.AccountType
import com.zyntasolutions.zyntapos.domain.model.NormalBalance
import com.zyntasolutions.zyntapos.domain.usecase.accounting.GetAccountsUseCase
import com.zyntasolutions.zyntapos.domain.usecase.accounting.SaveAccountUseCase
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel
import kotlin.time.Clock

// ── State ─────────────────────────────────────────────────────────────────────

/**
 * Immutable UI state for the Account Detail (create / edit) screen.
 *
 * @property account The original account loaded from the repository; null for new accounts.
 * @property isLoading True while the account is being fetched.
 * @property isSaving True while a save operation is in progress.
 * @property accountCode Editable account code field.
 * @property accountName Editable account name field.
 * @property accountType Editable account type selection.
 * @property subCategory Editable sub-category label.
 * @property description Optional extended description.
 * @property error Non-null when a use-case validation or repository error occurs.
 */
data class AccountDetailState(
    val account: Account? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val accountCode: String = "",
    val accountName: String = "",
    val accountType: AccountType = AccountType.EXPENSE,
    val subCategory: String = "",
    val description: String = "",
    val error: String? = null,
)

// ── Intent ────────────────────────────────────────────────────────────────────

/** User-initiated events for the Account Detail screen. */
sealed class AccountDetailIntent {
    /** Load an existing account by its UUID for editing. */
    data class Load(val accountId: String) : AccountDetailIntent()

    /** Initialise the form in "create new account" mode for the given [storeId]. */
    data class StartNew(val storeId: String) : AccountDetailIntent()

    /** Update the account code form field. */
    data class UpdateCode(val code: String) : AccountDetailIntent()

    /** Update the account name form field. */
    data class UpdateName(val name: String) : AccountDetailIntent()

    /** Change the selected account type. */
    data class UpdateType(val type: AccountType) : AccountDetailIntent()

    /** Update the sub-category form field. */
    data class UpdateSubCategory(val subCategory: String) : AccountDetailIntent()

    /** Update the optional description field. */
    data class UpdateDescription(val description: String) : AccountDetailIntent()

    /** Validate and persist the current form state (create or update). */
    data class Save(val storeId: String) : AccountDetailIntent()
}

// ── Effect ────────────────────────────────────────────────────────────────────

/** One-shot side-effects for the Account Detail screen. */
sealed class AccountDetailEffect {
    /** Display a snackbar-style error message. */
    data class ShowError(val message: String) : AccountDetailEffect()

    /** Account was persisted successfully; trigger post-save navigation in the UI. */
    data object SavedSuccessfully : AccountDetailEffect()

    /** Pop this screen from the back-stack. */
    data object NavigateBack : AccountDetailEffect()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

/**
 * ViewModel for the Account Detail screen (create and edit mode).
 *
 * In edit mode ([AccountDetailIntent.Load]) the account is fetched by UUID and the
 * form fields are pre-populated. In create mode ([AccountDetailIntent.StartNew]) the
 * form starts blank.  [AccountDetailIntent.Save] delegates to [SaveAccountUseCase]
 * which enforces uniqueness and system-account protection rules.
 *
 * @param getAccountsUseCase Provides one-shot access to fetch an account by ID.
 * @param saveAccountUseCase Validates and persists the account (create or update).
 */
class AccountDetailViewModel(
    private val getAccountsUseCase: GetAccountsUseCase,
    private val saveAccountUseCase: SaveAccountUseCase,
) : BaseViewModel<AccountDetailState, AccountDetailIntent, AccountDetailEffect>(
    initialState = AccountDetailState(),
) {
    override suspend fun handleIntent(intent: AccountDetailIntent) {
        when (intent) {
            is AccountDetailIntent.Load -> loadAccount(intent.accountId)
            is AccountDetailIntent.StartNew -> updateState { AccountDetailState() }
            is AccountDetailIntent.UpdateCode -> updateState { copy(accountCode = intent.code, error = null) }
            is AccountDetailIntent.UpdateName -> updateState { copy(accountName = intent.name, error = null) }
            is AccountDetailIntent.UpdateType -> updateState { copy(accountType = intent.type) }
            is AccountDetailIntent.UpdateSubCategory -> updateState { copy(subCategory = intent.subCategory) }
            is AccountDetailIntent.UpdateDescription -> updateState { copy(description = intent.description) }
            is AccountDetailIntent.Save -> saveAccount(intent.storeId)
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private suspend fun loadAccount(accountId: String) {
        updateState { copy(isLoading = true, error = null) }
        when (val result = getAccountsUseCase.executeById(accountId)) {
            is Result.Success -> {
                val account = result.data
                if (account == null) {
                    updateState { copy(isLoading = false, error = "Account not found") }
                    sendEffect(AccountDetailEffect.ShowError("Account not found"))
                } else {
                    updateState {
                        copy(
                            isLoading = false,
                            account = account,
                            accountCode = account.accountCode,
                            accountName = account.accountName,
                            accountType = account.accountType,
                            subCategory = account.subCategory,
                            description = account.description ?: "",
                        )
                    }
                }
            }
            is Result.Error -> {
                updateState { copy(isLoading = false, error = result.exception.message) }
                sendEffect(AccountDetailEffect.ShowError(result.exception.message ?: "Failed to load account"))
            }
            is Result.Loading -> Unit
        }
    }

    private suspend fun saveAccount(storeId: String) {
        val state = currentState
        updateState { copy(isSaving = true, error = null) }

        val now = Clock.System.now().toEpochMilliseconds()
        val existing = state.account

        // Determine the default NormalBalance from the selected AccountType.
        val normalBalance = when (state.accountType) {
            AccountType.ASSET, AccountType.COGS, AccountType.EXPENSE -> NormalBalance.DEBIT
            AccountType.LIABILITY, AccountType.EQUITY, AccountType.INCOME -> NormalBalance.CREDIT
        }

        val account = Account(
            id = existing?.id ?: generateUuid(),
            accountCode = state.accountCode.trim(),
            accountName = state.accountName.trim(),
            accountType = state.accountType,
            subCategory = state.subCategory.trim(),
            description = state.description.trim().takeIf { it.isNotBlank() },
            normalBalance = normalBalance,
            parentAccountId = existing?.parentAccountId,
            isSystemAccount = existing?.isSystemAccount ?: false,
            isActive = existing?.isActive ?: true,
            isHeaderAccount = existing?.isHeaderAccount ?: false,
            allowTransactions = existing?.allowTransactions ?: true,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )

        when (val result = saveAccountUseCase.execute(account, storeId)) {
            is Result.Success -> {
                updateState { copy(isSaving = false) }
                sendEffect(AccountDetailEffect.SavedSuccessfully)
            }
            is Result.Error -> {
                updateState { copy(isSaving = false, error = result.exception.message) }
                sendEffect(AccountDetailEffect.ShowError(result.exception.message ?: "Failed to save account"))
            }
            is Result.Loading -> Unit
        }
    }

    /**
     * Generates a simple UUID-like string for new account IDs.
     * Replace with `uuid4()` from a KMP UUID library if one is added to the catalog.
     */
    private fun generateUuid(): String {
        val now = Clock.System.now().toEpochMilliseconds()
        return "acct-$now-${(0..9999).random()}"
    }
}
