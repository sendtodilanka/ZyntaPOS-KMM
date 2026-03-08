package com.zyntasolutions.zyntapos.license.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Validates admin panel HS256 JWTs issued by the API service.
 * The license service shares the same ADMIN_JWT_SECRET so it can protect
 * admin-only license management endpoints without duplicating the admin user DB.
 */
class AdminJwtValidator(private val secret: String, private val issuer: String) {

    private val logger = LoggerFactory.getLogger(AdminJwtValidator::class.java)

    /** Returns the admin user UUID if the token is valid, null otherwise. */
    fun verify(token: String): UUID? = try {
        val algorithm = Algorithm.HMAC256(secret)
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

    companion object {
        fun fromEnvironment(): AdminJwtValidator {
            val secret = readSecret("ADMIN_JWT_SECRET_FILE")
                ?: System.getenv("ADMIN_JWT_SECRET")
                ?: error("ADMIN_JWT_SECRET_FILE or ADMIN_JWT_SECRET must be set")
            val issuer = System.getenv("ADMIN_JWT_ISSUER") ?: "https://panel.zyntapos.com"
            return AdminJwtValidator(secret, issuer)
        }

        private fun readSecret(envVar: String): String? {
            val path = System.getenv(envVar) ?: return null
            return try { java.io.File(path).readText().trim() } catch (_: Exception) { null }
        }
    }
}
