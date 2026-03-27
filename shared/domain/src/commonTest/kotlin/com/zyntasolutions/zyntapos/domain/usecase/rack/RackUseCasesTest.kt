package com.zyntasolutions.zyntapos.domain.usecase.rack

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeRackProductRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeWarehouseRackRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildRackProduct
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildWarehouseRack
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for rack use cases:
 * - [GetWarehouseRacksUseCase]
 * - [GetRackProductsUseCase]
 * - [SaveRackProductUseCase]
 * - [DeleteRackProductUseCase]
 *
 * Uses [FakeWarehouseRackRepository] and [FakeRackProductRepository] — no DB.
 */
class RackUseCasesTest {

    // ─── GetWarehouseRacksUseCase ─────────────────────────────────────────────

    @Test
    fun `getWarehouseRacks_returns_empty_flow_when_no_racks`() = runTest {
        val repo = FakeWarehouseRackRepository()
        val useCase = GetWarehouseRacksUseCase(repo)

        useCase("wh-01").test {
            val list = awaitItem()
            assertTrue(list.isEmpty(), "Should emit empty list for warehouse with no racks")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getWarehouseRacks_returns_racks_for_matching_warehouseId`() = runTest {
        val repo = FakeWarehouseRackRepository()
        repo.insert(buildWarehouseRack(id = "rack-01", warehouseId = "wh-01", name = "A1"))
        repo.insert(buildWarehouseRack(id = "rack-02", warehouseId = "wh-01", name = "A2"))
        repo.insert(buildWarehouseRack(id = "rack-03", warehouseId = "wh-99", name = "B1"))
        val useCase = GetWarehouseRacksUseCase(repo)

        useCase("wh-01").test {
            val list = awaitItem()
            assertEquals(2, list.size, "Should return only racks belonging to wh-01")
            assertTrue(list.all { it.warehouseId == "wh-01" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getWarehouseRacks_re_emits_when_new_rack_added`() = runTest {
        val repo = FakeWarehouseRackRepository()
        val useCase = GetWarehouseRacksUseCase(repo)

        useCase("wh-01").test {
            val empty = awaitItem()
            assertTrue(empty.isEmpty())

            repo.insert(buildWarehouseRack(id = "rack-new", warehouseId = "wh-01"))

            val updated = awaitItem()
            assertEquals(1, updated.size)
            assertEquals("rack-new", updated.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── GetRackProductsUseCase ───────────────────────────────────────────────

    @Test
    fun `getRackProducts_returns_empty_flow_for_empty_rack`() = runTest {
        val repo = FakeRackProductRepository()
        val useCase = GetRackProductsUseCase(repo)

        useCase("rack-01").test {
            val list = awaitItem()
            assertTrue(list.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getRackProducts_only_returns_products_for_the_requested_rack`() = runTest {
        val repo = FakeRackProductRepository()
        repo.upsert(buildRackProduct(rackId = "rack-01", productId = "prod-A"))
        repo.upsert(buildRackProduct(rackId = "rack-01", productId = "prod-B"))
        repo.upsert(buildRackProduct(rackId = "rack-99", productId = "prod-C"))
        val useCase = GetRackProductsUseCase(repo)

        useCase("rack-01").test {
            val list = awaitItem()
            assertEquals(2, list.size, "Should return only products for rack-01")
            assertTrue(list.all { it.rackId == "rack-01" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getRackProducts_re_emits_after_upsert`() = runTest {
        val repo = FakeRackProductRepository()
        val useCase = GetRackProductsUseCase(repo)

        useCase("rack-01").test {
            assertTrue(awaitItem().isEmpty())

            repo.upsert(buildRackProduct(rackId = "rack-01", productId = "prod-new"))

            val updated = awaitItem()
            assertEquals(1, updated.size)
            assertEquals("prod-new", updated.first().productId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── SaveRackProductUseCase ───────────────────────────────────────────────

    @Test
    fun `saveRackProduct_blank_rackId_returns_validation_error`() = runTest {
        val repo = FakeRackProductRepository()
        val useCase = SaveRackProductUseCase(repo)

        val result = useCase(rackId = "  ", productId = "prod-01", quantity = 5.0, binLocation = null)

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
        assertTrue(repo.rackProducts.isEmpty(), "No write should occur on validation failure")
    }

    @Test
    fun `saveRackProduct_blank_productId_returns_validation_error`() = runTest {
        val repo = FakeRackProductRepository()
        val useCase = SaveRackProductUseCase(repo)

        val result = useCase(rackId = "rack-01", productId = "", quantity = 5.0, binLocation = null)

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
        assertTrue(repo.rackProducts.isEmpty())
    }

    @Test
    fun `saveRackProduct_negative_quantity_returns_validation_error`() = runTest {
        val repo = FakeRackProductRepository()
        val useCase = SaveRackProductUseCase(repo)

        val result = useCase(rackId = "rack-01", productId = "prod-01", quantity = -1.0, binLocation = null)

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
        assertTrue(repo.rackProducts.isEmpty())
    }

    @Test
    fun `saveRackProduct_zero_quantity_is_accepted`() = runTest {
        val repo = FakeRackProductRepository()
        val useCase = SaveRackProductUseCase(repo)

        val result = useCase(rackId = "rack-01", productId = "prod-01", quantity = 0.0, binLocation = null)

        assertIs<Result.Success<Unit>>(result)
        assertTrue(repo.upsertCalled)
    }

    @Test
    fun `saveRackProduct_valid_input_delegates_to_repository_upsert`() = runTest {
        val repo = FakeRackProductRepository()
        val useCase = SaveRackProductUseCase(repo)

        val result = useCase(
            rackId = "rack-01",
            productId = "prod-01",
            quantity = 25.0,
            binLocation = "Row-2",
        )

        assertIs<Result.Success<Unit>>(result)
        assertTrue(repo.upsertCalled)
        val saved = repo.lastUpsertedProduct
        assertNotNull(saved)
        assertEquals("rack-01", saved.rackId)
        assertEquals("prod-01", saved.productId)
        assertEquals(25.0, saved.quantity)
        assertEquals("Row-2", saved.binLocation)
    }

    @Test
    fun `saveRackProduct_null_binLocation_is_accepted`() = runTest {
        val repo = FakeRackProductRepository()
        val useCase = SaveRackProductUseCase(repo)

        val result = useCase(rackId = "rack-01", productId = "prod-01", quantity = 10.0, binLocation = null)

        assertIs<Result.Success<Unit>>(result)
        assertEquals(null, repo.lastUpsertedProduct?.binLocation)
    }

    @Test
    fun `saveRackProduct_blank_binLocation_is_stored_as_null`() = runTest {
        val repo = FakeRackProductRepository()
        val useCase = SaveRackProductUseCase(repo)

        useCase(rackId = "rack-01", productId = "prod-01", quantity = 5.0, binLocation = "   ")

        assertEquals(null, repo.lastUpsertedProduct?.binLocation, "Blank binLocation should be null-trimmed")
    }

    @Test
    fun `saveRackProduct_repository_failure_propagates_as_error`() = runTest {
        val repo = FakeRackProductRepository().also { it.shouldFail = true }
        val useCase = SaveRackProductUseCase(repo)

        val result = useCase(rackId = "rack-01", productId = "prod-01", quantity = 5.0, binLocation = null)

        assertIs<Result.Error>(result)
    }

    // ─── DeleteRackProductUseCase ─────────────────────────────────────────────

    @Test
    fun `deleteRackProduct_blank_rackId_returns_validation_error`() = runTest {
        val repo = FakeRackProductRepository()
        val useCase = DeleteRackProductUseCase(repo)

        val result = useCase(rackId = "   ", productId = "prod-01")

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
    }

    @Test
    fun `deleteRackProduct_blank_productId_returns_validation_error`() = runTest {
        val repo = FakeRackProductRepository()
        val useCase = DeleteRackProductUseCase(repo)

        val result = useCase(rackId = "rack-01", productId = "")

        assertIs<Result.Error>(result)
        assertIs<ValidationException>(result.exception)
    }

    @Test
    fun `deleteRackProduct_valid_ids_delegates_delete_to_repository`() = runTest {
        val repo = FakeRackProductRepository()
        repo.upsert(buildRackProduct(rackId = "rack-01", productId = "prod-01"))
        val useCase = DeleteRackProductUseCase(repo)

        val result = useCase(rackId = "rack-01", productId = "prod-01")

        assertIs<Result.Success<Unit>>(result)
        assertEquals("rack-01", repo.lastDeletedRackId)
        assertEquals("prod-01", repo.lastDeletedProductId)
        assertTrue(repo.rackProducts.isEmpty(), "Product should be removed after delete")
    }

    @Test
    fun `deleteRackProduct_non_existent_mapping_propagates_repository_error`() = runTest {
        val repo = FakeRackProductRepository()
        val useCase = DeleteRackProductUseCase(repo)

        val result = useCase(rackId = "rack-01", productId = "non-existent-prod")

        assertIs<Result.Error>(result)
    }

    @Test
    fun `deleteRackProduct_repository_failure_propagates_as_error`() = runTest {
        val repo = FakeRackProductRepository().also { it.shouldFail = true }
        val useCase = DeleteRackProductUseCase(repo)

        val result = useCase(rackId = "rack-01", productId = "prod-01")

        assertIs<Result.Error>(result)
    }
}
