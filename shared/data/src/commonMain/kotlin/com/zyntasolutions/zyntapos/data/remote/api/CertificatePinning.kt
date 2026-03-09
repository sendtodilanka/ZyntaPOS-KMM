package com.zyntasolutions.zyntapos.data.remote.api

import io.ktor.client.HttpClientConfig

/**
 * ZyntaPOS — TLS Certificate Pinning (SEC-02)
 *
 * Installs platform-specific TLS certificate pinning into the Ktor [HttpClient] builder.
 *
 * Called from [buildApiClient] when [com.zyntasolutions.zyntapos.core.config.AppConfig.IS_DEBUG]
 * is `false` (production builds only). Debug/development builds skip pinning so local
 * servers and HTTP proxies used during development are not broken.
 *
 * ## Updating Pins
 * When the TLS certificate is rotated, extract the new SPKI pin:
 * ```bash
 * openssl s_client -connect api.zyntapos.com:443 2>/dev/null \
 *   | openssl x509 -pubkey -noout \
 *   | openssl pkey -pubin -outform der \
 *   | openssl dgst -sha256 -binary | base64
 * ```
 * Prefix with "sha256/" and update [API_SPKI_PIN_PRIMARY] in [CertificatePinConstants].
 *
 * ## Platform Implementations
 * - **Android** (`androidMain`): OkHttp [okhttp3.CertificatePinner]
 * - **JVM/Desktop** (`jvmMain`): Custom [javax.net.ssl.X509TrustManager] via CIO HTTPS config
 *
 * @param host    Hostname to pin, e.g. `"api.zyntapos.com"`.
 * @param spkiPin SPKI SHA-256 fingerprint prefixed with `"sha256/"`.
 */
internal expect fun HttpClientConfig<*>.installCertificatePinning(host: String, spkiPin: String)
