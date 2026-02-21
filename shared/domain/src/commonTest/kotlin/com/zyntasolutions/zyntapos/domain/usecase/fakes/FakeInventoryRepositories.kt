package com.zyntasolutions.zyntapos.domain.usecase.fakes

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.Category
import com.zyntasolutions.zyntapos.domain.model.Product
import com.zyntasolutions.zyntapos.domain.model.StockAdjustment
import com.zyntasolutions.zyntapos.domain.model.Supplier
import com.zyntasolutions.zyntapos.domain.model.SyncStatus
import com.zyntasolutions.zyntapos.domain.model.TaxGroup
import com.zyntasolutions.zyntapos.domain.model.UnitOfMeasure
import com.zyntasolutions.zyntapos.domain.repository.CategoryRepository
import com.zyntasolutions.zyntapos.domain.repository.ProductRepository
import com.zyntasolutions.zyntapos.domain.repository.StockRepository
import com.zyntasolutions.zyntapos.domain.repository.SupplierRepository
import com.zyntasolutions.zyntapos.domain.repository.TaxGroupRepository
import com.zyntasolutions.zyntapos.domain.repository.UnitGroupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

// ─────────────────────────────────────────────────────────────────────────────
// Fixtures
// ─────────────────────────────────────────────────────────────────────────────

/** Builds a test [Product] with sensible defaults. */
fun buildProduct(
    id: String = "prod-01",
    name: String = "Test Product",
    barcode: String = "1234567890",
    sku: String = "SKU-001",
    price: Double = 10.0,
    costPrice: Double = 5.0,
    stockQty: Double = 100.0,
    minStockQty: Double = 5.0,
    isActive: Boolean = true,
    categoryId: String = "cat-01",
) = Product(id = id, name = name, barcode = barcode, sku = sku, categoryId = categoryId,
    unitId = "unit-01", price = price, costPrice = costPrice, taxGroupId = "tax-01",
    stockQty = stockQty, minStockQty = minStockQty, imageUrl = null,
    description = "Test product description", isActive = isActive,
    createdAt = Clock.System.now(), updatedAt = Clock.System.now(),
    syncStatus = SyncStatus(state = SyncStatus.State.SYNCED))

/** Builds a [TaxGroup] with sensible test defaults. */
fun buildTaxGroup(
    id: String = "tax-01",
    name: String = "Standard VAT",
    rate: Double = 15.0,
    isInclusive: Boolean = false,
    isActive: Boolean = true,
) = TaxGroup(id = id, name = name, rate = rate, isInclusive = isInclusive, isActive = isActive)

/** Builds a [UnitOfMeasure] with sensible test defaults. */
fun buildUnit(
    id: String = "unit-01",
    name: String = "Kilogram",
    abbreviation: String = "kg",
    isBaseUnit: Boolean = true,
    conversionRate: Double = 1.0,
) = UnitOfMeasure(
    id = id, name = name, abbreviation = abbreviation,
    isBaseUnit = isBaseUnit, conversionRate = conversionRate,
)

// ─────────────────────────────────────────────────────────────────────────────
// FakeProductRepository
// ─────────────────────────────────────────────────────────────────────────────

class FakeProductRepository : ProductRepository {
    val products = mutableListOf<Product>()
    var shouldFailGetById: Boolean = false

    private val _products = MutableStateFlow<List<Product>>(emptyList())

    fun addProduct(product: Product) {
        products.add(product)
        _products.value = products.toList()
    }

    override fun getAll(): Flow<List<Product>> = _products

    override suspend fun getById(id: String): Result<Product> {
        if (shouldFailGetById) return Result.Error(DatabaseException("DB error"))
        val product = products.firstOrNull { it.id == id }
            ?: return Result.Error(DatabaseException("Product '$id' not found"))
        return Result.Success(product)
    }

    override fun search(query: String, categoryId: String?): Flow<List<Product>> =
        _products.map { list ->
            list.filter { p ->
                p.name.contains(query, ignoreCase = true) ||
                    p.barcode.contains(query) ||
                    p.sku.contains(query, ignoreCase = true)
            }.let { filtered ->
                if (categoryId != null) filtered.filter { it.categoryId == categoryId } else filtered
            }
        }

