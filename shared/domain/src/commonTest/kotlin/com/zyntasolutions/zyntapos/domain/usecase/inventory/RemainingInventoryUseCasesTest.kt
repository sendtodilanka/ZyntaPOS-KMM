package com.zyntasolutions.zyntapos.domain.usecase.inventory

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.LabelTemplate
import com.zyntasolutions.zyntapos.domain.model.MasterProduct
import com.zyntasolutions.zyntapos.domain.model.PurchaseOrder
import com.zyntasolutions.zyntapos.domain.model.PurchaseOrderItem
import com.zyntasolutions.zyntapos.domain.model.ReplenishmentRule
import com.zyntasolutions.zyntapos.domain.model.Supplier
import com.zyntasolutions.zyntapos.domain.model.WarehouseStock
import com.zyntasolutions.zyntapos.domain.repository.LabelTemplateRepository
import com.zyntasolutions.zyntapos.domain.repository.PurchaseOrderRepository
import com.zyntasolutions.zyntapos.domain.repository.ReplenishmentRuleRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeMasterProductRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeSupplierRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeWarehouseStockRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildMasterProduct
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// ─────────────────────────────────────────────────────────────────────────────
// Fakes
// ─────────────────────────────────────────────────────────────────────────────

private class FakeLabelTemplateRepo : LabelTemplateRepository {
    val templates = mutableListOf<LabelTemplate>()
    private val flow = MutableStateFlow(emptyList<LabelTemplate>())

    override fun getAll(): Flow<List<LabelTemplate>> = flow
    override suspend fun getById(id: String): Result<LabelTemplate> =
        templates.firstOrNull { it.id == id }?.let { Result.Success(it) }
            ?: Result.Error(DatabaseException("Not found"))
    override suspend fun save(template: LabelTemplate): Result<Unit> {
        templates.removeAll { it.id == template.id }
        templates.add(template)
        flow.value = templates.toList()
        return Result.Success(Unit)
    }
    override suspend fun delete(id: String): Result<Unit> {
        templates.removeAll { it.id == id }
        flow.value = templates.toList()
        return Result.Success(Unit)
    }
    override suspend fun count(): Int = templates.size
}

private class FakePurchaseOrderRepo : PurchaseOrderRepository {
    val orders = mutableListOf<PurchaseOrder>()
    var shouldFail = false

    private val flow = MutableStateFlow(emptyList<PurchaseOrder>())

    override fun getAll(): Flow<List<PurchaseOrder>> = flow
    override suspend fun getById(id: String): Result<PurchaseOrder> =
        orders.firstOrNull { it.id == id }?.let { Result.Success(it) }
            ?: Result.Error(DatabaseException("Not found"))
    override suspend fun getByDateRange(startDate: Long, endDate: Long): Result<List<PurchaseOrder>> =
        Result.Success(orders)
    override suspend fun getBySupplierId(supplierId: String): Result<List<PurchaseOrder>> =
        Result.Success(orders.filter { it.supplierId == supplierId })
    override suspend fun getByStatus(status: PurchaseOrder.Status): Result<List<PurchaseOrder>> =
        Result.Success(orders.filter { it.status == status })
    override suspend fun create(order: PurchaseOrder): Result<Unit> {
        if (shouldFail) return Result.Error(DatabaseException("DB error"))
        orders.add(order)
        flow.value = orders.toList()
        return Result.Success(Unit)
    }
    override suspend fun receiveItems(
        purchaseOrderId: String,
        receivedItems: Map<String, Double>,
        receivedBy: String,
    ): Result<Unit> = Result.Success(Unit)
    override suspend fun cancel(purchaseOrderId: String): Result<Unit> = Result.Success(Unit)
}

private class FakeReplenishmentRuleRepo(
    private val rules: List<ReplenishmentRule> = emptyList(),
) : ReplenishmentRuleRepository {
    override fun getAll(): Flow<List<ReplenishmentRule>> = MutableStateFlow(rules)
    override fun getByWarehouse(warehouseId: String): Flow<List<ReplenishmentRule>> =
        MutableStateFlow(rules.filter { it.warehouseId == warehouseId })
    override suspend fun getAutoApproveRules(): Result<List<ReplenishmentRule>> =
        Result.Success(rules.filter { it.autoApprove && it.isActive })
    override suspend fun getByProductAndWarehouse(productId: String, warehouseId: String): Result<ReplenishmentRule?> =
        Result.Success(rules.firstOrNull { it.productId == productId && it.warehouseId == warehouseId })
    override suspend fun upsert(rule: ReplenishmentRule): Result<Unit> = Result.Success(Unit)
    override suspend fun delete(id: String): Result<Unit> = Result.Success(Unit)
}

