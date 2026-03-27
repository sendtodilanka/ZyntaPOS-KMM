package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — SettingsRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [SettingsRepositoryImpl] against a real in-memory SQLite database.
 * No mocks — exercises the full SQLDelight query layer.
 *
 * Coverage:
 *  A. set → get round-trip returns correct value
 *  B. get for unknown key returns null
 *  C. set twice (upsert) overwrites existing value
 *  D. getAll returns all stored settings as a map
 *  E. observe emits current value and re-emits on update (Turbine)
 *  F. getAll returns empty map when no settings stored
 */
class SettingsRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: SettingsRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = SettingsRepositoryImpl(db)
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - set then get returns correct value`() = runTest {
        val setResult = repo.set("store.name", "ZyntaPOS Demo Store")
        assertIs<Result.Success<Unit>>(setResult)

        val value = repo.get("store.name")
        assertEquals("ZyntaPOS Demo Store", value)
    }

    @Test
    fun `B - get for unknown key returns null`() = runTest {
        val value = repo.get("non.existent.key")
        assertNull(value)
    }

    @Test
    fun `C - set twice overwrites existing value`() = runTest {
        repo.set("currency", "LKR")
        repo.set("currency", "USD")

        val value = repo.get("currency")
        assertEquals("USD", value)
    }

    @Test
    fun `D - getAll returns all stored settings as map`() = runTest {
        repo.set("key.a", "value_a")
        repo.set("key.b", "value_b")
        repo.set("key.c", "value_c")

        val all = repo.getAll()
        assertEquals(3, all.size)
        assertEquals("value_a", all["key.a"])
        assertEquals("value_b", all["key.b"])
        assertEquals("value_c", all["key.c"])
    }

    @Test
    fun `E - observe emits current value and re-emits on update`() = runTest {
        repo.set("theme", "light")

        repo.observe("theme").test {
            assertEquals("light", awaitItem())

            repo.set("theme", "dark")
            assertEquals("dark", awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `F - getAll returns empty map when no settings stored`() = runTest {
        val all = repo.getAll()
        assertTrue(all.isEmpty())
    }
}
