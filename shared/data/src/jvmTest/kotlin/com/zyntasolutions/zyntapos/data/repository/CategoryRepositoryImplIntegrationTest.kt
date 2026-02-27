package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.Category
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — CategoryRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [CategoryRepositoryImpl] against a real in-memory SQLite database
 * ([createTestDatabase]) — no mocking. Exercises the full SQL round-trip for
 * category CRUD, soft-delete, tree ordering, and FK constraint enforcement.
 *
 * Coverage:
 *  1. insert → getById round-trip preserves all fields
 *  2. getAll emits only active categories (Turbine)
 *  3. update changes the category name in the database
 *  4. delete soft-deletes the category (sets is_active = false)
 *  5. getTree returns parent categories before their children
 *  6. delete category referenced by active product returns ValidationException (CATEGORY_IN_USE)
 */
class CategoryRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: CategoryRepositoryImpl

    private val now get() = Clock.System.now().toEpochMilliseconds()

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        val syncEnqueuer = SyncEnqueuer(db)
        repo = CategoryRepositoryImpl(db, syncEnqueuer)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun makeCategory(
        id: String,
        name: String = "Test Category",
        parentId: String? = null,
        displayOrder: Int = 0,
        isActive: Boolean = true,
    ) = Category(
        id = id,
        name = name,
        parentId = parentId,
        imageUrl = null,
        displayOrder = displayOrder,
        isActive = isActive,
    )

    /**
     * Inserts a minimal product row that references [categoryId].
     * Used to exercise the CATEGORY_IN_USE FK guard in [CategoryRepositoryImpl.delete].
     */
    private fun insertProductWithCategory(productId: String, categoryId: String) {
        db.productsQueries.insertProduct(
            id            = productId,
            name          = "Test Product",
            barcode       = null,
            sku           = null,
            category_id   = categoryId,
            unit_id       = "pcs",
            price         = 9.99,
            cost_price    = 0.0,
            tax_group_id  = null,
            stock_qty     = 10.0,
            min_stock_qty = 2.0,
            image_url     = null,
            description   = null,
            is_active     = 1L,
            created_at    = now,
            updated_at    = now,
            sync_status   = "PENDING",
        )
    }

    // ── 1. insert → getById round-trip ────────────────────────────────────────

    @Test
    fun insert_then_getById_round_trip_preserves_all_fields() = runTest {
        val category = makeCategory(
            id           = "cat-1",
            name         = "Beverages",
            displayOrder = 1,
        )

        val insertResult = repo.insert(category)
        assertIs<Result.Success<Unit>>(insertResult)

        val getResult = repo.getById("cat-1")
        assertIs<Result.Success<Category>>(getResult)

        val retrieved = getResult.data
        assertEquals("cat-1",      retrieved.id)
        assertEquals("Beverages",  retrieved.name)
        assertEquals(1,            retrieved.displayOrder)
        assertEquals(null,         retrieved.parentId)
        assertTrue(retrieved.isActive)
    }

    // ── 2. getAll emits only active categories ────────────────────────────────

    @Test
    fun getAll_emits_only_active_categories() = runTest {
        repo.insert(makeCategory(id = "cat-active",   name = "Hot Drinks",   isActive = true))
        repo.insert(makeCategory(id = "cat-inactive", name = "Old Category", isActive = false))

        repo.getAll().test {
            val items = awaitItem()
            assertEquals(1, items.size, "Only active categories should be included")
            assertEquals("cat-active", items[0].id)
            assertEquals("Hot Drinks", items[0].name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── 3. update changes name in the database ────────────────────────────────

    @Test
    fun update_changes_category_name_in_database() = runTest {
        repo.insert(makeCategory(id = "cat-2", name = "Snacks"))

        val updated = makeCategory(id = "cat-2", name = "Healthy Snacks", displayOrder = 5)
        val updateResult = repo.update(updated)
        assertIs<Result.Success<Unit>>(updateResult)

        val getResult = repo.getById("cat-2")
        assertIs<Result.Success<Category>>(getResult)
        assertEquals("Healthy Snacks", getResult.data.name)
        assertEquals(5,                getResult.data.displayOrder)
    }

    // ── 4. delete soft-deletes the category ───────────────────────────────────

    @Test
    fun delete_soft_deletes_category_by_setting_isActive_false() = runTest {
        repo.insert(makeCategory(id = "cat-3", name = "Cold Drinks"))

        // Verify active before deletion
        val beforeResult = repo.getById("cat-3")
        assertIs<Result.Success<Category>>(beforeResult)
        assertTrue(beforeResult.data.isActive)

        val deleteResult = repo.delete("cat-3")
        assertIs<Result.Success<Unit>>(deleteResult)

        // getById returns the row but isActive must be false after soft-delete
        val afterResult = repo.getById("cat-3")
        assertIs<Result.Success<Category>>(afterResult)
        assertTrue(!afterResult.data.isActive, "Category should be inactive after soft-delete")

        // getAll (which filters is_active = 1) must not include it
        repo.getAll().test {
            val items = awaitItem()
            assertTrue(items.none { it.id == "cat-3" }, "Soft-deleted category must not appear in getAll")
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── 5. getTree returns parents before children ─────────────────────────────

    @Test
    fun getTree_returns_parent_categories_before_their_children() = runTest {
        // Insert child first to confirm ordering is by SQL, not insertion order
        repo.insert(makeCategory(id = "child-1",  name = "Hot Drinks",  parentId = "parent-1", displayOrder = 0))
        repo.insert(makeCategory(id = "parent-1", name = "Beverages",   parentId = null,        displayOrder = 0))

        repo.getTree().test {
            val tree = awaitItem()
            assertNotNull(tree.find { it.id == "parent-1" }, "Parent category must be present in tree")
            assertNotNull(tree.find { it.id == "child-1"  }, "Child category must be present in tree")

            val parentIndex = tree.indexOfFirst { it.id == "parent-1" }
            val childIndex  = tree.indexOfFirst { it.id == "child-1"  }
            assertTrue(
                parentIndex < childIndex,
                "Parent must appear before its child in the tree (parentIndex=$parentIndex, childIndex=$childIndex)",
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── 6. delete category with active products → ValidationException ─────────

    @Test
    fun delete_category_with_active_products_returns_CATEGORY_IN_USE_error() = runTest {
        repo.insert(makeCategory(id = "cat-used", name = "Electronics"))
        insertProductWithCategory(productId = "prod-1", categoryId = "cat-used")

        val deleteResult = repo.delete("cat-used")

        assertIs<Result.Error>(deleteResult, "Expected Result.Error but got $deleteResult")
        val exception = deleteResult.exception
        assertIs<ValidationException>(exception)
        assertEquals("CATEGORY_IN_USE", exception.rule,
            "Error rule should be CATEGORY_IN_USE but was '${exception.rule}'")
    }
}