private fun buildPurchaseOrderItem(
    id: String = "item-01",
    productId: String = "prod-01",
    quantity: Double = 10.0,
    unitCost: Double = 5.0,
) = PurchaseOrderItem(
    id = id,
    purchaseOrderId = "",
    productId = productId,
    quantityOrdered = quantity,
    unitCost = unitCost,
    lineTotal = quantity * unitCost,
)

private fun buildLabelTemplate(id: String = "tmpl-01") = LabelTemplate(
    id = id,
    name = "Standard Label",
    paperType = LabelTemplate.PaperType.CONTINUOUS_ROLL,
    paperWidthMm = 58.0,
    labelHeightMm = 30.0,
    columns = 1,
    rows = 0,
    gapHorizontalMm = 0.0,
    gapVerticalMm = 2.0,
    marginTopMm = 1.0,
    marginBottomMm = 1.0,
    marginLeftMm = 1.0,
    marginRightMm = 1.0,
    isDefault = false,
    createdAt = 1_000_000L,
    updatedAt = 1_000_000L,
)

private fun buildReplenishmentRule(
    id: String = "rule-01",
    productId: String = "prod-01",
    warehouseId: String = "wh-01",
    supplierId: String = "sup-01",
    reorderPoint: Double = 10.0,
    reorderQty: Double = 50.0,
    autoApprove: Boolean = true,
) = ReplenishmentRule(
    id = id,
    productId = productId,
    warehouseId = warehouseId,
    supplierId = supplierId,
    reorderPoint = reorderPoint,
    reorderQty = reorderQty,
    autoApprove = autoApprove,
    isActive = true,
    createdAt = 1_000_000L,
    updatedAt = 1_000_000L,
)

// ─────────────────────────────────────────────────────────────────────────────
// GetLabelTemplatesUseCaseTest
// ─────────────────────────────────────────────────────────────────────────────

class GetLabelTemplatesUseCaseTest {

