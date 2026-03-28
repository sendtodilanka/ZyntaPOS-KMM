package com.zyntasolutions.zyntapos.data.remote.api

import com.zyntasolutions.zyntapos.core.logger.ZyntaLogger
import com.zyntasolutions.zyntapos.data.remote.dto.TlsPinsDto
import com.zyntasolutions.zyntapos.domain.port.SecureStorageKeys
import com.zyntasolutions.zyntapos.domain.port.SecureStoragePort
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * ZyntaPOS — TLS Signed Pin List Fetcher (ADR-011)
 *
 * Fetches `GET /.well-known/tls-pins.json`, verifies the Ed25519 signature,
 * checks the expiry, and caches the verified pin list in [SecureStoragePort].
 *
 * ## Startup flow
 * 1. [refresh] — fetch the signed pin list from the server (CA-only TLS, no pinning).
 * 2. Verify Ed25519 signature using [signingPublicKeyDer] (hardcoded in the binary).
 * 3. Reject if [TlsPinsDto.expiresAt] is in the past.
 * 4. Persist [SecureStorageKeys.KEY_TLS_PINS] and [SecureStorageKeys.KEY_TLS_PINS_EXPIRES_AT]
 *    in [SecureStoragePort].
 * 5. Return the newly verified pin list.
 *
 * ## Fallback chain (used by [ApiClient])
 * ```
 * stored + valid   → use stored pins
 * fetch succeeds   → use fetched & verified pins (also persists them)
 * fetch fails      → use stored pins (may be expired — better than no pins)
 * no stored pins   → use API_SPKI_PIN_BACKUP (emergency hardcoded fallback)
 * ```
 *
 * ## Why the fetch uses CA-only validation
 * The pin list fetch cannot itself use certificate pinning — we don't have fresh pins
 * yet. Standard CA validation is sufficient here because the Ed25519 signature
 * provides app-level integrity: a MITM can intercept the response but cannot forge
 * a valid signature without the Ed25519 private key.
 *
 * @param baseUrl              Base URL of the API server, e.g. `"https://api.zyntapos.com"`.
 * @param signingPublicKeyDer  Ed25519 public key in X.509 SubjectPublicKeyInfo DER encoding.
 *                             Defaults to [API_PIN_SIGNING_PUBLIC_KEY] decoded from Base64.
 * @param httpClientEngine     Optional Ktor engine override. Defaults to the platform default
 *                             (OkHttp on Android, CIO on JVM). Inject a [MockEngine] in tests.
 */
class PinListFetcher @OptIn(ExperimentalEncodingApi::class) constructor(
    private val baseUrl: String,
    private val signingPublicKeyDer: ByteArray = Base64.Default.decode(API_PIN_SIGNING_PUBLIC_KEY),
    private val httpClientEngine: HttpClientEngine? = null,
) {
    private val log = ZyntaLogger.forModule("PinListFetcher")

    /**
     * Fetches and verifies the pin list from the server.
     *
     * On success: persists the verified pins to [prefs] and returns them.
     * On any failure (network error, invalid signature, malformed response,
     * expired list): logs the reason and returns `null`. The caller should
     * fall back to [loadStored] or [API_SPKI_PIN_BACKUP].
     *
     * The Ktor client used here has **no certificate pinning** installed —
     * only standard CA chain validation. This is intentional: see class KDoc.
     */
    suspend fun refresh(prefs: SecureStoragePort): List<String>? {
        return try {
            val dto = fetchDto()
            verifyAndStore(dto, prefs)
        } catch (e: Exception) {
            log.w("Pin list fetch failed: ${e.message}")
            null
        }
    }

    /**
     * Loads the previously verified and stored pin list from [prefs].
     *
     * Returns `null` if no pins are stored. Does NOT check expiry — expired
     * stored pins are still preferred over a network failure. The next
     * successful [refresh] call will replace them.
     */
    fun loadStored(prefs: SecureStoragePort): List<String>? {
        val raw = prefs.get(SecureStorageKeys.KEY_TLS_PINS) ?: return null
        return raw.split(",").filter { it.startsWith("sha256/") }.takeIf { it.isNotEmpty() }
    }

    /**
     * Returns the best available pins using the full fallback chain:
     * 1. Fetches fresh pins from the server (verified by Ed25519 + expiry).
     * 2. If the fetch fails, uses stored pins (possibly expired — better than nothing).
     * 3. If no stored pins exist, returns the single [API_SPKI_PIN_BACKUP] pin.
     *
     * Designed to be called once at startup before [ApiClient] is constructed.
     */
    suspend fun resolveActivePins(prefs: SecureStoragePort): List<String> {
        val fetched = refresh(prefs)
        if (fetched != null) return fetched

        val stored = loadStored(prefs)
        if (stored != null) {
            log.w("Using cached pin list (server unreachable)")
            return stored
        }

        log.w("No stored pins — falling back to emergency backup pin")
        return listOf(API_SPKI_PIN_BACKUP)
    }

    // ── Internal ──────────────────────────────────────────────────────────

    private suspend fun fetchDto(): TlsPinsDto {
        // Build a plain Ktor client — CA validation only, no certificate pinning.
        // An injected engine (httpClientEngine) is used in tests (MockEngine); production
        // uses the platform default discovered via the service-loader mechanism.
        val client = if (httpClientEngine != null) {
            HttpClient(httpClientEngine) { configurePinFetchClient() }
        } else {
            HttpClient { configurePinFetchClient() }
        }
        return client.use { it.get("$baseUrl/.well-known/tls-pins.json").body() }
    }

    private fun io.ktor.client.HttpClientConfig<*>.configurePinFetchClient() {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000L
            connectTimeoutMillis = 10_000L
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun verifyAndStore(dto: TlsPinsDto, prefs: SecureStoragePort): List<String>? {
        // 1. Check expiry first (fast, no crypto)
        val expiresAt = runCatching { Instant.parse(dto.expiresAt) }.getOrNull()
        if (expiresAt == null || expiresAt <= Clock.System.now()) {
            log.w("Pin list rejected: expired or unparseable expiresAt='${dto.expiresAt}'")
            return null
        }

        // 2. Validate pin format
        val validPins = dto.pins.filter { it.startsWith("sha256/") }
        if (validPins.isEmpty()) {
            log.w("Pin list rejected: no valid sha256/ pins")
            return null
        }

        // 3. Reconstruct canonical signed message
        //    Format: sorted_pin[0]\nsorted_pin[1]\n...\nexpiresAt
        val canonicalMessage = (validPins.sorted() + dto.expiresAt)
            .joinToString("\n")
            .encodeToByteArray()

        // 4. Verify Ed25519 signature
        val signatureBytes = runCatching { Base64.Default.decode(dto.signature) }.getOrNull()
        if (signatureBytes == null) {
            log.w("Pin list rejected: signature is not valid Base64")
            return null
        }
        val signatureValid = verifyEd25519Signature(canonicalMessage, signatureBytes, signingPublicKeyDer)
        if (!signatureValid) {
            log.w("Pin list rejected: Ed25519 signature verification failed")
            return null
        }

        // 5. Persist verified pins
        prefs.put(SecureStorageKeys.KEY_TLS_PINS, validPins.joinToString(","))
        prefs.put(SecureStorageKeys.KEY_TLS_PINS_EXPIRES_AT, dto.expiresAt)
        log.d("Pin list verified and stored: ${validPins.size} pins, expires ${dto.expiresAt}")

        return validPins
    }
}
