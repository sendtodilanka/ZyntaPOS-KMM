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
 *    matching **any** of [spkiPins]. Reject the connection if none match.
 *
 * The unchecked cast is safe: the CIO engine is the only Ktor engine on JVM/Desktop
 * (declared in `jvmMain.dependencies` of `:shared:data`).
 */
@Suppress("UNCHECKED_CAST")
internal actual fun HttpClientConfig<*>.installCertificatePinning(host: String, vararg spkiPins: String) {
    val config = this as HttpClientConfig<CIOEngineConfig>
    config.engine {
        https {
            trustManager = SpkiPinnedTrustManager(host, spkiPins.toList())
        }
    }
}

/**
 * TLS trust manager that enforces SPKI SHA-256 certificate pinning.
 *
 * A connection is accepted if **any** pin in [spkiPins] matches **any** certificate
 * in the server's chain. This allows a backup intermediate CA pin to survive leaf
 * certificate renewals without requiring an app update.
 *
 * @param pinnedHost Hostname for which pinning is enforced (used in the error message).
 * @param spkiPins   SPKI SHA-256 pins in `"sha256/<base64>"` format.
 */
private class SpkiPinnedTrustManager(
    private val pinnedHost: String,
    spkiPins: List<String>,
) : X509TrustManager {

    private val pinnedHashes: List<ByteArray> = spkiPins.map { pin ->
        Base64.getDecoder().decode(pin.removePrefix("sha256/"))
    }

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

        // Step 2: SPKI pin check — at least one cert in the chain must match at least one pin
        val md = MessageDigest.getInstance("SHA-256")
        val pinMatched = chain.any { cert ->
            val certHash = md.digest(cert.publicKey.encoded).also { md.reset() }
            pinnedHashes.any { it.contentEquals(certHash) }
        }
        if (!pinMatched) {
            throw SSLPeerUnverifiedException(
                "Certificate pinning failed for $pinnedHost — no SPKI match. " +
                    "If the certificate was rotated, update API_SPKI_PIN_PRIMARY in CertificatePinConstants.kt."
            )
        }
    }
}