    @Test
    fun `execute_emitsAllTemplates`() = runTest {
        val repo = FakeLabelTemplateRepo()
        repo.save(buildLabelTemplate("tmpl-01"))
        repo.save(buildLabelTemplate("tmpl-02"))

        GetLabelTemplatesUseCase(repo).execute().test {
            val list = awaitItem()
            assertEquals(2, list.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `execute_emptyRepo_emitsEmptyList`() = runTest {
        GetLabelTemplatesUseCase(FakeLabelTemplateRepo()).execute().test {
            assertEquals(0, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GetMasterProductCatalogUseCaseTest
// ─────────────────────────────────────────────────────────────────────────────

class GetMasterProductCatalogUseCaseTest {

    @Test
    fun `getAll_returnsAllMasterProducts`() = runTest {
        val repo = FakeMasterProductRepository()
        repo.masterProducts.add(buildMasterProduct(id = "mp-01", name = "Widget A"))
        repo.masterProducts.add(buildMasterProduct(id = "mp-02", name = "Widget B"))

        GetMasterProductCatalogUseCase(repo).getAll().test {
            val list = awaitItem()
            assertEquals(2, list.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `search_filtersProductsByName`() = runTest {
        val repo = FakeMasterProductRepository()
        repo.masterProducts.add(buildMasterProduct(id = "mp-01", name = "Blue Widget"))
        repo.masterProducts.add(buildMasterProduct(id = "mp-02", name = "Red Widget"))
        repo.masterProducts.add(buildMasterProduct(id = "mp-03", name = "Green Gadget"))

        GetMasterProductCatalogUseCase(repo).search("Widget").test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertTrue(list.none { it.id == "mp-03" })
            cancelAndIgnoreRemainingEvents()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CreatePurchaseOrderUseCaseTest
// ─────────────────────────────────────────────────────────────────────────────

class CreatePurchaseOrderUseCaseTest {

    private fun makeUseCase(
        poRepo: FakePurchaseOrderRepo = FakePurchaseOrderRepo(),
        supplierRepo: FakeSupplierRepository = FakeSupplierRepository(),
    ) = CreatePurchaseOrderUseCase(poRepo, supplierRepo) to poRepo

    private fun buildValidItem() = buildPurchaseOrderItem("item-01", "prod-01", 10.0, 5.0)

    @Test
    fun `blankSupplierId_returnsValidationError`() = runTest {
        val (useCase, _) = makeUseCase()
        val result = useCase("", "PO-001", listOf(buildValidItem()), createdBy = "user-01")
        assertIs<Result.Error>(result)
        assertIs<ValidationException>((result as Result.Error).exception)
    }

    @Test
    fun `unknownSupplierId_returnsNotFoundError`() = runTest {
        val (useCase, _) = makeUseCase()
        val result = useCase("unknown-sup", "PO-001", listOf(buildValidItem()), createdBy = "user-01")
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("NOT_FOUND", ex.rule)
    }

    @Test
    fun `emptyItems_returnsValidationError`() = runTest {
        val supplierRepo = FakeSupplierRepository()
        supplierRepo.insert(Supplier(id = "sup-01", name = "Test Supplier"))
        val (useCase, _) = makeUseCase(supplierRepo = supplierRepo)

        val result = useCase("sup-01", "PO-001", emptyList(), createdBy = "user-01")
        assertIs<Result.Error>(result)
        val ex = (result as Result.Error).exception as ValidationException
        assertEquals("REQUIRED", ex.rule)
    }

    @Test
    fun `zeroQuantityItem_returnsValidationError`() = runTest {
        val supplierRepo = FakeSupplierRepository()
        supplierRepo.insert(Supplier(id = "sup-01", name = "Test Supplier"))
        val (useCase, _) = makeUseCase(supplierRepo = supplierRepo)
        val item = buildPurchaseOrderItem(quantity = 0.0)

        val result = useCase("sup-01", "PO-001", listOf(item), createdBy = "user-01")
        assertIs<Result.Error>(result)
        assertIs<ValidationException>((result as Result.Error).exception)
    }

    @Test
    fun `negativeUnitCost_returnsValidationError`() = runTest {
        val supplierRepo = FakeSupplierRepository()
        supplierRepo.insert(Supplier(id = "sup-01", name = "Test Supplier"))
        val (useCase, _) = makeUseCase(supplierRepo = supplierRepo)
        val item = buildPurchaseOrderItem(unitCost = -1.0)

        val result = useCase("sup-01", "PO-001", listOf(item), createdBy = "user-01")
        assertIs<Result.Error>(result)
        assertIs<ValidationException>((result as Result.Error).exception)
    }

    @Test
    fun `validOrder_createsSuccessfully`() = runTest {
        val supplierRepo = FakeSupplierRepository()
        supplierRepo.insert(Supplier(id = "sup-01", name = "Test Supplier"))
        val (useCase, poRepo) = makeUseCase(supplierRepo = supplierRepo)

        val result = useCase("sup-01", "PO-001", listOf(buildValidItem()), createdBy = "user-01")
        assertIs<Result.Success<*>>(result)
        assertEquals(1, poRepo.orders.size)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AutoReplenishmentUseCaseTest
// ─────────────────────────────────────────────────────────────────────────────

class AutoReplenishmentUseCaseTest {

    private fun makeUseCase(
        rules: List<ReplenishmentRule> = emptyList(),
        stockQty: Double = 5.0,
    ): AutoReplenishmentUseCase {
        val ruleRepo = FakeReplenishmentRuleRepo(rules)
        val stockRepo = FakeWarehouseStockRepository()
        if (rules.isNotEmpty()) {
            val rule = rules.first()
            stockRepo.seed(
                WarehouseStock(
                    id = "ws-01",
                    warehouseId = rule.warehouseId,
                    productId = rule.productId,
                    quantity = stockQty,
                )
            )
        }
        val supplierRepo = FakeSupplierRepository()
        supplierRepo.suppliers.add(Supplier(id = "sup-01", name = "Test Supplier"))
        val poRepo = FakePurchaseOrderRepo()
        val createPo = CreatePurchaseOrderUseCase(poRepo, supplierRepo)
        return AutoReplenishmentUseCase(ruleRepo, stockRepo, createPo)
    }

    @Test
    fun `noAutoApproveRules_returnsZeroCreated`() = runTest {
        val result = makeUseCase(rules = emptyList()).invoke("system")
        assertEquals(0, result.ordersCreated)
        assertEquals(0, result.rulesSkipped)
    }

    @Test
    fun `stockBelowReorderPoint_createsPurchaseOrder`() = runTest {
        val rule = buildReplenishmentRule(
            productId = "prod-01",
            warehouseId = "wh-01",
            supplierId = "sup-01",
            reorderPoint = 10.0,
            reorderQty = 50.0,
            autoApprove = true,
        )
        val result = makeUseCase(rules = listOf(rule), stockQty = 5.0).invoke("system")
        assertEquals(1, result.ordersCreated)
    }

    @Test
    fun `stockAboveReorderPoint_skipsRule`() = runTest {
        val rule = buildReplenishmentRule(
            reorderPoint = 10.0,
            autoApprove = true,
        )
        val result = makeUseCase(rules = listOf(rule), stockQty = 20.0).invoke("system")
        assertEquals(0, result.ordersCreated)
        assertEquals(1, result.rulesSkipped)
    }
}
