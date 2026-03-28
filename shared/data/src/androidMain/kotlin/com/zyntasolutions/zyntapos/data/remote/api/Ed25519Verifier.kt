package com.zyntasolutions.zyntapos.data.remote.api

import java.security.KeyFactory
import java.security.NoSuchAlgorithmException
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Android actual — Ed25519 signature verification.
 *
 * Ed25519 availability by API level:
 * - API 28–30: `"EdDSA"` via Conscrypt.
 * - API 31+  : both `"Ed25519"` and `"EdDSA"` available.
 * - API 24–27: neither algorithm in standard providers — returns `false`, triggering
 *              the [PinListFetcher] fallback chain (stored pins → backup pin).
 *
 * The `runCatching` block around each attempt ensures any `NoSuchAlgorithmException`
 * or other security-provider failure is handled gracefully.
 */
internal actual fun verifyEd25519Signature(
    message: ByteArray,
    signature: ByteArray,
    publicKeyDer: ByteArray,
): Boolean {
    // Attempt 1: "Ed25519" (API 31+ standard name)
    val attempt1 = runCatching {
        val publicKey = KeyFactory.getInstance("Ed25519")
            .generatePublic(X509EncodedKeySpec(publicKeyDer))
        val sig = Signature.getInstance("Ed25519")
        sig.initVerify(publicKey)
        sig.update(message)
        sig.verify(signature)
    }
    if (attempt1.isSuccess) return attempt1.getOrDefault(false)

    // Attempt 2: "EdDSA" (API 28–30 via Conscrypt)
    if (attempt1.exceptionOrNull() is NoSuchAlgorithmException) {
        return runCatching {
            val publicKey = KeyFactory.getInstance("EdDSA")
                .generatePublic(X509EncodedKeySpec(publicKeyDer))
            val sig = Signature.getInstance("EdDSA")
            sig.initVerify(publicKey)
            sig.update(message)
            sig.verify(signature)
        }.getOrDefault(false)
    }

    return false
}
