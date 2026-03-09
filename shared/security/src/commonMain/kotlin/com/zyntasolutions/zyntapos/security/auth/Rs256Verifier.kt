package com.zyntasolutions.zyntapos.security.auth

/**
 * Platform-specific RSA-SHA256 signature verification (expect/actual).
 *
 * Verifies a JWT RS256 signature: checks that [signature] is a valid
 * RSASSA-PKCS1-v1_5 / SHA-256 signature over [signedData] using the RSA
 * public key encoded as [publicKeyDer] (SubjectPublicKeyInfo DER bytes).
 *
 * Both Android and Desktop actuals use `java.security.Signature` with the
 * `SHA256withRSA` algorithm and `java.security.KeyFactory` with the `RSA` provider.
 *
 * @param signedData   The bytes that were signed — for JWTs this is the UTF-8 encoding
 *                     of `"<base64url-header>.<base64url-payload>"`.
 * @param signature    The raw (decoded) signature bytes from the JWT's third segment.
 * @param publicKeyDer The RSA public key in SubjectPublicKeyInfo DER encoding.
 * @return `true` if the signature is cryptographically valid; `false` otherwise.
 */
expect fun verifyRs256Signature(
    signedData: ByteArray,
    signature: ByteArray,
    publicKeyDer: ByteArray,
): Boolean