    override suspend fun getByBarcode(barcode: String): Result<Product> {
        val product = products.firstOrNull { it.barcode == barcode }
            ?: return Result.Error(DatabaseException("Barcode '$barcode' not found"))
        return Result.Success(product)
    }

    override suspend fun insert(product: Product): Result<Unit> {
        val barcodeConflict = products.any { it.barcode == product.barcode && it.id != product.id }
        if (barcodeConflict) return Result.Error(ValidationException("Barcode duplicate", field = "barcode", rule = "BARCODE_DUPLICATE"))
        val skuConflict = products.any { it.sku == product.sku && it.id != product.id }
        if (skuConflict) return Result.Error(ValidationException("SKU duplicate", field = "sku", rule = "SKU_DUPLICATE"))
        products.add(product)
        _products.value = products.toList()
        return Result.Success(Unit)
    }

    override suspend fun update(product: Product): Result<Unit> {
        val index = products.indexOfFirst { it.id == product.id }
        if (index == -1) return Result.Error(DatabaseException("Product not found"))
        products[index] = product
        _products.value = products.toList()
        return Result.Success(Unit)
    }

    override suspend fun delete(id: String): Result<Unit> {
        products.removeAll { it.id == id }
        _products.value = products.toList()
        return Result.Success(Unit)
    }

    override suspend fun getCount(): Int = products.size
}

// ─────────────────────────────────────────────────────────────────────────────
// FakeStockRepository
// ─────────────────────────────────────────────────────────────────────────────

class FakeStockRepository : StockRepository {
    val adjustments = mutableListOf<StockAdjustment>()
    var shouldFailAdjust: Boolean = false

    override suspend fun adjustStock(adjustment: StockAdjustment): Result<Unit> {
        if (shouldFailAdjust) return Result.Error(DatabaseException("Stock adjust failed"))
        adjustments.add(adjustment)
        return Result.Success(Unit)
    }

    override fun getMovements(productId: String): Flow<List<StockAdjustment>> =
        MutableStateFlow(adjustments.filter { it.productId == productId })

    override fun getAlerts(threshold: Double): Flow<List<Product>> =
        MutableStateFlow(emptyList())
}

// ─────────────────────────────────────────────────────────────────────────────
// FakeCategoryRepository
// ─────────────────────────────────────────────────────────────────────────────

class FakeCategoryRepository : CategoryRepository {
    val categories = mutableListOf<Category>()
    private val _cats = MutableStateFlow<List<Category>>(emptyList())

    override fun getAll(): Flow<List<Category>> = _cats

    override suspend fun getById(id: String): Result<Category> {
        return categories.firstOrNull { it.id == id }
            ?.let { Result.Success(it) }
            ?: Result.Error(DatabaseException("Category not found"))
    }

    override suspend fun insert(category: Category): Result<Unit> {
        categories.add(category)
        _cats.value = categories.toList()
        return Result.Success(Unit)
    }

    override suspend fun update(category: Category): Result<Unit> {
        val index = categories.indexOfFirst { it.id == category.id }
        if (index == -1) return Result.Error(DatabaseException("Not found"))
        categories[index] = category
        _cats.value = categories.toList()
        return Result.Success(Unit)
    }

    override suspend fun delete(id: String): Result<Unit> {
        categories.removeAll { it.id == id }
        _cats.value = categories.toList()
        return Result.Success(Unit)
    }

    override fun getTree(): Flow<List<Category>> = _cats
}

// ─────────────────────────────────────────────────────────────────────────────
// FakeSupplierRepository
// ─────────────────────────────────────────────────────────────────────────────

