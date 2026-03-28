package com.zyntasolutions.zyntapos.data.remote.api

import io.ktor.client.HttpClientConfig

/**
 * ZyntaPOS — TLS Certificate Pinning (SEC-02)
 *
 * Installs platform-specific TLS certificate pinning into the Ktor [HttpClient] builder.
 *
 * Called from [buildApiClient] when [com.zyntasolutions.zyntapos.core.config.AppConfig.IS_DEBUG]
 * is `false` (production builds only). Debug/development builds skip pinning so local
 * servers and HTTP inspection proxies used during development are not broken.
 *
 * ## Pin set
 * Multiple pins are accepted (vararg). A TLS handshake succeeds if **any** pin in the
 * set matches any certificate in the server's chain. This enables:
 * - [API_SPKI_PIN_PRIMARY] — leaf certificate pin (changes on each Caddy renewal)
 * - [API_SPKI_PIN_BACKUP]  — intermediate CA pin (Let's Encrypt E7; stable across renewals)
 *
 * The backup pin is the safety net: if the leaf is renewed but the app has not yet been
 * updated with the new PRIMARY pin, the intermediate pin keeps connections alive.
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
 * @param host      Hostname to pin, e.g. `"api.zyntapos.com"`.
 * @param spkiPins  One or more SPKI SHA-256 fingerprints prefixed with `"sha256/"`.
 *                  Connection succeeds if any pin matches any cert in the chain.
 */
internal expect fun HttpClientConfig<*>.installCertificatePinning(host: String, vararg spkiPins: String)
