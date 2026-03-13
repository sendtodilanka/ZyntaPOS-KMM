package com.zyntasolutions.zyntapos.data.remote.ird

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Android actual implementation of [IrdApiClient].
 *
 * Uses Ktor with the OkHttp engine for HTTP transport.
 * Configures mTLS by loading the PKCS12 client certificate from [certPath]
 * and injecting it into an [OkHttpClient] via [SSLContext].
 *
 * Falls back to standard HTTPS (no client certificate) if [certPath] is blank
 * or the certificate file does not exist — safe for development/staging environments.
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
        val sslCtx = if (certFile != null && certFile.exists()) {
            log.i { "Loading IRD client certificate from: $certPath" }
            buildSslContext(certFile, certPassword)
        } else {
            if (certPath.isNotBlank()) {
                log.w { "IRD certificate file not found at '$certPath'; falling back to standard HTTPS" }
            }
            null
        }

        return HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000L
                connectTimeoutMillis = 10_000L
            }
            engine {
                if (sslCtx != null) {
                    val trustManager = getDefaultTrustManager()
                    preconfigured = OkHttpClient.Builder()
                        .sslSocketFactory(sslCtx.socketFactory, trustManager)
                        .build()
                }
            }
        }
    }

    private fun buildSslContext(certFile: File, certPassword: String): SSLContext {
        val password = certPassword.toCharArray()
        val keyStore = KeyStore.getInstance("PKCS12")
        FileInputStream(certFile).use { keyStore.load(it, password) }

        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, password)

        val sslCtx = SSLContext.getInstance("TLS")
        sslCtx.init(kmf.keyManagers, null, null)
        return sslCtx
    }

    private fun getDefaultTrustManager(): X509TrustManager {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as KeyStore?)
        return tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
    }
}
