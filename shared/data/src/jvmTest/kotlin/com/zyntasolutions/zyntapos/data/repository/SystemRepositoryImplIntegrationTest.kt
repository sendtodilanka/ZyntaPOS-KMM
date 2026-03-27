package com.zyntasolutions.zyntapos.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.SystemHealth
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import java.util.Properties

/**
 * ZyntaPOS — SystemRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [SystemRepositoryImpl] against a real in-memory SQLite database.
 * Requires access to the raw SqlDriver (not available from createTestDatabase()).
 *
 * Coverage:
 *  A. getSystemHealth returns valid health metrics
 *  B. getSystemHealth reflects appVersion and buildNumber
 *  C. getDatabaseStats returns stats for known tables
 *  D. vacuumDatabase completes successfully
 *  E. purgeExpiredData completes successfully and returns PurgeResult
 */
class SystemRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var repo: SystemRepositoryImpl

    @BeforeTest
    fun setup() {
        driver = JdbcSqliteDriver(
            url = JdbcSqliteDriver.IN_MEMORY,
            properties = Properties().apply { put("foreign_keys", "true") },
        )
        ZyntaDatabase.Schema.create(driver)
        db = ZyntaDatabase(driver)
        repo = SystemRepositoryImpl(
            db = db,
            driver = driver,
            appVersion = "1.0.0",
            buildNumber = 42,
            networkOnline = true,
        )
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - getSystemHealth returns valid health metrics`() = runTest {
        val result = repo.getSystemHealth()
        assertIs<Result.Success<SystemHealth>>(result)
        val health = result.data
        assertTrue(health.databaseSizeBytes > 0L)
        assertTrue(health.totalMemoryBytes > 0L)
        assertTrue(health.usedMemoryBytes >= 0L)
        assertEquals(0, health.pendingSyncCount)  // empty DB = no pending sync
        assertTrue(health.isOnline)
    }

    @Test
    fun `B - getSystemHealth reflects injected appVersion and buildNumber`() = runTest {
        val result = repo.getSystemHealth()
        assertIs<Result.Success<SystemHealth>>(result)
        assertEquals("1.0.0", result.data.appVersion)
        assertEquals(42, result.data.buildNumber)
    }

    @Test
    fun `C - getDatabaseStats returns stats for known tables`() = runTest {
        val result = repo.getDatabaseStats()
        assertIs<Result.Success<*>>(result)
        val stats = result.data
        assertNotNull(stats)
    }

    @Test
    fun `D - vacuumDatabase completes successfully`() = runTest {
        val result = repo.vacuumDatabase()
        assertIs<Result.Success<Unit>>(result)
    }

    @Test
    fun `E - purgeExpiredData completes and returns PurgeResult`() = runTest {
        val result = repo.purgeExpiredData(olderThanMillis = 86_400_000L)
        assertIs<Result.Success<*>>(result)
        val purgeResult = result.data
        assertNotNull(purgeResult)
    }
}
