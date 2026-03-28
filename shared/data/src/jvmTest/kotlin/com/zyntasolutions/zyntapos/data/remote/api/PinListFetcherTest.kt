package com.zyntasolutions.zyntapos.data.remote.api

import com.zyntasolutions.zyntapos.data.remote.dto.TlsPinsDto
import com.zyntasolutions.zyntapos.domain.port.SecureStorageKeys
import com.zyntasolutions.zyntapos.domain.port.SecureStoragePort
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ZyntaPOS — PinListFetcherTest (jvmTest)
 *
 * Unit tests for [PinListFetcher]: verifies the full Ed25519 verification path and
 * all fallback scenarios described in ADR-011.
 *
 * Test cases:
 *  A. Valid signature + future expiry → pins stored and returned
 *  B. Invalid Ed25519 signature (signed with different key) → rejected
 *  C. Expired pin list (expiresAt in the past) → rejected
 *  D. Network failure (HTTP 500) → returns null
 *  E. Malformed JSON response → returns null
 *  F. resolveActivePins: fetch fails → falls back to stored pins
 *  G. resolveActivePins: no stored pins + fetch fails → emergency backup pin
 *  H. loadStored: returns persisted pins from prefs
 *  I. Single-pin list accepted correctly
 */
class PinListFetcherTest {

    // ── Fixtures ───────────────────────────────────────────────────────

    private val testPin1 = "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    private val testPin2 = "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB="
    private val testPins = listOf(testPin1, testPin2)
    private val futureExpiry = "2099-12-31T00:00:00Z"
    private val pastExpiry   = "2020-01-01T00:00:00Z"

    private fun generateKeyPair() = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()

    /**
     * Builds a canonical Ed25519 signature over [pins] + [expiresAt].
     * Must mirror the server-side logic in `scripts/generate-tls-signing-key.sh`.
     */
    private fun signPinList(pins: List<String>, expiresAt: String, privateKey: PrivateKey): String {
        val message = (pins.sorted() + expiresAt).joinToString("\n").encodeToByteArray()
        val sig = Signature.getInstance("Ed25519")
        sig.initSign(privateKey)
        sig.update(message)
        return Base64.getEncoder().encodeToString(sig.sign())
    }

    private fun buildDto(
        pins: List<String> = testPins,
        expiresAt: String = futureExpiry,
        privateKey: PrivateKey,
    ): TlsPinsDto = TlsPinsDto(
        pins = pins,
        expiresAt = expiresAt,
        signature = signPinList(pins, expiresAt, privateKey),
    )

    private fun jsonBody(dto: TlsPinsDto) = Json.encodeToString(dto)

    private fun successEngine(body: String): MockEngine = MockEngine { _ ->
        respond(
            content = body,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )
    }

    private fun errorEngine(): MockEngine = MockEngine { _ ->
        respondError(HttpStatusCode.InternalServerError)
    }

    private fun fakePrefs(): SecureStoragePort = object : SecureStoragePort {
        private val store = mutableMapOf<String, String>()
        override fun put(key: String, value: String) { store[key] = value }
        override fun get(key: String): String? = store[key]
        override fun remove(key: String) { store.remove(key) }
        override fun clear() { store.clear() }
        override fun contains(key: String): Boolean = key in store
    }

    // ── A: valid signature + future expiry ────────────────────────────

    @Test
    fun `A - valid signature stores and returns pins`() = runTest {
        val kp = generateKeyPair()
        val engine = successEngine(jsonBody(buildDto(privateKey = kp.private)))

        val fetcher = PinListFetcher(
            baseUrl = "https://api.zyntapos.com",
            signingPublicKeyDer = kp.public.encoded,
            httpClientEngine = engine,
        )
        val prefs = fakePrefs()

        val result = fetcher.refresh(prefs)

        assertNotNull(result, "refresh must succeed for valid signature")
        assertEquals(testPins.toSet(), result.toSet())

        val storedRaw = prefs.get(SecureStorageKeys.KEY_TLS_PINS)
        assertNotNull(storedRaw, "Pins must be persisted in prefs")
        assertTrue(storedRaw.contains("sha256/"))
        assertEquals(futureExpiry, prefs.get(SecureStorageKeys.KEY_TLS_PINS_EXPIRES_AT))
    }

    // ── B: invalid Ed25519 signature ──────────────────────────────────

    @Test
    fun `B - invalid signature returns null without storing pins`() = runTest {
        val kp = generateKeyPair()
        val wrongKp = generateKeyPair() // signed with a different key

        val dto = buildDto(privateKey = wrongKp.private) // correct format, wrong key
        val engine = successEngine(jsonBody(dto))

        val fetcher = PinListFetcher(
            baseUrl = "https://api.zyntapos.com",
            signingPublicKeyDer = kp.public.encoded, // verification key ≠ signing key
            httpClientEngine = engine,
        )
        val prefs = fakePrefs()

        val result = fetcher.refresh(prefs)

        assertNull(result, "refresh must return null for invalid signature")
        assertNull(prefs.get(SecureStorageKeys.KEY_TLS_PINS), "Pins must NOT be stored on bad sig")
    }

