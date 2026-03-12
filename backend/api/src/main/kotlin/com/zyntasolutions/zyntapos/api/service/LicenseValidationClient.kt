package com.zyntasolutions.zyntapos.api.service

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Internal HTTP client for validating POS license keys against the License service (S2-12).
 *
 * ## Fail-open strategy
 * When the license service is unreachable (network error, timeout), login is **allowed**
 * to support offline/degraded-network scenarios. A warning is logged but the user is not
 * blocked. When the license service returns a definitive EXPIRED/REVOKED/SUSPENDED status,
 * login is **denied** with a 403 error.
 */
class LicenseValidationClient {

    private val logger = LoggerFactory.getLogger(LicenseValidationClient::class.java)

    private val licenseServiceBaseUrl: String =
        System.getenv("LICENSE_SERVICE_URL") ?: "http://license:8083"

    private val httpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            connectTimeoutMillis = 2_000L
            requestTimeoutMillis = 5_000L
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    @Serializable
    data class LicenseStatusResponse(
        val key: String? = null,
        val edition: String? = null,
        val status: String? = null,
        val maxDevices: Int? = null,
        val activeDevices: Int? = null,
        val issuedAt: Long? = null,
        val expiresAt: Long? = null,
        val lastHeartbeatAt: Long? = null,
    )

    /**
     * Validates a store's license key by querying the License service.
     *
     * @param licenseKey The store's license key to validate.
     * @return [LicenseValidationResult.VALID] if the license is active,
     *         [LicenseValidationResult.INVALID] if expired/revoked/suspended,
     *         [LicenseValidationResult.UNAVAILABLE] if the license service is unreachable.
     */
    suspend fun validate(licenseKey: String): LicenseValidationResult {
        return try {
            val response = httpClient.get("$licenseServiceBaseUrl/api/v1/license/$licenseKey")

            if (!response.status.isSuccess()) {
                if (response.status.value == 404) {
                    logger.warn("License validation: key not found (404) for ****${licenseKey.takeLast(4)}")
                    return LicenseValidationResult.INVALID
                }
                logger.warn("License validation: unexpected status ${response.status} for ****${licenseKey.takeLast(4)}")
                return LicenseValidationResult.UNAVAILABLE
            }

            val body = Json.decodeFromString<LicenseStatusResponse>(response.bodyAsText())
            when (body.status?.uppercase()) {
                "ACTIVE" -> {
                    logger.info("License validation: ACTIVE for ****${licenseKey.takeLast(4)}")
                    LicenseValidationResult.VALID
                }
                "EXPIRED", "REVOKED", "SUSPENDED" -> {
                    logger.warn("License validation: ${body.status} for ****${licenseKey.takeLast(4)}")
                    LicenseValidationResult.INVALID
                }
                else -> {
                    logger.warn("License validation: unknown status '${body.status}' for ****${licenseKey.takeLast(4)}")
                    LicenseValidationResult.UNAVAILABLE
                }
            }
        } catch (e: Exception) {
            // Fail-open: allow login when license service is unreachable
            logger.warn("License validation unavailable — allowing login (fail-open): ${e.message}")
            LicenseValidationResult.UNAVAILABLE
        }
    }
}

enum class LicenseValidationResult {
    VALID,
    INVALID,
    UNAVAILABLE,
}
