package com.zyntasolutions.zyntapos.domain.usecase.accounting

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.JournalEntry
import com.zyntasolutions.zyntapos.domain.model.JournalEntryLine
import com.zyntasolutions.zyntapos.domain.model.JournalReferenceType
import com.zyntasolutions.zyntapos.domain.model.PeriodStatus
import com.zyntasolutions.zyntapos.domain.model.StandardAccountCodes
import com.zyntasolutions.zyntapos.domain.repository.AccountRepository
import com.zyntasolutions.zyntapos.domain.repository.AccountingPeriodRepository
import com.zyntasolutions.zyntapos.domain.repository.JournalRepository

/**
 * Auto-posts a balanced journal entry for a completed sale.
 *
 * Standard double-entry pattern:
 *   DR 1010 Cash (or 1100 Accounts Receivable)   = totalAmount
 *   CR 4010 Sales Revenue                          = subtotal
 *   CR 2100 Sales Tax Payable                      = taxAmount (if > 0)
 *
 * Looks up accounts by code. Returns ValidationException if required accounts are not found
 * or if no open period exists for the entry date.
 */
class PostSaleJournalEntryUseCase(
    private val journalRepository: JournalRepository,
    private val accountRepository: AccountRepository,
    private val periodRepository: AccountingPeriodRepository,
) {
    @Suppress("LongParameterList")
    suspend fun execute(
        storeId: String,
        orderId: String,
        totalAmount: Double,
        subtotal: Double,
        taxAmount: Double,
        cashierId: String,
        entryDate: String,
        now: Long,
    ): Result<Unit> {
        // Validate the accounting period is open
        val periodResult = periodRepository.getPeriodForDate(storeId, entryDate)
        if (periodResult is Result.Error) return periodResult
        val period = (periodResult as Result.Success).data
        if (period == null || period.status != PeriodStatus.OPEN) {
            return Result.Error(
                ValidationException(
                    "No open accounting period found for sale date $entryDate.",
                    field = "entryDate",
                    rule = "PERIOD_NOT_OPEN",
                ),
            )
        }

        // Lookup debit account: Cash
        val cashAccountResult = accountRepository.getByCode(storeId, StandardAccountCodes.CASH)
        if (cashAccountResult is Result.Error) return cashAccountResult
        val cashAccount = (cashAccountResult as Result.Success).data
            ?: return Result.Error(
                ValidationException(
                    "Required account '${StandardAccountCodes.CASH} Cash' not found. Seed the Chart of Accounts first.",
                    field = "accountCode",
                    rule = "ACCOUNT_NOT_FOUND",
                ),
            )

        // Lookup credit account: Sales Revenue
        val revenueAccountResult = accountRepository.getByCode(storeId, StandardAccountCodes.SALES_REVENUE)
        if (revenueAccountResult is Result.Error) return revenueAccountResult
        val revenueAccount = (revenueAccountResult as Result.Success).data
            ?: return Result.Error(
                ValidationException(
                    "Required account '${StandardAccountCodes.SALES_REVENUE} Sales Revenue' not found. Seed the Chart of Accounts first.",
                    field = "accountCode",
                    rule = "ACCOUNT_NOT_FOUND",
                ),
            )

        // Lookup credit account: Sales Tax Payable — required only if taxAmount > 0
        val taxAccount = if (taxAmount > 0.0) {
            val taxAccountResult = accountRepository.getByCode(storeId, StandardAccountCodes.SALES_TAX_PAYABLE)
            if (taxAccountResult is Result.Error) return taxAccountResult
            (taxAccountResult as Result.Success).data
                ?: return Result.Error(
                    ValidationException(
                        "Required account '${StandardAccountCodes.SALES_TAX_PAYABLE} Sales Tax Payable' not found. Seed the Chart of Accounts first.",
                        field = "accountCode",
                        rule = "ACCOUNT_NOT_FOUND",
                    ),
                )
        } else null

        // Fetch next entry number
        val nextNumberResult = journalRepository.getNextEntryNumber(storeId)
        if (nextNumberResult is Result.Error) return nextNumberResult
        val entryNumber = (nextNumberResult as Result.Success).data

        // Build entry ID deterministically from orderId so duplicates are prevented
        val entryId = "je-sale-$orderId"

        val lines = mutableListOf<JournalEntryLine>()
        var lineOrder = 1

        // DR Cash = totalAmount
        lines.add(
            JournalEntryLine(
                id = "$entryId-line-1",
                journalEntryId = entryId,
                accountId = cashAccount.id,
                debitAmount = totalAmount,
                creditAmount = 0.0,
                lineDescription = "Cash received for order $orderId",
                lineOrder = lineOrder++,
                createdAt = now,
                accountCode = cashAccount.accountCode,
                accountName = cashAccount.accountName,
            ),
        )

        // CR Sales Revenue = subtotal
        lines.add(
            JournalEntryLine(
                id = "$entryId-line-2",
                journalEntryId = entryId,
                accountId = revenueAccount.id,
                debitAmount = 0.0,
                creditAmount = subtotal,
                lineDescription = "Sales revenue for order $orderId",
                lineOrder = lineOrder++,
                createdAt = now,
                accountCode = revenueAccount.accountCode,
                accountName = revenueAccount.accountName,
            ),
        )

        // CR Sales Tax Payable = taxAmount (if applicable)
        if (taxAmount > 0.0 && taxAccount != null) {
            lines.add(
                JournalEntryLine(
                    id = "$entryId-line-3",
                    journalEntryId = entryId,
                    accountId = taxAccount.id,
                    debitAmount = 0.0,
                    creditAmount = taxAmount,
                    lineDescription = "Sales tax collected for order $orderId",
                    lineOrder = lineOrder,
                    createdAt = now,
                    accountCode = taxAccount.accountCode,
                    accountName = taxAccount.accountName,
                ),
            )
        }

        val entry = JournalEntry(
            id = entryId,
            entryNumber = entryNumber,
            storeId = storeId,
            entryDate = entryDate,
            entryTime = now,
            description = "Sale — Order $orderId",
            referenceType = JournalReferenceType.SALE,
            referenceId = orderId,
            isPosted = true,
            createdBy = cashierId,
            createdAt = now,
            updatedAt = now,
            postedAt = now,
            lines = lines,
        )

        val saveResult = journalRepository.saveDraftEntry(entry.copy(isPosted = false))
        if (saveResult is Result.Error) return saveResult

        return journalRepository.postEntry(entryId, now)
    }
}
