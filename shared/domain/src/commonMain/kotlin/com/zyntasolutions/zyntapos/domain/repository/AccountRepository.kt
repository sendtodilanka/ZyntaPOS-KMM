package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Account
import com.zyntasolutions.zyntapos.domain.model.AccountBalance
import com.zyntasolutions.zyntapos.domain.model.AccountType
import kotlinx.coroutines.flow.Flow

/**
 * Contract for Chart of Accounts management.
 *
 * Accounts are the fundamental building blocks of the double-entry ledger. This repository
 * provides both reactive [Flow]-based observers for UI updates and [suspend] one-shot
 * operations for mutations and lookups.
 *
 * All write operations validate preconditions (e.g. duplicate account code) before persisting.
 */
interface AccountRepository {

    /**
     * Observes all [Account] records for [storeId], ordered by [Account.accountCode] ascending.
     *
     * Emits a new list whenever any account is created, updated, or deactivated.
     *
     * @param storeId Scopes the query to a specific store.
     * @return A [Flow] that emits the full account list on every change.
     */
    fun getAll(storeId: String): Flow<List<Account>>

    /**
     * Observes all [Account] records of a specific [AccountType] for [storeId],
     * ordered by [Account.accountCode] ascending.
     *
     * Useful for populating account pickers filtered by type (e.g. only expense accounts).
     *
     * @param storeId Scopes the query to a specific store.
     * @param accountType The classification to filter by.
     * @return A [Flow] that emits the filtered account list on every change.
     */
    fun getByType(storeId: String, accountType: AccountType): Flow<List<Account>>

    /**
     * Looks up a single [Account] by its UUID.
     *
     * @param id The unique identifier of the account.
     * @return [Result] wrapping the [Account] if found, or `null` if no record exists.
     */
    suspend fun getById(id: String): Result<Account?>

    /**
     * Looks up a single [Account] by its numeric account code within a store.
     *
     * Account codes must be unique per store. Returns `null` inside [Result] when not found.
     *
     * @param storeId Scopes the query to a specific store.
     * @param accountCode The chart-of-accounts code to search for (e.g. "1010", "4010").
     * @return [Result] wrapping the matching [Account], or `null` if no record exists.
     */
    suspend fun getByCode(storeId: String, accountCode: String): Result<Account?>

    /**
     * Retrieves the cached [AccountBalance] for a specific account within a given period.
     *
     * Returns `null` inside [Result] when no balance cache entry exists for the combination.
     * Callers should trigger [FinancialStatementRepository.rebuildAllBalances] if balances
     * are stale or missing.
     *
     * @param accountId FK to the [Account] whose balance is requested.
     * @param periodId FK to the [AccountingPeriod] that scopes the balance.
     * @return [Result] wrapping the [AccountBalance], or `null` if not yet computed.
     */
    suspend fun getBalance(accountId: String, periodId: String): Result<AccountBalance?>

    /**
     * Observes all [AccountBalance] records for every account in a given period.
     *
     * Emits a new list whenever any balance record for [periodId] changes.
     *
     * @param storeId Scopes the query to a specific store.
     * @param periodId FK to the [AccountingPeriod] that scopes the balances.
     * @return A [Flow] that emits the full balance list on every change.
     */
    fun getAllBalances(storeId: String, periodId: String): Flow<List<AccountBalance>>

    /**
     * Inserts a new [Account] into the Chart of Accounts.
     *
     * The implementation must enforce uniqueness of [Account.accountCode] within [Account.id]'s
     * store scope before persisting. Returns a failure [Result] if a duplicate code exists.
     *
     * @param account The fully-populated account record to insert.
     * @return [Result.Success] on success, [Result.Failure] on constraint violations or DB errors.
     */
    suspend fun create(account: Account): Result<Unit>

    /**
     * Updates an existing [Account] record identified by [Account.id].
     *
     * System-reserved accounts ([Account.isSystemAccount] = true) may have restricted fields
     * that the implementation should guard. Returns a failure [Result] if the record does not exist.
     *
     * @param account The account record with updated field values.
     * @return [Result.Success] on success, [Result.Failure] on missing record or DB errors.
     */
    suspend fun update(account: Account): Result<Unit>

    /**
     * Soft-deletes an account by setting its [Account.isActive] flag to `false`.
     *
     * Deactivated accounts are hidden from transaction entry screens but retained for
     * historical report integrity. System accounts ([Account.isSystemAccount] = true)
     * must not be deactivatable.
     *
     * @param id The UUID of the account to deactivate.
     * @param updatedAt Epoch millis to record as the modification timestamp.
     * @return [Result.Success] on success, [Result.Failure] if the account is not found or is a system account.
     */
    suspend fun deactivate(id: String, updatedAt: Long): Result<Unit>

    /**
     * Checks whether an account code is already in use for the given store.
     *
     * Used by validation logic before create or update operations to provide a user-friendly
     * duplicate-code error before attempting the DB write.
     *
     * @param storeId Scopes the uniqueness check to a specific store.
     * @param code The account code to test.
     * @param excludeId Optional UUID of the account currently being edited. When provided,
     *   the existing record with this [excludeId] is excluded from the check so that an
     *   account may be saved with its own code unchanged.
     * @return [Result] wrapping `true` if the code is already taken, `false` if it is available.
     */
    suspend fun isAccountCodeTaken(storeId: String, code: String, excludeId: String? = null): Result<Boolean>

    /**
     * Seeds the Chart of Accounts with a list of default [Account] records.
     *
     * This operation is **idempotent**: records whose [Account.id] already exist in the database
     * are skipped (INSERT OR IGNORE semantics). Intended for first-run setup and for restoring
     * a pristine chart during testing.
     *
     * @param accounts The list of default accounts to seed.
     * @return [Result.Success] once all inserts (or skips) complete, [Result.Failure] on DB error.
     */
    suspend fun seedDefaultAccounts(accounts: List<Account>): Result<Unit>
}