    // ── C: expired pin list ────────────────────────────────────────────

    @Test
    fun `C - expired pin list returns null without storing pins`() = runTest {
        val kp = generateKeyPair()
        val dto = buildDto(expiresAt = pastExpiry, privateKey = kp.private)
        val engine = successEngine(jsonBody(dto))

        val fetcher = PinListFetcher(
            baseUrl = "https://api.zyntapos.com",
            signingPublicKeyDer = kp.public.encoded,
            httpClientEngine = engine,
        )
        val prefs = fakePrefs()

        val result = fetcher.refresh(prefs)

        assertNull(result, "refresh must return null for expired list")
        assertNull(prefs.get(SecureStorageKeys.KEY_TLS_PINS), "Pins must NOT be stored for expired list")
    }

    // ── D: network failure (HTTP 500) ─────────────────────────────────

    @Test
    fun `D - HTTP 500 returns null`() = runTest {
        val kp = generateKeyPair()

        val fetcher = PinListFetcher(
            baseUrl = "https://api.zyntapos.com",
            signingPublicKeyDer = kp.public.encoded,
            httpClientEngine = errorEngine(),
        )

        val result = fetcher.refresh(fakePrefs())

        assertNull(result, "refresh must return null on HTTP 500")
    }

    // ── E: malformed JSON ─────────────────────────────────────────────

    @Test
    fun `E - malformed JSON returns null`() = runTest {
        val kp = generateKeyPair()
        val engine = successEngine("not valid json at all")

        val fetcher = PinListFetcher(
            baseUrl = "https://api.zyntapos.com",
            signingPublicKeyDer = kp.public.encoded,
            httpClientEngine = engine,
        )

        val result = fetcher.refresh(fakePrefs())

        assertNull(result, "refresh must return null on malformed JSON")
    }

    // ── F: resolveActivePins falls back to stored pins ─────────────────

    @Test
    fun `F - resolveActivePins uses stored pins when server unreachable`() = runTest {
        val kp = generateKeyPair()

        val fetcher = PinListFetcher(
            baseUrl = "https://api.zyntapos.com",
            signingPublicKeyDer = kp.public.encoded,
            httpClientEngine = errorEngine(),
        )
        val prefs = fakePrefs()
        prefs.put(SecureStorageKeys.KEY_TLS_PINS, testPins.joinToString(","))

        val result = fetcher.resolveActivePins(prefs)

        assertEquals(testPins, result, "Should return stored pins on network failure")
    }

    // ── G: resolveActivePins → emergency backup ────────────────────────

    @Test
    fun `G - resolveActivePins returns backup pin when no stored pins and server fails`() = runTest {
        val kp = generateKeyPair()

        val fetcher = PinListFetcher(
            baseUrl = "https://api.zyntapos.com",
            signingPublicKeyDer = kp.public.encoded,
            httpClientEngine = errorEngine(),
        )
        val prefs = fakePrefs() // empty — no stored pins

        val result = fetcher.resolveActivePins(prefs)

        assertEquals(listOf(API_SPKI_PIN_BACKUP), result)
    }

    // ── H: loadStored ─────────────────────────────────────────────────

    @Test
    fun `H - loadStored returns previously persisted pins`() {
        val fetcher = PinListFetcher(
            baseUrl = "https://api.zyntapos.com",
            signingPublicKeyDer = ByteArray(32),
        )
        val prefs = fakePrefs()
        prefs.put(SecureStorageKeys.KEY_TLS_PINS, testPins.joinToString(","))

        val result = fetcher.loadStored(prefs)

        assertNotNull(result)
        assertEquals(testPins.toSet(), result.toSet())
    }

    @Test
    fun `H - loadStored returns null when no pins in prefs`() {
        val fetcher = PinListFetcher(
            baseUrl = "https://api.zyntapos.com",
            signingPublicKeyDer = ByteArray(32),
        )
        assertNull(fetcher.loadStored(fakePrefs()))
    }

    // ── I: single-pin list ─────────────────────────────────────────────

    @Test
    fun `I - single pin list accepted`() = runTest {
        val kp = generateKeyPair()
        val singlePin = listOf(testPin1)
        val dto = buildDto(pins = singlePin, privateKey = kp.private)
        val engine = successEngine(jsonBody(dto))

        val fetcher = PinListFetcher(
            baseUrl = "https://api.zyntapos.com",
            signingPublicKeyDer = kp.public.encoded,
            httpClientEngine = engine,
        )
        val prefs = fakePrefs()

        val result = fetcher.refresh(prefs)

        assertNotNull(result)
        assertEquals(singlePin, result)
    }
}
