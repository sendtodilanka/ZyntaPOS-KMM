package com.zyntasolutions.zyntapos.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO for the `GET /.well-known/tls-pins.json` response (ADR-011 — Signed Pin List).
 *
 * The server signs the canonical message (see below) with an Ed25519 private key stored
 * offline. The app verifies the signature using the hardcoded [API_PIN_SIGNING_PUBLIC_KEY]
 * before trusting the pin list.
 *
 * ## JSON format
 * ```json
 * {
 *   "pins": ["sha256/AAAA...=", "sha256/BBBB...="],
 *   "expires_at": "2026-06-01T00:00:00Z",
 *   "signature": "<Base64-encoded Ed25519 signature>"
 * }
 * ```
 *
 * ## Canonical signed message
 * The Ed25519 signature covers the UTF-8 encoding of:
 * ```
 * <pin[0]>\n<pin[1]>\n...\n<expires_at>
 * ```
 * where pins are sorted lexicographically before joining. This deterministic format
 * ensures server and client construct identical byte sequences for sign / verify.
 *
 * @param pins       SPKI SHA-256 pins in `"sha256/<base64>"` format.
 * @param expiresAt  ISO-8601 UTC timestamp after which the pin list should be refreshed.
 * @param signature  Standard Base64-encoded raw Ed25519 signature (64 bytes → 88 chars).
 */
@Serializable
data class TlsPinsDto(
    val pins: List<String>,
    @SerialName("expires_at") val expiresAt: String,
    val signature: String,
)
