package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.CustomerWallet
import com.zyntasolutions.zyntapos.domain.model.WalletTransaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — CustomerWalletRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [CustomerWalletRepositoryImpl] against a real in-memory SQLite database.
 * Requires a customer seeded to satisfy the customer_id FK constraint.
 *
 * Coverage:
 *  A. getOrCreate creates a new wallet with zero balance for unknown customer
 *  B. getOrCreate returns existing wallet (idempotent)
 *  C. observeWallet emits null for customer without wallet via Turbine
 *  D. observeWallet emits wallet after creation
 *  E. credit increases balance and records transaction
 *  F. debit decreases balance and records transaction
 *  G. debit fails with insufficient balance error
 *  H. getTransactions returns all transactions for a wallet
 */
class CustomerWalletRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: CustomerWalletRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = CustomerWalletRepositoryImpl(db, SyncEnqueuer(db))

        val now = Clock.System.now().toEpochMilliseconds()

        // Seed customers required by customer_wallets FK
        db.customersQueries.insertCustomer(
            id = "cust-01", name = "Alice Smith", phone = null, email = null, address = null,
            group_id = null, loyalty_points = 0L, notes = null, is_active = 1L,
            credit_limit = 0.0, credit_enabled = 0L, gender = null, birthday = null,
            is_walk_in = 0L, store_id = "store-01", created_at = now, updated_at = now,
            sync_status = "PENDING",
        )
        db.customersQueries.insertCustomer(
            id = "cust-02", name = "Bob Jones", phone = null, email = null, address = null,
            group_id = null, loyalty_points = 0L, notes = null, is_active = 1L,
            credit_limit = 0.0, credit_enabled = 0L, gender = null, birthday = null,
            is_walk_in = 0L, store_id = "store-01", created_at = now, updated_at = now,
            sync_status = "PENDING",
        )
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - getOrCreate creates wallet with zero balance for new customer`() = runTest {
        val result = repo.getOrCreate("cust-01")
        assertIs<Result.Success<CustomerWallet>>(result)
        val wallet = result.data
        assertEquals("cust-01", wallet.customerId)
        assertEquals(0.0, wallet.balance)
        assertNotNull(wallet.id)
    }

    @Test
    fun `B - getOrCreate returns same wallet on subsequent calls (idempotent)`() = runTest {
        val first = (repo.getOrCreate("cust-01") as Result.Success).data
        val second = (repo.getOrCreate("cust-01") as Result.Success).data

        assertEquals(first.id, second.id)
        assertEquals(first.customerId, second.customerId)
    }

    @Test
    fun `C - observeWallet emits null for customer with no wallet`() = runTest {
        repo.observeWallet("cust-01").test {
            val emission = awaitItem()
            assertNull(emission)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `D - observeWallet emits wallet after creation`() = runTest {
        repo.getOrCreate("cust-01")

        repo.observeWallet("cust-01").test {
            val wallet = awaitItem()
            assertNotNull(wallet)
            assertEquals("cust-01", wallet!!.customerId)
            assertEquals(0.0, wallet.balance)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `E - credit increases balance and records a CREDIT transaction`() = runTest {
        val walletId = (repo.getOrCreate("cust-01") as Result.Success).data.id

        val creditResult = repo.credit(
            walletId = walletId,
            amount = 500.0,
            referenceType = "MANUAL",
            referenceId = "ref-01",
            note = "Top-up",
        )
        assertIs<Result.Success<Unit>>(creditResult)

        repo.observeWallet("cust-01").test {
            val wallet = awaitItem()
            assertNotNull(wallet)
            assertEquals(500.0, wallet!!.balance)
            cancelAndIgnoreRemainingEvents()
        }

        repo.getTransactions(walletId).test {
            val txns = awaitItem()
            assertEquals(1, txns.size)
            val txn = txns.first()
            assertEquals(WalletTransaction.TransactionType.CREDIT, txn.type)
            assertEquals(500.0, txn.amount)
            assertEquals(500.0, txn.balanceAfter)
            assertEquals("MANUAL", txn.referenceType)
            assertEquals("ref-01", txn.referenceId)
            assertEquals("Top-up", txn.note)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `F - debit decreases balance and records a DEBIT transaction`() = runTest {
        val walletId = (repo.getOrCreate("cust-01") as Result.Success).data.id
        repo.credit(walletId, 1000.0, null, null, null)

        val debitResult = repo.debit(
            walletId = walletId,
            amount = 300.0,
            referenceType = "ORDER",
            referenceId = "ord-01",
            note = "Purchase",
        )
        assertIs<Result.Success<Unit>>(debitResult)

        repo.observeWallet("cust-01").test {
            val wallet = awaitItem()
            assertEquals(700.0, wallet!!.balance)
            cancelAndIgnoreRemainingEvents()
        }

        repo.getTransactions(walletId).test {
            val txns = awaitItem()
            assertEquals(2, txns.size)
            val debitTxn = txns.first { it.type == WalletTransaction.TransactionType.DEBIT }
            assertEquals(300.0, debitTxn.amount)
            assertEquals(700.0, debitTxn.balanceAfter)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `G - debit fails when balance is insufficient`() = runTest {
        val walletId = (repo.getOrCreate("cust-01") as Result.Success).data.id
        repo.credit(walletId, 100.0, null, null, null)

        val result = repo.debit(walletId, 500.0, null, null, null)
        assertIs<Result.Error>(result)
        assertNotNull(result.exception)
        assertTrue(result.exception.message?.contains("Insufficient", ignoreCase = true) == true
            || result.exception.message?.contains("balance", ignoreCase = true) == true)
    }

    @Test
    fun `H - getTransactions returns all transactions for a wallet`() = runTest {
        val walletId = (repo.getOrCreate("cust-01") as Result.Success).data.id

        repo.credit(walletId, 100.0, null, null, note = "First")
        repo.credit(walletId, 200.0, null, null, note = "Second")
        repo.credit(walletId, 50.0, null, null, note = "Third")

        repo.getTransactions(walletId).test {
            val txns = awaitItem()
            assertEquals(3, txns.size)
            // All transactions belong to this wallet and are CREDITs
            assertTrue(txns.all { it.walletId == walletId })
            assertTrue(txns.all { it.type == WalletTransaction.TransactionType.CREDIT })
            // Exactly one transaction records the final cumulative balance of 350.0
            assertEquals(1, txns.count { it.balanceAfter == 350.0 })
            cancelAndIgnoreRemainingEvents()
        }
    }
}
