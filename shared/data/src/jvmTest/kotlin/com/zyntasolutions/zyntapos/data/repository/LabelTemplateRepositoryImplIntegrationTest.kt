package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.LabelTemplate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — LabelTemplateRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [LabelTemplateRepositoryImpl] against a real in-memory SQLite database.
 * No mocks — exercises the full SQLDelight query layer.
 *
 * Coverage:
 *  A. save → getById round-trip preserves all fields (including extended fields)
 *  B. getAll emits saved templates (Turbine)
 *  C. save with isDefault=true is preserved
 *  D. save twice (upsert) updates existing template
 *  E. delete removes template from DB
 *  F. getById for unknown ID returns Result.Error
 *  G. count returns correct number of templates
 *  H. delete a non-existent ID succeeds (no-op)
 */
class LabelTemplateRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: LabelTemplateRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = LabelTemplateRepositoryImpl(db)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun makeTemplate(
        id: String = "tmpl-01",
        name: String = "Standard Label",
        paperType: LabelTemplate.PaperType = LabelTemplate.PaperType.CONTINUOUS_ROLL,
        paperWidthMm: Double = 58.0,
        labelHeightMm: Double = 30.0,
        columns: Int = 1,
        rows: Int = 0,
        gapHorizontalMm: Double = 0.0,
        gapVerticalMm: Double = 2.0,
        marginTopMm: Double = 1.0,
        marginBottomMm: Double = 1.0,
        marginLeftMm: Double = 1.5,
        marginRightMm: Double = 1.5,
        isDefault: Boolean = false,
        showSalePrice: Boolean = false,
        salePriceLabel: String = "Sale",
        showExpiryDate: Boolean = false,
        showBatchNumber: Boolean = false,
        showSequentialSerial: Boolean = false,
        createdAt: Long = 1_000_000L,
        updatedAt: Long = 1_000_000L,
    ) = LabelTemplate(
        id = id,
        name = name,
        paperType = paperType,
        paperWidthMm = paperWidthMm,
        labelHeightMm = labelHeightMm,
        columns = columns,
        rows = rows,
        gapHorizontalMm = gapHorizontalMm,
        gapVerticalMm = gapVerticalMm,
        marginTopMm = marginTopMm,
        marginBottomMm = marginBottomMm,
        marginLeftMm = marginLeftMm,
        marginRightMm = marginRightMm,
        isDefault = isDefault,
        showSalePrice = showSalePrice,
        salePriceLabel = salePriceLabel,
        showExpiryDate = showExpiryDate,
        showBatchNumber = showBatchNumber,
        showSequentialSerial = showSequentialSerial,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - save then getById returns full template`() = runTest {
        val template = makeTemplate(
            id = "tmpl-01",
            name = "Pharmacy Label",
            paperType = LabelTemplate.PaperType.A4_SHEET,
            paperWidthMm = 210.0,
            labelHeightMm = 50.0,
            columns = 3,
            rows = 6,
            gapHorizontalMm = 3.0,
            gapVerticalMm = 2.5,
            marginTopMm = 5.0,
            marginBottomMm = 5.0,
            marginLeftMm = 7.0,
            marginRightMm = 7.0,
            isDefault = false,
            showSalePrice = true,
            salePriceLabel = "Sale",
            showExpiryDate = true,
            showBatchNumber = true,
            showSequentialSerial = false,
        )

        val saveResult = repo.save(template)
        assertIs<Result.Success<Unit>>(saveResult)

        val fetchResult = repo.getById("tmpl-01")
        assertIs<Result.Success<LabelTemplate>>(fetchResult)
        val fetched = fetchResult.data
        assertEquals("tmpl-01", fetched.id)
        assertEquals("Pharmacy Label", fetched.name)
        assertEquals(LabelTemplate.PaperType.A4_SHEET, fetched.paperType)
        assertEquals(210.0, fetched.paperWidthMm)
        assertEquals(50.0, fetched.labelHeightMm)
        assertEquals(3, fetched.columns)
        assertEquals(6, fetched.rows)
        assertEquals(3.0, fetched.gapHorizontalMm)
        assertEquals(2.5, fetched.gapVerticalMm)
        assertEquals(5.0, fetched.marginTopMm)
        assertEquals(5.0, fetched.marginBottomMm)
        assertEquals(7.0, fetched.marginLeftMm)
        assertEquals(7.0, fetched.marginRightMm)
        assertTrue(fetched.showSalePrice)
        assertEquals("Sale", fetched.salePriceLabel)
        assertTrue(fetched.showExpiryDate)
        assertTrue(fetched.showBatchNumber)
        assertTrue(!fetched.showSequentialSerial)
    }

    @Test
    fun `B - getAll emits saved templates`() = runTest {
        repo.save(makeTemplate(id = "tmpl-01", name = "Label A"))
        repo.save(makeTemplate(id = "tmpl-02", name = "Label B"))

        repo.getAll().test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertTrue(list.any { it.name == "Label A" })
            assertTrue(list.any { it.name == "Label B" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `C - save with isDefault=true is persisted`() = runTest {
        repo.save(makeTemplate(id = "tmpl-01", isDefault = true))

        val fetched = (repo.getById("tmpl-01") as Result.Success).data
        assertTrue(fetched.isDefault)
    }

    @Test
    fun `D - save twice updates existing template (upsert)`() = runTest {
        repo.save(makeTemplate(id = "tmpl-01", name = "Original Name"))

        val updated = makeTemplate(id = "tmpl-01", name = "Updated Name", columns = 2)
        val updateResult = repo.save(updated)
        assertIs<Result.Success<Unit>>(updateResult)

        val fetched = (repo.getById("tmpl-01") as Result.Success).data
        assertEquals("Updated Name", fetched.name)
        assertEquals(2, fetched.columns)
    }

    @Test
    fun `E - delete removes template from DB`() = runTest {
        repo.save(makeTemplate(id = "tmpl-01", name = "To Delete"))

        val deleteResult = repo.delete("tmpl-01")
        assertIs<Result.Success<Unit>>(deleteResult)

        val fetchResult = repo.getById("tmpl-01")
        assertIs<Result.Error>(fetchResult)

        repo.getAll().test {
            val list = awaitItem()
            assertTrue(list.none { it.id == "tmpl-01" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `F - getById for unknown ID returns error`() = runTest {
        val result = repo.getById("non-existent")
        assertIs<Result.Error>(result)
        assertNotNull((result as Result.Error).exception)
    }

    @Test
    fun `G - count returns correct number of templates`() = runTest {
        assertEquals(0, repo.count())

        repo.save(makeTemplate(id = "tmpl-01"))
        assertEquals(1, repo.count())

        repo.save(makeTemplate(id = "tmpl-02"))
        assertEquals(2, repo.count())

        repo.delete("tmpl-01")
        assertEquals(1, repo.count())
    }

    @Test
    fun `H - delete non-existent ID succeeds as no-op`() = runTest {
        val result = repo.delete("non-existent")
        assertIs<Result.Success<Unit>>(result)
    }
}
