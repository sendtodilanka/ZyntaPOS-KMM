package com.zyntasolutions.zyntapos.domain.usecase.pos

import com.zyntasolutions.zyntapos.domain.model.ExchangeRate
import com.zyntasolutions.zyntapos.domain.usecase.fakes.FakeExchangeRateRepository
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConvertCurrencyUseCaseTest {

    private lateinit var repo: FakeExchangeRateRepository
    private lateinit var useCase: ConvertCurrencyUseCase

    @BeforeTest
    fun setup() {
        repo = FakeExchangeRateRepository()
        useCase = ConvertCurrencyUseCase(repo)
    }

    @Test
    fun `same currency returns identity conversion`() = runTest {
        val result = useCase(100.0, "USD", "USD")
        assertNotNull(result)
        assertEquals(100.0, result.convertedAmount)
        assertEquals(1.0, result.rate)
    }

    @Test
    fun `direct rate converts correctly`() = runTest {
        repo.addRate(ExchangeRate(
            id = "1", sourceCurrency = "USD", targetCurrency = "LKR",
            rate = 298.5, effectiveDate = 0L,
        ))
        val result = useCase(100.0, "USD", "LKR")
        assertNotNull(result)
        assertEquals(29850.0, result.convertedAmount)
        assertEquals(298.5, result.rate)
        assertEquals("USD", result.sourceCurrency)
        assertEquals("LKR", result.targetCurrency)
    }

    @Test
    fun `inverse rate converts correctly`() = runTest {
        repo.addRate(ExchangeRate(
            id = "1", sourceCurrency = "USD", targetCurrency = "LKR",
            rate = 298.5, effectiveDate = 0L,
        ))
        val result = useCase(29850.0, "LKR", "USD")
        assertNotNull(result)
        assertTrue(abs(result.convertedAmount - 100.0) < 0.01)
    }

    @Test
    fun `no rate returns null`() = runTest {
        val result = useCase(100.0, "USD", "EUR")
        assertNull(result)
    }

    @Test
    fun `zero rate inverse returns null`() = runTest {
        repo.addRate(ExchangeRate(
            id = "1", sourceCurrency = "EUR", targetCurrency = "USD",
            rate = 0.0, effectiveDate = 0L,
        ))
        val result = useCase(100.0, "USD", "EUR")
        assertNull(result)
    }

    @Test
    fun `fractional amounts work correctly`() = runTest {
        repo.addRate(ExchangeRate(
            id = "1", sourceCurrency = "USD", targetCurrency = "EUR",
            rate = 0.92, effectiveDate = 0L,
        ))
        val result = useCase(50.0, "USD", "EUR")
        assertNotNull(result)
        assertEquals(46.0, result.convertedAmount)
    }

    @Test
    fun `small amounts preserve precision`() = runTest {
        repo.addRate(ExchangeRate(
            id = "1", sourceCurrency = "JPY", targetCurrency = "LKR",
            rate = 2.0, effectiveDate = 0L,
        ))
        val result = useCase(1.0, "JPY", "LKR")
        assertNotNull(result)
        assertEquals(2.0, result.convertedAmount)
    }

    @Test
    fun `large amounts convert correctly`() = runTest {
        repo.addRate(ExchangeRate(
            id = "1", sourceCurrency = "USD", targetCurrency = "LKR",
            rate = 298.5, effectiveDate = 0L,
        ))
        val result = useCase(1000000.0, "USD", "LKR")
        assertNotNull(result)
        assertEquals(298500000.0, result.convertedAmount)
    }
}
