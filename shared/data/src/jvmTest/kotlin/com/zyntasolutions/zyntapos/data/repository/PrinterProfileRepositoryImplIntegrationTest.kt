package com.zyntasolutions.zyntapos.data.repository

import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.domain.model.PrinterJobType
import com.zyntasolutions.zyntapos.core.result.Result
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class PrinterProfileRepositoryImplIntegrationTest {

    private lateinit var repo: PrinterProfileRepositoryImpl
    private val now get() = Clock.System.now().toEpochMilliseconds()

    @BeforeTest
    fun setup() {
        repo = PrinterProfileRepositoryImpl(createTestDatabase())
    }

    private fun profile(
        id: String = "p-1",
        name: String = "Main Receipt",
        jobType: PrinterJobType = PrinterJobType.RECEIPT,
        printerType: String = "TCP",
        isDefault: Boolean = false,
    ) = com.zyntasolutions.zyntapos.domain.model.PrinterProfile(
        id          = id,
        name        = name,
        jobType     = jobType,
        printerType = printerType,
        tcpHost     = "192.168.1.10",
        tcpPort     = 9100,
        isDefault   = isDefault,
        createdAt   = now,
        updatedAt   = now,
    )

    // ── save + getById round-trip ───────────────────────────────────────────

    @Test
    fun `save then getById returns the profile`() = runTest {
        val p = profile(id = "p-1", name = "Kitchen Printer", jobType = PrinterJobType.KITCHEN)
        repo.save(p)

        val result = repo.getById("p-1")

        assertIs<Result.Success<*>>(result)
        val loaded = (result as Result.Success).data
        assertEquals("p-1",                   loaded.id)
        assertEquals("Kitchen Printer",        loaded.name)
        assertEquals(PrinterJobType.KITCHEN,   loaded.jobType)
    }

    // ── getById non-existent ────────────────────────────────────────────────

    @Test
    fun `getById non-existent id returns error`() = runTest {
        val result = repo.getById("does-not-exist")
        assertIs<Result.Error>(result)
    }

    // ── getAll flow ─────────────────────────────────────────────────────────

    @Test
    fun `getAll flow emits all saved profiles`() = runTest {
        repo.save(profile(id = "r1", jobType = PrinterJobType.RECEIPT))
        repo.save(profile(id = "k1", jobType = PrinterJobType.KITCHEN))

        val profiles = repo.getAll().first()

        assertEquals(2, profiles.size)
    }

    // ── getDefault ──────────────────────────────────────────────────────────

    @Test
    fun `getDefault returns null when no default set`() = runTest {
        repo.save(profile(id = "p1", isDefault = false))

        val result = repo.getDefault(PrinterJobType.RECEIPT)

        assertIs<Result.Success<*>>(result)
        assertNull((result as Result.Success).data)
    }

    @Test
    fun `getDefault returns the default profile for job type`() = runTest {
        repo.save(profile(id = "p1", isDefault = true))
        repo.save(profile(id = "p2", isDefault = false))

        val result = repo.getDefault(PrinterJobType.RECEIPT)

        assertIs<Result.Success<*>>(result)
        assertEquals("p1", (result as Result.Success).data?.id)
    }

    // ── save with isDefault clears others ───────────────────────────────────

    @Test
    fun `save with isDefault clears previous default for same job type`() = runTest {
        repo.save(profile(id = "p1", isDefault = true))
        repo.save(profile(id = "p2", isDefault = true))

        val default = repo.getDefault(PrinterJobType.RECEIPT)
        assertIs<Result.Success<*>>(default)
        assertEquals("p2", (default as Result.Success).data?.id)

        // p1 should no longer be default
        val p1 = repo.getById("p1")
        assertIs<Result.Success<*>>(p1)
        assertTrue(!(p1 as Result.Success).data.isDefault)
    }

    // ── delete ──────────────────────────────────────────────────────────────

    @Test
    fun `delete removes profile from repository`() = runTest {
        repo.save(profile(id = "p-del"))
        repo.delete("p-del")

        val result = repo.getById("p-del")
        assertIs<Result.Error>(result)
    }
}
