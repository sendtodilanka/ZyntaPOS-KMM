package com.zyntasolutions.zyntapos.license.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.zyntasolutions.zyntapos.common.JwtDefaults
import org.slf4j.LoggerFactory
import java.security.PublicKey
import java.security.interfaces.RSAPublicKey
import java.util.UUID

/**
 * Validates admin panel RS256 JWTs issued by the API service.
 * Uses the same RSA public key as POS token validation — no shared secret needed.
 * Migrated from HS256 to RS256 (A7) for consistent asymmetric auth across all services.
 */
class AdminJwtValidator(private val publicKey: PublicKey, private val issuer: String) {

    private val logger = LoggerFactory.getLogger(AdminJwtValidator::class.java)

    /** Returns the admin user UUID if the token is valid, null otherwise. */
    fun verify(token: String): UUID? = try {
        val algorithm = Algorithm.RSA256(publicKey as RSAPublicKey, null)
        val decoded = JWT.require(algorithm)
            .withIssuer(issuer)
            .withClaim("type", "admin_access")
            .build()
            .verify(token)
        UUID.fromString(decoded.subject)
    } catch (e: Exception) {
        logger.debug("Admin JWT verification failed: ${e.message}")
        null
    }

    /**
     * Returns the admin user UUID and role if the token is valid, null otherwise.
     * Role claim is optional; falls back to empty string when absent.
     */
    fun verifyWithRole(token: String): Pair<UUID, String>? = try {
        val algorithm = Algorithm.RSA256(publicKey as RSAPublicKey, null)
        val decoded = JWT.require(algorithm)
            .withIssuer(issuer)
            .withClaim("type", "admin_access")
            .build()
            .verify(token)
        val adminId = UUID.fromString(decoded.subject)
        val role = decoded.getClaim("role").asString() ?: ""
        Pair(adminId, role)
    } catch (e: Exception) {
        logger.debug("Admin JWT verification failed: ${e.message}")
        null
    }

    companion object {
        fun fromEnvironment(): AdminJwtValidator {
            val publicKeyPem = JwtDefaults.readKeyFile("RS256_PUBLIC_KEY_PATH")
                ?: System.getenv("RS256_PUBLIC_KEY")
                ?: error("RS256_PUBLIC_KEY_PATH or RS256_PUBLIC_KEY must be set")
            val publicKey = JwtDefaults.parseRsaPublicKey(publicKeyPem)
            val issuer = System.getenv("ADMIN_JWT_ISSUER") ?: JwtDefaults.ADMIN_ISSUER
            return AdminJwtValidator(publicKey, issuer)
        }
    }
}
