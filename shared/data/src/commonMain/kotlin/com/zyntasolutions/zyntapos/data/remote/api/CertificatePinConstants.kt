package com.zyntasolutions.zyntapos.data.remote.api

/** Hostname of the ZyntaPOS API server that is subject to TLS certificate pinning. */
internal const val API_HOST = "api.zyntapos.com"

/**
 * SPKI SHA-256 pins for `api.zyntapos.com` (Cloudflare Origin Certificate).
 *
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║  TODO-SEC02 — PRODUCTION BLOCKER                                ║
 * ║                                                                  ║
 * ║  Both constants below are PLACEHOLDERS. A release build with     ║
 * ║  these values will reject every HTTPS connection to the API.     ║
 * ║                                                                  ║
 * ║  Steps before shipping:                                          ║
 * ║  1. Extract the leaf SPKI pin from the provisioned certificate:  ║
 * ║       openssl s_client -connect api.zyntapos.com:443 2>/dev/null \║
 * ║         | openssl x509 -pubkey -noout                            ║
 * ║         | openssl pkey -pubin -outform der                       ║
 * ║         | openssl dgst -sha256 -binary | base64                  ║
 * ║     Prefix the output with "sha256/" → API_SPKI_PIN_PRIMARY.    ║
 * ║                                                                  ║
 * ║  2. Extract the intermediate CA SPKI pin (for rotation safety):  ║
 * ║       openssl s_client -connect api.zyntapos.com:443 2>/dev/null \║
 * ║         | openssl x509 -noout -text | grep -A1 "Subject:"       ║
 * ║     Use the intermediate cert's public key hash →               ║
 * ║     API_SPKI_PIN_BACKUP.                                         ║
 * ║                                                                  ║
 * ║  3. NEVER ship with a backup pin that equals the primary pin —   ║
 * ║     they must cover different certificates (leaf vs intermediate) ║
 * ║     so that rotation is possible without an app update.          ║
 * ╚══════════════════════════════════════════════════════════════════╝
 */
internal const val API_SPKI_PIN_PRIMARY = "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="

/**
 * Backup SPKI SHA-256 pin (intermediate CA) for `api.zyntapos.com`.
 *
 * Pinning the intermediate CA certificate allows the leaf certificate to be
 * rotated (renewed) without requiring an app update, as long as the same CA
 * signs the new certificate.
 *
 * TODO-SEC02: Replace with the actual intermediate CA SPKI pin before release.
 */
internal const val API_SPKI_PIN_BACKUP = "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB="
