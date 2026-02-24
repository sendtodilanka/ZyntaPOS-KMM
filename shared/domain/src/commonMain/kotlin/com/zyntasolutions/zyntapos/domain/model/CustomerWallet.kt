package com.zyntasolutions.zyntapos.domain.model

/**
 * A pre-paid wallet (store-credit account) linked to a single customer.
 *
 * The balance is the authoritative value stored in the database.
 * Never compute balance from [WalletTransaction] history at runtime — always read [balance] directly.
 *
 * @property id Unique identifier (UUID v4).
 * @property customerId FK to the owning [Customer].
 * @property balance Current credit balance. Always >= 0.
 */
data class CustomerWallet(
    val id: String,
    val customerId: String,
    val balance: Double = 0.0,
) {
    init {
        require(balance >= 0.0) { "Wallet balance cannot be negative" }
    }
}

/**
 * An immutable ledger entry representing a single balance change on a [CustomerWallet].
 *
 * @property id Unique identifier (UUID v4).
 * @property walletId FK to the owning [CustomerWallet].
 * @property type Nature of the transaction.
 * @property amount Absolute amount of the change (always positive).
 * @property balanceAfter Wallet balance immediately after this transaction was applied.
 * @property referenceType Optional type of the originating entity (e.g., ORDER).
 * @property referenceId Optional ID of the originating entity.
 * @property note Human-readable note for the ledger.
 */
data class WalletTransaction(
    val id: String,
    val walletId: String,
    val type: TransactionType,
    val amount: Double,
    val balanceAfter: Double,
    val referenceType: String? = null,
    val referenceId: String? = null,
    val note: String? = null,
    val createdAt: Long,
) {
    enum class TransactionType { CREDIT, DEBIT, REFUND }

    init {
        require(amount > 0.0) { "Transaction amount must be positive" }
    }
}
