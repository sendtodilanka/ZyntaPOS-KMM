package com.zyntasolutions.zyntapos.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response from `GET /.well-known/public-key`.
 *
 * @property publicKey Standard Base64-encoded DER (SubjectPublicKeyInfo) of the
 *                     RS256 public key used by the backend to sign JWTs.
 *                     Passed directly to [com.zyntasolutions.zyntapos.security.auth.JwtManager.cachePublicKey].
 */
@Serializable
data class PublicKeyResponseDto(
    @SerialName("public_key") val publicKey: String,
)
