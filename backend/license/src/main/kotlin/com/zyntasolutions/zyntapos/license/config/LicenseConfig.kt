package com.zyntasolutions.zyntapos.license.config

import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

data class LicenseConfig(
    val jwtIssuer: String,
    val jwtAudience: String,
    val jwtPublicKey: PublicKey
) {
    companion object {
        fun fromEnvironment(): LicenseConfig {
            val publicKeyPem = readKeyFile("RS256_PUBLIC_KEY_PATH")
                ?: System.getenv("RS256_PUBLIC_KEY")
                ?: error("RS256_PUBLIC_KEY_PATH or RS256_PUBLIC_KEY must be set")

            val stripped = publicKeyPem
                .replace("-----BEGIN.*?-----".toRegex(), "")
                .replace("-----END.*?-----".toRegex(), "")
                .replace("\\s".toRegex(), "")
            val publicKey = KeyFactory.getInstance("RSA").generatePublic(
                X509EncodedKeySpec(Base64.getDecoder().decode(stripped))
            )
            return LicenseConfig(
                jwtIssuer = System.getenv("JWT_ISSUER") ?: "https://api.zyntapos.com",
                jwtAudience = System.getenv("JWT_AUDIENCE") ?: "zyntapos-app",
                jwtPublicKey = publicKey
            )
        }

        private fun readKeyFile(envVar: String): String? {
            val path = System.getenv(envVar) ?: return null
            return try { java.io.File(path).readText() } catch (_: Exception) { null }
        }
    }
}
