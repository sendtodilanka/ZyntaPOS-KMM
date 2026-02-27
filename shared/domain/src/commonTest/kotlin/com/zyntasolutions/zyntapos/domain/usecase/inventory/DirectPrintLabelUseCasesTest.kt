package com.zyntasolutions.zyntapos.domain.usecase.inventory

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.LabelPrintItem
import com.zyntasolutions.zyntapos.domain.model.LabelTemplate
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeLabelPrinterPort
import com.zyntasolutions.zyntapos.domain.usecase.fakes.buildProduct
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit tests for direct hardware label-printing use cases:
 * [PrintTestLabelUseCase], [PrintLabelBatchUseCase].
 */
class DirectPrintLabelUseCasesTest {

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun buildTemplate() = LabelTemplate(
        id = "tmpl-01", name = "58mm Roll",
        paperType = LabelTemplate.PaperType.CONTINUOUS_ROLL,
        paperWidthMm = 58.0, labelHeightMm = 30.0,
        columns = 1, rows = 0,
        gapHorizontalMm = 0.0, gapVerticalMm = 2.0,
        marginTopMm = 2.0, marginBottomMm = 2.0,
        marginLeftMm = 3.0, marginRightMm = 3.0,
        isDefault = true, createdAt = 0L, updatedAt = 0L,
    )

    private fun buildItem(
        productId: String = "prod-01",
        barcode: String = "1234567890",
        copies: Int = 1,
    ) = LabelPrintItem(
        productId = productId,
        productName = "Test Product",
        barcode = barcode,
        price = 9.99,
        copies = copies,
    )

    // ─── PrintTestLabelUseCase ─────────────────────────────────────────────────

    @Test
    fun `test label - product with barcode - sends single label job`() = runTest {
        val port = FakeLabelPrinterPort()
        val useCase = PrintTestLabelUseCase(port)
        val product = buildProduct(id = "prod-01", barcode = "1234567890123")

        val result = useCase.execute(product, buildTemplate())

        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, port.labelJobs.size)
        assertEquals(1, port.labelJobs.first().items.size)
        assertEquals(1, port.labelJobs.first().items.first().copies)
    }

    @Test
    fun `test label - product without barcode - returns validation error`() = runTest {
        val port = FakeLabelPrinterPort()
        val useCase = PrintTestLabelUseCase(port)
        // Empty string barcode triggers validation
        val product = buildProduct(id = "prod-01", barcode = "")

        val result = useCase.execute(product, buildTemplate())

        assertIs<Result.Error>(result)
        assertTrue(port.labelJobs.isEmpty())
    }

    @Test
    fun `test label - printer fails - returns error`() = runTest {
        val port = FakeLabelPrinterPort().apply { shouldFail = true }
        val useCase = PrintTestLabelUseCase(port)
        val product = buildProduct(id = "prod-01", barcode = "1234567890")

        val result = useCase.execute(product, buildTemplate())

        assertIs<Result.Error>(result)
    }

    @Test
    fun `test label - always prints exactly 1 copy regardless of product stock qty`() = runTest {
        val port = FakeLabelPrinterPort()
        val useCase = PrintTestLabelUseCase(port)
        val product = buildProduct(id = "prod-01", barcode = "1234567890", stockQty = 500.0)

        useCase.execute(product, buildTemplate())

        assertEquals(1, port.labelJobs.first().items.first().copies)
    }

    @Test
    fun `test label - product name and price are propagated to label item`() = runTest {
        val port = FakeLabelPrinterPort()
        val useCase = PrintTestLabelUseCase(port)
        val product = buildProduct(id = "prod-01", name = "Deluxe Widget", barcode = "ABC", price = 29.99)

        useCase.execute(product, buildTemplate())

        val item = port.labelJobs.first().items.first()
        assertEquals("Deluxe Widget", item.productName)
        assertEquals(29.99, item.price)
        assertEquals("ABC", item.barcode)
    }

    // ─── PrintLabelBatchUseCase ────────────────────────────────────────────────

    @Test
    fun `batch print - non-empty items list - delegates to printer port`() = runTest {
        val port = FakeLabelPrinterPort()
        val useCase = PrintLabelBatchUseCase(port)
        val items = listOf(buildItem("p1"), buildItem("p2", "9876543210", 3))

        val result = useCase.execute(items, buildTemplate())

        assertIs<Result.Success<Unit>>(result)
        assertEquals(1, port.labelJobs.size)
        assertEquals(2, port.labelJobs.first().items.size)
    }

    @Test
    fun `batch print - empty items list - returns validation error`() = runTest {
        val port = FakeLabelPrinterPort()
        val useCase = PrintLabelBatchUseCase(port)

        val result = useCase.execute(emptyList(), buildTemplate())

        assertIs<Result.Error>(result)
        assertTrue(port.labelJobs.isEmpty())
    }

    @Test
    fun `batch print - printer fails - returns error`() = runTest {
        val port = FakeLabelPrinterPort().apply { shouldFail = true }
        val useCase = PrintLabelBatchUseCase(port)
        val items = listOf(buildItem())

        val result = useCase.execute(items, buildTemplate())

        assertIs<Result.Error>(result)
    }

    @Test
    fun `batch print - item copies preserved in delegated job`() = runTest {
        val port = FakeLabelPrinterPort()
        val useCase = PrintLabelBatchUseCase(port)
        val items = listOf(buildItem(copies = 5))

        useCase.execute(items, buildTemplate())

        assertEquals(5, port.labelJobs.first().items.first().copies)
    }

    @Test
    fun `batch print - template is propagated to printer port`() = runTest {
        val port = FakeLabelPrinterPort()
        val useCase = PrintLabelBatchUseCase(port)
        val template = buildTemplate()
        val items = listOf(buildItem())

        useCase.execute(items, template)

        assertEquals(template, port.labelJobs.first().template)
    }
}
