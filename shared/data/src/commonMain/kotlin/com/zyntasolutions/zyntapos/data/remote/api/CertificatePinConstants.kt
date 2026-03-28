package com.zyntasolutions.zyntapos.data.remote.api

/** Hostname of the ZyntaPOS API server that is subject to TLS certificate pinning. */
internal const val API_HOST = "api.zyntapos.com"

/**
 * SPKI SHA-256 pins for `api.zyntapos.com`.
 *
 * Extracted on 2026-03-28 via:
 *   openssl s_client -connect api.zyntapos.com:443 </dev/null 2>/dev/null \
 *     | openssl x509 -pubkey -noout \
 *     | openssl pkey -pubin -outform der \
 *     | openssl dgst -sha256 -binary | base64
 *
 * Certificate chain at time of extraction:
 *   Leaf  : CN=zyntapos.com  (issued by Let's Encrypt E7, expires 2026-05-31)
 *   Inter : CN=E7             (Let's Encrypt, issued by ISRG Root X1)
 *
 * ── Rotation policy ────────────────────────────────────────────────────────
 * Caddy auto-renews the leaf cert every ~60 days (Let's Encrypt 90-day limit).
 * The PRIMARY pin will change with each renewal. Update it before the current
 * cert expires AND ship the app update ≥7 days before expiry.
 *
 * The BACKUP pin covers the Let's Encrypt E7 intermediate CA — it stays valid
 * as long as LE continues to use the E7 intermediate for `zyntapos.com`. If
 * LE rotates to E8 or another intermediate, update BACKUP immediately.
 *
 * ── How to re-extract after a renewal ──────────────────────────────────────
 *   # PRIMARY — leaf cert public key
 *   openssl s_client -connect api.zyntapos.com:443 </dev/null 2>/dev/null \
 *     | openssl x509 -pubkey -noout \
 *     | openssl pkey -pubin -outform der \
 *     | openssl dgst -sha256 -binary | base64
 *   # → prefix with "sha256/" and replace API_SPKI_PIN_PRIMARY
 *
 *   # BACKUP — intermediate CA (cert #2 in chain)
 *   openssl s_client -connect api.zyntapos.com:443 -showcerts </dev/null 2>/dev/null \
 *     | awk "/BEGIN CERTIFICATE/{n++} n==2,/END CERTIFICATE/ && n==2" \
 *     | openssl x509 -pubkey -noout \
 *     | openssl pkey -pubin -outform der \
 *     | openssl dgst -sha256 -binary | base64
 *   # → prefix with "sha256/" and replace API_SPKI_PIN_BACKUP
 */
internal const val API_SPKI_PIN_PRIMARY = "sha256/U15ycHcHA6rYMzCokiEnm+i851hmZT+RiFMlagBiKyc="

/**
 * Backup SPKI SHA-256 pin for `api.zyntapos.com` — covers the Let's Encrypt E7
 * intermediate CA (`C=US, O=Let's Encrypt, CN=E7`, signed by ISRG Root X1).
 *
 * Pinning the intermediate CA allows the leaf certificate to be renewed by Caddy
 * without requiring an app update, provided Let's Encrypt continues to use E7 as
 * the signing intermediate for this domain.
 */
internal const val API_SPKI_PIN_BACKUP = "sha256/y7xVm0TVJNahMr2sZydE2jQH8SquXV9yLF9seROHHHU="
