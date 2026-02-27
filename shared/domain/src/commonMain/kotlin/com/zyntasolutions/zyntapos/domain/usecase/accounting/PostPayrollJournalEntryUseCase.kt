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
 * Auto-posts a balanced journal entry for a payroll payment.
 *
 * Standard double-entry pattern:
 *   DR 6010 Salaries Expense    = grossPay
 *   CR 1010 Cash                = netPay
 *   CR 2200 Accrued Liabilities = deductions (tax withholding etc.)
 *
 * referenceType = PAYROLL, referenceId = payrollId
 */
class PostPayrollJournalEntryUseCase(
    private val journalRepository: JournalRepository,
    private val accountRepository: AccountRepository,
    private val periodRepository: AccountingPeriodRepository,
) {
    @Suppress("LongParameterList")
    suspend fun execute(
        storeId: String,
        payrollId: String,
        grossPay: Double,
        netPay: Double,
        deductions: Double,
        createdBy: String,
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
                    "No open accounting period found for payroll date $entryDate.",
                    field = "entryDate",
                    rule = "PERIOD_NOT_OPEN",
                ),
            )
        }

        // Lookup DR account: Salaries Expense (6010)
        val salariesAccountResult = accountRepository.getByCode(storeId, "6010")
        if (salariesAccountResult is Result.Error) return salariesAccountResult
        val salariesAccount = (salariesAccountResult as Result.Success).data
            ?: return Result.Error(
                ValidationException(
                    "Required account '6010 Salaries Expense' not found. Seed the Chart of Accounts first.",
                    field = "accountCode",
                    rule = "ACCOUNT_NOT_FOUND",
                ),
            )

        // Lookup CR account: Cash (1010)
        val cashAccountResult = accountRepository.getByCode(storeId, "1010")
        if (cashAccountResult is Result.Error) return cashAccountResult
        val cashAccount = (cashAccountResult as Result.Success).data
            ?: return Result.Error(
                ValidationException(
                    "Required account '1010 Cash' not found. Seed the Chart of Accounts first.",
                    field = "accountCode",
                    rule = "ACCOUNT_NOT_FOUND",
                ),
            )

        // Lookup CR account: Accrued Liabilities (2200) — only if deductions > 0
        val accruedAccount = if (deductions > 0.0) {
            val accruedAccountResult = accountRepository.getByCode(storeId, "2200")
            if (accruedAccountResult is Result.Error) return accruedAccountResult
            (accruedAccountResult as Result.Success).data
                ?: return Result.Error(
                    ValidationException(
                        "Required account '2200 Accrued Liabilities' not found. Seed the Chart of Accounts first.",
                        field = "accountCode",
                        rule = "ACCOUNT_NOT_FOUND",
                    ),
                )
        } else null

        // Fetch next entry number
        val nextNumberResult = journalRepository.getNextEntryNumber(storeId)
        if (nextNumberResult is Result.Error) return nextNumberResult
        val entryNumber = (nextNumberResult as Result.Success).data

        val entryId = "je-payroll-$payrollId"

        val lines = mutableListOf<JournalEntryLine>()

        // DR Salaries Expense = grossPay
        lines.add(
            JournalEntryLine(
                id = "$entryId-line-1",
                journalEntryId = entryId,
                accountId = salariesAccount.id,
                debitAmount = grossPay,
                creditAmount = 0.0,
                lineDescription = "Gross payroll for payroll run $payrollId",
                lineOrder = 1,
                createdAt = now,
                accountCode = salariesAccount.accountCode,
                accountName = salariesAccount.accountName,
            ),
        )

        // CR Cash = netPay
        lines.add(
            JournalEntryLine(
                id = "$entryId-line-2",
                journalEntryId = entryId,
                accountId = cashAccount.id,
                debitAmount = 0.0,
                creditAmount = netPay,
                lineDescription = "Net pay disbursed for payroll run $payrollId",
                lineOrder = 2,
                createdAt = now,
                accountCode = cashAccount.accountCode,
                accountName = cashAccount.accountName,
            ),
        )

        // CR Accrued Liabilities = deductions (if any)
        if (deductions > 0.0 && accruedAccount != null) {
            lines.add(
                JournalEntryLine(
                    id = "$entryId-line-3",
                    journalEntryId = entryId,
                    accountId = accruedAccount.id,
                    debitAmount = 0.0,
                    creditAmount = deductions,
                    lineDescription = "Tax withholding and deductions for payroll run $payrollId",
                    lineOrder = 3,
                    createdAt = now,
                    accountCode = accruedAccount.accountCode,
                    accountName = accruedAccount.accountName,
                ),
            )
        }

        val entry = JournalEntry(
            id = entryId,
            entryNumber = entryNumber,
            storeId = storeId,
            entryDate = entryDate,
            entryTime = now,
            description = "Payroll run — $payrollId",
            referenceType = JournalReferenceType.PAYROLL,
            referenceId = payrollId,
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
