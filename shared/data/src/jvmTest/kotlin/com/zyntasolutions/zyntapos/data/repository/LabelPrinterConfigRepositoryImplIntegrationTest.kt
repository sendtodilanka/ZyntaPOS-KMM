package com.zyntasolutions.zyntapos.data.repository

import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.domain.model.LabelPrinterConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class LabelPrinterConfigRepositoryImplIntegrationTest {

    private lateinit var repo: LabelPrinterConfigRepositoryImpl

    @BeforeTest
    fun setup() {
        repo = LabelPrinterConfigRepositoryImpl(createTestDatabase())
    }

    // ── get returns null when never saved ───────────────────────────────────

    @Test
    fun `get returns null when no config saved`() = runTest {
        val result = repo.get()

        assertIs<Result.Success<*>>(result)
        assertNull((result as Result.Success).data)
    }

    // ── save then get round-trip ────────────────────────────────────────────

    @Test
    fun `save then get returns the config`() = runTest {
        val config = LabelPrinterConfig(
            printerType   = "ZPL_TCP",
            tcpHost       = "192.168.1.201",
            tcpPort       = 9100,
            darknessLevel = 10,
            speedLevel    = 5,
        )

        repo.save(config)
        val result = repo.get()

        assertIs<Result.Success<*>>(result)
        val loaded = (result as Result.Success).data!!
        assertEquals("ZPL_TCP",       loaded.printerType)
        assertEquals("192.168.1.201", loaded.tcpHost)
        assertEquals(9100,            loaded.tcpPort)
        assertEquals(10,              loaded.darknessLevel)
        assertEquals(5,               loaded.speedLevel)
    }

    // ── save overwrites previous config (singleton upsert) ──────────────────

    @Test
    fun `save overwrites previous config`() = runTest {
        repo.save(LabelPrinterConfig(printerType = "ZPL_TCP", tcpHost = "10.0.0.1"))
        repo.save(LabelPrinterConfig(printerType = "TSPL_TCP", tcpHost = "10.0.0.2"))

        val result = repo.get()

        assertIs<Result.Success<*>>(result)
        val loaded = (result as Result.Success).data!!
        assertEquals("TSPL_TCP", loaded.printerType)
        assertEquals("10.0.0.2", loaded.tcpHost)
    }

    // ── NONE type saves correctly ────────────────────────────────────────────

    @Test
    fun `save NONE type persists as NONE`() = runTest {
        repo.save(LabelPrinterConfig(printerType = "NONE"))

        val result = repo.get()

        assertIs<Result.Success<*>>(result)
        assertEquals("NONE", (result as Result.Success).data?.printerType)
    }
}
