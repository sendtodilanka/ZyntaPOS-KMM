package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.Account
import com.zyntasolutions.zyntapos.domain.model.AccountType
import com.zyntasolutions.zyntapos.domain.model.NormalBalance
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — AccountRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [AccountRepositoryImpl] against a real in-memory SQLite database.
 * chart_of_accounts has no external FK constraints (parent_account_id is
 * self-referential, optional). No pre-seeding required.
 *
 * Coverage:
 *  A. create → getById round-trip preserves all fields
 *  B. getAll emits only active accounts via Turbine
 *  C. getByType emits accounts of specified type via Turbine
 *  D. getByCode returns matching account
 *  E. update changes name, description, parentAccountId, isActive
 *  F. deactivate sets isActive to false (excluded from getAll)
 *  G. isAccountCodeTaken returns true for existing code
 *  H. isAccountCodeTaken returns false for new code
 *  I. seedDefaultAccounts inserts multiple accounts idempotently
 */
class AccountRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: AccountRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = AccountRepositoryImpl(db, SyncEnqueuer(db))
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private val now get() = Clock.System.now().toEpochMilliseconds()

    private fun makeAccount(
        id: String = "acc-01",
        accountCode: String = "1010",
        accountName: String = "Cash",
        accountType: AccountType = AccountType.ASSET,
        subCategory: String = "Current Assets",
        normalBalance: NormalBalance = NormalBalance.DEBIT,
        isActive: Boolean = true,
        isSystemAccount: Boolean = false,
        isHeaderAccount: Boolean = false,
        allowTransactions: Boolean = true,
        parentAccountId: String? = null,
    ) = Account(
        id = id,
        accountCode = accountCode,
        accountName = accountName,
        accountType = accountType,
        subCategory = subCategory,
        description = null,
        normalBalance = normalBalance,
        parentAccountId = parentAccountId,
        isSystemAccount = isSystemAccount,
        isActive = isActive,
        isHeaderAccount = isHeaderAccount,
        allowTransactions = allowTransactions,
        createdAt = now,
        updatedAt = now,
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - create then getById round-trip preserves all fields`() = runTest {
        val account = makeAccount(
            id = "acc-01",
            accountCode = "1010",
            accountName = "Cash",
            accountType = AccountType.ASSET,
            subCategory = "Current Assets",
            normalBalance = NormalBalance.DEBIT,
            isActive = true,
            isSystemAccount = true,
            allowTransactions = true,
        )
        val createResult = repo.create(account)
        assertIs<Result.Success<Unit>>(createResult)

        val fetchResult = repo.getById("acc-01")
        assertIs<Result.Success<Account?>>(fetchResult)
        val fetched = fetchResult.data
        assertNotNull(fetched)
        assertEquals("acc-01", fetched.id)
        assertEquals("1010", fetched.accountCode)
        assertEquals("Cash", fetched.accountName)
        assertEquals(AccountType.ASSET, fetched.accountType)
        assertEquals("Current Assets", fetched.subCategory)
        assertEquals(NormalBalance.DEBIT, fetched.normalBalance)
        assertTrue(fetched.isActive)
        assertTrue(fetched.isSystemAccount)
        assertNull(fetched.parentAccountId)
    }

    @Test
    fun `B - getAll emits only active accounts via Turbine`() = runTest {
        repo.create(makeAccount(id = "acc-01", accountCode = "1010", accountName = "Cash", isActive = true))
        repo.create(makeAccount(id = "acc-02", accountCode = "1020", accountName = "Savings", isActive = true))
        // Deactivate acc-02 directly
        repo.deactivate("acc-02", now)

        repo.getAll("store-01").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("acc-01", list.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `C - getByType emits accounts of specified type only`() = runTest {
        repo.create(makeAccount(id = "acc-asset", accountCode = "1010", accountName = "Cash", accountType = AccountType.ASSET))
        repo.create(makeAccount(id = "acc-income", accountCode = "4010", accountName = "Sales Revenue",
            accountType = AccountType.INCOME, subCategory = "Revenue", normalBalance = NormalBalance.CREDIT))
        repo.create(makeAccount(id = "acc-expense", accountCode = "5010", accountName = "Rent Expense",
            accountType = AccountType.EXPENSE, subCategory = "Operating Expenses", normalBalance = NormalBalance.DEBIT))

        repo.getByType("store-01", AccountType.ASSET).test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals(AccountType.ASSET, list.first().accountType)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `D - getByCode returns the matching account`() = runTest {
        repo.create(makeAccount(id = "acc-01", accountCode = "2010", accountName = "Accounts Payable",
            accountType = AccountType.LIABILITY, subCategory = "Current Liabilities",
            normalBalance = NormalBalance.CREDIT))

        val result = repo.getByCode("store-01", "2010")
        assertIs<Result.Success<Account?>>(result)
        assertNotNull(result.data)
        assertEquals("acc-01", result.data!!.id)
        assertEquals("Accounts Payable", result.data!!.accountName)
    }

    @Test
    fun `E - update changes account name description and isActive`() = runTest {
        repo.create(makeAccount(id = "acc-01", accountCode = "1010", accountName = "Cash"))

        val original = (repo.getById("acc-01") as Result.Success).data!!
        val updated = original.copy(accountName = "Petty Cash", isActive = false)
        val updateResult = repo.update(updated)
        assertIs<Result.Success<Unit>>(updateResult)

        // getById still returns even for inactive (getAll would exclude it)
        val fetched = (repo.getById("acc-01") as Result.Success).data
        assertNotNull(fetched)
        assertEquals("Petty Cash", fetched.accountName)
        assertFalse(fetched.isActive)
    }

    @Test
    fun `F - deactivate sets isActive to false and excludes from getAll`() = runTest {
        repo.create(makeAccount(id = "acc-01", accountCode = "1010", accountName = "Cash"))
        repo.create(makeAccount(id = "acc-02", accountCode = "1020", accountName = "Savings"))

        val deactivateResult = repo.deactivate("acc-01", now)
        assertIs<Result.Success<Unit>>(deactivateResult)

        repo.getAll("store-01").test {
            val list = awaitItem()
            // acc-01 deactivated, only acc-02 active
            assertEquals(1, list.size)
            assertEquals("acc-02", list.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `G - isAccountCodeTaken returns true for existing code`() = runTest {
        repo.create(makeAccount(id = "acc-01", accountCode = "1010", accountName = "Cash"))

        val result = repo.isAccountCodeTaken("store-01", "1010", excludeId = null)
        assertIs<Result.Success<Boolean>>(result)
        assertTrue(result.data)
    }

    @Test
    fun `H - isAccountCodeTaken returns false for new code`() = runTest {
        val result = repo.isAccountCodeTaken("store-01", "9999", excludeId = null)
        assertIs<Result.Success<Boolean>>(result)
        assertFalse(result.data)
    }

    @Test
    fun `I - seedDefaultAccounts inserts multiple accounts idempotently`() = runTest {
        val accounts = listOf(
            makeAccount(id = "acc-seed-1", accountCode = "1001", accountName = "Cash on Hand",
                accountType = AccountType.ASSET, subCategory = "Current Assets"),
            makeAccount(id = "acc-seed-2", accountCode = "2001", accountName = "Short-term Loans",
                accountType = AccountType.LIABILITY, subCategory = "Current Liabilities",
                normalBalance = NormalBalance.CREDIT),
            makeAccount(id = "acc-seed-3", accountCode = "4001", accountName = "Net Sales",
                accountType = AccountType.INCOME, subCategory = "Revenue",
                normalBalance = NormalBalance.CREDIT),
        )

        val firstSeed = repo.seedDefaultAccounts(accounts)
        assertIs<Result.Success<Unit>>(firstSeed)

        // Second call must be idempotent (INSERT OR IGNORE)
        val secondSeed = repo.seedDefaultAccounts(accounts)
        assertIs<Result.Success<Unit>>(secondSeed)

        repo.getAll("store-01").test {
            val list = awaitItem()
            assertEquals(3, list.size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
