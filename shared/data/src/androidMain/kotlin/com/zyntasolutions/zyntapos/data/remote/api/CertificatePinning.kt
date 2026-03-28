package com.zyntasolutions.zyntapos.data.remote.api

import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttpConfig
import okhttp3.CertificatePinner

/**
 * Android actual — OkHttp [CertificatePinner].
 *
 * OkHttp validates the SPKI pin before completing the TLS handshake, so any
 * connection to a server presenting a non-pinned certificate is immediately
 * rejected with an [javax.net.ssl.SSLPeerUnverifiedException].
 *
 * All [spkiPins] are added for [host]. OkHttp accepts the connection if any pin
 * matches any certificate in the server's chain, so the backup (intermediate CA)
 * pin keeps connections alive across leaf cert renewals.
 *
 * The unchecked cast is safe: the OkHttp engine is the only Ktor engine
 * on Android (declared in `androidMain.dependencies` of `:shared:data`).
 */
@Suppress("UNCHECKED_CAST")
internal actual fun HttpClientConfig<*>.installCertificatePinning(host: String, vararg spkiPins: String) {
    val config = this as HttpClientConfig<OkHttpConfig>
    config.engine {
        config {
            certificatePinner(
                CertificatePinner.Builder()
                    .apply { spkiPins.forEach { pin -> add(host, pin) } }
                    .build()
            )
        }
    }
}
