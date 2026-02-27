package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.data.local.SyncEnqueuer
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.Customer
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — CustomerRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [CustomerRepositoryImpl] against a real in-memory SQLite database
 * ([createTestDatabase]) — no mocking. Exercises the full SQL round-trip for
 * customer CRUD, FTS5 search, and soft-delete.
 *
 * Coverage:
 *  1. insert → getById round-trip preserves all fields
 *  2. search by name returns matching customers (FTS5 prefix match)
 *  3. search by phone returns matching customers (FTS5 prefix match)
 *  4. update changes customer fields in the database
 *  5. delete soft-deletes the customer; deleted customer no longer appears in getAll
 */
class CustomerRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: CustomerRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        val syncEnqueuer = SyncEnqueuer(db)
        repo = CustomerRepositoryImpl(db, syncEnqueuer)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun makeCustomer(
        id: String,
        name: String = "Jane Doe",
        phone: String = "0771234567",
        email: String? = null,
        isActive: Boolean = true,
    ) = Customer(
        id       = id,
        name     = name,
        phone    = phone,
        email    = email,
        isActive = isActive,
    )

    // ── 1. insert → getById round-trip ────────────────────────────────────────

    @Test
    fun insert_then_getById_round_trip_preserves_all_fields() = runTest {
        val customer = makeCustomer(
            id    = "cust-1",
            name  = "Alice Perera",
            phone = "0711111111",
            email = "alice@example.com",
        )

        val insertResult = repo.insert(customer)
        assertIs<Result.Success<Unit>>(insertResult)

        val getResult = repo.getById("cust-1")
        assertIs<Result.Success<Customer>>(getResult)

        val retrieved = getResult.data
        assertEquals("cust-1",            retrieved.id)
        assertEquals("Alice Perera",      retrieved.name)
        assertEquals("0711111111",        retrieved.phone)
        assertEquals("alice@example.com", retrieved.email)
        assertTrue(retrieved.isActive)
    }

    // ── 2. search by name returns matching customers ──────────────────────────

    @Test
    fun search_by_name_returns_matching_customers() = runTest {
        repo.insert(makeCustomer(id = "cust-a", name = "John Smith",   phone = "0770000001"))
        repo.insert(makeCustomer(id = "cust-b", name = "Johnny Walker", phone = "0770000002"))
        repo.insert(makeCustomer(id = "cust-c", name = "Bob Brown",    phone = "0770000003"))

        repo.search("John").test {
            val results = awaitItem()
            assertEquals(2, results.size, "Both 'John Smith' and 'Johnny Walker' should match 'John'")
            assertTrue(results.any { it.id == "cust-a" }, "John Smith should be in results")
            assertTrue(results.any { it.id == "cust-b" }, "Johnny Walker should be in results")
            assertTrue(results.none { it.id == "cust-c" }, "Bob Brown should not be in results")
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── 3. search by phone returns matching customers ─────────────────────────

    @Test
    fun search_by_phone_returns_matching_customers() = runTest {
        repo.insert(makeCustomer(id = "cust-p1", name = "Mary Jane",   phone = "0712345678"))
        repo.insert(makeCustomer(id = "cust-p2", name = "Sam Wilson",  phone = "0712345699"))
        repo.insert(makeCustomer(id = "cust-p3", name = "Tony Stark",  phone = "0779999999"))

        repo.search("071234").test {
            val results = awaitItem()
            assertEquals(2, results.size, "Both customers with phone starting '071234' should match")
            assertTrue(results.any { it.id == "cust-p1" }, "Mary Jane should be in results")
            assertTrue(results.any { it.id == "cust-p2" }, "Sam Wilson should be in results")
            assertTrue(results.none { it.id == "cust-p3" }, "Tony Stark should not be in results")
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── 4. update changes customer fields in the database ────────────────────

    @Test
    fun update_changes_customer_fields_in_database() = runTest {
        repo.insert(makeCustomer(id = "cust-2", name = "Old Name", phone = "0770000000"))

        val updated = makeCustomer(id = "cust-2", name = "New Name", phone = "0779876543")
        val updateResult = repo.update(updated)
        assertIs<Result.Success<Unit>>(updateResult)

        val getResult = repo.getById("cust-2")
        assertIs<Result.Success<Customer>>(getResult)
        assertEquals("New Name",   getResult.data.name)
        assertEquals("0779876543", getResult.data.phone)
    }

    // ── 5. delete soft-deletes customer; absent from getAll ───────────────────

    @Test
    fun delete_soft_deletes_customer_and_getAll_excludes_them() = runTest {
        repo.insert(makeCustomer(id = "cust-3", name = "Delete Me", phone = "0770000099"))

        // Confirm customer is returned by getAll before deletion
        val beforeAll = repo.getAll().first()
        assertTrue(beforeAll.any { it.id == "cust-3" }, "Customer must appear in getAll before deletion")

        val deleteResult = repo.delete("cust-3")
        assertIs<Result.Success<Unit>>(deleteResult)

        // After soft-delete, getAll must exclude the customer
        repo.getAll().test {
            val afterAll = awaitItem()
            assertTrue(
                afterAll.none { it.id == "cust-3" },
                "Soft-deleted customer must not appear in getAll",
            )
            cancelAndIgnoreRemainingEvents()
        }

        // getById still returns the row, but isActive must be false
        val getResult = repo.getById("cust-3")
        assertIs<Result.Success<Customer>>(getResult)
        assertTrue(!getResult.data.isActive, "Soft-deleted customer must have isActive = false")
    }
}
