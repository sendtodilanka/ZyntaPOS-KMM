package com.zyntasolutions.zyntapos.data.remote.ird

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

/**
 * JVM (Desktop) actual implementation of [IrdApiClient].
 *
 * Uses Ktor with the CIO engine for HTTP transport.
 * Configures mTLS by loading the PKCS12 client certificate from [certPath],
 * building an [SSLContext] with the key managers, and setting it as the JVM
 * default SSL context before the HTTP client is initialized.
 *
 * Falls back to standard HTTPS (no client certificate) if [certPath] is blank
 * or the certificate file does not exist — safe for development/staging environments.
 *
 * ## Note on JVM default SSLContext
 * Setting `SSLContext.setDefault()` is a JVM-wide operation. For a production deployment,
 * consider scoping the SSLContext to the specific connection via a custom `HostnameVerifier`.
 * For Phase 3, the default-context approach is sufficient and keeps the implementation simple.
 */
actual class IrdApiClient actual constructor(
    private val endpoint: String,
    private val certPath: String,
    private val certPassword: String,
) {
    private val log = Logger.withTag("IrdApiClient")

    private val httpClient: HttpClient by lazy { buildClient() }

    actual suspend fun submitInvoice(payload: IrdInvoicePayload): IrdApiResponse {
        return try {
            val response = httpClient.post("$endpoint/invoices") {
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
            when (response.status) {
                HttpStatusCode.OK, HttpStatusCode.Created ->
                    response.body<IrdApiResponse>()
                HttpStatusCode.BadRequest ->
                    IrdApiResponse(success = false, errorCode = "INVALID_REQUEST",
                        errorMessage = "IRD rejected the invoice: HTTP 400")
                HttpStatusCode.Unauthorized ->
                    IrdApiResponse(success = false, errorCode = "AUTH_FAILED",
                        errorMessage = "IRD certificate authentication failed: HTTP 401")
                else ->
                    IrdApiResponse(success = false, errorCode = "SERVER_ERROR",
                        errorMessage = "IRD API error: HTTP ${response.status.value}")
            }
        } catch (e: Exception) {
            log.e(e) { "IRD API submitInvoice failed: ${e.message}" }
            IrdApiResponse(success = false, errorMessage = e.message ?: "Network error")
        }
    }

    actual fun close() {
        httpClient.close()
    }

    private fun buildClient(): HttpClient {
        val certFile = if (certPath.isNotBlank()) File(certPath) else null
        if (certFile != null && certFile.exists()) {
            log.i { "Loading IRD client certificate from: $certPath" }
            configureSslContext(certFile, certPassword)
        } else {
            if (certPath.isNotBlank()) {
                log.w { "IRD certificate file not found at '$certPath'; falling back to standard HTTPS" }
            }
        }

        return HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000L
                connectTimeoutMillis = 10_000L
            }
        }
    }

    /**
     * Loads the PKCS12 certificate and sets it as the JVM default SSLContext
     * so that the Ktor CIO engine picks it up for all TLS connections.
     */
    private fun configureSslContext(certFile: File, certPassword: String) {
        val password = certPassword.toCharArray()
        val keyStore = KeyStore.getInstance("PKCS12")
        FileInputStream(certFile).use { keyStore.load(it, password) }

        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, password)

        val sslCtx = SSLContext.getInstance("TLS")
        sslCtx.init(kmf.keyManagers, null, null)

        // Set as JVM default so CIO engine uses it for outgoing connections
        SSLContext.setDefault(sslCtx)
        log.i { "IRD mTLS SSLContext configured successfully" }
    }
}
