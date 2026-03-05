package com.zyntasolutions.zyntapos.api.config

import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

data class AppConfig(
    val jwtIssuer: String,
    val jwtAudience: String,
    val jwtPublicKey: PublicKey,
    val jwtPrivateKey: PrivateKey,
    val accessTokenTtlMs: Long,
    val refreshTokenTtlMs: Long
) {
    companion object {
        fun fromEnvironment(): AppConfig {
            val issuer = System.getenv("JWT_ISSUER") ?: "https://api.zyntapos.com"
            val audience = System.getenv("JWT_AUDIENCE") ?: "zyntapos-app"
            val publicKeyPem = readKeyFile("RS256_PUBLIC_KEY_PATH")
                ?: System.getenv("RS256_PUBLIC_KEY")
                ?: error("RS256_PUBLIC_KEY_PATH or RS256_PUBLIC_KEY must be set")
            val privateKeyPem = readKeyFile("RS256_PRIVATE_KEY_PATH")
                ?: System.getenv("RS256_PRIVATE_KEY")
                ?: error("RS256_PRIVATE_KEY_PATH or RS256_PRIVATE_KEY must be set")

            val keyFactory = KeyFactory.getInstance("RSA")
            val publicKey = keyFactory.generatePublic(
                X509EncodedKeySpec(Base64.getDecoder().decode(stripPemHeaders(publicKeyPem)))
            )
            val privateKey = keyFactory.generatePrivate(
                PKCS8EncodedKeySpec(Base64.getDecoder().decode(stripPemHeaders(privateKeyPem)))
            )

            return AppConfig(
                jwtIssuer = issuer,
                jwtAudience = audience,
                jwtPublicKey = publicKey,
                jwtPrivateKey = privateKey,
                accessTokenTtlMs = (System.getenv("ACCESS_TOKEN_TTL_MINUTES")?.toLongOrNull() ?: 60L) * 60_000L,
                refreshTokenTtlMs = (System.getenv("REFRESH_TOKEN_TTL_DAYS")?.toLongOrNull() ?: 30L) * 86_400_000L
            )
        }

        private fun stripPemHeaders(pem: String): String =
            pem.replace("-----BEGIN.*?-----".toRegex(), "")
               .replace("-----END.*?-----".toRegex(), "")
               .replace("\\s".toRegex(), "")

        private fun readKeyFile(envVar: String): String? {
            val path = System.getenv(envVar) ?: return null
            return try { java.io.File(path).readText() } catch (_: Exception) { null }
        }
    }
}
