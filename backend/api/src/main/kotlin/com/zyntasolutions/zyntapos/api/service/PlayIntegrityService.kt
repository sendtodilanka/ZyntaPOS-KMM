package com.zyntasolutions.zyntapos.api.service

import com.zyntasolutions.zyntapos.api.config.AppConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

/**
 * Verifies Android app integrity tokens via Google Play Integrity API (TODO-008 ASO).
 *
 * ## Flow
 * 1. POS Android app obtains an integrity token via `StandardIntegrityManager` or
 *    `IntegrityManager.requestIntegrityToken()`.
 * 2. App POSTs the token to `POST /v1/integrity/verify`.
 * 3. This service calls Google Play Integrity API to decode and verify the token.
 * 4. Returns a [VerifyResponse] with the verdict — the caller decides enforcement policy.
 *
 * ## Phase 1 Policy (Soft Enforcement)
 * Non-passing devices receive a warning but are NOT blocked from logging in.
 * Hard enforcement (rejecting logins on compromised devices) is Phase 2.
 *
 * ## Configuration
 * - `PLAY_INTEGRITY_PACKAGE_NAME` — Android app package name (default: `com.zyntasolutions.zyntapos`)
 * - `PLAY_INTEGRITY_API_KEY` — Google Cloud API key with Play Integrity API enabled.
 *   If blank, all tokens are accepted without verification (dev/staging environments).
 *
 * ## Google API endpoint
 * `POST https://playintegrity.googleapis.com/v1/{packageName}:decodeIntegrityToken?key={apiKey}`
 */
class PlayIntegrityService(private val config: AppConfig) {

    private val log = LoggerFactory.getLogger(PlayIntegrityService::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
    }

    @Serializable
    data class VerifyResponse(
        val passed: Boolean,
        val appVerdicts: List<String>,
        val deviceVerdicts: List<String>,
        /** Human-readable reason — populated only on failure or misconfiguration. */
        val reason: String? = null,
    )

    /**
     * Verifies a Play Integrity [token] obtained by the POS Android app.
     *
     * Always returns a result — never throws. Errors are logged and return
     * `passed = false` so the caller can decide policy.
     */
    suspend fun verify(token: String): VerifyResponse {
        if (config.playIntegrityApiKey.isBlank()) {
            log.warn("PLAY_INTEGRITY_API_KEY not configured — all tokens accepted (dev mode)")
            return VerifyResponse(
                passed = true,
                appVerdicts = listOf("PLAY_RECOGNIZED"),
                deviceVerdicts = listOf("MEETS_DEVICE_INTEGRITY"),
                reason = "not_configured",
            )
        }

        return runCatching {
            val url = "https://playintegrity.googleapis.com/v1/${config.playIntegrityPackageName}:decodeIntegrityToken"
            val response = client.post(url) {
                parameter("key", config.playIntegrityApiKey)
                contentType(ContentType.Application.Json)
                setBody("""{"integrity_token":"$token"}""")
            }

            if (response.status != HttpStatusCode.OK) {
                val body = response.bodyAsText()
                log.warn("Play Integrity API HTTP ${response.status}: $body")
                return VerifyResponse(
                    passed = false,
                    appVerdicts = emptyList(),
                    deviceVerdicts = emptyList(),
                    reason = "api_http_${response.status.value}",
                )
            }

            val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val tokenPayload = root["tokenPayloadExternal"]?.jsonObject

            // appIntegrity.appRecognitionVerdict — "PLAY_RECOGNIZED" | "UNRECOGNIZED_VERSION" | "UNEVALUATED"
            val appVerdicts = tokenPayload
                ?.get("appIntegrity")?.jsonObject
                ?.get("appRecognitionVerdict")?.jsonPrimitive?.content
                ?.let { listOf(it) }
                ?: emptyList()

            // deviceIntegrity.deviceRecognitionVerdict — array of labels
            val deviceVerdicts = tokenPayload
                ?.get("deviceIntegrity")?.jsonObject
                ?.get("deviceRecognitionVerdict")?.jsonArray
                ?.map { it.jsonPrimitive.content }
                ?: emptyList()

            val appPassed = appVerdicts.any { it in ACCEPTED_APP_VERDICTS }
            val devicePassed = deviceVerdicts.any { it in ACCEPTED_DEVICE_VERDICTS }
            val passed = appPassed && devicePassed

            if (!passed) {
                log.warn("Play Integrity check failed — appVerdicts=$appVerdicts deviceVerdicts=$deviceVerdicts")
            }

            VerifyResponse(passed = passed, appVerdicts = appVerdicts, deviceVerdicts = deviceVerdicts)
        }.getOrElse { e ->
            log.error("Play Integrity API call failed: ${e.message}", e)
            VerifyResponse(
                passed = false,
                appVerdicts = emptyList(),
                deviceVerdicts = emptyList(),
                reason = "exception",
            )
        }
    }

    fun close() = client.close()

    private companion object {
        // App verdicts that indicate the APK was distributed via Google Play
        val ACCEPTED_APP_VERDICTS = setOf("PLAY_RECOGNIZED")
        // Device verdicts indicating the device passes Play Protect checks
        val ACCEPTED_DEVICE_VERDICTS = setOf(
            "MEETS_DEVICE_INTEGRITY",
            "MEETS_STRONG_INTEGRITY",
            "MEETS_BASIC_INTEGRITY",
        )
    }
}
