package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.core.utils.IdGenerator
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.Customer_wallets
import com.zyntasolutions.zyntapos.db.Wallet_transactions
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.CustomerWallet
import com.zyntasolutions.zyntapos.domain.model.SyncOperation
import com.zyntasolutions.zyntapos.domain.model.WalletTransaction
import com.zyntasolutions.zyntapos.domain.repository.CustomerWalletRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock

class CustomerWalletRepositoryImpl(
    private val db: ZyntaDatabase,
    private val syncEnqueuer: SyncEnqueuer,
) : CustomerWalletRepository {

    private val wq get() = db.customer_walletsQueries

    override suspend fun getOrCreate(customerId: String): Result<CustomerWallet> = withContext(Dispatchers.IO) {
        runCatching {
            val existing = wq.getWalletByCustomer(customerId).executeAsOneOrNull()
            if (existing != null) return@withContext Result.Success(toDomain(existing))
            val now = Clock.System.now().toEpochMilliseconds()
            val id = IdGenerator.newId()
            wq.insertWallet(id = id, customer_id = customerId, balance = 0.0, created_at = now, updated_at = now, sync_status = "PENDING")
            syncEnqueuer.enqueue(SyncOperation.EntityType.CUSTOMER_WALLET, id, SyncOperation.Operation.INSERT)
            Result.Success(CustomerWallet(id = id, customerId = customerId, balance = 0.0))
        }.getOrElse { t -> Result.Error(DatabaseException(t.message ?: "DB error", cause = t)) }
    }

    override fun observeWallet(customerId: String): Flow<CustomerWallet?> =
        wq.getWalletByCustomer(customerId)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { row -> row?.let { toDomain(it) } }

    override fun getTransactions(walletId: String): Flow<List<WalletTransaction>> =
        db.customer_walletsQueries.getTransactionsByWallet(walletId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map(::toTransactionDomain) }

    override suspend fun credit(
        walletId: String,
        amount: Double,
        referenceType: String?,
        referenceId: String?,
        note: String?,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val wallet = wq.getWalletById(walletId).executeAsOneOrNull()
                ?: return@withContext Result.Error(DatabaseException("Wallet not found: $walletId"))
            val newBalance = wallet.balance + amount
            val now = Clock.System.now().toEpochMilliseconds()
            val txnId = IdGenerator.newId()
            db.transaction {
                wq.updateWalletBalance(balance = newBalance, updated_at = now, id = walletId)
                wq.insertWalletTransaction(
                    id = txnId, wallet_id = walletId,
                    type = WalletTransaction.TransactionType.CREDIT.name,
                    amount = amount, balance_after = newBalance,
                    reference_type = referenceType, reference_id = referenceId,
                    note = note, created_at = now, sync_status = "PENDING",
                )
                syncEnqueuer.enqueue(SyncOperation.EntityType.WALLET_TRANSACTION, txnId, SyncOperation.Operation.INSERT)
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Credit failed", cause = t)) },
        )
    }

    override suspend fun debit(
        walletId: String,
        amount: Double,
        referenceType: String?,
        referenceId: String?,
        note: String?,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val wallet = wq.getWalletById(walletId).executeAsOneOrNull()
                ?: return@withContext Result.Error(DatabaseException("Wallet not found: $walletId"))
            if (wallet.balance < amount) {
                return@withContext Result.Error(
                    ValidationException("Insufficient wallet balance: available ${wallet.balance}, requested $amount")
                )
            }
            val newBalance = wallet.balance - amount
            val now = Clock.System.now().toEpochMilliseconds()
            val txnId = IdGenerator.newId()
            db.transaction {
                wq.updateWalletBalance(balance = newBalance, updated_at = now, id = walletId)
                wq.insertWalletTransaction(
                    id = txnId, wallet_id = walletId,
                    type = WalletTransaction.TransactionType.DEBIT.name,
                    amount = amount, balance_after = newBalance,
                    reference_type = referenceType, reference_id = referenceId,
                    note = note, created_at = now, sync_status = "PENDING",
                )
                syncEnqueuer.enqueue(SyncOperation.EntityType.WALLET_TRANSACTION, txnId, SyncOperation.Operation.INSERT)
            }
        }.fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { t -> Result.Error(DatabaseException(t.message ?: "Debit failed", cause = t)) },
        )
    }

    private fun toDomain(row: Customer_wallets) = CustomerWallet(
        id = row.id, customerId = row.customer_id, balance = row.balance
    )

    private fun toTransactionDomain(row: Wallet_transactions) = WalletTransaction(
        id = row.id,
        walletId = row.wallet_id,
        type = runCatching { WalletTransaction.TransactionType.valueOf(row.type) }.getOrDefault(WalletTransaction.TransactionType.CREDIT),
        amount = row.amount,
        balanceAfter = row.balance_after,
        referenceType = row.reference_type,
        referenceId = row.reference_id,
        note = row.note,
        createdAt = row.created_at,
    )
}
