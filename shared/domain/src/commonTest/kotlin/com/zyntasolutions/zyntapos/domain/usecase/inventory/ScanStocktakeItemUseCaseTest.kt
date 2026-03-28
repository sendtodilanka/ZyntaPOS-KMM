package com.zyntasolutions.zyntapos.domain.usecase.inventory

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeProductRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeStocktakeRepository
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildProduct
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — ScanStocktakeItemUseCase Unit Tests (commonTest)
 *
 * Coverage:
 *  A.  Blank barcode returns BARCODE_BLANK error
 *  B.  Whitespace-only barcode returns BARCODE_BLANK error
 *  C.  Unknown barcode returns product-not-found error
 *  D.  First scan of a new barcode sets countedQty=1
 *  E.  Second scan of same barcode increments countedQty from 1 to 2
 *  F.  Returned StocktakeCount has correct productId, barcode, productName, systemQty
 *  G.  Count is persisted in the repository
 */
class ScanStocktakeItemUseCaseTest {

    private fun makeUseCase(): Triple<ScanStocktakeItemUseCase, FakeProductRepository, FakeStocktakeRepository> {
        val productRepo = FakeProductRepository()
        val stocktakeRepo = FakeStocktakeRepository()
        return Triple(ScanStocktakeItemUseCase(productRepo, stocktakeRepo), productRepo, stocktakeRepo)
    }

    @Test
    fun `A - blank barcode returns BARCODE_BLANK error`() = runTest {
        val (useCase, _, _) = makeUseCase()
        val result = useCase.execute(sessionId = "sess-1", barcode = "")
        assertIs<Result.Error>(result)
        val ex = result.exception as ValidationException
        assertEquals("BARCODE_BLANK", ex.rule)
        assertEquals("barcode", ex.field)
    }

    @Test
    fun `B - whitespace-only barcode returns BARCODE_BLANK error`() = runTest {
        val (useCase, _, _) = makeUseCase()
        val result = useCase.execute(sessionId = "sess-1", barcode = "   ")
        assertIs<Result.Error>(result)
        assertEquals("BARCODE_BLANK", (result.exception as ValidationException).rule)
    }

    @Test
    fun `C - unknown barcode returns product-not-found error`() = runTest {
        val (useCase, _, stocktakeRepo) = makeUseCase()
        stocktakeRepo.counts["sess-1"] = mutableMapOf()
        val result = useCase.execute(sessionId = "sess-1", barcode = "9999999999")
        assertIs<Result.Error>(result)
    }

    @Test
    fun `D - first scan of new barcode sets countedQty to 1`() = runTest {
        val (useCase, productRepo, stocktakeRepo) = makeUseCase()
        productRepo.products.add(buildProduct(id = "p1", barcode = "1234567890", stockQty = 50.0))
        stocktakeRepo.counts["sess-1"] = mutableMapOf()

        val result = useCase.execute(sessionId = "sess-1", barcode = "1234567890")
        assertIs<Result.Success<*>>(result)
        assertEquals(1, (result as Result.Success).data.countedQty)
    }

    @Test
    fun `E - second scan increments countedQty from 1 to 2`() = runTest {
        val (useCase, productRepo, stocktakeRepo) = makeUseCase()
        productRepo.products.add(buildProduct(id = "p1", barcode = "1234567890"))
        stocktakeRepo.counts["sess-1"] = mutableMapOf("1234567890" to 1)

        val result = useCase.execute(sessionId = "sess-1", barcode = "1234567890")
        assertIs<Result.Success<*>>(result)
        assertEquals(2, (result as Result.Success).data.countedQty)
    }

    @Test
    fun `F - returned StocktakeCount has correct fields`() = runTest {
        val (useCase, productRepo, stocktakeRepo) = makeUseCase()
        productRepo.products.add(
            buildProduct(id = "p-abc", name = "Milk Powder", barcode = "555555", stockQty = 30.0)
        )
        stocktakeRepo.counts["sess-1"] = mutableMapOf()

        val result = useCase.execute(sessionId = "sess-1", barcode = "555555")
        assertIs<Result.Success<*>>(result)
        val count = (result as Result.Success).data
        assertEquals("p-abc", count.productId)
        assertEquals("555555", count.barcode)
        assertEquals("Milk Powder", count.productName)
        assertEquals(30, count.systemQty)
        assertEquals(1, count.countedQty)
    }

    @Test
    fun `G - counted quantity persisted in repository`() = runTest {
        val (useCase, productRepo, stocktakeRepo) = makeUseCase()
        productRepo.products.add(buildProduct(barcode = "ABC"))
        stocktakeRepo.counts["sess-1"] = mutableMapOf()

        useCase.execute(sessionId = "sess-1", barcode = "ABC")

        assertEquals(1, stocktakeRepo.counts["sess-1"]?.get("ABC"))
    }
}
