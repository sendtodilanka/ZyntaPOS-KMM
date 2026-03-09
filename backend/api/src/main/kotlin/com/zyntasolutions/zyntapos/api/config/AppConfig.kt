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
    val refreshTokenTtlMs: Long,
    // Admin panel JWT (HS256 — separate from POS app RS256 tokens)
    val adminJwtSecret: String,
    val adminJwtIssuer: String,
    val adminAccessTokenTtlMs: Long,
    val adminRefreshTokenTtlDays: Long,
    // Google OAuth (optional — empty string disables Google SSO)
    val googleClientId: String,
    val googleClientSecret: String,
    val googleRedirectUri: String,
    // Restrict Google SSO to a specific email domain (e.g. "zyntapos.com"). Empty = allow any domain.
    val googleAllowedDomain: String,
    // Base URL of the admin panel — used for post-OAuth redirects
    val adminPanelUrl: String,
    // Redis URL for pub/sub (force-sync notifications to connected WS devices)
    val redisUrl: String,
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

            val adminSecret = readSecret("ADMIN_JWT_SECRET_FILE")
                ?: System.getenv("ADMIN_JWT_SECRET")
                ?: error("ADMIN_JWT_SECRET_FILE or ADMIN_JWT_SECRET must be set")

            return AppConfig(
                jwtIssuer = issuer,
                jwtAudience = audience,
                jwtPublicKey = publicKey,
                jwtPrivateKey = privateKey,
                accessTokenTtlMs = (System.getenv("ACCESS_TOKEN_TTL_MINUTES")?.toLongOrNull() ?: 60L) * 60_000L,
                refreshTokenTtlMs = (System.getenv("REFRESH_TOKEN_TTL_DAYS")?.toLongOrNull() ?: 30L) * 86_400_000L,
                adminJwtSecret = adminSecret,
                adminJwtIssuer = System.getenv("ADMIN_JWT_ISSUER") ?: "https://panel.zyntapos.com",
                adminAccessTokenTtlMs = (System.getenv("ADMIN_ACCESS_TOKEN_TTL_MINUTES")?.toLongOrNull() ?: 15L) * 60_000L,
                adminRefreshTokenTtlDays = System.getenv("ADMIN_REFRESH_TOKEN_TTL_DAYS")?.toLongOrNull() ?: 7L,
                googleClientId = System.getenv("GOOGLE_CLIENT_ID") ?: "",
                googleClientSecret = System.getenv("GOOGLE_CLIENT_SECRET") ?: "",
                googleRedirectUri = System.getenv("GOOGLE_REDIRECT_URI") ?: "https://api.zyntapos.com/admin/auth/google/callback",
                googleAllowedDomain = System.getenv("GOOGLE_ALLOWED_DOMAIN") ?: "",
                adminPanelUrl = System.getenv("ADMIN_PANEL_URL") ?: "https://panel.zyntapos.com",
                redisUrl = System.getenv("REDIS_URL") ?: "redis://localhost:6379",
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

        private fun readSecret(envVar: String): String? {
            val path = System.getenv(envVar) ?: return null
            return try { java.io.File(path).readText().trim() } catch (_: Exception) { null }
        }
    }
}
