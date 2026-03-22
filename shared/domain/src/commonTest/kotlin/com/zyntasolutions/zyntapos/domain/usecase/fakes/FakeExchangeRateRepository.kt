package com.zyntasolutions.zyntapos.domain.usecase.fakes

import com.zyntasolutions.zyntapos.domain.model.ExchangeRate
import com.zyntasolutions.zyntapos.domain.repository.ExchangeRateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeExchangeRateRepository : ExchangeRateRepository {

    private val rates = mutableListOf<ExchangeRate>()
    private val flow = MutableStateFlow<List<ExchangeRate>>(emptyList())

    fun addRate(rate: ExchangeRate) {
        rates.add(rate)
        flow.value = rates.toList()
    }

    fun clear() {
        rates.clear()
        flow.value = emptyList()
    }

    override fun getAll(): Flow<List<ExchangeRate>> = flow

    override suspend fun getEffectiveRate(
        sourceCurrency: String,
        targetCurrency: String,
    ): ExchangeRate? = rates.find {
        it.sourceCurrency == sourceCurrency && it.targetCurrency == targetCurrency
    }

    override suspend fun getRatesForCurrency(currencyCode: String): List<ExchangeRate> =
        rates.filter { it.sourceCurrency == currencyCode || it.targetCurrency == currencyCode }

    override suspend fun upsert(rate: ExchangeRate) {
        rates.removeAll { it.sourceCurrency == rate.sourceCurrency && it.targetCurrency == rate.targetCurrency }
        rates.add(rate)
        flow.value = rates.toList()
    }

    override suspend fun delete(id: String) {
        rates.removeAll { it.id == id }
        flow.value = rates.toList()
    }
}
