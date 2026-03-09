package com.zyntasolutions.zyntapos.data.remote.api

import io.ktor.client.HttpClientConfig

/**
 * ZyntaPOS — TLS Certificate Pinning (SEC-02)
 *
 * Pins the SPKI SHA-256 fingerprint of the `api.zyntapos.com` TLS certificate
 * so that MITM attacks using rogue CAs are detected and rejected at the
 * transport layer, even if the device's system trust store is compromised.
 *
 * ## Updating Pins
 * When the TLS certificate is rotated, extract the new SPKI pin:
 * ```bash
 * openssl s_client -connect api.zyntapos.com:443 2>/dev/null \
 *   | openssl x509 -pubkey -noout \
 *   | openssl pkey -pubin -outform der \
 *   | openssl dgst -sha256 -binary | base64
 * ```
 * Prefix the output with "sha256/" and update [API_SPKI_PIN_PRIMARY].
 * Always add a backup pin (intermediate CA) before removing the primary.
 *
 * ## Platform Implementations
 * - **Android** (`androidMain`): OkHttp [CertificatePinner] — enforced at OkHttp level
 * - **JVM/Desktop** (`jvmMain`): Custom [javax.net.ssl.X509TrustManager] via CIO engine HTTPS config
 */

/** SPKI SHA-256 pin for the primary TLS certificate on `api.zyntapos.com`. */
internal const val API_HOST = "api.zyntapos.com"

/**
 * SPKI SHA-256 pin for api.zyntapos.com (Cloudflare Origin Certificate).
 *
 * TODO-SEC02: Replace with actual pin extracted from the provisioned certificate.
 * The placeholder value below will cause connections to fail in production until
 * updated. Run the openssl command in the KDoc above to obtain the correct pin.
 */
internal const val API_SPKI_PIN_PRIMARY = "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="

/**
 * Installs platform-specific TLS certificate pinning into the Ktor [HttpClient] builder.
 *
 * Called from [buildApiClient] when [com.zyntasolutions.zyntapos.core.config.AppConfig.IS_DEBUG]
 * is `false` (production builds only). Debug/development builds skip pinning so local
 * servers and HTTP proxies used during development are not broken.
 *
 * @param host    Hostname to pin, e.g. `"api.zyntapos.com"`.
 * @param spkiPin SPKI SHA-256 fingerprint prefixed with `"sha256/"`.
 */
internal expect fun HttpClientConfig<*>.installCertificatePinning(host: String, spkiPin: String)