class FakeSupplierRepository : SupplierRepository {
    val suppliers = mutableListOf<Supplier>()
    private val _flow = MutableStateFlow<List<Supplier>>(emptyList())
    override fun getAll(): Flow<List<Supplier>> = _flow
    override suspend fun getById(id: String): Result<Supplier> =
        suppliers.firstOrNull { it.id == id }?.let { Result.Success(it) }
            ?: Result.Error(DatabaseException("Not found"))
    override suspend fun insert(supplier: Supplier): Result<Unit> {
        suppliers.add(supplier)
        _flow.value = suppliers.toList()
        return Result.Success(Unit)
    }
    override suspend fun update(supplier: Supplier): Result<Unit> {
        val i = suppliers.indexOfFirst { it.id == supplier.id }
        if (i == -1) return Result.Error(DatabaseException("Not found"))
        suppliers[i] = supplier
        _flow.value = suppliers.toList()
        return Result.Success(Unit)
    }
    override suspend fun delete(id: String): Result<Unit> {
        suppliers.removeAll { it.id == id }
        _flow.value = suppliers.toList()
        return Result.Success(Unit)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FakeTaxGroupRepository
// ─────────────────────────────────────────────────────────────────────────────

/**
 * In-memory implementation of [TaxGroupRepository] for unit tests.
 * Mimics the data-layer's name-uniqueness constraint and basic CRUD behaviour.
 */
class FakeTaxGroupRepository : TaxGroupRepository {
    val taxGroups = mutableListOf<TaxGroup>()
    private val _flow = MutableStateFlow<List<TaxGroup>>(emptyList())

    override fun getAll(): Flow<List<TaxGroup>> = _flow

    override suspend fun getById(id: String): Result<TaxGroup> =
        taxGroups.firstOrNull { it.id == id }
            ?.let { Result.Success(it) }
            ?: Result.Error(DatabaseException("TaxGroup '$id' not found"))

    override suspend fun insert(taxGroup: TaxGroup): Result<Unit> {
        val nameConflict = taxGroups.any { it.name.equals(taxGroup.name, ignoreCase = true) && it.id != taxGroup.id }
        if (nameConflict) {
            return Result.Error(ValidationException("Tax group name '${taxGroup.name}' already exists.", field = "name", rule = "NAME_DUPLICATE"))
        }
        taxGroups.add(taxGroup)
        _flow.value = taxGroups.toList()
        return Result.Success(Unit)
    }

    override suspend fun update(taxGroup: TaxGroup): Result<Unit> {
        val index = taxGroups.indexOfFirst { it.id == taxGroup.id }
        if (index == -1) return Result.Error(DatabaseException("TaxGroup not found"))
        taxGroups[index] = taxGroup
        _flow.value = taxGroups.toList()
        return Result.Success(Unit)
    }

    override suspend fun delete(id: String): Result<Unit> {
        taxGroups.removeAll { it.id == id }
        _flow.value = taxGroups.toList()
        return Result.Success(Unit)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FakeUnitGroupRepository
// ─────────────────────────────────────────────────────────────────────────────

/**
 * In-memory implementation of [UnitGroupRepository] for unit tests.
 * Enforces the base-unit uniqueness constraint within each unit group.
 */
class FakeUnitGroupRepository : UnitGroupRepository {
    val units = mutableListOf<UnitOfMeasure>()
    private val _flow = MutableStateFlow<List<UnitOfMeasure>>(emptyList())
    var shouldFailDelete: Boolean = false

    override fun getAll(): Flow<List<UnitOfMeasure>> = _flow

    override suspend fun getById(id: String): Result<UnitOfMeasure> =
        units.firstOrNull { it.id == id }
            ?.let { Result.Success(it) }
            ?: Result.Error(DatabaseException("Unit '$id' not found"))

    override suspend fun insert(unit: UnitOfMeasure): Result<Unit> {
        units.add(unit)
        _flow.value = units.toList()
        return Result.Success(Unit)
    }

    override suspend fun update(unit: UnitOfMeasure): Result<Unit> {
        val index = units.indexOfFirst { it.id == unit.id }
        if (index == -1) return Result.Error(DatabaseException("Unit not found"))
        units[index] = unit
        _flow.value = units.toList()
        return Result.Success(Unit)
    }

    override suspend fun delete(id: String): Result<Unit> {
        if (shouldFailDelete) {
            return Result.Error(ValidationException("Cannot delete unit in use.", field = "unitId", rule = "IN_USE"))
        }
        units.removeAll { it.id == id }
        _flow.value = units.toList()
        return Result.Success(Unit)
    }
}
