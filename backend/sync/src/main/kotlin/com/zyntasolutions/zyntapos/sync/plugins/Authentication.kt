package com.zyntasolutions.zyntapos.sync.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.zyntasolutions.zyntapos.sync.config.SyncConfig
import io.lettuce.core.api.StatefulRedisConnection
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory revocation cache for the sync service.
 * Checks Redis set `revoked_jtis` (populated by API service on token revocation).
 * Falls back to in-memory cache if Redis is unavailable (graceful degradation).
 */
object SyncTokenRevocationCache {
    private const val CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes
    private const val REDIS_KEY = "revoked_jtis"
    private val logger = LoggerFactory.getLogger(SyncTokenRevocationCache::class.java)

    // jti → (isRevoked, cachedAtMs)
    private val cache = ConcurrentHashMap<String, Pair<Boolean, Long>>()

    fun isRevoked(jti: String, redis: StatefulRedisConnection<String, String>?): Boolean {
        // Check in-memory cache first
        val entry = cache[jti]
        if (entry != null && System.currentTimeMillis() - entry.second <= CACHE_TTL_MS) {
            return entry.first
        }

        // Check Redis set
        if (redis != null) {
            try {
                val revoked = redis.sync().sismember(REDIS_KEY, jti)
                cache[jti] = revoked to System.currentTimeMillis()
                return revoked
            } catch (e: Exception) {
                logger.warn("Redis revocation check failed for jti=$jti: ${e.message}")
            }
        }

        // Redis unavailable — if no cache entry, assume not revoked (graceful degradation)
        return false
    }

    /** Evict stale entries. Called periodically or on size threshold. */
    fun evictStale() {
        val now = System.currentTimeMillis()
        cache.entries.removeIf { now - it.value.second > CACHE_TTL_MS }
    }
}

fun Application.configureAuthentication() {
    val config: SyncConfig by inject()
    val redis: StatefulRedisConnection<String, String>? by inject()

    install(Authentication) {
        jwt("jwt-rs256") {
            realm = "ZyntaPOS Sync"
            verifier(
                JWT.require(Algorithm.RSA256(config.jwtPublicKey as RSAPublicKey, null))
                    .withIssuer(config.jwtIssuer)
                    .withAudience(config.jwtAudience)
                    .build()
            )
            validate { credential ->
                val subject = credential.payload.subject
                val storeId = credential.payload.getClaim("storeId")?.asString()
                if (subject == null || storeId == null) return@validate null

                // Check token revocation via Redis-backed cache
                val jti = credential.payload.id
                if (jti != null && SyncTokenRevocationCache.isRevoked(jti, redis)) {
                    return@validate null
                }

                JWTPrincipal(credential.payload)
            }
        }
    }
}
