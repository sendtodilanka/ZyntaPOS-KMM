package com.zyntasolutions.zyntapos.data.repository

import app.cash.turbine.test
import com.zyntasolutions.zyntapos.data.createTestDatabase
import com.zyntasolutions.zyntapos.db.ZyntaDatabase
import com.zyntasolutions.zyntapos.domain.model.ExchangeRate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest

/**
 * ZyntaPOS — ExchangeRateRepositoryImpl Integration Tests (jvmTest)
 *
 * Validates [ExchangeRateRepositoryImpl] against a real in-memory SQLite database.
 * No FK constraints on exchange_rates — no pre-seeding required.
 *
 * Coverage:
 *  A. upsert → getAll round-trip via Turbine
 *  B. getRatesForCurrency returns rates for a given currency
 *  C. getEffectiveRate returns active rate within validity window
 *  D. getEffectiveRate returns null when no rate matches
 *  E. getEffectiveRate returns null when rate has expired
 *  F. delete removes a rate from getAll
 *  G. upsert updates existing rate (idempotent)
 */
class ExchangeRateRepositoryImplIntegrationTest {

    private lateinit var db: ZyntaDatabase
    private lateinit var repo: ExchangeRateRepositoryImpl

    @BeforeTest
    fun setup() {
        db = createTestDatabase()
        repo = ExchangeRateRepositoryImpl(db)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private val now get() = Clock.System.now().toEpochMilliseconds()

    private fun makeRate(
        id: String = "rate-01",
        sourceCurrency: String = "USD",
        targetCurrency: String = "LKR",
        rate: Double = 325.0,
        effectiveDate: Long = 0L,
        expiresAt: Long? = null,
        source: String = "manual",
        createdAt: Long = 0L,
    ) = ExchangeRate(
        id = id,
        sourceCurrency = sourceCurrency,
        targetCurrency = targetCurrency,
        rate = rate,
        effectiveDate = effectiveDate,
        expiresAt = expiresAt,
        source = source,
        createdAt = createdAt,
        updatedAt = 0L,
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `A - upsert then getAll returns rates via Turbine`() = runTest {
        repo.upsert(makeRate(id = "rate-01", sourceCurrency = "USD", targetCurrency = "LKR", rate = 325.0))
        repo.upsert(makeRate(id = "rate-02", sourceCurrency = "EUR", targetCurrency = "LKR", rate = 360.0))

        repo.getAll().test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertTrue(list.any { it.id == "rate-01" && it.rate == 325.0 })
            assertTrue(list.any { it.id == "rate-02" && it.rate == 360.0 })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `B - getRatesForCurrency returns rates for given source currency`() = runTest {
        repo.upsert(makeRate(id = "rate-01", sourceCurrency = "USD", targetCurrency = "LKR"))
        repo.upsert(makeRate(id = "rate-02", sourceCurrency = "USD", targetCurrency = "EUR"))
        repo.upsert(makeRate(id = "rate-03", sourceCurrency = "EUR", targetCurrency = "LKR"))

        val rates = repo.getRatesForCurrency("USD")
        assertEquals(2, rates.size)
        assertTrue(rates.all { it.sourceCurrency == "USD" })
    }

    @Test
    fun `C - getEffectiveRate returns rate within validity window`() = runTest {
        val current = now
        val past = current - 100_000L
        val future = current + 100_000L

        repo.upsert(makeRate(
            id = "rate-01",
            sourceCurrency = "USD",
            targetCurrency = "LKR",
            rate = 325.0,
            effectiveDate = past,
            expiresAt = future,
        ))

        val result = repo.getEffectiveRate("USD", "LKR")
        assertNotNull(result)
        assertEquals("rate-01", result.id)
        assertEquals(325.0, result.rate)
    }

    @Test
    fun `D - getEffectiveRate returns null when no rate matches`() = runTest {
        val result = repo.getEffectiveRate("GBP", "LKR")
        assertNull(result)
    }

    @Test
    fun `E - getEffectiveRate returns null when rate has expired`() = runTest {
        val current = now
        val longAgo = current - 1_000_000L

        repo.upsert(makeRate(
            id = "rate-expired",
            sourceCurrency = "USD",
            targetCurrency = "LKR",
            rate = 300.0,
            effectiveDate = longAgo - 100_000L,
            expiresAt = longAgo,  // already expired
        ))

        val result = repo.getEffectiveRate("USD", "LKR")
        assertNull(result)
    }

    @Test
    fun `F - delete removes rate from getAll`() = runTest {
        repo.upsert(makeRate(id = "rate-01"))
        repo.upsert(makeRate(id = "rate-02", sourceCurrency = "EUR"))

        repo.delete("rate-01")

        repo.getAll().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("rate-02", list.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `G - upsert with same id updates the existing rate`() = runTest {
        repo.upsert(makeRate(id = "rate-01", rate = 300.0))
        repo.upsert(makeRate(id = "rate-01", rate = 330.0))

        repo.getAll().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals(330.0, list.first().rate)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
