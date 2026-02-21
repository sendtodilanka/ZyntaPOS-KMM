package com.zyntasolutions.zyntapos.data.remote.api

import co.touchlab.kermit.Logger
import com.zyntasolutions.zyntapos.core.config.AppConfig
import com.zyntasolutions.zyntapos.data.local.security.SecurePreferences
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
 * - **Auth** — Bearer token injected from [SecurePreferences]; refresh callback reuses
 *   the stored refresh token (caller must handle 401 propagation via [ZentaException.AuthException])
 * - **HttpTimeout** — connect: 10 s | request: 30 s | socket: 30 s
 * - **HttpRequestRetry** — 3 attempts, exponential backoff: 1 s → 2 s → 4 s
 * - **Logging** — Kermit-backed; active **only in DEBUG builds** (controlled by [AppConfig.isDebug])
 *
 * @param prefs [SecurePreferences] used to load/store the JWT access & refresh tokens.
 * @param baseUrl Override for the server base URL (defaults to [AppConfig.BASE_URL]).
 *                Useful for injecting a [MockEngine] base URL in integration tests.
 */
fun buildApiClient(
    prefs: SecurePreferences,
    baseUrl: String = AppConfig.BASE_URL,
): HttpClient = HttpClient {

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
                val access  = prefs.get(SecurePreferences.Keys.ACCESS_TOKEN)  ?: return@loadTokens null
                val refresh = prefs.get(SecurePreferences.Keys.REFRESH_TOKEN) ?: return@loadTokens null
                BearerTokens(accessToken = access, refreshToken = refresh)
            }
            refreshTokens {
                // Refresh token flow: the caller (AuthRepositoryImpl) handles token persistence.
                // Here we simply surface the stored refresh token so Ktor re-uses it on 401.
                val refresh = prefs.get(SecurePreferences.Keys.REFRESH_TOKEN) ?: return@refreshTokens null
                BearerTokens(
                    accessToken  = prefs.get(SecurePreferences.Keys.ACCESS_TOKEN) ?: "",
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
                private val log = Logger.withTag("ZyntaPOS-Ktor")
                override fun log(message: String) = log.d { message }
            }
        }
    }
}
