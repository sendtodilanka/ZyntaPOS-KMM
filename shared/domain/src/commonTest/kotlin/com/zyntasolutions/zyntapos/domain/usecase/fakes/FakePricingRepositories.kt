package com.zyntasolutions.zyntapos.domain.usecase.fakes

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.MasterProduct
import com.zyntasolutions.zyntapos.domain.model.PricingRule
import com.zyntasolutions.zyntapos.domain.model.StoreProductOverride
import com.zyntasolutions.zyntapos.domain.repository.MasterProductRepository
import com.zyntasolutions.zyntapos.domain.repository.PricingRuleRepository
import com.zyntasolutions.zyntapos.domain.repository.StoreProductOverrideRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

// ── Fake MasterProductRepository ──────────────────────────────────────────

class FakeMasterProductRepository : MasterProductRepository {
    val masterProducts = mutableListOf<MasterProduct>()

    override fun getAll(): Flow<List<MasterProduct>> =
        MutableStateFlow(masterProducts.toList())

    override suspend fun getById(id: String): Result<MasterProduct> {
        val mp = masterProducts.find { it.id == id }
            ?: return Result.Error(DatabaseException("Not found: $id"))
        return Result.Success(mp)
    }

    override suspend fun getByBarcode(barcode: String): Result<MasterProduct> {
        val mp = masterProducts.find { it.barcode == barcode }
            ?: return Result.Error(DatabaseException("Not found: $barcode"))
        return Result.Success(mp)
    }

    override fun search(query: String): Flow<List<MasterProduct>> =
        MutableStateFlow(masterProducts.filter { it.name.contains(query, ignoreCase = true) })

    override suspend fun upsertFromSync(masterProduct: MasterProduct): Result<Unit> {
        masterProducts.removeAll { it.id == masterProduct.id }
        masterProducts.add(masterProduct)
        return Result.Success(Unit)
    }

    override suspend fun getCount(): Int = masterProducts.size
}

fun buildMasterProduct(
    id: String = "mp-1",
    name: String = "Master Widget",
    basePrice: Double = 100.0,
    costPrice: Double = 50.0,
    sku: String? = "MW-001",
    barcode: String? = null,
): MasterProduct = MasterProduct(
    id = id,
    sku = sku,
    barcode = barcode,
    name = name,
    description = null,
    basePrice = basePrice,
    costPrice = costPrice,
    categoryId = null,
    taxGroupId = null,
    unitId = null,
    imageUrl = null,
    isActive = true,
    createdAt = Instant.fromEpochMilliseconds(0L),
    updatedAt = Instant.fromEpochMilliseconds(0L),
)

// ── Fake StoreProductOverrideRepository ────────────────────────────────────

class FakeStoreProductOverrideRepository : StoreProductOverrideRepository {
    val overrides = mutableListOf<StoreProductOverride>()

    override fun getByStore(storeId: String): Flow<List<StoreProductOverride>> =
        MutableStateFlow(overrides.filter { it.storeId == storeId })

    override suspend fun getOverride(masterProductId: String, storeId: String): Result<StoreProductOverride> {
        val o = overrides.find { it.masterProductId == masterProductId && it.storeId == storeId }
            ?: return Result.Error(DatabaseException("Not found"))
        return Result.Success(o)
    }

    override suspend fun upsertFromSync(override: StoreProductOverride): Result<Unit> {
        overrides.removeAll { it.id == override.id }
        overrides.add(override)
        return Result.Success(Unit)
    }

    override suspend fun updateLocalPrice(masterProductId: String, storeId: String, price: Double?): Result<Unit> =
        Result.Success(Unit)

    override suspend fun updateLocalStock(masterProductId: String, storeId: String, qty: Double): Result<Unit> =
        Result.Success(Unit)
}

fun buildStoreOverride(
    id: String = "so-1",
    masterProductId: String = "mp-1",
    storeId: String = "store-1",
    localPrice: Double? = null,
    localCostPrice: Double? = null,
): StoreProductOverride = StoreProductOverride(
    id = id,
    masterProductId = masterProductId,
    storeId = storeId,
    localPrice = localPrice,
    localCostPrice = localCostPrice,
    createdAt = Instant.fromEpochMilliseconds(0L),
    updatedAt = Instant.fromEpochMilliseconds(0L),
)

// ── Fake PricingRuleRepository ─────────────────────────────────────────────

class FakePricingRuleRepository : PricingRuleRepository {
    val rules = mutableListOf<PricingRule>()

    override fun getActiveRulesForProduct(productId: String, storeId: String): Flow<List<PricingRule>> =
        MutableStateFlow(rules.filter {
            it.productId == productId && it.isActive && (it.storeId == storeId || it.storeId == null)
        })

    override suspend fun getEffectiveRule(
        productId: String,
        storeId: String,
        nowEpochMs: Long,
    ): Result<PricingRule?> {
        val effective = rules
            .filter { r ->
                r.productId == productId &&
                    r.isActive &&
                    (r.storeId == storeId || r.storeId == null) &&
                    (r.validFrom == null || r.validFrom <= nowEpochMs) &&
                    (r.validTo == null || r.validTo >= nowEpochMs)
            }
            .sortedWith(
                compareBy<PricingRule> { if (it.storeId != null) 0 else 1 }
                    .thenByDescending { it.priority }
            )
            .firstOrNull()
        return Result.Success(effective)
    }

    override fun getAllRules(): Flow<List<PricingRule>> =
        MutableStateFlow(rules.toList())

    override fun getRulesForProduct(productId: String): Flow<List<PricingRule>> =
        MutableStateFlow(rules.filter { it.productId == productId })

    override suspend fun upsert(rule: PricingRule): Result<Unit> {
        rules.removeAll { it.id == rule.id }
        rules.add(rule)
        return Result.Success(Unit)
    }

    override suspend fun delete(ruleId: String): Result<Unit> {
        rules.removeAll { it.id == ruleId }
        return Result.Success(Unit)
    }
}

fun buildPricingRule(
    id: String = "pr-1",
    productId: String = "p-1",
    storeId: String? = null,
    price: Double = 80.0,
    priority: Int = 0,
    validFrom: Long? = null,
    validTo: Long? = null,
    isActive: Boolean = true,
    description: String = "Test rule",
): PricingRule = PricingRule(
    id = id,
    productId = productId,
    storeId = storeId,
    price = price,
    priority = priority,
    validFrom = validFrom,
    validTo = validTo,
    isActive = isActive,
    description = description,
)
