package com.zyntasolutions.zyntapos.security.auth

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

actual fun verifyRs256Signature(
    signedData: ByteArray,
    signature: ByteArray,
    publicKeyDer: ByteArray,
): Boolean = runCatching {
    val keyFactory = KeyFactory.getInstance("RSA")
    val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyDer))
    val sig = Signature.getInstance("SHA256withRSA")
    sig.initVerify(publicKey)
    sig.update(signedData)
    sig.verify(signature)
}.getOrDefault(false)
