package com.zyntasolutions.zyntapos.domain.usecase.fakes

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.Customer
import com.zyntasolutions.zyntapos.domain.model.CustomerGroup
import com.zyntasolutions.zyntapos.domain.model.CustomerWallet
import com.zyntasolutions.zyntapos.domain.model.LoyaltyTier
import com.zyntasolutions.zyntapos.domain.model.RewardPoints
import com.zyntasolutions.zyntapos.domain.model.WalletTransaction
import com.zyntasolutions.zyntapos.domain.repository.CustomerGroupRepository
import com.zyntasolutions.zyntapos.domain.repository.CustomerWalletRepository
import com.zyntasolutions.zyntapos.domain.repository.LoyaltyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Clock

// ─────────────────────────────────────────────────────────────────────────────
// Fixtures
// ─────────────────────────────────────────────────────────────────────────────

fun buildCustomer(
    id: String = "cust-01",
    name: String = "Alice Smith",
    phone: String = "+94771234567",
    email: String? = "alice@example.com",
    groupId: String? = null,
    loyaltyPoints: Int = 0,
    creditLimit: Double = 0.0,
    creditEnabled: Boolean = false,
) = Customer(id = id, name = name, phone = phone, email = email, groupId = groupId,
    loyaltyPoints = loyaltyPoints, creditLimit = creditLimit, creditEnabled = creditEnabled)

fun buildCustomerGroup(
    id: String = "grp-01",
    name: String = "VIP",
    discountValue: Double = 10.0,
    priceType: CustomerGroup.PriceType = CustomerGroup.PriceType.RETAIL,
) = CustomerGroup(id = id, name = name, discountValue = discountValue, priceType = priceType)

fun buildCustomerWallet(
    id: String = "wallet-01",
    customerId: String = "cust-01",
    balance: Double = 0.0,
) = CustomerWallet(id = id, customerId = customerId, balance = balance)

fun buildLoyaltyTier(
    id: String = "tier-01",
    name: String = "Silver",
    minPoints: Int = 0,
    multiplier: Double = 1.0,
) = LoyaltyTier(id = id, name = name, minPoints = minPoints, pointsMultiplier = multiplier)

// ─────────────────────────────────────────────────────────────────────────────
// Fake Repositories
// ─────────────────────────────────────────────────────────────────────────────

/**
 * In-memory fake for [CustomerGroupRepository].
 */
class FakeCustomerGroupRepository : CustomerGroupRepository {
    val store = mutableListOf<CustomerGroup>()
    var shouldFail = false

    private val _flow = MutableStateFlow<List<CustomerGroup>>(emptyList())

    override fun getAll(): Flow<List<CustomerGroup>> = _flow

    override suspend fun getById(id: String): Result<CustomerGroup> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        return store.find { it.id == id }
            ?.let { Result.Success(it) }
            ?: Result.Error(DatabaseException("Not found: $id"))
    }

    override suspend fun insert(group: CustomerGroup): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        store.add(group)
        _flow.value = store.toList()
        return Result.Success(Unit)
    }

    override suspend fun update(group: CustomerGroup): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        store.removeAll { it.id == group.id }
        store.add(group)
        _flow.value = store.toList()
        return Result.Success(Unit)
    }

    override suspend fun delete(id: String): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        store.removeAll { it.id == id }
        _flow.value = store.toList()
        return Result.Success(Unit)
    }
}

/**
 * In-memory fake for [LoyaltyRepository].
 */
class FakeLoyaltyRepository : LoyaltyRepository {
    val pointsStore = mutableMapOf<String, Int>() // customerId -> balance
    val ledger = mutableListOf<RewardPoints>()
    val tiers = mutableListOf<LoyaltyTier>()
    var shouldFail = false

    private val _historyFlow = MutableStateFlow<List<RewardPoints>>(emptyList())
    private val _tiersFlow = MutableStateFlow<List<LoyaltyTier>>(emptyList())

