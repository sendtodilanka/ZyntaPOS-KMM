package com.zyntasolutions.zyntapos.domain.usecase.crm

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.Customer
import com.zyntasolutions.zyntapos.domain.repository.CustomerRepository
import com.zyntasolutions.zyntapos.domain.repository.CustomerWalletRepository
import com.zyntasolutions.zyntapos.domain.repository.LoyaltyRepository

/**
 * Merges two customer profiles into one (C4.3 — Centralized Customer Profiles).
 *
 * The **target** customer absorbs the **source** customer's data:
 * - Loyalty points are summed
 * - Wallet balance is transferred (credit to target, debit from source)
 * - The source customer is soft-deleted after merge
 * - Contact info from source fills in any blanks on target
 *
 * Order reassignment is NOT handled here — it requires direct DB access
 * via the data layer (OrderRepository does not expose reassignment).
 * The caller should handle order reassignment separately.
 *
 * ## Thread safety
 * Safe to invoke from any coroutine dispatcher.
 */
class MergeCustomersUseCase(
    private val customerRepo: CustomerRepository,
    private val walletRepo: CustomerWalletRepository,
    private val loyaltyRepo: LoyaltyRepository,
) {

    /**
     * Merges [sourceId] into [targetId].
     *
     * @param targetId The customer that will be kept (absorbs source data).
     * @param sourceId The customer that will be soft-deleted after merge.
     * @return [Result.Success] with a [MergeResult] summary, or [Result.Error] on failure.
     */
    suspend operator fun invoke(targetId: String, sourceId: String): Result<MergeResult> {
        if (targetId == sourceId) {
            return Result.Error(ValidationException("Cannot merge a customer with itself"))
        }

        val target = when (val r = customerRepo.getById(targetId)) {
            is Result.Success -> r.data
            is Result.Error -> return r
            is Result.Loading -> return Result.Error(DatabaseException("Unexpected loading state"))
        }

        val source = when (val r = customerRepo.getById(sourceId)) {
            is Result.Success -> r.data
            is Result.Error -> return r
            is Result.Loading -> return Result.Error(DatabaseException("Unexpected loading state"))
        }

        // 1. Combine loyalty points
        val combinedPoints = target.loyaltyPoints + source.loyaltyPoints
        customerRepo.updateLoyaltyPoints(targetId, combinedPoints)

        // 2. Transfer wallet balance (if source has a wallet with balance)
        var walletTransferred = 0.0
        val sourceWalletResult = walletRepo.getOrCreate(sourceId)
        if (sourceWalletResult is Result.Success) {
            val sourceWallet = sourceWalletResult.data
            if (sourceWallet.balance > 0.0) {
                walletTransferred = sourceWallet.balance
                // Debit source wallet
                walletRepo.debit(
                    walletId = sourceWallet.id,
                    amount = sourceWallet.balance,
                    referenceType = "MERGE",
                    referenceId = targetId,
                    note = "Wallet transfer: merge into customer $targetId",
                )
                // Credit target wallet
                val targetWalletResult = walletRepo.getOrCreate(targetId)
                if (targetWalletResult is Result.Success) {
                    walletRepo.credit(
                        walletId = targetWalletResult.data.id,
                        amount = walletTransferred,
                        referenceType = "MERGE",
                        referenceId = sourceId,
                        note = "Wallet transfer: merged from customer $sourceId",
                    )
                }
            }
        }

        // 3. Fill in missing contact info on target from source
        val merged = target.copy(
            loyaltyPoints = combinedPoints,
            email = target.email ?: source.email,
            address = target.address ?: source.address,
            gender = target.gender ?: source.gender,
            birthday = target.birthday ?: source.birthday,
            notes = mergeNotes(target.notes, source.notes),
            creditLimit = maxOf(target.creditLimit, source.creditLimit),
            creditEnabled = target.creditEnabled || source.creditEnabled,
            storeId = null, // Make merged customer global
        )
        customerRepo.update(merged)

        // 4. Soft-delete the source customer
        customerRepo.delete(sourceId)

        return Result.Success(
            MergeResult(
                targetCustomerId = targetId,
                sourceCustomerId = sourceId,
                pointsMerged = source.loyaltyPoints,
                walletBalanceTransferred = walletTransferred,
                contactInfoFilled = listOfNotNull(
                    if (target.email == null && source.email != null) "email" else null,
                    if (target.address == null && source.address != null) "address" else null,
                    if (target.gender == null && source.gender != null) "gender" else null,
                    if (target.birthday == null && source.birthday != null) "birthday" else null,
                ),
            )
        )
    }

    private fun mergeNotes(targetNotes: String?, sourceNotes: String?): String? {
        if (targetNotes == null && sourceNotes == null) return null
        if (targetNotes == null) return sourceNotes
        if (sourceNotes == null) return targetNotes
        return "$targetNotes\n--- Merged from duplicate ---\n$sourceNotes"
    }
}

/**
 * Summary of a customer merge operation.
 */
data class MergeResult(
    val targetCustomerId: String,
    val sourceCustomerId: String,
    val pointsMerged: Int,
    val walletBalanceTransferred: Double,
    val contactInfoFilled: List<String>,
)
