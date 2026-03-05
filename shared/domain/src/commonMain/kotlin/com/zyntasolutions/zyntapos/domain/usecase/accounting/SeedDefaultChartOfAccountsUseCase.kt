package com.zyntasolutions.zyntapos.domain.usecase.accounting

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Account
import com.zyntasolutions.zyntapos.domain.model.AccountType
import com.zyntasolutions.zyntapos.domain.model.NormalBalance
import com.zyntasolutions.zyntapos.domain.repository.AccountRepository

/**
 * Seeds the default Chart of Accounts for a new store.
 *
 * Uses INSERT OR IGNORE semantics via [AccountRepository.seedDefaultAccounts] so it is safe
 * to call multiple times without creating duplicates.
 *
 * Account IDs are deterministic ("sys-acct-{accountCode}") so the operation is idempotent.
 */
class SeedDefaultChartOfAccountsUseCase(
    private val accountRepository: AccountRepository,
) {
    suspend fun execute(_storeId: String, now: Long): Result<Unit> {
        val accounts = buildDefaultAccounts(_storeId, now)
        return accountRepository.seedDefaultAccounts(accounts)
    }

    @Suppress("LongMethod")
    private fun buildDefaultAccounts(_storeId: String, now: Long): List<Account> {
        fun account(
            code: String,
            name: String,
            type: AccountType,
            subCategory: String,
            normalBalance: NormalBalance,
            description: String? = null,
        ) = Account(
            id = "sys-acct-$code",
            accountCode = code,
            accountName = name,
            accountType = type,
            subCategory = subCategory,
            description = description,
            normalBalance = normalBalance,
            parentAccountId = null,
            isSystemAccount = true,
            isActive = true,
            isHeaderAccount = false,
            allowTransactions = true,
            createdAt = now,
            updatedAt = now,
        )

        return listOf(
            // ── ASSETS ──────────────────────────────────────────────────────
            account("1010", "Cash", AccountType.ASSET, "Current Assets", NormalBalance.DEBIT,
                "Physical cash on hand at the store."),
            account("1020", "Bank Account", AccountType.ASSET, "Current Assets", NormalBalance.DEBIT,
                "Business checking or savings account."),
            account("1030", "Petty Cash", AccountType.ASSET, "Current Assets", NormalBalance.DEBIT,
                "Small cash fund for minor day-to-day expenditures."),
            account("1100", "Accounts Receivable", AccountType.ASSET, "Current Assets", NormalBalance.DEBIT,
                "Amounts owed by customers for credit sales."),
            account("1110", "Allowance for Doubtful Accounts", AccountType.ASSET, "Current Assets", NormalBalance.CREDIT,
                "Contra-asset estimate of uncollectible receivables."),
            account("1200", "Inventory", AccountType.ASSET, "Current Assets", NormalBalance.DEBIT,
                "Goods held for sale."),
            account("1300", "Prepaid Expenses", AccountType.ASSET, "Current Assets", NormalBalance.DEBIT,
                "Expenses paid in advance (e.g. insurance, rent)."),
            account("1400", "Other Current Assets", AccountType.ASSET, "Current Assets", NormalBalance.DEBIT,
                "Miscellaneous short-term assets."),
            account("1500", "Property and Equipment", AccountType.ASSET, "Non-Current Assets", NormalBalance.DEBIT,
                "Tangible fixed assets (furniture, hardware, vehicles)."),
            account("1510", "Accumulated Depreciation", AccountType.ASSET, "Non-Current Assets", NormalBalance.CREDIT,
                "Contra-asset: cumulative depreciation on fixed assets."),
            account("1600", "Intangible Assets", AccountType.ASSET, "Non-Current Assets", NormalBalance.DEBIT,
                "Licenses, goodwill, and other non-physical assets."),

            // ── LIABILITIES ─────────────────────────────────────────────────
            account("2010", "Accounts Payable", AccountType.LIABILITY, "Current Liabilities", NormalBalance.CREDIT,
                "Amounts owed to suppliers for purchases on credit."),
            account("2100", "Sales Tax Payable", AccountType.LIABILITY, "Current Liabilities", NormalBalance.CREDIT,
                "Collected sales tax remittable to the government."),
            account("2110", "VAT Payable", AccountType.LIABILITY, "Current Liabilities", NormalBalance.CREDIT,
                "Value-Added Tax collected and due for remittance."),
            account("2200", "Accrued Liabilities", AccountType.LIABILITY, "Current Liabilities", NormalBalance.CREDIT,
                "Expenses incurred but not yet paid (wages, utilities)."),
            account("2210", "Payroll Liabilities", AccountType.LIABILITY, "Current Liabilities", NormalBalance.CREDIT,
                "Wages earned by employees not yet disbursed."),
            account("2300", "Customer Deposits", AccountType.LIABILITY, "Current Liabilities", NormalBalance.CREDIT,
                "Advance payments received from customers."),
            account("2400", "Short-Term Loans", AccountType.LIABILITY, "Current Liabilities", NormalBalance.CREDIT,
                "Bank loans or credit facilities due within one year."),
            account("2500", "Long-Term Debt", AccountType.LIABILITY, "Non-Current Liabilities", NormalBalance.CREDIT,
                "Obligations due beyond one year."),

            // ── EQUITY ──────────────────────────────────────────────────────
            account("3010", "Owner's Capital", AccountType.EQUITY, "Equity", NormalBalance.CREDIT,
                "Initial and ongoing capital contributions by the owner."),
            account("3020", "Owner's Drawings", AccountType.EQUITY, "Equity", NormalBalance.DEBIT,
                "Withdrawals made by the owner."),
            account("3900", "Retained Earnings", AccountType.EQUITY, "Equity", NormalBalance.CREDIT,
                "Cumulative net profit/loss retained in the business."),

            // ── INCOME ──────────────────────────────────────────────────────
            account("4010", "Sales Revenue", AccountType.INCOME, "Revenue", NormalBalance.CREDIT,
                "Income from the sale of goods."),
            account("4020", "Service Revenue", AccountType.INCOME, "Revenue", NormalBalance.CREDIT,
                "Income from providing services."),
            account("4030", "Discount Given", AccountType.INCOME, "Revenue", NormalBalance.DEBIT,
                "Contra-revenue: discounts granted to customers."),
            account("4040", "Sales Returns", AccountType.INCOME, "Revenue", NormalBalance.DEBIT,
                "Contra-revenue: returned goods credited to customers."),
            account("4900", "Other Income", AccountType.INCOME, "Other Income", NormalBalance.CREDIT,
                "Miscellaneous income not from core operations."),

            // ── COGS ────────────────────────────────────────────────────────
            account("5010", "Cost of Goods Sold", AccountType.COGS, "COGS", NormalBalance.DEBIT,
                "Direct cost of merchandise sold."),
            account("5020", "Purchase Returns", AccountType.COGS, "COGS", NormalBalance.CREDIT,
                "Contra-COGS: goods returned to suppliers."),
            account("5030", "Freight In", AccountType.COGS, "COGS", NormalBalance.DEBIT,
                "Shipping costs to receive inventory."),

            // ── EXPENSES ────────────────────────────────────────────────────
            account("6010", "Salaries Expense", AccountType.EXPENSE, "Operating Expenses", NormalBalance.DEBIT,
                "Gross wages and salaries paid to employees."),
            account("6020", "Rent Expense", AccountType.EXPENSE, "Operating Expenses", NormalBalance.DEBIT,
                "Monthly rent for business premises."),
            account("6030", "Utilities Expense", AccountType.EXPENSE, "Operating Expenses", NormalBalance.DEBIT,
                "Electricity, water, internet, and telephone costs."),
            account("6040", "Depreciation Expense", AccountType.EXPENSE, "Operating Expenses", NormalBalance.DEBIT,
                "Periodic allocation of fixed asset costs."),
            account("6050", "Office Supplies", AccountType.EXPENSE, "Operating Expenses", NormalBalance.DEBIT,
                "Consumable office and stationery supplies."),
            account("6060", "Marketing Expense", AccountType.EXPENSE, "Operating Expenses", NormalBalance.DEBIT,
                "Advertising, promotions, and marketing costs."),
            account("6070", "Insurance Expense", AccountType.EXPENSE, "Operating Expenses", NormalBalance.DEBIT,
                "Business insurance premiums."),
            account("6080", "Repairs and Maintenance", AccountType.EXPENSE, "Operating Expenses", NormalBalance.DEBIT,
                "Costs to repair and maintain equipment and premises."),
            account("6090", "Bank Charges", AccountType.EXPENSE, "Operating Expenses", NormalBalance.DEBIT,
                "Bank fees, transaction charges, and merchant fees."),
            account("6100", "Professional Fees", AccountType.EXPENSE, "Operating Expenses", NormalBalance.DEBIT,
                "Accounting, legal, and consulting fees."),
            account("6900", "Miscellaneous Expense", AccountType.EXPENSE, "Other Expenses", NormalBalance.DEBIT,
                "Other expenses not classified elsewhere."),
        )
    }
}
