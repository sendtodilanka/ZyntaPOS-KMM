package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.ReturnStockPolicy
import com.zyntasolutions.zyntapos.domain.model.Store
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant

/**
 * ZyntaPOS — StoreRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [StoreRepositoryImpl] against a real in-memory SQLite database.
 * No mocks — exercises the full SQLDelight query layer.
 *
 * Coverage:
 *  A. upsertFromSync (insert) → getById round-trip preserves all fields
 *  B. getAllStores emits only active stores (Turbine)
 *  C. getAllStores excludes inactive stores
 *  D. getById returns null for unknown store
 *  E. getStoreName returns correct name
 *  F. getStoreName returns null for unknown store
 *  G. upsertFromSync (update) changes name and settings
 *  H. upsertFromSync preserves returnStockPolicy enum
 */
class StoreRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: StoreRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = StoreRepositoryImpl(db)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun makeStore(
        id: String = "store-01",
        name: String = "Main Branch",
        address: String? = "123 Main St",
        phone: String? = "+94 11 000 0000",
        email: String? = "main@zyntapos.com",
        currency: String = "LKR",
        timezone: String = "Asia/Colombo",
        isActive: Boolean = true,
        isHeadquarters: Boolean = false,
        maxDiscountPercent: Double? = 20.0,
        maxDiscountAmount: Double? = 500.0,
        returnStockPolicy: ReturnStockPolicy = ReturnStockPolicy.RETURN_TO_CURRENT_STORE,
    ) = Store(
        id = id,
        name = name,
        address = address,
        phone = phone,
        email = email,
        currency = currency,
        timezone = timezone,
        isActive = isActive,
        isHeadquarters = isHeadquarters,
        createdAt = Instant.fromEpochMilliseconds(1_000_000L),
        updatedAt = Instant.fromEpochMilliseconds(1_000_000L),
        maxDiscountPercent = maxDiscountPercent,
        maxDiscountAmount = maxDiscountAmount,
        returnStockPolicy = returnStockPolicy,
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - upsertFromSync insert then getById returns full store`() = runTest {
        val store = makeStore(
            id = "store-01",
            name = "Colombo Branch",
            address = "1 Galle Road",
            currency = "LKR",
            isHeadquarters = true,
            maxDiscountPercent = 15.0,
            maxDiscountAmount = 1000.0,
            returnStockPolicy = ReturnStockPolicy.RETURN_TO_ORIGINAL_STORE,
        )
        repo.upsertFromSync(store)

        val fetched = repo.getById("store-01")
        assertNotNull(fetched)
        assertEquals("store-01", fetched.id)
        assertEquals("Colombo Branch", fetched.name)
        assertEquals("1 Galle Road", fetched.address)
        assertEquals("LKR", fetched.currency)
        assertTrue(fetched.isActive)
        assertTrue(fetched.isHeadquarters)
        assertEquals(15.0, fetched.maxDiscountPercent)
        assertEquals(1000.0, fetched.maxDiscountAmount)
        assertEquals(ReturnStockPolicy.RETURN_TO_ORIGINAL_STORE, fetched.returnStockPolicy)
    }

    @Test
    fun `B - getAllStores emits only active stores via Turbine`() = runTest {
        repo.upsertFromSync(makeStore(id = "store-01", name = "Active Store 1", isActive = true))
        repo.upsertFromSync(makeStore(id = "store-02", name = "Active Store 2", isActive = true))
        repo.upsertFromSync(makeStore(id = "store-03", name = "Inactive Store", isActive = false))

        repo.getAllStores().test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertTrue(list.any { it.name == "Active Store 1" })
            assertTrue(list.any { it.name == "Active Store 2" })
            assertTrue(list.none { it.name == "Inactive Store" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `C - getAllStores excludes inactive stores`() = runTest {
        repo.upsertFromSync(makeStore(id = "store-01", name = "Active Only", isActive = true))
        repo.upsertFromSync(makeStore(id = "store-02", name = "Also Inactive", isActive = false))

        repo.getAllStores().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("Active Only", list.first().name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `D - getById returns null for unknown store`() = runTest {
        val result = repo.getById("non-existent-store")
        assertNull(result)
    }

    @Test
    fun `E - getStoreName returns correct name`() = runTest {
        repo.upsertFromSync(makeStore(id = "store-01", name = "Kandy Branch"))

        val name = repo.getStoreName("store-01")
        assertEquals("Kandy Branch", name)
    }

    @Test
    fun `F - getStoreName returns null for unknown store`() = runTest {
        val name = repo.getStoreName("non-existent-store")
        assertNull(name)
    }

    @Test
    fun `G - upsertFromSync update changes name and settings`() = runTest {
        repo.upsertFromSync(makeStore(id = "store-01", name = "Old Name", maxDiscountPercent = 10.0))

        val updated = makeStore(
            id = "store-01",
            name = "New Name",
            maxDiscountPercent = 25.0,
            maxDiscountAmount = 2000.0,
            currency = "USD",
        )
        repo.upsertFromSync(updated)

        val fetched = repo.getById("store-01")
        assertNotNull(fetched)
        assertEquals("New Name", fetched.name)
        assertEquals(25.0, fetched.maxDiscountPercent)
        assertEquals(2000.0, fetched.maxDiscountAmount)
        assertEquals("USD", fetched.currency)
    }

    @Test
    fun `H - upsertFromSync preserves RETURN_TO_ORIGINAL_STORE policy`() = runTest {
        repo.upsertFromSync(
            makeStore(
                id = "store-01",
                returnStockPolicy = ReturnStockPolicy.RETURN_TO_ORIGINAL_STORE,
            )
        )

        val fetched = repo.getById("store-01")
        assertNotNull(fetched)
        assertEquals(ReturnStockPolicy.RETURN_TO_ORIGINAL_STORE, fetched.returnStockPolicy)
    }
}
