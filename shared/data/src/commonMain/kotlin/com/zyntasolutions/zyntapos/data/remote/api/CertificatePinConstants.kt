package com.zyntasolutions.zyntapos.data.remote.api

/** Hostname of the ZyntaPOS API server that is subject to TLS certificate pinning. */
internal const val API_HOST = "api.zyntapos.com"

/**
 * Ed25519 public key used to verify the signed TLS pin list at `GET /.well-known/tls-pins.json`.
 *
 * Encoded as standard Base64 (not URL-safe) of the X.509 SubjectPublicKeyInfo DER bytes.
 * This key is long-lived (years) and stored offline, separate from the TLS private key.
 *
 * ── Generating a new signing keypair ──────────────────────────────────────
 * Run `scripts/generate-tls-signing-key.sh` and replace this constant with the
 * printed public key value:
 *
 * ```bash
 * # 1. Generate Ed25519 keypair
 * openssl genpkey -algorithm ed25519 -out pin-signing-key.pem
 *
 * # 2. Print Base64 DER public key (X.509 SubjectPublicKeyInfo format)
 * openssl pkey -pubout -outform der -in pin-signing-key.pem | base64 -w 0
 * ```
 *
 * ── Rotation policy ──────────────────────────────────────────────────────
 * Only a compromise or deprecation of this Ed25519 signing key requires an
 * app update — a rare event measured in years, not months. Routine TLS leaf
 * certificate renewals do NOT require updating this constant.
 *
 * ── Security model ───────────────────────────────────────────────────────
 * An attacker intercepting the pin-list fetch cannot forge a valid signature
 * without the corresponding Ed25519 private key. The app only accepts pin
 * updates that pass this signature check.
 *
 * @see PinListFetcher
 * @see [ADR-011] docs/adr/ADR-011-TLS-Certificate-Pinning-Strategy.md
 */
internal const val API_PIN_SIGNING_PUBLIC_KEY =
    "MCowBQYDK2VwAyEA0fL9TrCpNRRIJIQ+LFhxRmQKBCvlLHYDVBZpSEamqBM="

/**
 * Emergency backup SPKI SHA-256 pin for `api.zyntapos.com`.
 *
 * Covers the **Let's Encrypt E7 intermediate CA** (`C=US, O=Let's Encrypt, CN=E7`,
 * signed by ISRG Root X1). This pin is the **last-resort fallback** used when:
 *  - The app has no verified pin list stored in [SecurePreferences], AND
 *  - The server is unreachable so no pin list can be fetched.
 *
 * ── When it changes ──────────────────────────────────────────────────────
 * This pin only changes if Let's Encrypt rotates the E7 intermediate CA —
 * an infrequent event. If it changes, update it here AND ship an app update
 * before the old intermediate expires.
 *
 * ── Re-extraction command ─────────────────────────────────────────────────
 * ```bash
 * # BACKUP — intermediate CA (cert #2 in chain)
 * openssl s_client -connect api.zyntapos.com:443 -showcerts </dev/null 2>/dev/null \
 *   | awk "/BEGIN CERTIFICATE/{n++} n==2,/END CERTIFICATE/ && n==2" \
 *   | openssl x509 -pubkey -noout \
 *   | openssl pkey -pubin -outform der \
 *   | openssl dgst -sha256 -binary | base64
 * # → prefix with "sha256/" and replace API_SPKI_PIN_BACKUP
 * ```
 *
 * Extracted on 2026-03-28 — CN=E7 (Let's Encrypt), Issuer: ISRG Root X1.
 */
internal const val API_SPKI_PIN_BACKUP = "sha256/y7xVm0TVJNahMr2sZydE2jQH8SquXV9yLF9seROHHHU="
