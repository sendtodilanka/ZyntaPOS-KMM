package com.zyntasolutions.zyntapos.domain.usecase.crm

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Customer
import com.zyntasolutions.zyntapos.domain.model.CustomerWallet
import com.zyntasolutions.zyntapos.domain.model.LoyaltyTier
import com.zyntasolutions.zyntapos.domain.model.RewardPoints
import com.zyntasolutions.zyntapos.domain.model.WalletTransaction
import com.zyntasolutions.zyntapos.domain.repository.CustomerRepository
import com.zyntasolutions.zyntapos.domain.repository.CustomerWalletRepository
import com.zyntasolutions.zyntapos.domain.repository.LoyaltyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for MergeCustomersUseCase (C4.3).
 *
 * Validates:
 * - Successful merge combines loyalty points
 * - Wallet balance transfer from source to target
 * - Contact info gap-filling (email, address, gender, birthday)
 * - Source customer soft-deleted after merge
 * - Self-merge rejected
 * - Non-existent customer handled gracefully
 */
class MergeCustomersUseCaseTest {

    private fun targetCustomer(
        points: Int = 100,
        email: String? = "target@test.com",
        address: String? = null,
        gender: String? = null,
        birthday: String? = null,
        creditLimit: Double = 50.0,
    ) = Customer(
        id = "target-001",
        name = "Target Customer",
        phone = "0771111111",
        email = email,
        address = address,
        loyaltyPoints = points,
        gender = gender,
        birthday = birthday,
        creditLimit = creditLimit,
        storeId = "store-A",
    )

    private fun sourceCustomer(
        points: Int = 50,
        email: String? = "source@test.com",
        address: String? = "123 Source St",
        gender: String? = "M",
        birthday: String? = "1990-05-15",
        creditLimit: Double = 200.0,
    ) = Customer(
        id = "source-001",
        name = "Source Customer",
        phone = "0772222222",
        email = email,
        address = address,
        loyaltyPoints = points,
        gender = gender,
        birthday = birthday,
        creditLimit = creditLimit,
        storeId = "store-B",
    )

    @Test
    fun `merge combines loyalty points`() = runTest {
        val target = targetCustomer(points = 100)
        val source = sourceCustomer(points = 75)
        val repo = FakeCustomerRepo(listOf(target, source))
        val useCase = MergeCustomersUseCase(repo, FakeWalletRepo(), FakeLoyaltyRepo())

        val result = useCase(target.id, source.id)

        assertIs<Result.Success<MergeResult>>(result)
        assertEquals(75, result.data.pointsMerged)
        // Target should have 100 + 75 = 175 points
        val updated = repo.customers[target.id]!!
        assertEquals(175, updated.loyaltyPoints)
    }

    @Test
    fun `merge transfers wallet balance`() = runTest {
        val target = targetCustomer()
        val source = sourceCustomer()
        val walletRepo = FakeWalletRepo(
            wallets = mutableMapOf(
                source.id to CustomerWallet(id = "w-source", customerId = source.id, balance = 500.0),
                target.id to CustomerWallet(id = "w-target", customerId = target.id, balance = 100.0),
            )
        )
        val useCase = MergeCustomersUseCase(FakeCustomerRepo(listOf(target, source)), walletRepo, FakeLoyaltyRepo())

        val result = useCase(target.id, source.id)

        assertIs<Result.Success<MergeResult>>(result)
        assertEquals(500.0, result.data.walletBalanceTransferred)
    }

    @Test
    fun `merge fills in missing contact info from source`() = runTest {
        val target = targetCustomer(email = "target@test.com", address = null, gender = null, birthday = null)
        val source = sourceCustomer(email = "source@test.com", address = "456 Ave", gender = "F", birthday = "1985-01-01")
        val repo = FakeCustomerRepo(listOf(target, source))
        val useCase = MergeCustomersUseCase(repo, FakeWalletRepo(), FakeLoyaltyRepo())

        val result = useCase(target.id, source.id)

        assertIs<Result.Success<MergeResult>>(result)
        val updated = repo.customers[target.id]!!
        assertEquals("target@test.com", updated.email) // target email kept
        assertEquals("456 Ave", updated.address) // filled from source
        assertEquals("F", updated.gender) // filled from source
        assertEquals("1985-01-01", updated.birthday) // filled from source
        assertTrue(result.data.contactInfoFilled.containsAll(listOf("address", "gender", "birthday")))
    }

