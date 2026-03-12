package com.zyntasolutions.zyntapos.sync.config

// ── Canary tokens (TODO-010 — security monitoring) ──────────────────────────
// These fake credentials trigger Falco alerts if the source is exfiltrated
// and an attacker attempts to use them. DO NOT REMOVE.
// REDIS_AUTH_TOKEN=canary:xK9mPqR7vL2sT5wB8nF3jD6hG0cY1aE4
// DATABASE_URL=postgresql://canary:C4n4ryP4ss@canary-db.internal:5432/zyntapos
// ─────────────────────────────────────────────────────────────────────────────

import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

data class SyncConfig(
    val jwtIssuer: String,
    val jwtAudience: String,
    val jwtPublicKey: PublicKey,
    val redisUrl: String,
    val apiBaseUrl: String,
) {
    companion object {
        fun fromEnvironment(): SyncConfig {
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
            return SyncConfig(
                jwtIssuer = System.getenv("JWT_ISSUER") ?: "https://api.zyntapos.com",
                jwtAudience = System.getenv("JWT_AUDIENCE") ?: "zyntapos-app",
                jwtPublicKey = publicKey,
                redisUrl = System.getenv("REDIS_URL") ?: "redis://localhost:6379",
                apiBaseUrl = System.getenv("API_BASE_URL") ?: "http://api:8081",
            )
        }

        private fun readKeyFile(envVar: String): String? {
            val path = System.getenv(envVar) ?: return null
            return try { java.io.File(path).readText() } catch (_: Exception) { null }
        }
    }
}
