package com.zyntasolutions.zyntapos.data.remote.api

/** Hostname of the ZyntaPOS API server that is subject to TLS certificate pinning. */
internal const val API_HOST = "api.zyntapos.com"

/**
 * SPKI SHA-256 pin for `api.zyntapos.com` (Cloudflare Origin Certificate).
 *
 * TODO-SEC02: Replace with the actual pin extracted from the provisioned certificate.
 * The placeholder value below will cause production connections to fail until updated.
 *
 * Run the following command to extract the correct pin value:
 * ```bash
 * openssl s_client -connect api.zyntapos.com:443 2>/dev/null \
 *   | openssl x509 -pubkey -noout \
 *   | openssl pkey -pubin -outform der \
 *   | openssl dgst -sha256 -binary | base64
 * ```
 * Then prefix the output with `"sha256/"` and update this constant.
 * Always add a backup pin (intermediate CA) before rotating the primary.
 */
internal const val API_SPKI_PIN_PRIMARY = "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