    @Test
    fun `merge keeps higher credit limit`() = runTest {
        val target = targetCustomer(creditLimit = 50.0)
        val source = sourceCustomer(creditLimit = 200.0)
        val repo = FakeCustomerRepo(listOf(target, source))
        val useCase = MergeCustomersUseCase(repo, FakeWalletRepo(), FakeLoyaltyRepo())

        val result = useCase(target.id, source.id)

        assertIs<Result.Success<MergeResult>>(result)
        val updated = repo.customers[target.id]!!
        assertEquals(200.0, updated.creditLimit)
    }

    @Test
    fun `merge makes target customer global`() = runTest {
        val target = targetCustomer()
        val source = sourceCustomer()
        val repo = FakeCustomerRepo(listOf(target, source))
        val useCase = MergeCustomersUseCase(repo, FakeWalletRepo(), FakeLoyaltyRepo())

        useCase(target.id, source.id)

        val updated = repo.customers[target.id]!!
        assertNull(updated.storeId)
    }

    @Test
    fun `merge soft-deletes source customer`() = runTest {
        val target = targetCustomer()
        val source = sourceCustomer()
        val repo = FakeCustomerRepo(listOf(target, source))
        val useCase = MergeCustomersUseCase(repo, FakeWalletRepo(), FakeLoyaltyRepo())

        useCase(target.id, source.id)

        assertTrue(repo.deletedIds.contains(source.id))
    }

    @Test
    fun `self-merge returns validation error`() = runTest {
        val customer = targetCustomer()
        val repo = FakeCustomerRepo(listOf(customer))
        val useCase = MergeCustomersUseCase(repo, FakeWalletRepo(), FakeLoyaltyRepo())

        val result = useCase(customer.id, customer.id)

        assertIs<Result.Error>(result)
        assertTrue(result.exception.message!!.contains("itself"))
    }

    @Test
    fun `merge with non-existent target returns error`() = runTest {
        val source = sourceCustomer()
        val repo = FakeCustomerRepo(listOf(source))
        val useCase = MergeCustomersUseCase(repo, FakeWalletRepo(), FakeLoyaltyRepo())

        val result = useCase("non-existent", source.id)

        assertIs<Result.Error>(result)
    }

    @Test
    fun `merge with non-existent source returns error`() = runTest {
        val target = targetCustomer()
        val repo = FakeCustomerRepo(listOf(target))
        val useCase = MergeCustomersUseCase(repo, FakeWalletRepo(), FakeLoyaltyRepo())

        val result = useCase(target.id, "non-existent")

        assertIs<Result.Error>(result)
    }

    @Test
    fun `merge with zero wallet balance skips wallet transfer`() = runTest {
        val target = targetCustomer()
        val source = sourceCustomer()
        val walletRepo = FakeWalletRepo(
            wallets = mutableMapOf(
                source.id to CustomerWallet(id = "w-source", customerId = source.id, balance = 0.0),
            )
        )
        val useCase = MergeCustomersUseCase(FakeCustomerRepo(listOf(target, source)), walletRepo, FakeLoyaltyRepo())

        val result = useCase(target.id, source.id)

        assertIs<Result.Success<MergeResult>>(result)
        assertEquals(0.0, result.data.walletBalanceTransferred)
    }

    @Test
    fun `merge notes concatenation`() = runTest {
        val target = targetCustomer().copy(notes = "VIP customer")
        val source = sourceCustomer().copy(notes = "Prefers delivery")
        val repo = FakeCustomerRepo(listOf(target, source))
        val useCase = MergeCustomersUseCase(repo, FakeWalletRepo(), FakeLoyaltyRepo())

        useCase(target.id, source.id)

        val updated = repo.customers[target.id]!!
        assertTrue(updated.notes!!.contains("VIP customer"))
        assertTrue(updated.notes!!.contains("Prefers delivery"))
    }

