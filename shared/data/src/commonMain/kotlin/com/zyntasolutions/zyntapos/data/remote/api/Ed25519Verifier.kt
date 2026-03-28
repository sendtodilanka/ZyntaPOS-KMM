package com.zyntasolutions.zyntapos.data.remote.api

/**
 * Platform-specific Ed25519 signature verification (expect/actual).
 *
 * Verifies that [signature] is a valid Ed25519 signature over [message] using the
 * Ed25519 public key encoded as [publicKeyDer] (X.509 SubjectPublicKeyInfo DER bytes).
 *
 * Used by [PinListFetcher] to authenticate the signed TLS pin list fetched from
 * `GET /.well-known/tls-pins.json` (ADR-011 — Signed Pin List).
 *
 * ## Platform notes
 * - **JVM** (Java 15+): uses `Signature.getInstance("Ed25519")` natively.
 * - **Android API 28+**: uses `Signature.getInstance("EdDSA")` (Conscrypt provider).
 * - **Android API 24–27**: Ed25519 not available in platform providers; returns `false`,
 *   which causes [PinListFetcher] to fall back to the stored or backup pin.
 *
 * @param message      The byte sequence that was signed. Must match the canonical message
 *                     format used by the server (pins sorted + "\n" + expires_at).
 * @param signature    Raw 64-byte Ed25519 signature (not Base64-encoded).
 * @param publicKeyDer Ed25519 public key in X.509 SubjectPublicKeyInfo DER encoding.
 * @return `true` if the signature is cryptographically valid; `false` otherwise.
 */
internal expect fun verifyEd25519Signature(
    message: ByteArray,
    signature: ByteArray,
    publicKeyDer: ByteArray,
): Boolean