    override fun getPointsHistory(customerId: String): Flow<List<RewardPoints>> = _historyFlow

    override suspend fun getBalance(customerId: String): Result<Int> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        return Result.Success(pointsStore.getOrDefault(customerId, 0))
    }

    override suspend fun recordPoints(entry: RewardPoints): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        ledger.add(entry)
        pointsStore[entry.customerId] = entry.balanceAfter
        _historyFlow.value = ledger.toList()
        return Result.Success(Unit)
    }

    override fun getAllTiers(): Flow<List<LoyaltyTier>> = _tiersFlow

    override suspend fun getTierForPoints(points: Int): Result<LoyaltyTier?> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        val tier = tiers.filter { it.minPoints <= points }.maxByOrNull { it.minPoints }
        return Result.Success(tier)
    }

    override suspend fun saveTier(tier: LoyaltyTier): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        tiers.removeAll { it.id == tier.id }
        tiers.add(tier)
        _tiersFlow.value = tiers.toList()
        return Result.Success(Unit)
    }

    override suspend fun deleteTier(id: String): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        tiers.removeAll { it.id == id }
        _tiersFlow.value = tiers.toList()
        return Result.Success(Unit)
    }
}

/**
 * In-memory fake for [CustomerWalletRepository].
 */
class FakeCustomerWalletRepository : CustomerWalletRepository {
    val wallets = mutableMapOf<String, CustomerWallet>() // customerId -> wallet
    val transactions = mutableListOf<WalletTransaction>()
    var shouldFail = false
    var shouldFailCredit = false

    private val _walletFlow = MutableStateFlow<CustomerWallet?>(null)
    private val _txFlow = MutableStateFlow<List<WalletTransaction>>(emptyList())

    override suspend fun getOrCreate(customerId: String): Result<CustomerWallet> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        val existing = wallets[customerId]
        return if (existing != null) {
            Result.Success(existing)
        } else {
            val newWallet = CustomerWallet(
                id = "wallet-$customerId",
                customerId = customerId,
                balance = 0.0,
            )
            wallets[customerId] = newWallet
            _walletFlow.value = newWallet
            Result.Success(newWallet)
        }
    }

    override fun observeWallet(customerId: String): Flow<CustomerWallet?> = _walletFlow

    override fun getTransactions(walletId: String): Flow<List<WalletTransaction>> = _txFlow

    override suspend fun credit(
        walletId: String,
        amount: Double,
        referenceType: String?,
        referenceId: String?,
        note: String?,
    ): Result<Unit> {
        if (shouldFail || shouldFailCredit) return Result.Error(DatabaseException("DB error"))
        val wallet = wallets.values.find { it.id == walletId }
            ?: return Result.Error(DatabaseException("Wallet not found: $walletId"))
        val newBalance = wallet.balance + amount
        wallets[wallet.customerId] = wallet.copy(balance = newBalance)
        transactions.add(
            WalletTransaction(
                id = "tx-${transactions.size + 1}",
                walletId = walletId,
                type = WalletTransaction.TransactionType.CREDIT,
                amount = amount,
                balanceAfter = newBalance,
                referenceType = referenceType,
                referenceId = referenceId,
                note = note,
                createdAt = Clock.System.now().toEpochMilliseconds(),
            )
        )
        _txFlow.value = transactions.toList()
        return Result.Success(Unit)
    }

    override suspend fun debit(
        walletId: String,
        amount: Double,
        referenceType: String?,
        referenceId: String?,
        note: String?,
    ): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        val wallet = wallets.values.find { it.id == walletId }
            ?: return Result.Error(DatabaseException("Wallet not found: $walletId"))
        if (wallet.balance < amount) {
            return Result.Error(ValidationException("Insufficient wallet balance"))
        }
        val newBalance = wallet.balance - amount
        wallets[wallet.customerId] = wallet.copy(balance = newBalance)
        return Result.Success(Unit)
    }
}