    @Test
    fun `merge enables credit if either customer had it enabled`() = runTest {
        val target = targetCustomer().copy(creditEnabled = false)
        val source = sourceCustomer().copy(creditEnabled = true)
        val repo = FakeCustomerRepo(listOf(target, source))
        val useCase = MergeCustomersUseCase(repo, FakeWalletRepo(), FakeLoyaltyRepo())

        useCase(target.id, source.id)

        val updated = repo.customers[target.id]!!
        assertTrue(updated.creditEnabled)
    }

    // ── Fake implementations ────────────────────────────────────────────────

    private class FakeCustomerRepo(initial: List<Customer> = emptyList()) : CustomerRepository {
        val customers = initial.associateBy { it.id }.toMutableMap()
        val deletedIds = mutableListOf<String>()

        override fun getAll(): Flow<List<Customer>> = flowOf(customers.values.toList())
        override suspend fun getById(id: String): Result<Customer> {
            val c = customers[id] ?: return Result.Error(DatabaseException("Not found: $id"))
            return Result.Success(c)
        }
        override fun search(query: String): Flow<List<Customer>> = flowOf(
            customers.values.filter { it.name.contains(query, ignoreCase = true) }
        )
        override suspend fun insert(customer: Customer): Result<Unit> {
            customers[customer.id] = customer
            return Result.Success(Unit)
        }
        override suspend fun update(customer: Customer): Result<Unit> {
            customers[customer.id] = customer
            return Result.Success(Unit)
        }
        override suspend fun delete(id: String): Result<Unit> {
            deletedIds.add(id)
            customers[id] = customers[id]!!.copy(isActive = false)
            return Result.Success(Unit)
        }
        override fun searchGlobal(query: String): Flow<List<Customer>> = search(query)
        override fun getByStore(storeId: String): Flow<List<Customer>> = flowOf(
            customers.values.filter { it.storeId == storeId }
        )
        override fun getGlobalCustomers(): Flow<List<Customer>> = flowOf(
            customers.values.filter { it.storeId == null }
        )
        override suspend fun makeGlobal(customerId: String): Result<Unit> {
            val c = customers[customerId] ?: return Result.Error(DatabaseException("Not found"))
            customers[customerId] = c.copy(storeId = null)
            return Result.Success(Unit)
        }
        override suspend fun updateLoyaltyPoints(customerId: String, points: Int): Result<Unit> {
            val c = customers[customerId] ?: return Result.Error(DatabaseException("Not found"))
            customers[customerId] = c.copy(loyaltyPoints = points)
            return Result.Success(Unit)
        }
    }

    private class FakeWalletRepo(
        val wallets: MutableMap<String, CustomerWallet> = mutableMapOf(),
    ) : CustomerWalletRepository {
        override suspend fun getOrCreate(customerId: String): Result<CustomerWallet> {
            val wallet = wallets[customerId] ?: CustomerWallet(
                id = "w-$customerId", customerId = customerId, balance = 0.0,
            )
            wallets[customerId] = wallet
            return Result.Success(wallet)
        }
        override fun observeWallet(customerId: String): Flow<CustomerWallet?> = flowOf(wallets[customerId])
        override fun getTransactions(walletId: String): Flow<List<WalletTransaction>> = flowOf(emptyList())
        override suspend fun credit(walletId: String, amount: Double, referenceType: String?, referenceId: String?, note: String?): Result<Unit> = Result.Success(Unit)
        override suspend fun debit(walletId: String, amount: Double, referenceType: String?, referenceId: String?, note: String?): Result<Unit> = Result.Success(Unit)
    }

    private class FakeLoyaltyRepo : LoyaltyRepository {
        override fun getPointsHistory(customerId: String): Flow<List<RewardPoints>> = flowOf(emptyList())
        override suspend fun getBalance(customerId: String): Result<Int> = Result.Success(0)
        override suspend fun recordPoints(entry: RewardPoints): Result<Unit> = Result.Success(Unit)
        override fun getAllTiers(): Flow<List<LoyaltyTier>> = flowOf(emptyList())
        override suspend fun getTierForPoints(points: Int): Result<LoyaltyTier?> = Result.Success(null)
        override suspend fun saveTier(tier: LoyaltyTier): Result<Unit> = Result.Success(Unit)
        override suspend fun deleteTier(id: String): Result<Unit> = Result.Success(Unit)
        override suspend fun expirePointsForCustomer(customerId: String, nowEpochMillis: Long): Result<Int> = Result.Success(0)
    }
}
