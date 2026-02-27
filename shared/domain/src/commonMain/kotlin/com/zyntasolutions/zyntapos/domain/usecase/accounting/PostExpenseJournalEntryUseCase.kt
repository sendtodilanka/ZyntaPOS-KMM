package com.zyntasolutions.zyntapos.domain.usecase.accounting

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.JournalEntry
import com.zyntasolutions.zyntapos.domain.model.JournalEntryLine
import com.zyntasolutions.zyntapos.domain.model.JournalReferenceType
import com.zyntasolutions.zyntapos.domain.model.PeriodStatus
import com.zyntasolutions.zyntapos.domain.repository.AccountRepository
import com.zyntasolutions.zyntapos.domain.repository.AccountingPeriodRepository
import com.zyntasolutions.zyntapos.domain.repository.JournalRepository

/**
 * Auto-posts a balanced journal entry for an approved expense.
 *
 * Standard double-entry pattern:
 *   DR [expenseAccountCode] Expense Account = amount
 *   CR [paymentAccountCode] Cash / AP       = amount
 *
 * referenceType = EXPENSE, referenceId = expenseId
 */
class PostExpenseJournalEntryUseCase(
    private val journalRepository: JournalRepository,
    private val accountRepository: AccountRepository,
    private val periodRepository: AccountingPeriodRepository,
) {
    @Suppress("LongParameterList")
    suspend fun execute(
        storeId: String,
        expenseId: String,
        amount: Double,
        expenseAccountCode: String,
        paymentAccountCode: String,
        createdBy: String,
        description: String,
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
                    "No open accounting period found for expense date $entryDate.",
                    field = "entryDate",
                    rule = "PERIOD_NOT_OPEN",
                ),
            )
        }

        // Lookup expense account
        val expenseAccountResult = accountRepository.getByCode(storeId, expenseAccountCode)
        if (expenseAccountResult is Result.Error) return expenseAccountResult
        val expenseAccount = (expenseAccountResult as Result.Success).data
            ?: return Result.Error(
                ValidationException(
                    "Expense account '$expenseAccountCode' not found.",
                    field = "expenseAccountCode",
                    rule = "ACCOUNT_NOT_FOUND",
                ),
            )

        // Lookup payment account (Cash or AP)
        val paymentAccountResult = accountRepository.getByCode(storeId, paymentAccountCode)
        if (paymentAccountResult is Result.Error) return paymentAccountResult
        val paymentAccount = (paymentAccountResult as Result.Success).data
            ?: return Result.Error(
                ValidationException(
                    "Payment account '$paymentAccountCode' not found.",
                    field = "paymentAccountCode",
                    rule = "ACCOUNT_NOT_FOUND",
                ),
            )

        // Fetch next entry number
        val nextNumberResult = journalRepository.getNextEntryNumber(storeId)
        if (nextNumberResult is Result.Error) return nextNumberResult
        val entryNumber = (nextNumberResult as Result.Success).data

        val entryId = "je-expense-$expenseId"

        val lines = listOf(
            // DR Expense Account = amount
            JournalEntryLine(
                id = "$entryId-line-1",
                journalEntryId = entryId,
                accountId = expenseAccount.id,
                debitAmount = amount,
                creditAmount = 0.0,
                lineDescription = description,
                lineOrder = 1,
                createdAt = now,
                accountCode = expenseAccount.accountCode,
                accountName = expenseAccount.accountName,
            ),
            // CR Cash / AP = amount
            JournalEntryLine(
                id = "$entryId-line-2",
                journalEntryId = entryId,
                accountId = paymentAccount.id,
                debitAmount = 0.0,
                creditAmount = amount,
                lineDescription = "Payment for expense $expenseId",
                lineOrder = 2,
                createdAt = now,
                accountCode = paymentAccount.accountCode,
                accountName = paymentAccount.accountName,
            ),
        )

        val entry = JournalEntry(
            id = entryId,
            entryNumber = entryNumber,
            storeId = storeId,
            entryDate = entryDate,
            entryTime = now,
            description = description,
            referenceType = JournalReferenceType.EXPENSE,
            referenceId = expenseId,
            isPosted = false,
            createdBy = createdBy,
            createdAt = now,
            updatedAt = now,
            lines = lines,
        )

        val saveResult = journalRepository.saveDraftEntry(entry)
        if (saveResult is Result.Error) return saveResult

        return journalRepository.postEntry(entryId, now)
    }
}
