package com.zyntasolutions.zyntapos.domain.usecase.multistore

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.PickList
import com.zyntasolutions.zyntapos.domain.model.StockTransfer
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeProductRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeWarehouseRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildProduct
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildStockTransfer
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildWarehouse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Unit tests for [GeneratePickListUseCase] (P3-B1).
 *
 * Covers:
 * - Happy path: APPROVED transfer generates pick list with product + rack info
 * - Happy path: APPROVED transfer with no rack location - item has null rack/bin
 * - Blank transferId: ValidationException
 * - Transfer not APPROVED: ValidationException
 * - Transfer not found: Result.Error propagated
 * - Product not found: Result.Error propagated
 */
class GeneratePickListUseCaseTest {

    private fun makeUseCase(
        warehouseRepo: FakeWarehouseRepository = FakeWarehouseRepository(),
        productRepo: FakeProductRepository = FakeProductRepository(),
    ) = GeneratePickListUseCase(warehouseRepo, productRepo) to warehouseRepo to productRepo

    @Test
    fun `APPROVED transfer generates pick list with rack location`() = runTest {
        val warehouseRepo = FakeWarehouseRepository()
        val productRepo = FakeProductRepository()
        val useCase = GeneratePickListUseCase(warehouseRepo, productRepo)

        warehouseRepo.warehouses.add(buildWarehouse(id = "wh-01", name = "Main Warehouse"))
        warehouseRepo.warehouses.add(buildWarehouse(id = "wh-02", name = "Downtown Store"))
        warehouseRepo.transfers.add(
            buildStockTransfer(
                id = "t-01",
                sourceWarehouseId = "wh-01",
                destWarehouseId = "wh-02",
                productId = "prod-01",
                quantity = 10.0,
                status = StockTransfer.Status.APPROVED,
            )
        )
        warehouseRepo.rackLocations["prod-01" to "wh-01"] = "A1" to "Row-2-Bin-4"
        productRepo.addProduct(buildProduct(id = "prod-01", name = "Widget X", sku = "SKU-WX"))

        val result = useCase("t-01")

        assertIs<Result.Success<PickList>>(result)
        val pickList = result.data
        assertEquals("t-01", pickList.transferId)
        assertEquals("Main Warehouse", pickList.sourceStoreName)
        assertEquals("Downtown Store", pickList.destinationStoreName)
        assertEquals(1, pickList.items.size)

        val item = pickList.items.first()
        assertEquals("prod-01", item.productId)
        assertEquals("Widget X", item.productName)
        assertEquals("SKU-WX", item.sku)
        assertEquals(10.0, item.quantity)
        assertEquals("A1", item.rackLocation)
        assertEquals("Row-2-Bin-4", item.binLocation)
    }

    @Test
    fun `APPROVED transfer with no rack location - null rack and bin`() = runTest {
        val warehouseRepo = FakeWarehouseRepository()
        val productRepo = FakeProductRepository()
        val useCase = GeneratePickListUseCase(warehouseRepo, productRepo)

        warehouseRepo.warehouses.add(buildWarehouse(id = "wh-01", name = "Source"))
        warehouseRepo.warehouses.add(buildWarehouse(id = "wh-02", name = "Dest"))
        warehouseRepo.transfers.add(
            buildStockTransfer(id = "t-02", status = StockTransfer.Status.APPROVED)
        )
        productRepo.addProduct(buildProduct(id = "prod-01", name = "Gadget Y"))
        // No rack location added - should be null

        val result = useCase("t-02")

        assertIs<Result.Success<PickList>>(result)
        val item = result.data.items.first()
        assertNull(item.rackLocation)
        assertNull(item.binLocation)
    }

    @Test
    fun `blank transferId - returns ValidationException`() = runTest {
        val useCase = GeneratePickListUseCase(FakeWarehouseRepository(), FakeProductRepository())

        val result = useCase("  ")

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
    }

    @Test
    fun `PENDING transfer - returns ValidationException`() = runTest {
        val warehouseRepo = FakeWarehouseRepository()
        val productRepo = FakeProductRepository()
        val useCase = GeneratePickListUseCase(warehouseRepo, productRepo)

        warehouseRepo.transfers.add(
            buildStockTransfer(id = "t-pending", status = StockTransfer.Status.PENDING)
        )

        val result = useCase("t-pending")

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
        assertNotNull(result.exception.message?.contains("APPROVED", ignoreCase = true))
    }

    @Test
    fun `IN_TRANSIT transfer - returns ValidationException`() = runTest {
        val warehouseRepo = FakeWarehouseRepository()
        val productRepo = FakeProductRepository()
        val useCase = GeneratePickListUseCase(warehouseRepo, productRepo)

        warehouseRepo.transfers.add(
            buildStockTransfer(id = "t-transit", status = StockTransfer.Status.IN_TRANSIT)
        )

        val result = useCase("t-transit")

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
    }

    @Test
    fun `transfer not found - returns Result Error`() = runTest {
        val useCase = GeneratePickListUseCase(FakeWarehouseRepository(), FakeProductRepository())

        val result = useCase("non-existent")

        assertIs<Result.Error>(result)
    }

    @Test
    fun `product not found - returns Result Error`() = runTest {
        val warehouseRepo = FakeWarehouseRepository()
        val productRepo = FakeProductRepository()
        val useCase = GeneratePickListUseCase(warehouseRepo, productRepo)

        warehouseRepo.warehouses.add(buildWarehouse(id = "wh-01"))
        warehouseRepo.warehouses.add(buildWarehouse(id = "wh-02"))
        warehouseRepo.transfers.add(
            buildStockTransfer(id = "t-no-prod", status = StockTransfer.Status.APPROVED)
        )
        // No product added - should fail

        val result = useCase("t-no-prod")

        assertIs<Result.Error>(result)
    }
}
