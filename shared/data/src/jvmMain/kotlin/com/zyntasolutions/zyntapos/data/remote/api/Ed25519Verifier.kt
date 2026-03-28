package com.zyntasolutions.zyntapos.data.remote.api

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * JVM/Desktop actual — Ed25519 signature verification.
 *
 * Java 15+ provides native Ed25519 support via `Signature.getInstance("Ed25519")`.
 * The JVM target for `:shared:data` is JVM 17, so no fallback is required.
 */
internal actual fun verifyEd25519Signature(
    message: ByteArray,
    signature: ByteArray,
    publicKeyDer: ByteArray,
): Boolean = runCatching {
    val publicKey = KeyFactory.getInstance("Ed25519")
        .generatePublic(X509EncodedKeySpec(publicKeyDer))
    val sig = Signature.getInstance("Ed25519")
    sig.initVerify(publicKey)
    sig.update(message)
    sig.verify(signature)
}.getOrDefault(false)
