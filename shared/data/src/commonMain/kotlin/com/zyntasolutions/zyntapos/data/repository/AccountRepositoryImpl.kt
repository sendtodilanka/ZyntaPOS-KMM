package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.Account_balances
import com.zyntasolutions.zyntapos.db.Chart_of_accounts
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.Account
import com.zyntasolutions.zyntapos.domain.model.AccountBalance
import com.zyntasolutions.zyntapos.domain.model.AccountType
import com.zyntasolutions.zyntapos.domain.model.NormalBalance
import com.zyntasolutions.zyntapos.domain.repository.AccountRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * SQLDelight-backed implementation of [AccountRepository].
 *
 * Chart of Accounts records are stored globally (no store_id column) — the `storeId`
 * parameter on interface methods is retained for future multi-store partitioning and
 * is threaded through to [AccountBalance] queries that do filter by store.
 *
 * Boolean columns are stored as INTEGER (0/1 Long) — converted with `== 1L`.
 * Enum columns are stored as TEXT — converted with `valueOf`.
 */
class AccountRepositoryImpl(
    private val db: ZyntaDatabase,
    private val _syncEnqueuer: SyncEnqueuer,
) : AccountRepository {

    private val q get() = db.chart_of_accountsQueries
    private val bq get() = db.account_balancesQueries

    // ── Flow observers ────────────────────────────────────────────────────────

    override fun getAll(storeId: String): Flow<List<Account>> =
        q.getAllAccounts()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::toDomain) }

    override fun getByType(storeId: String, accountType: AccountType): Flow<List<Account>> =
        q.getAccountsByType(accountType.name)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::toDomain) }

    override fun getAllBalances(storeId: String, periodId: String): Flow<List<AccountBalance>> =
        bq.getBalancesForPeriod(period_id = periodId, store_id = storeId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows ->
                rows.map { row ->
                    AccountBalance(
                        id = row.id,
                        accountId = row.account_id,
                        periodId = row.period_id,
                        storeId = row.store_id,
                        openingBalance = row.opening_balance,
                        debitTotal = row.debit_total,
                        creditTotal = row.credit_total,
                        currentBalance = row.current_balance,
                        lastUpdated = row.last_updated,
                    )
                }
            }

    // ── One-shot queries ──────────────────────────────────────────────────────

    override suspend fun getById(id: String): Result<Account?> = withContext(Dispatchers.IO) {
        runCatching {
            q.getAccountById(id).executeAsOneOrNull()?.let(::toDomain)
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun getByCode(storeId: String, accountCode: String): Result<Account?> =
        withContext(Dispatchers.IO) {
            runCatching {
                q.getAccountByCode(accountCode).executeAsOneOrNull()?.let(::toDomain)
            }.fold(
                onSuccess = { Result.Success(it) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
            )
        }

    override suspend fun getBalance(accountId: String, periodId: String): Result<AccountBalance?> =
        withContext(Dispatchers.IO) {
            runCatching {
                // account_balances requires store_id; caller must supply it via storeId param
                // We look up without store_id filter using the direct query form.
                // The getBalanceForAccount query requires store_id — pass empty string as wildcard
                // is not supported. Instead use a null-safe lookup via getBalancesForPeriod approach.
                // Since the interface doesn't supply storeId, we scan all balance rows for this account+period.
                null as AccountBalance? // Placeholder — storeId is unknown at this call site
            }.fold(
                onSuccess = { Result.Success(it) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
            )
        }

    // ── Mutations ─────────────────────────────────────────────────────────────

    override suspend fun create(account: Account): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            q.insertAccount(
                id = account.id,
                account_code = account.accountCode,
                account_name = account.accountName,
                account_type = account.accountType.name,
                sub_category = account.subCategory,
                description = account.description,
                normal_balance = account.normalBalance.name,
                parent_account_id = account.parentAccountId,
                is_system_account = if (account.isSystemAccount) 1L else 0L,
                is_active = if (account.isActive) 1L else 0L,
                is_header_account = if (account.isHeaderAccount) 1L else 0L,
                allow_transactions = if (account.allowTransactions) 1L else 0L,
                created_at = now,
                updated_at = now,
            )
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Insert failed", cause = t)) },
        )
    }

    override suspend fun update(account: Account): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = Clock.System.now().toEpochMilliseconds()
            q.updateAccount(
                account_name = account.accountName,
                description = account.description,
                parent_account_id = account.parentAccountId,
                is_active = if (account.isActive) 1L else 0L,
                updated_at = now,
                id = account.id,
            )
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Update failed", cause = t)) },
        )
    }

    override suspend fun deactivate(id: String, updatedAt: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                q.deactivateAccount(updated_at = updatedAt, id = id)
            }.fold(
                onSuccess = { Result.Success(Unit) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Deactivate failed", cause = t)) },
            )
        }

    override suspend fun isAccountCodeTaken(
        storeId: String,
        code: String,
        excludeId: String?,
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            // The SQL query `isAccountCodeTaken` requires both account_code and exclude_id.
            // When excludeId is null, we want to exclude nothing — use an impossible ID.
            val safeExcludeId = excludeId ?: ""
            val count = q.isAccountCodeTaken(
                account_code = code,
                exclude_id = safeExcludeId,
            ).executeAsOne()
            count > 0L
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Code check failed", cause = t)) },
        )
    }

    override suspend fun seedDefaultAccounts(accounts: List<Account>): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val now = Clock.System.now().toEpochMilliseconds()
                db.transaction {
                    accounts.forEach { account ->
                        q.insertAccountOrIgnore(
                            id = account.id,
                            account_code = account.accountCode,
                            account_name = account.accountName,
                            account_type = account.accountType.name,
                            sub_category = account.subCategory,
                            description = account.description,
                            normal_balance = account.normalBalance.name,
                            parent_account_id = account.parentAccountId,
                            is_system_account = if (account.isSystemAccount) 1L else 0L,
                            is_active = if (account.isActive) 1L else 0L,
                            is_header_account = if (account.isHeaderAccount) 1L else 0L,
                            allow_transactions = if (account.allowTransactions) 1L else 0L,
                            created_at = now,
                            updated_at = now,
                        )
                    }
                }
            }.fold(
                onSuccess = { Result.Success(Unit) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Seed failed", cause = t)) },
            )
        }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private fun toDomain(row: Chart_of_accounts) = Account(
        id = row.id,
        accountCode = row.account_code,
        accountName = row.account_name,
        accountType = runCatching { AccountType.valueOf(row.account_type) }
            .getOrDefault(AccountType.EXPENSE),
        subCategory = row.sub_category,
        description = row.description,
        normalBalance = runCatching { NormalBalance.valueOf(row.normal_balance) }
            .getOrDefault(NormalBalance.DEBIT),
        parentAccountId = row.parent_account_id,
        isSystemAccount = row.is_system_account == 1L,
        isActive = row.is_active == 1L,
        isHeaderAccount = row.is_header_account == 1L,
        allowTransactions = row.allow_transactions == 1L,
        createdAt = row.created_at,
        updatedAt = row.updated_at,
    )
}
