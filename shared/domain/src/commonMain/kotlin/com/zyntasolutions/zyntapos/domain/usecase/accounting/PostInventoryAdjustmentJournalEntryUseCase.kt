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
import kotlin.math.abs

/**
 * Auto-posts a balanced journal entry for an inventory adjustment.
 *
 * If adjustmentValue > 0 (stock increase):
 *   DR 1200 Inventory   = adjustmentValue
 *   CR 5010 COGS        = adjustmentValue
 *
 * If adjustmentValue < 0 (stock decrease / shrinkage):
 *   DR 5010 COGS        = abs(adjustmentValue)
 *   CR 1200 Inventory   = abs(adjustmentValue)
 *
 * referenceType = STOCK_ADJUST, referenceId = adjustmentId
 */
class PostInventoryAdjustmentJournalEntryUseCase(
    private val journalRepository: JournalRepository,
    private val accountRepository: AccountRepository,
    private val periodRepository: AccountingPeriodRepository,
) {
    @Suppress("LongParameterList")
    suspend fun execute(
        storeId: String,
        adjustmentId: String,
        adjustmentValue: Double,
        createdBy: String,
        description: String,
        entryDate: String,
        now: Long,
    ): Result<Unit> {
        if (adjustmentValue == 0.0) {
            return Result.Error(
                ValidationException(
                    "Adjustment value must be non-zero.",
                    field = "adjustmentValue",
                    rule = "ZERO_AMOUNT",
                ),
            )
        }

        // Validate the accounting period is open
        val periodResult = periodRepository.getPeriodForDate(storeId, entryDate)
        if (periodResult is Result.Error) return periodResult
        val period = (periodResult as Result.Success).data
        if (period == null || period.status != PeriodStatus.OPEN) {
            return Result.Error(
                ValidationException(
                    "No open accounting period found for adjustment date $entryDate.",
                    field = "entryDate",
                    rule = "PERIOD_NOT_OPEN",
                ),
            )
        }

        // Lookup Inventory account (1200)
        val inventoryAccountResult = accountRepository.getByCode(storeId, "1200")
        if (inventoryAccountResult is Result.Error) return inventoryAccountResult
        val inventoryAccount = (inventoryAccountResult as Result.Success).data
            ?: return Result.Error(
                ValidationException(
                    "Required account '1200 Inventory' not found. Seed the Chart of Accounts first.",
                    field = "accountCode",
                    rule = "ACCOUNT_NOT_FOUND",
                ),
            )

        // Lookup COGS account (5010)
        val cogsAccountResult = accountRepository.getByCode(storeId, "5010")
        if (cogsAccountResult is Result.Error) return cogsAccountResult
        val cogsAccount = (cogsAccountResult as Result.Success).data
            ?: return Result.Error(
                ValidationException(
                    "Required account '5010 Cost of Goods Sold' not found. Seed the Chart of Accounts first.",
                    field = "accountCode",
                    rule = "ACCOUNT_NOT_FOUND",
                ),
            )

        // Fetch next entry number
        val nextNumberResult = journalRepository.getNextEntryNumber(storeId)
        if (nextNumberResult is Result.Error) return nextNumberResult
        val entryNumber = (nextNumberResult as Result.Success).data

        val entryId = "je-stock-adj-$adjustmentId"
        val absoluteValue = abs(adjustmentValue)

        val lines: List<JournalEntryLine> = if (adjustmentValue > 0.0) {
            // Stock increase: DR Inventory, CR COGS
            listOf(
                JournalEntryLine(
                    id = "$entryId-line-1",
                    journalEntryId = entryId,
                    accountId = inventoryAccount.id,
                    debitAmount = absoluteValue,
                    creditAmount = 0.0,
                    lineDescription = "Stock increase — $description",
                    lineOrder = 1,
                    createdAt = now,
                    accountCode = inventoryAccount.accountCode,
                    accountName = inventoryAccount.accountName,
                ),
                JournalEntryLine(
                    id = "$entryId-line-2",
                    journalEntryId = entryId,
                    accountId = cogsAccount.id,
                    debitAmount = 0.0,
                    creditAmount = absoluteValue,
                    lineDescription = "COGS offset for stock increase — $description",
                    lineOrder = 2,
                    createdAt = now,
                    accountCode = cogsAccount.accountCode,
                    accountName = cogsAccount.accountName,
                ),
            )
        } else {
            // Stock decrease / shrinkage: DR COGS, CR Inventory
            listOf(
                JournalEntryLine(
                    id = "$entryId-line-1",
                    journalEntryId = entryId,
                    accountId = cogsAccount.id,
                    debitAmount = absoluteValue,
                    creditAmount = 0.0,
                    lineDescription = "Stock shrinkage / decrease — $description",
                    lineOrder = 1,
                    createdAt = now,
                    accountCode = cogsAccount.accountCode,
                    accountName = cogsAccount.accountName,
                ),
                JournalEntryLine(
                    id = "$entryId-line-2",
                    journalEntryId = entryId,
                    accountId = inventoryAccount.id,
                    debitAmount = 0.0,
                    creditAmount = absoluteValue,
                    lineDescription = "Inventory reduction — $description",
                    lineOrder = 2,
                    createdAt = now,
                    accountCode = inventoryAccount.accountCode,
                    accountName = inventoryAccount.accountName,
                ),
            )
        }

        val entry = JournalEntry(
            id = entryId,
            entryNumber = entryNumber,
            storeId = storeId,
            entryDate = entryDate,
            entryTime = now,
            description = "Inventory adjustment — $description",
            referenceType = JournalReferenceType.STOCK_ADJUST,
            referenceId = adjustmentId,
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
