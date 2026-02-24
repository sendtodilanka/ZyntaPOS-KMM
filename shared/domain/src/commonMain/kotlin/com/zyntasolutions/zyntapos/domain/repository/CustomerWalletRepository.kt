package com.zyntasolutions.zyntapos.domain.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.CustomerWallet
import com.zyntasolutions.zyntapos.domain.model.WalletTransaction
import kotlinx.coroutines.flow.Flow

/**
 * Contract for customer wallet (store-credit) operations.
 *
 * Balance mutations ([credit], [debit]) are atomic — the balance update
 * and transaction ledger entry are written in a single database transaction.
 */
interface CustomerWalletRepository {

    /** Returns the wallet for the given customer. Creates one if it does not exist. */
    suspend fun getOrCreate(customerId: String): Result<CustomerWallet>

    /** Emits the wallet for a customer, re-emitting when the balance changes. */
    fun observeWallet(customerId: String): Flow<CustomerWallet?>

    /** Returns the ledger of all [WalletTransaction]s for a wallet, most recent first. */
    fun getTransactions(walletId: String): Flow<List<WalletTransaction>>

    /**
     * Credits [amount] to the wallet.
     * Atomically updates the balance and appends a CREDIT ledger entry.
     */
    suspend fun credit(
        walletId: String,
        amount: Double,
        referenceType: String? = null,
        referenceId: String? = null,
        note: String? = null,
    ): Result<Unit>

    /**
     * Debits [amount] from the wallet.
     * Fails if [amount] exceeds the current balance.
     * Atomically updates the balance and appends a DEBIT ledger entry.
     */
    suspend fun debit(
        walletId: String,
        amount: Double,
        referenceType: String? = null,
        referenceId: String? = null,
        note: String? = null,
    ): Result<Unit>
}
