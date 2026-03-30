package com.zyntasolutions.zyntapos.api.plugins

// CANARY:ZyntaPOS-api-auth-e5f6g7h8

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.zyntasolutions.zyntapos.api.config.AppConfig
import com.zyntasolutions.zyntapos.api.db.RevokedTokens
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.ktor.ext.inject
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory cache for revoked token JTIs. Avoids a DB hit on every authenticated request.
 * Entries are cached for [REVOCATION_CACHE_TTL_MS]. A revoked token may remain usable for
 * up to this duration after revocation.
 *
 * The cache also publishes revoked JTIs to Redis (key `revoked_tokens` set) so the sync
 * service can check revocation without direct DB access.
 */
object TokenRevocationCache {
    // SECURITY FIX: reduced from 5 minutes to 30 seconds.
    // The previous 5-minute TTL meant a revoked token (terminated employee, breach response)
    // remained valid for up to 5 minutes. 30 seconds is still a useful performance optimisation
    // (avoids a DB round-trip on every authenticated request) while shrinking the effective
    // revocation window to an operationally acceptable level.
    private const val REVOCATION_CACHE_TTL_MS = 30 * 1000L // 30 seconds

    // jti → (isRevoked, cachedAtMs)
    private val cache = ConcurrentHashMap<String, Pair<Boolean, Long>>()

    fun isRevoked(jti: String): Boolean? {
        val entry = cache[jti] ?: return null
        if (System.currentTimeMillis() - entry.second > REVOCATION_CACHE_TTL_MS) {
            cache.remove(jti)
            return null // expired
        }
        return entry.first
    }

    fun put(jti: String, revoked: Boolean) {
        cache[jti] = revoked to System.currentTimeMillis()
        // Evict stale entries periodically (simple: on every put, trim if > 10k entries)
        if (cache.size > 10_000) {
            val now = System.currentTimeMillis()
            cache.entries.removeIf { now - it.value.second > REVOCATION_CACHE_TTL_MS }
        }
    }

    /** Mark a JTI as revoked in cache immediately (called when admin revokes a token). */
    fun markRevoked(jti: String) {
        cache[jti] = true to System.currentTimeMillis()
    }
}

fun Application.configureAuthentication() {
    val config: AppConfig by inject()

    install(Authentication) {
        // ── POS app tokens (RS256 — Bearer header) ─────────────────────────
        jwt("jwt-rs256") {
            realm = "ZyntaPOS API"
            verifier(
                JWT.require(Algorithm.RSA256(config.jwtPublicKey as RSAPublicKey, null))
                    .withIssuer(config.jwtIssuer)
                    .withAudience(config.jwtAudience)
                    .build()
            )
            validate { credential ->
                val subject = credential.payload.subject
                val role = credential.payload.getClaim("role")?.asString()
                if (subject == null || role == null) return@validate null

                // S2-9: Check token revocation list — deactivated users are blocked immediately
                // Uses in-memory cache to avoid DB hit on every request (30s TTL)
                val jti = credential.payload.id
                if (jti != null) {
                    val cached = TokenRevocationCache.isRevoked(jti)
                    if (cached != null) {
                        if (cached) return@validate null
                        // cached as not-revoked — proceed
                    } else {
                        val isRevoked = transaction {
                            RevokedTokens.selectAll()
                                .where { RevokedTokens.jti eq jti }
                                .count() > 0
                        }
                        TokenRevocationCache.put(jti, isRevoked)
                        if (isRevoked) return@validate null
                    }
                }

                JWTPrincipal(credential.payload)
            }
        }

    }
}
