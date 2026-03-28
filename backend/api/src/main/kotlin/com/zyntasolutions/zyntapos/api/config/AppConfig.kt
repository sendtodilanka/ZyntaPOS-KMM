package com.zyntasolutions.zyntapos.api.config

import com.zyntasolutions.zyntapos.common.JwtDefaults
import java.net.URI
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

data class AppConfig(
    val jwtIssuer: String,
    val jwtAudience: String,
    val jwtPublicKey: PublicKey,
    val jwtPrivateKey: PrivateKey,
    val accessTokenTtlMs: Long,
    val refreshTokenTtlMs: Long,
    // Admin panel JWT (RS256 — uses same RSA keypair as POS tokens)
    val adminJwtPublicKey: PublicKey,
    val adminJwtPrivateKey: PrivateKey,
    val adminJwtIssuer: String,
    val adminAccessTokenTtlMs: Long,
    val adminRefreshTokenTtlDays: Long,
    // Base URL of the admin panel — used for post-auth redirects (password reset links)
    val adminPanelUrl: String,
    // Redis URL for pub/sub (force-sync notifications to connected WS devices)
    val redisUrl: String,
    // Email system — Resend transactional email (TODO-008a)
    val resendApiKey: String,
    val emailFromAddress: String,
    val emailFromName: String,
    // Google Play Integrity API — device/app attestation (TODO-008 ASO)
    val playIntegrityPackageName: String,
    val playIntegrityApiKey: String,
    // Inbound email HMAC secret — CF Worker → /internal/email/inbound (TODO-008a)
    val inboundEmailHmacSecret: String,
    // Chatwoot — customer support platform integration (TODO-008a)
    val chatwootApiUrl: String,
    val chatwootApiToken: String,
    val chatwootAccountId: String,
    val chatwootInboxId: String,
) {
    companion object {
        fun fromEnvironment(): AppConfig {
            // S2-1: Use centralized defaults from common module
            val issuer = System.getenv("JWT_ISSUER") ?: JwtDefaults.POS_ISSUER
            val audience = System.getenv("JWT_AUDIENCE") ?: JwtDefaults.POS_AUDIENCE
            val publicKeyPem = JwtDefaults.readKeyFile("RS256_PUBLIC_KEY_PATH")
                ?: System.getenv("RS256_PUBLIC_KEY")
                ?: error("RS256_PUBLIC_KEY_PATH or RS256_PUBLIC_KEY must be set")
            val privateKeyPem = JwtDefaults.readKeyFile("RS256_PRIVATE_KEY_PATH")
                ?: System.getenv("RS256_PRIVATE_KEY")
                ?: error("RS256_PRIVATE_KEY_PATH or RS256_PRIVATE_KEY must be set")

            val publicKey = JwtDefaults.parseRsaPublicKey(publicKeyPem)
            val keyFactory = KeyFactory.getInstance("RSA")
            val decodedKeyBytes = Base64.getDecoder().decode(JwtDefaults.stripPemHeaders(privateKeyPem))
            val privateKey = try {
                keyFactory.generatePrivate(PKCS8EncodedKeySpec(decodedKeyBytes))
            } finally {
                // Zero intermediate key material to reduce exposure window
                java.util.Arrays.fill(decodedKeyBytes, 0)
            }

            return AppConfig(
                jwtIssuer = issuer,
                jwtAudience = audience,
                jwtPublicKey = publicKey,
                jwtPrivateKey = privateKey,
                accessTokenTtlMs = (System.getenv("ACCESS_TOKEN_TTL_MINUTES")?.toLongOrNull()
                    ?: JwtDefaults.POS_ACCESS_TOKEN_TTL_MINUTES) * 60_000L,
                refreshTokenTtlMs = (System.getenv("REFRESH_TOKEN_TTL_DAYS")?.toLongOrNull()
                    ?: JwtDefaults.POS_REFRESH_TOKEN_TTL_DAYS) * 86_400_000L,
                // Admin panel reuses same RSA keypair as POS (HS256→RS256 migration, A7)
                adminJwtPublicKey = publicKey,
                adminJwtPrivateKey = privateKey,
                adminJwtIssuer = System.getenv("ADMIN_JWT_ISSUER") ?: JwtDefaults.ADMIN_ISSUER,
                adminAccessTokenTtlMs = (System.getenv("ADMIN_ACCESS_TOKEN_TTL_MINUTES")?.toLongOrNull()
                    ?: JwtDefaults.ADMIN_ACCESS_TOKEN_TTL_MINUTES) * 60_000L,
                adminRefreshTokenTtlDays = System.getenv("ADMIN_REFRESH_TOKEN_TTL_DAYS")?.toLongOrNull()
                    ?: JwtDefaults.ADMIN_REFRESH_TOKEN_TTL_DAYS,
                // ADMIN_PANEL_URL default is intentionally the production URL — password-reset
                // links embed this host. Set ADMIN_PANEL_URL explicitly in staging/local .env
                // to avoid reset emails pointing to production from non-production environments.
                adminPanelUrl = validateUrl(
                    System.getenv("ADMIN_PANEL_URL") ?: "https://panel.zyntapos.com",
                    "ADMIN_PANEL_URL"
                ),
                redisUrl = System.getenv("REDIS_URL") ?: "redis://localhost:6379",
                resendApiKey = System.getenv("RESEND_API_KEY") ?: "",
                emailFromAddress = System.getenv("EMAIL_FROM_ADDRESS") ?: "noreply@zyntapos.com",
                emailFromName = System.getenv("EMAIL_FROM_NAME") ?: "ZyntaPOS",
                playIntegrityPackageName = System.getenv("PLAY_INTEGRITY_PACKAGE_NAME")
                    ?: "com.zyntasolutions.zyntapos",
                playIntegrityApiKey = System.getenv("PLAY_INTEGRITY_API_KEY") ?: "",
                inboundEmailHmacSecret = System.getenv("INBOUND_EMAIL_HMAC_SECRET") ?: "",
                chatwootApiUrl = System.getenv("CHATWOOT_API_URL") ?: "http://chatwoot:3000",
                chatwootApiToken = System.getenv("CHATWOOT_API_TOKEN") ?: "",
                chatwootAccountId = System.getenv("CHATWOOT_ACCOUNT_ID") ?: "",
                chatwootInboxId = System.getenv("CHATWOOT_INBOX_ID") ?: "",
            )
        }

        /** S2-11: Validates a URL at startup — prevents misconfigured env vars from leaking tokens. */
        private fun validateUrl(url: String, envName: String): String {
            val parsed = URI.create(url)
            require(parsed.scheme in listOf("http", "https")) {
                "$envName must use http or https scheme, got: ${parsed.scheme}"
            }
            require(parsed.host != null && parsed.host.isNotBlank()) {
                "$envName must have a valid host, got: $url"
            }
            return url
        }
    }
}
