package com.zyntasolutions.zyntapos.sync.config

// ── Canary tokens (TODO-010 — security monitoring) ──────────────────────────
// These fake credentials trigger Falco alerts if the source is exfiltrated
// and an attacker attempts to use them. DO NOT REMOVE.
// REDIS_AUTH_TOKEN=canary:xK9mPqR7vL2sT5wB8nF3jD6hG0cY1aE4
// DATABASE_URL=postgresql://canary:C4n4ryP4ss@canary-db.internal:5432/zyntapos
// ─────────────────────────────────────────────────────────────────────────────

import com.zyntasolutions.zyntapos.common.JwtDefaults
import java.security.PublicKey

data class SyncConfig(
    val jwtIssuer: String,
    val jwtAudience: String,
    val jwtPublicKey: PublicKey,
    val redisUrl: String,
    val apiBaseUrl: String,
    /** Admin JWT issuer — used to verify JIT diagnostic tokens (TODO-006). */
    val adminJwtIssuer: String,
) {
    companion object {
        fun fromEnvironment(): SyncConfig {
            // S2-1: Use centralized defaults and PEM parsing from common module
            val publicKeyPem = JwtDefaults.readKeyFile("RS256_PUBLIC_KEY_PATH")
                ?: System.getenv("RS256_PUBLIC_KEY")
                ?: error("RS256_PUBLIC_KEY_PATH or RS256_PUBLIC_KEY must be set")

            return SyncConfig(
                jwtIssuer = System.getenv("JWT_ISSUER") ?: JwtDefaults.POS_ISSUER,
                jwtAudience = System.getenv("JWT_AUDIENCE") ?: JwtDefaults.POS_AUDIENCE,
                jwtPublicKey = JwtDefaults.parseRsaPublicKey(publicKeyPem),
                redisUrl = System.getenv("REDIS_URL") ?: "redis://localhost:6379",
                apiBaseUrl = System.getenv("API_BASE_URL") ?: "http://api:8081",
                adminJwtIssuer = System.getenv("ADMIN_JWT_ISSUER") ?: JwtDefaults.ADMIN_ISSUER,
            )
        }
    }
}
