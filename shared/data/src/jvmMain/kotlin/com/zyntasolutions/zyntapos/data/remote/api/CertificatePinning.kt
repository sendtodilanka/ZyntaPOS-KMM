package com.zyntasolutions.zyntapos.data.remote.api

import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIOEngineConfig
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * JVM/Desktop actual — custom [X509TrustManager] with SPKI SHA-256 pinning via CIO engine.
 *
 * Validation sequence:
 * 1. Delegate standard chain validation to the platform's default [TrustManagerFactory]
 *    (ensures the certificate is signed by a trusted CA).
 * 2. Check that at least one certificate in the chain has an SPKI SHA-256 fingerprint
 *    matching [spkiPin]. Reject the connection if none match.
 *
 * The unchecked cast is safe: the CIO engine is the only Ktor engine on JVM/Desktop
 * (declared in `jvmMain.dependencies` of `:shared:data`).
 */
@Suppress("UNCHECKED_CAST")
internal actual fun HttpClientConfig<*>.installCertificatePinning(host: String, spkiPin: String) {
    val config = this as HttpClientConfig<CIOEngineConfig>
    config.engine {
        https {
            trustManager = SpkiPinnedTrustManager(host, spkiPin)
        }
    }
}

/**
 * TLS trust manager that enforces SPKI SHA-256 certificate pinning.
 *
 * @param pinnedHost Hostname for which pinning is enforced (used in the error message).
 * @param spkiPin    SPKI SHA-256 pin in `"sha256/<base64>"` format.
 */
private class SpkiPinnedTrustManager(
    private val pinnedHost: String,
    spkiPin: String,
) : X509TrustManager {

    private val pinnedHash: ByteArray = Base64.getDecoder()
        .decode(spkiPin.removePrefix("sha256/"))

    private val delegate: X509TrustManager = TrustManagerFactory
        .getInstance(TrustManagerFactory.getDefaultAlgorithm())
        .also { it.init(null as KeyStore?) }
        .trustManagers
        .filterIsInstance<X509TrustManager>()
        .first()

    override fun getAcceptedIssuers(): Array<X509Certificate> = delegate.acceptedIssuers

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
        delegate.checkClientTrusted(chain, authType)

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        // Step 1: Standard CA chain validation
        delegate.checkServerTrusted(chain, authType)

        // Step 2: SPKI pin check — at least one cert in the chain must match
        val md = MessageDigest.getInstance("SHA-256")
        val pinMatched = chain.any { cert ->
            md.reset()
            md.digest(cert.publicKey.encoded).contentEquals(pinnedHash)
        }
        if (!pinMatched) {
            throw SSLPeerUnverifiedException(
                "Certificate pinning failed for $pinnedHost — no SPKI match. " +
                    "If the certificate was rotated, update API_SPKI_PIN_PRIMARY in CertificatePinning.kt."
            )
        }
    }
}
