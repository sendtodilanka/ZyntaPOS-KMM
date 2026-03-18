package com.zyntasolutions.zyntapos.api.service

import com.zyntasolutions.zyntapos.api.repository.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for AdminStoresService -- health status/score calculation,
 * store listing, and config updates.
 */
class AdminStoresServiceTest {

    private val storesRepo = mockk<AdminStoresRepository>()
    private val service = AdminStoresService(storesRepo)

    private fun sampleStore(
        id: String = "store-1",
        isActive: Boolean = true,
    ) = StoreAdminRow(
        id = id, name = "Main Store", licenseKey = "LIC-001",
        timezone = "Asia/Colombo", currency = "LKR", isActive = isActive,
        createdAt = "2026-01-01T00:00:00Z", updatedAt = "2026-03-01T00:00:00Z",
    )

    // ── Health status calculation ────────────────────────────────────────

    @Test
    fun `health status is HEALTHY for active store with few pending ops`() = runTest {
        val store = sampleStore(isActive = true)
        coEvery { storesRepo.findById("store-1") } returns store
        coEvery { storesRepo.countPendingOps("store-1") } returns 10

        val health = service.getStoreHealth("store-1")!!
        assertEquals("HEALTHY", health.status)
        assertEquals(100, health.healthScore)
        assertEquals(10, health.syncQueueDepth)
    }

    @Test
    fun `health status is WARNING when pending ops exceed 50`() = runTest {
        val store = sampleStore(isActive = true)
        coEvery { storesRepo.findById("store-1") } returns store
        coEvery { storesRepo.countPendingOps("store-1") } returns 75

        val health = service.getStoreHealth("store-1")!!
        assertEquals("WARNING", health.status)
        assertEquals(60, health.healthScore)
    }

    @Test
    fun `health status is OFFLINE for inactive store`() = runTest {
        val store = sampleStore(isActive = false)
        coEvery { storesRepo.findById("store-1") } returns store
        coEvery { storesRepo.countPendingOps("store-1") } returns 0

        val health = service.getStoreHealth("store-1")!!
        assertEquals("OFFLINE", health.status)
        assertEquals(0, health.healthScore)
    }

    @Test
    fun `getStoreHealth returns null for unknown store`() = runTest {
        coEvery { storesRepo.findById("unknown") } returns null

        assertNull(service.getStoreHealth("unknown"))
    }

    // ── Health status at boundary (exactly 50) ───────────────────────────

    @Test
    fun `health status is HEALTHY with exactly 50 pending ops`() = runTest {
        val store = sampleStore(isActive = true)
        coEvery { storesRepo.findById("s1") } returns store
        coEvery { storesRepo.countPendingOps("s1") } returns 50

        val health = service.getStoreHealth("s1")!!
        assertEquals("HEALTHY", health.status)
    }

    @Test
    fun `health status is WARNING with 51 pending ops`() = runTest {
        val store = sampleStore(isActive = true)
        coEvery { storesRepo.findById("s1") } returns store
        coEvery { storesRepo.countPendingOps("s1") } returns 51

        val health = service.getStoreHealth("s1")!!
        assertEquals("WARNING", health.status)
    }

    // ── listStores status filter ─────────────────────────────────────────

    @Test
    fun `listStores maps OFFLINE status to isActive false`() = runTest {
        val page = StoreAdminPage(emptyList(), 0, 10, 0, 0)
        coEvery { storesRepo.list(null, false, 0, 10) } returns page

        service.listStores(0, 10, null, "OFFLINE")

        coVerify { storesRepo.list(null, false, 0, 10) }
    }

    @Test
    fun `listStores maps any non-OFFLINE status to isActive true`() = runTest {
        val page = StoreAdminPage(emptyList(), 0, 10, 0, 0)
        coEvery { storesRepo.list(null, true, 0, 10) } returns page

        service.listStores(0, 10, null, "HEALTHY")

        coVerify { storesRepo.list(null, true, 0, 10) }
    }

    @Test
    fun `listStores passes null when no status filter`() = runTest {
        val page = StoreAdminPage(emptyList(), 0, 10, 0, 0)
        coEvery { storesRepo.list("search", null, 0, 10) } returns page

        service.listStores(0, 10, "search", null)

        coVerify { storesRepo.list("search", null, 0, 10) }
    }

    // ── getStore ─────────────────────────────────────────────────────────

    @Test
    fun `getStore returns mapped AdminStore`() = runTest {
        val store = sampleStore()
        coEvery { storesRepo.findById("store-1") } returns store

        val result = service.getStore("store-1")!!
        assertEquals("store-1", result.id)
        assertEquals("Main Store", result.name)
        assertEquals("HEALTHY", result.status)
    }

    @Test
    fun `getStore returns null for unknown store`() = runTest {
        coEvery { storesRepo.findById("none") } returns null
        assertNull(service.getStore("none"))
    }

    // ── getAllStoreHealthSummaries ────────────────────────────────────────

    @Test
    fun `getAllStoreHealthSummaries maps health status correctly`() = runTest {
        val store1 = sampleStore("s1", isActive = true)
        val store2 = sampleStore("s2", isActive = false)
        coEvery { storesRepo.listAllWithPendingOps() } returns listOf(
            store1 to 10,  // HEALTHY
            store2 to 0,   // OFFLINE
        )

        val summaries = service.getAllStoreHealthSummaries()
        assertEquals(2, summaries.size)
        assertEquals("healthy", summaries[0].status)
        assertEquals("offline", summaries[1].status)
    }
}
