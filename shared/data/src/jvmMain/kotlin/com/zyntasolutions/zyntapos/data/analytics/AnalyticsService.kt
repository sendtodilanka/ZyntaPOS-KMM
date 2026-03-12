package com.zyntasolutions.zyntapos.data.analytics

import co.touchlab.kermit.Logger
import com.zyntasolutions.zyntapos.core.analytics.AnalyticsTracker
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Desktop JVM implementation of [AnalyticsService].
 *
 * Uses the GA4 Measurement Protocol to send events to the same GA4 property
 * that Firebase Android uses — unified analytics dashboard.
 *
 * When [measurementId] or [apiSecret] are blank, events are logged locally only.
 *
 * GA4 Measurement Protocol reference:
 * https://developers.google.com/analytics/devguides/collection/protocol/ga4
 */
actual class AnalyticsService : AnalyticsTracker {

    private val log = Logger.withTag("AnalyticsService")

    private val measurementId: String = System.getenv("GA4_MEASUREMENT_ID") ?: ""
    private val apiSecret: String = System.getenv("GA4_API_SECRET") ?: ""
    private val clientId: String = UUID.randomUUID().toString()
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "analytics-ga4").apply { isDaemon = true }
    }

    private var currentUserId: String? = null
    private val userProperties = mutableMapOf<String, String>()

    actual override fun logEvent(name: String, params: Map<String, String>) {
        log.d { "Analytics event (desktop): $name params=$params" }
        sendToGA4(name, params)
    }

    actual override fun logScreenView(screenName: String, screenClass: String) {
        log.d { "Analytics screen (desktop): $screenName" }
        sendToGA4("screen_view", mapOf("screen_name" to screenName, "screen_class" to screenClass))
    }

    actual override fun setUserId(userId: String?) {
        currentUserId = userId
        log.d { "Analytics userId (desktop): ${userId ?: "(cleared)"}" }
    }

    actual override fun setUserProperty(name: String, value: String) {
        userProperties[name] = value
        log.d { "Analytics property (desktop): $name=$value" }
    }

    private fun sendToGA4(eventName: String, params: Map<String, String>) {
        if (measurementId.isBlank() || apiSecret.isBlank()) return

        executor.submit {
            try {
                val url = "https://www.google-analytics.com/mp/collect" +
                    "?measurement_id=$measurementId&api_secret=$apiSecret"

                // Build GA4 event JSON payload
                val paramsJson = params.entries.joinToString(",") { (k, v) ->
                    "\"${escapeJson(k)}\":\"${escapeJson(v)}\""
                }
                val userPropsJson = if (userProperties.isNotEmpty()) {
                    val props = userProperties.entries.joinToString(",") { (k, v) ->
                        "\"${escapeJson(k)}\":{\"value\":\"${escapeJson(v)}\"}"
                    }
                    ",\"user_properties\":{$props}"
                } else ""

                val userIdField = currentUserId?.let { ",\"user_id\":\"${escapeJson(it)}\"" } ?: ""

                val body = """{"client_id":"$clientId"$userIdField,"events":[{"name":"${escapeJson(eventName)}","params":{$paramsJson}}]$userPropsJson}"""

                val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    log.w { "GA4 returned HTTP $responseCode for event $eventName" }
                }
                connection.disconnect()
            } catch (e: Exception) {
                log.w { "GA4 send failed for event $eventName: ${e.message}" }
            }
        }
    }

    private fun escapeJson(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
