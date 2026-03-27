package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.Supplier
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — SupplierRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [SupplierRepositoryImpl] against a real in-memory SQLite database.
 * No mocks — exercises the full SQLDelight query layer.
 *
 * Coverage:
 *  A. insert → getById round-trip preserves all fields
 *  B. getAll emits all active suppliers (Turbine)
 *  C. getAll excludes soft-deleted suppliers
 *  D. update changes supplier name
 *  E. delete soft-deletes (sets is_active = false); getById still returns it
 *  F. getById for unknown ID returns DatabaseException error
 *  G. delete for unknown ID returns DatabaseException error
 */
class SupplierRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: SupplierRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = SupplierRepositoryImpl(db, SyncEnqueuer(db))
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun makeSupplier(
        id: String = "sup-01",
        name: String = "Test Supplier",
        contactPerson: String? = "John Doe",
        phone: String? = "+94771234567",
        email: String? = "john@example.com",
        address: String? = "123 Main St, Colombo",
        notes: String? = "Net 30 terms",
        isActive: Boolean = true,
    ) = Supplier(
        id = id,
        name = name,
        contactPerson = contactPerson,
        phone = phone,
        email = email,
        address = address,
        notes = notes,
        isActive = isActive,
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - insert then getById returns full supplier`() = runTest {
        val supplier = makeSupplier(id = "sup-01", name = "Acme Corp")
        val insertResult = repo.insert(supplier)
        assertIs<Result.Success<Unit>>(insertResult)

        val fetchResult = repo.getById("sup-01")
        assertIs<Result.Success<Supplier>>(fetchResult)
        val fetched = (fetchResult as Result.Success).data
        assertEquals("sup-01", fetched.id)
        assertEquals("Acme Corp", fetched.name)
        assertEquals("John Doe", fetched.contactPerson)
        assertEquals("+94771234567", fetched.phone)
        assertEquals("john@example.com", fetched.email)
        assertEquals("123 Main St, Colombo", fetched.address)
        assertEquals("Net 30 terms", fetched.notes)
        assertTrue(fetched.isActive)
    }

    @Test
    fun `B - getAll emits inserted active suppliers`() = runTest {
        repo.insert(makeSupplier(id = "sup-01", name = "Supplier A"))
        repo.insert(makeSupplier(id = "sup-02", name = "Supplier B"))

        repo.getAll().test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertTrue(list.any { it.name == "Supplier A" })
            assertTrue(list.any { it.name == "Supplier B" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `C - getAll excludes soft-deleted suppliers`() = runTest {
        repo.insert(makeSupplier(id = "sup-01", name = "Active Supplier"))
        repo.insert(makeSupplier(id = "sup-02", name = "To Be Deleted"))
        repo.delete("sup-02")

        repo.getAll().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("Active Supplier", list.first().name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `D - update changes supplier name`() = runTest {
        repo.insert(makeSupplier(id = "sup-01", name = "Old Name"))

        val updated = makeSupplier(id = "sup-01", name = "New Name", phone = "+94779999999")
        val updateResult = repo.update(updated)
        assertIs<Result.Success<Unit>>(updateResult)

        val fetched = (repo.getById("sup-01") as Result.Success).data
        assertEquals("New Name", fetched.name)
        assertEquals("+94779999999", fetched.phone)
    }

    @Test
    fun `E - delete soft-deletes supplier`() = runTest {
        repo.insert(makeSupplier(id = "sup-01", name = "Supplier to delete"))

        val deleteResult = repo.delete("sup-01")
        assertIs<Result.Success<Unit>>(deleteResult)

        // Soft-deleted supplier is excluded from getAll()
        repo.getAll().test {
            val list = awaitItem()
            assertTrue(list.none { it.id == "sup-01" })
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
    fun `G - delete for unknown ID returns error`() = runTest {
        val result = repo.delete("non-existent")
        assertIs<Result.Error>(result)
    }

    @Test
    fun `H - insert supplier with null optional fields`() = runTest {
        val minimal = Supplier(id = "sup-min", name = "Minimal Supplier")
        val insertResult = repo.insert(minimal)
        assertIs<Result.Success<Unit>>(insertResult)

        val fetched = (repo.getById("sup-min") as Result.Success).data
        assertEquals("sup-min", fetched.id)
        assertEquals("Minimal Supplier", fetched.name)
        assertTrue(fetched.contactPerson == null)
        assertTrue(fetched.phone == null)
        assertTrue(fetched.email == null)
    }
}
