package com.zyntasolutions.zyntapos.api.service

import com.zyntasolutions.zyntapos.api.repository.ExchangeRateRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

/**
 * Background job that fetches live exchange rates from the open.er-api.com free tier.
 *
 * Endpoint: https://open.er-api.com/v6/latest/USD
 * Response contains USD-based rates for ~160 currencies.
 * Rates are stored in exchange_rates with source="LIVE".
 *
 * The job runs every [intervalSeconds] (default 3600 = 1 hour).
 * If [apiKey] is set (EXCHANGE_RATE_API_KEY env var), it uses the authenticated
 * endpoint which supports more currencies and higher request limits.
 *
 * If the external API is unreachable, the existing manual rates are preserved.
 * The job skips currencies with rate <= 0 or that fail to parse.
 */
class ExchangeRateSyncJob(
    private val exchangeRateRepo: ExchangeRateRepository,
    private val apiKey: String = System.getenv("EXCHANGE_RATE_API_KEY") ?: "",
) {
    private val log = LoggerFactory.getLogger(ExchangeRateSyncJob::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
    }

    // Currencies we actively sync (common set for multi-currency POS deployments)
    private val TARGET_CURRENCIES = setOf(
        "EUR", "GBP", "LKR", "INR", "AUD", "CAD", "JPY", "CHF", "CNY", "SGD",
        "AED", "SAR", "MYR", "THB", "IDR", "BDT", "PKR", "NZD", "KRW", "HKD",
    )

    fun start(intervalSeconds: Long = 3600L) {
        if (apiKey.isEmpty()) {
            log.info("ExchangeRateSyncJob: no EXCHANGE_RATE_API_KEY set — using unauthenticated free tier (limited)")
        }
        scope.launch {
            log.info("ExchangeRateSyncJob started (interval: ${intervalSeconds}s)")
            while (true) {
                try {
                    syncRates()
                } catch (e: Exception) {
                    log.error("ExchangeRateSyncJob error: ${e.message}", e)
                }
                delay(intervalSeconds * 1_000L)
            }
        }
    }

    private suspend fun syncRates() {
        val url = if (apiKey.isNotEmpty()) {
            "https://v6.exchangerate-api.com/v6/$apiKey/latest/USD"
        } else {
            "https://open.er-api.com/v6/latest/USD"
        }

        val response = client.get(url)
        if (!response.status.isSuccess()) {
            log.warn("ExchangeRateSyncJob: API returned ${response.status} — skipping this cycle")
            return
        }

        val body = response.bodyAsText()
        val root = json.parseToJsonElement(body).jsonObject

        val result = root["result"]?.jsonPrimitive?.content
        if (result != "success") {
            log.warn("ExchangeRateSyncJob: API result=${result} — skipping this cycle")
            return
        }

        val rates: JsonObject = root["rates"]?.jsonObject ?: run {
            log.warn("ExchangeRateSyncJob: No 'rates' field in response")
            return
        }

        var updated = 0
        for (currency in TARGET_CURRENCIES) {
            val rate = rates[currency]?.jsonPrimitive?.double ?: continue
            if (rate <= 0) continue
            try {
                exchangeRateRepo.upsertRate(
                    sourceCurrency = "USD",
                    targetCurrency = currency,
                    rate = rate,
                    source = "LIVE",
                )
                updated++
            } catch (e: Exception) {
                log.warn("ExchangeRateSyncJob: failed to upsert USD→$currency: ${e.message}")
            }
        }
        log.info("ExchangeRateSyncJob: synced $updated/${TARGET_CURRENCIES.size} rates from external API")
    }
}
