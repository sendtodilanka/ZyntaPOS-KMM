package com.zyntasolutions.zyntapos.data.repository

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.Accounting_entries
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.AccountingEntry
import com.zyntasolutions.zyntapos.domain.model.AccountingEntryType
import com.zyntasolutions.zyntapos.domain.model.AccountingReferenceType
import com.zyntasolutions.zyntapos.domain.model.AccountSummary
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.repository.AccountingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.math.abs

class AccountingRepositoryImpl(
    private val db: ZyntaDatabase,
    private val syncEnqueuer: SyncEnqueuer,
) : AccountingRepository {

    private val q get() = db.accounting_entriesQueries

    override suspend fun getByStoreAndPeriod(
        storeId: String,
        fiscalPeriod: String,
    ): Result<List<AccountingEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            q.selectByStoreAndPeriod(storeId, fiscalPeriod)
                .executeAsList()
                .map(::toDomain)
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun getByAccountAndPeriod(
        storeId: String,
        accountCode: String,
        fiscalPeriod: String,
    ): Result<List<AccountingEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            q.selectByAccountAndPeriod(storeId, accountCode, fiscalPeriod)
                .executeAsList()
                .map(::toDomain)
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun getByReference(
        referenceType: AccountingReferenceType,
        referenceId: String,
    ): Result<List<AccountingEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            q.selectByReference(referenceType.name, referenceId)
                .executeAsList()
                .map(::toDomain)
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) },
        )
    }

    override suspend fun getSummaryForPeriodRange(
        storeId: String,
        fromPeriod: String,
        toPeriod: String,
    ): Result<List<AccountSummary>> = withContext(Dispatchers.IO) {
        runCatching {
            q.sumByAccountForPeriod(storeId, fromPeriod, toPeriod)
                .executeAsList()
                .map { row ->
                    AccountSummary(
                        accountCode = row.account_code,
                        accountName = row.account_name,
                        entryType = runCatching { AccountingEntryType.valueOf(row.entry_type) }
                            .getOrDefault(AccountingEntryType.DEBIT),
                        total = row.total ?: 0.0,
                    )
                }
        }.fold(
            onSuccess = { Result.Success(it) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "getSummary failed", cause = t)) },
        )
    }

    override suspend fun insertEntries(entries: List<AccountingEntry>): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (entries.isEmpty()) return@withContext Result.Success(Unit)

            // Validate double-entry balance: sum(DEBIT) must equal sum(CREDIT) within tolerance
            val debitTotal = entries.filter { it.entryType == AccountingEntryType.DEBIT }.sumOf { it.amount }
            val creditTotal = entries.filter { it.entryType == AccountingEntryType.CREDIT }.sumOf { it.amount }
            if (abs(debitTotal - creditTotal) > 0.005) {
                return@withContext Result.Error(
                    ValidationException(
                        "Unbalanced accounting entries: DEBIT=$debitTotal, CREDIT=$creditTotal"
                    )
                )
            }

            runCatching {
                val now = Clock.System.now().toEpochMilliseconds()
                db.transaction {
                    entries.forEach { entry ->
                        q.insertEntry(
                            id = entry.id,
                            store_id = entry.storeId,
                            account_code = entry.accountCode,
                            account_name = entry.accountName,
                            entry_type = entry.entryType.name,
                            amount = entry.amount,
                            reference_type = entry.referenceType.name,
                            reference_id = entry.referenceId,
                            description = entry.description,
                            entry_date = entry.entryDate,
                            fiscal_period = entry.fiscalPeriod,
                            created_by = entry.createdBy,
                            created_at = now,
                            sync_status = "PENDING",
                        )
                        syncEnqueuer.enqueue(
                            SyncOperation.EntityType.ACCOUNTING_ENTRY,
                            entry.id,
                            SyncOperation.Operation.INSERT,
                        )
                    }
                }
            }.fold(
                onSuccess = { Result.Success(Unit) },
                onFailure = { t -> Result.Error(DatabaseException(t.message ?: "insertEntries failed", cause = t)) },
            )
        }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private fun toDomain(row: Accounting_entries) = AccountingEntry(
        id = row.id,
        storeId = row.store_id,
        accountCode = row.account_code,
        accountName = row.account_name,
        entryType = runCatching { AccountingEntryType.valueOf(row.entry_type) }
            .getOrDefault(AccountingEntryType.DEBIT),
        amount = row.amount,
        referenceType = runCatching { AccountingReferenceType.valueOf(row.reference_type) }
            .getOrDefault(AccountingReferenceType.ORDER),
        referenceId = row.reference_id,
        description = row.description,
        entryDate = row.entry_date,
        fiscalPeriod = row.fiscal_period,
        createdBy = row.created_by,
        createdAt = row.created_at,
    )
}
