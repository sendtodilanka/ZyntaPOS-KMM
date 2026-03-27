package com.zyntasolutions.zyntapos.data.repository

import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.CompoundTaxComponent
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — CompoundTaxRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [CompoundTaxRepositoryImpl] against a real in-memory SQLite database.
 * Requires tax_groups seeded (INNER JOIN in getComponentsForTaxGroup query).
 *
 * Coverage:
 *  A. insertComponent → getComponentsForTaxGroup returns component with tax_group fields
 *  B. getComponentsForTaxGroup returns empty list when no components exist
 *  C. getAllCompoundTaxGroupIds returns distinct parent IDs
 *  D. deleteComponent removes a single component
 *  E. deleteAllForTaxGroup removes all components for a parent
 *  F. application_order preserved correctly
 */
class CompoundTaxRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: CompoundTaxRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = CompoundTaxRepositoryImpl(db)

        val now = Clock.System.now().toEpochMilliseconds()

        // Seed tax groups required by the INNER JOIN in getComponentsForTaxGroup
        db.tax_groupsQueries.insertTaxGroup(
            "tg-parent", "Compound Tax", 0.0, 0L, 1L, now, now, null, "PENDING",
        )
        db.tax_groupsQueries.insertTaxGroup(
            "tg-vat", "VAT 15%", 0.15, 0L, 1L, now, now, null, "PENDING",
        )
        db.tax_groupsQueries.insertTaxGroup(
            "tg-svc", "Service Charge 10%", 0.10, 0L, 1L, now, now, null, "PENDING",
        )
        db.tax_groupsQueries.insertTaxGroup(
            "tg-local", "Local Surcharge 2%", 0.02, 0L, 1L, now, now, null, "PENDING",
        )
        db.tax_groupsQueries.insertTaxGroup(
            "tg-parent-2", "Another Compound Tax", 0.0, 0L, 1L, now, now, null, "PENDING",
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun makeComponent(
        id: String,
        parentTaxGroupId: String = "tg-parent",
        componentTaxGroupId: String = "tg-vat",
        applicationOrder: Int = 0,
        isCompounding: Boolean = false,
    ) = CompoundTaxComponent(
        id = id,
        parentTaxGroupId = parentTaxGroupId,
        componentTaxGroupId = componentTaxGroupId,
        componentName = "",  // populated by join on read
        componentRate = 0.0, // populated by join on read
        componentIsInclusive = false, // populated by join on read
        applicationOrder = applicationOrder,
        isCompounding = isCompounding,
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - insertComponent then getComponentsForTaxGroup returns component with tax group fields`() = runTest {
        val insertResult = repo.insertComponent(makeComponent(
            id = "ctc-01",
            parentTaxGroupId = "tg-parent",
            componentTaxGroupId = "tg-vat",
            applicationOrder = 1,
        ))
        assertIs<Result.Success<Unit>>(insertResult)

        val fetchResult = repo.getComponentsForTaxGroup("tg-parent")
        assertIs<Result.Success<List<CompoundTaxComponent>>>(fetchResult)
        val components = fetchResult.data
        assertEquals(1, components.size)
        val comp = components.first()
        assertEquals("ctc-01", comp.id)
        assertEquals("tg-parent", comp.parentTaxGroupId)
        assertEquals("tg-vat", comp.componentTaxGroupId)
        assertEquals("VAT 15%", comp.componentName)
        assertEquals(0.15, comp.componentRate)
        assertEquals(1, comp.applicationOrder)
    }

    @Test
    fun `B - getComponentsForTaxGroup returns empty list when no components exist`() = runTest {
        val result = repo.getComponentsForTaxGroup("tg-parent")
        assertIs<Result.Success<List<CompoundTaxComponent>>>(result)
        assertTrue(result.data.isEmpty())
    }

    @Test
    fun `C - getAllCompoundTaxGroupIds returns distinct parent IDs`() = runTest {
        repo.insertComponent(makeComponent(id = "ctc-01", parentTaxGroupId = "tg-parent", componentTaxGroupId = "tg-vat"))
        repo.insertComponent(makeComponent(id = "ctc-02", parentTaxGroupId = "tg-parent", componentTaxGroupId = "tg-svc"))
        repo.insertComponent(makeComponent(id = "ctc-03", parentTaxGroupId = "tg-parent-2", componentTaxGroupId = "tg-local"))

        val result = repo.getAllCompoundTaxGroupIds()
        assertIs<Result.Success<List<String>>>(result)
        val ids = result.data
        assertEquals(2, ids.size)
        assertTrue(ids.contains("tg-parent"))
        assertTrue(ids.contains("tg-parent-2"))
    }

    @Test
    fun `D - deleteComponent removes a single component`() = runTest {
        repo.insertComponent(makeComponent(id = "ctc-01", componentTaxGroupId = "tg-vat"))
        repo.insertComponent(makeComponent(id = "ctc-02", componentTaxGroupId = "tg-svc"))

        val deleteResult = repo.deleteComponent("ctc-01")
        assertIs<Result.Success<Unit>>(deleteResult)

        val fetchResult = repo.getComponentsForTaxGroup("tg-parent")
        assertIs<Result.Success<List<CompoundTaxComponent>>>(fetchResult)
        assertEquals(1, fetchResult.data.size)
        assertEquals("ctc-02", fetchResult.data.first().id)
    }

    @Test
    fun `E - deleteAllForTaxGroup removes all components for a parent`() = runTest {
        repo.insertComponent(makeComponent(id = "ctc-01", parentTaxGroupId = "tg-parent", componentTaxGroupId = "tg-vat"))
        repo.insertComponent(makeComponent(id = "ctc-02", parentTaxGroupId = "tg-parent", componentTaxGroupId = "tg-svc"))
        repo.insertComponent(makeComponent(id = "ctc-03", parentTaxGroupId = "tg-parent-2", componentTaxGroupId = "tg-local"))

        val deleteResult = repo.deleteAllForTaxGroup("tg-parent")
        assertIs<Result.Success<Unit>>(deleteResult)

        val parentResult = repo.getComponentsForTaxGroup("tg-parent")
        assertIs<Result.Success<List<CompoundTaxComponent>>>(parentResult)
        assertTrue(parentResult.data.isEmpty())

        // Other parent's components should be unaffected
        val otherResult = repo.getComponentsForTaxGroup("tg-parent-2")
        assertIs<Result.Success<List<CompoundTaxComponent>>>(otherResult)
        assertEquals(1, otherResult.data.size)
    }

    @Test
    fun `F - components returned in application_order`() = runTest {
        repo.insertComponent(makeComponent(id = "ctc-03", componentTaxGroupId = "tg-local", applicationOrder = 3))
        repo.insertComponent(makeComponent(id = "ctc-01", componentTaxGroupId = "tg-vat", applicationOrder = 1))
        repo.insertComponent(makeComponent(id = "ctc-02", componentTaxGroupId = "tg-svc", applicationOrder = 2))

        val result = repo.getComponentsForTaxGroup("tg-parent")
        assertIs<Result.Success<List<CompoundTaxComponent>>>(result)
        val components = result.data
        assertEquals(3, components.size)
        assertEquals(1, components[0].applicationOrder)
        assertEquals(2, components[1].applicationOrder)
        assertEquals(3, components[2].applicationOrder)
    }
}
