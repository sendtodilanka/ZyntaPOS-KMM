package com.zyntasolutions.zyntapos.data.remote.api

import com.zyntasolutions.zyntapos.core.logger.ZyntaLogger
import com.zyntasolutions.zyntapos.core.config.AppConfig
import com.zyntasolutions.zyntapos.domain.port.SecureStorageKeys
import com.zyntasolutions.zyntapos.domain.port.SecureStoragePort
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.Logger as KtorLogger
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * ZyntaPOS — Ktor HTTP Client factory.
 *
 * Configured with:
 * - **ContentNegotiation** — kotlinx.serialization JSON (lenient, snake_case friendly)
 * - **Auth** — Bearer token injected from [SecureStoragePort]; refresh callback reuses
 *   the stored refresh token (caller must handle 401 propagation via [ZyntaException.AuthException])
 * - **HttpTimeout** — connect: 10 s | request: 30 s | socket: 30 s
 * - **HttpRequestRetry** — 3 attempts, exponential backoff: 1 s → 2 s → 4 s
 * - **Logging** — Kermit-backed; active **only in DEBUG builds** (controlled by [AppConfig.isDebug])
 * - **TLS Pinning** — pins sourced from [SecureStoragePort] (set by [PinListFetcher] on
 *   startup); falls back to [API_SPKI_PIN_BACKUP] when no verified pins are stored.
 *
 * MERGED-F3 (2026-02-22): [prefs] parameter type changed from `SecurePreferences`
 * (`:shared:security`) to [SecureStoragePort] (`:shared:domain`) so `:shared:data`
 * holds no compile-time dependency on `:shared:security`.
 *
 * ADR-011 (Signed Pin List): [PinListFetcher.resolveActivePins] should be called before
 * this function so that [prefs] contains the latest verified pins. If called before the
 * fetcher has run (first cold start), the backup pin provides connectivity.
 *
 * @param prefs   [SecureStoragePort] used to load/store JWT tokens and verified TLS pins.
 * @param baseUrl Override for the server base URL (defaults to [AppConfig.BASE_URL]).
 *                Useful for injecting a [MockEngine] base URL in integration tests.
 */
fun buildApiClient(
    prefs: SecureStoragePort,
    baseUrl: String = AppConfig.BASE_URL,
): HttpClient = HttpClient {

    // ── TLS Certificate Pinning (SEC-02 / ADR-011) ─────────────────────
    // Enforced in production builds only. Development/debug builds skip
    // pinning so local servers and HTTP inspection proxies work normally.
    //
    // Pin resolution order (ADR-011 Signed Pin List):
    //   1. Verified pins stored in prefs by PinListFetcher.resolveActivePins()
    //   2. Emergency fallback: API_SPKI_PIN_BACKUP (Let's Encrypt E7 intermediate CA)
    if (!AppConfig.IS_DEBUG) {
        val host = baseUrl
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
        val storedPins = prefs.get(SecureStorageKeys.KEY_TLS_PINS)
            ?.split(",")
            ?.filter { it.startsWith("sha256/") }
            ?.takeIf { it.isNotEmpty() }
        val activePins = storedPins ?: listOf(API_SPKI_PIN_BACKUP)
        installCertificatePinning(host, *activePins.toTypedArray())
    }

    // ── JSON Serialization ─────────────────────────────────────────────
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
                encodeDefaults = true
            }
        )
    }

    // ── Bearer Token Auth ──────────────────────────────────────────────
    // Tokens are persisted in encrypted storage between sessions.
    install(Auth) {
        bearer {
            loadTokens {
                val access  = prefs.get(SecureStorageKeys.KEY_ACCESS_TOKEN)  ?: return@loadTokens null
                val refresh = prefs.get(SecureStorageKeys.KEY_REFRESH_TOKEN) ?: return@loadTokens null
                BearerTokens(accessToken = access, refreshToken = refresh)
            }
            refreshTokens {
                // Refresh token flow: the caller (AuthRepositoryImpl) handles token persistence.
                // Here we simply surface the stored refresh token so Ktor re-uses it on 401.
                val refresh = prefs.get(SecureStorageKeys.KEY_REFRESH_TOKEN) ?: return@refreshTokens null
                BearerTokens(
                    accessToken  = prefs.get(SecureStorageKeys.KEY_ACCESS_TOKEN) ?: "",
                    refreshToken = refresh,
                )
            }
            sendWithoutRequest { request ->
                request.url.host == baseUrl
                    .removePrefix("https://")
                    .removePrefix("http://")
                    .substringBefore("/")
            }
        }
    }

    // ── Content Encoding (GZIP) ─────────────────────────────────────
    // Enables transparent GZIP compression for request/response bodies.
    // Caddy (reverse proxy) already supports gzip; this reduces sync payload
    // bandwidth by ~60-80% for large product catalogs. (C6.1 Item 4)
    install(io.ktor.client.plugins.compression.ContentEncoding) {
        gzip()
    }

    // ── Timeouts ───────────────────────────────────────────────────────
    install(HttpTimeout) {
        connectTimeoutMillis = 10_000L
        requestTimeoutMillis = 30_000L
        socketTimeoutMillis  = 30_000L
    }

    // ── Retry (exponential backoff: 1s, 2s, 4s) ───────────────────────
    install(HttpRequestRetry) {
        retryOnServerErrors(maxRetries = 3)
        retryOnException(maxRetries = 3, retryOnTimeout = true)
        exponentialDelay(base = 2.0, maxDelayMs = 4_000L)
    }

    // ── Logging (DEBUG builds only) ────────────────────────────────────
    if (AppConfig.IS_DEBUG) {
        install(Logging) {
            level  = LogLevel.HEADERS
            logger = object : KtorLogger {
                private val log = ZyntaLogger.forModule("ZyntaPOS-Ktor")
                override fun log(message: String) = log.d(message)
            }
        }
    }
}
