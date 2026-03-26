package com.zyntasolutions.zyntapos.feature.inventory

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.pagination.PageRequest
import com.zyntasolutions.zyntapos.core.pagination.PaginatedResult
import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.LabelTemplate
import com.zyntasolutions.zyntapos.domain.model.Product
import com.zyntasolutions.zyntapos.domain.repository.LabelTemplateRepository
import com.zyntasolutions.zyntapos.domain.repository.ProductRepository
import com.zyntasolutions.zyntapos.domain.usecase.inventory.DeleteLabelTemplateUseCase
import com.zyntasolutions.zyntapos.domain.usecase.inventory.GetLabelTemplatesUseCase
import com.zyntasolutions.zyntapos.domain.usecase.inventory.SaveLabelTemplateUseCase
import com.zyntasolutions.zyntapos.domain.usecase.inventory.SeedDefaultLabelTemplatesUseCase
import com.zyntasolutions.zyntapos.feature.inventory.label.BarcodeLabelPrintEffect
import com.zyntasolutions.zyntapos.feature.inventory.label.BarcodeLabelPrintIntent
import com.zyntasolutions.zyntapos.feature.inventory.label.BarcodeLabelPrintViewModel
import com.zyntasolutions.zyntapos.feature.inventory.label.LabelPdfRenderer
import com.zyntasolutions.zyntapos.feature.inventory.label.PrintQueueItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [BarcodeLabelPrintViewModel].
 *
 * Focuses on pure-state operations (queue management, template selection,
 * editor toggling, error/success dismissal) and error guards on print
 * operations, which can be tested without a real [LabelPdfRenderer].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BarcodeLabelPrintViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val mockTemplate = LabelTemplate(
        id = "tmpl-001",
        name = "Standard 58mm",
        paperType = LabelTemplate.PaperType.CONTINUOUS_ROLL,
        paperWidthMm = 58.0,
        labelHeightMm = 30.0,
        columns = 1,
        rows = 0,
        gapHorizontalMm = 0.0,
        gapVerticalMm = 2.0,
        marginTopMm = 2.0,
        marginBottomMm = 2.0,
        marginLeftMm = 2.0,
        marginRightMm = 2.0,
        isDefault = true,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L,
    )

    private val mockProduct = Product(
        id = "prod-001",
        name = "Espresso Beans",
        barcode = "1234567890123",
        sku = "SKU-001",
        categoryId = "cat-001",
        unitId = "unit-001",
        price = 1500.0,
        stockQty = 50.0,
        createdAt = Instant.fromEpochMilliseconds(1_700_000_000_000L),
        updatedAt = Instant.fromEpochMilliseconds(1_700_000_000_000L),
    )

    private val templatesFlow = MutableStateFlow(listOf(mockTemplate))
    private var saveTemplateResult: Result<Unit> = Result.Success(Unit)
    private var deleteTemplateResult: Result<Unit> = Result.Success(Unit)

    private val fakeLabelTemplateRepo = object : LabelTemplateRepository {
        override fun getAll(): Flow<List<LabelTemplate>> = templatesFlow
        override suspend fun getById(id: String): Result<LabelTemplate> = Result.Success(mockTemplate)
        override suspend fun save(template: LabelTemplate): Result<Unit> = saveTemplateResult
        override suspend fun delete(id: String): Result<Unit> = deleteTemplateResult
        override suspend fun count(): Int = templatesFlow.value.size
    }

    private val fakeProductRepo = object : ProductRepository {
        override fun getAll(): Flow<List<Product>> = MutableStateFlow(listOf(mockProduct))
        override suspend fun getById(id: String): Result<Product> = Result.Success(mockProduct)
        override fun search(query: String, categoryId: String?): Flow<List<Product>> =
            MutableStateFlow(emptyList())
        override suspend fun getByBarcode(barcode: String): Result<Product> = Result.Success(mockProduct)
        override suspend fun insert(product: Product): Result<Unit> = Result.Success(Unit)
        override suspend fun update(product: Product): Result<Unit> = Result.Success(Unit)
        override suspend fun delete(id: String): Result<Unit> = Result.Success(Unit)
        override suspend fun getCount(): Int = 1
        override suspend fun getPage(pageRequest: PageRequest, categoryId: String?, searchQuery: String?): PaginatedResult<Product> =
            PaginatedResult(items = emptyList(), totalCount = 0L, hasMore = false)
    }

    private val fakeLabelPdfRenderer = object : LabelPdfRenderer {
        override suspend fun render(items: List<PrintQueueItem>, template: LabelTemplate): ByteArray =
            ByteArray(256) { it.toByte() }
    }

    private lateinit var viewModel: BarcodeLabelPrintViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = BarcodeLabelPrintViewModel(
            productRepository = fakeProductRepo,
            getLabelTemplatesUseCase = GetLabelTemplatesUseCase(fakeLabelTemplateRepo),
            saveLabelTemplateUseCase = SaveLabelTemplateUseCase(fakeLabelTemplateRepo),
            deleteLabelTemplateUseCase = DeleteLabelTemplateUseCase(fakeLabelTemplateRepo),
            seedDefaultLabelTemplatesUseCase = SeedDefaultLabelTemplatesUseCase(fakeLabelTemplateRepo),
            labelPdfRenderer = fakeLabelPdfRenderer,
        )
    }

    @AfterTest
    fun teardown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ──────────────────────────────────────────────────────────

    @Test
    fun `initial state has empty queue and no error`() {
        val state = viewModel.state.value
        assertTrue(state.queue.isEmpty())
        assertFalse(state.isPrinting)
        assertFalse(state.isTemplateEditorOpen)
        assertNull(state.editingTemplate)
        assertNull(state.error)
    }

    @Test
    fun `templates are loaded from repository on init`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.state.value.templates.size)
        assertNotNull(viewModel.state.value.selectedTemplate)
    }

    @Test
    fun `default template is selected on init`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("tmpl-001", viewModel.state.value.selectedTemplate?.id)
    }

    // ── Queue operations ───────────────────────────────────────────────────────

    @Test
    fun `AddToQueue adds product to queue`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.handleIntentForTest(BarcodeLabelPrintIntent.AddToQueue(mockProduct))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.state.value.queue.size)
        assertEquals("prod-001", viewModel.state.value.queue.first().productId)
    }

    @Test
    fun `AddToQueue increments quantity for duplicate product`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.handleIntentForTest(BarcodeLabelPrintIntent.AddToQueue(mockProduct))
        viewModel.handleIntentForTest(BarcodeLabelPrintIntent.AddToQueue(mockProduct))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.state.value.queue.size) // same product, not two items
        assertEquals(2, viewModel.state.value.queue.first().quantity)
    }

    @Test
    fun `RemoveFromQueue removes the item`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.handleIntentForTest(BarcodeLabelPrintIntent.AddToQueue(mockProduct))
        testDispatcher.scheduler.advanceUntilIdle()
        val itemId = viewModel.state.value.queue.first().id

        viewModel.handleIntentForTest(BarcodeLabelPrintIntent.RemoveFromQueue(itemId))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.queue.isEmpty())
    }

    @Test
    fun `SetQuantity updates the item quantity`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.handleIntentForTest(BarcodeLabelPrintIntent.AddToQueue(mockProduct))
        testDispatcher.scheduler.advanceUntilIdle()
        val itemId = viewModel.state.value.queue.first().id

        viewModel.handleIntentForTest(BarcodeLabelPrintIntent.SetQuantity(itemId, 5))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(5, viewModel.state.value.queue.first().quantity)
    }

    @Test
    fun `SetQuantity with zero removes the item`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.handleIntentForTest(BarcodeLabelPrintIntent.AddToQueue(mockProduct))
        testDispatcher.scheduler.advanceUntilIdle()
        val itemId = viewModel.state.value.queue.first().id

        viewModel.handleIntentForTest(BarcodeLabelPrintIntent.SetQuantity(itemId, 0))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.queue.isEmpty())
    }

    @Test
    fun `ClearQueue empties the queue`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.handleIntentForTest(BarcodeLabelPrintIntent.AddToQueue(mockProduct))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.state.value.queue.size)

        viewModel.handleIntentForTest(BarcodeLabelPrintIntent.ClearQueue)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.queue.isEmpty())
    }

    // ── Template selection ─────────────────────────────────────────────────────

    @Test
    fun `SelectTemplate updates selectedTemplate`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        val newTemplate = mockTemplate.copy(id = "tmpl-002", name = "A4 Sheet")
        viewModel.state.test {
            awaitItem()
            viewModel.handleIntentForTest(BarcodeLabelPrintIntent.SelectTemplate(newTemplate))
            val updated = awaitItem()
            assertEquals("tmpl-002", updated.selectedTemplate?.id)
        }
    }

    // ── Template editor ────────────────────────────────────────────────────────

    @Test
    fun `OpenNewTemplateEditor sets isTemplateEditorOpen to true`() = runTest {
        viewModel.state.test {
            awaitItem()
            viewModel.handleIntentForTest(BarcodeLabelPrintIntent.OpenNewTemplateEditor)
            val updated = awaitItem()
            assertTrue(updated.isTemplateEditorOpen)
            assertNull(updated.editingTemplate)
        }
    }

    @Test
    fun `OpenEditTemplateEditor sets isTemplateEditorOpen and editingTemplate`() = runTest {
        viewModel.state.test {
            awaitItem()
            viewModel.handleIntentForTest(BarcodeLabelPrintIntent.OpenEditTemplateEditor(mockTemplate))
            val updated = awaitItem()
            assertTrue(updated.isTemplateEditorOpen)
            assertEquals("tmpl-001", updated.editingTemplate?.id)
        }
    }

    @Test
    fun `DismissTemplateEditor closes editor and clears editingTemplate`() = runTest {
        viewModel.handleIntentForTest(BarcodeLabelPrintIntent.OpenNewTemplateEditor)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value.isTemplateEditorOpen)

        viewModel.state.test {
            awaitItem()
            viewModel.handleIntentForTest(BarcodeLabelPrintIntent.DismissTemplateEditor)
            val updated = awaitItem()
            assertFalse(updated.isTemplateEditorOpen)
            assertNull(updated.editingTemplate)
        }
    }

    // ── Template persistence ───────────────────────────────────────────────────

    @Test
    fun `SaveTemplate success closes editor`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        saveTemplateResult = Result.Success(Unit)

        viewModel.handleIntentForTest(BarcodeLabelPrintIntent.OpenEditTemplateEditor(mockTemplate))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.handleIntentForTest(BarcodeLabelPrintIntent.SaveTemplate(mockTemplate))
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.isTemplateEditorOpen)
        assertNull(viewModel.state.value.editingTemplate)
    }

    @Test
    fun `DeleteTemplate failure emits ShowError effect`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        deleteTemplateResult = Result.Error(DatabaseException("Cannot delete default template"))

        viewModel.effects.test {
            viewModel.handleIntentForTest(BarcodeLabelPrintIntent.DeleteTemplate("tmpl-001"))
            testDispatcher.scheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is BarcodeLabelPrintEffect.ShowError)
        }
    }

    // ── Print guards ───────────────────────────────────────────────────────────

    @Test
    fun `PrintLabels with empty queue emits ShowError effect`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        // Template is selected (default), but queue is empty

        viewModel.effects.test {
            viewModel.handleIntentForTest(BarcodeLabelPrintIntent.PrintLabels)
            testDispatcher.scheduler.advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is BarcodeLabelPrintEffect.ShowError)
            assertTrue((effect as BarcodeLabelPrintEffect.ShowError).msg.contains("empty", ignoreCase = true))
        }
    }

    // ── DismissError / DismissSuccess ─────────────────────────────────────────

    @Test
    fun `DismissError clears error in state`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        deleteTemplateResult = Result.Error(DatabaseException("error"))

        viewModel.handleIntentForTest(BarcodeLabelPrintIntent.DeleteTemplate("tmpl-001"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.handleIntentForTest(BarcodeLabelPrintIntent.DismissError)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.state.value.error)
    }
}

// ─── Extension to expose handleIntent for testing ────────────────────────────

private fun BarcodeLabelPrintViewModel.handleIntentForTest(intent: BarcodeLabelPrintIntent) =
    dispatch(intent)
