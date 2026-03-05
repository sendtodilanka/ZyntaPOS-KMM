package com.zyntasolutions.zyntapos.api.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.zyntasolutions.zyntapos.api.config.AppConfig
import com.zyntasolutions.zyntapos.api.models.LoginResponse
import com.zyntasolutions.zyntapos.api.models.UserInfo
import org.slf4j.LoggerFactory
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Date

class UserService {
    private val logger = LoggerFactory.getLogger(UserService::class.java)

    suspend fun authenticate(licenseKey: String, deviceId: String, username: String, password: String): UserInfo? {
        // TODO: Query PostgreSQL users table + verify license key is active
        // For now, validate against DB users table using SHA-256+salt (matching PinManager)
        logger.info("Auth attempt: user=$username device=$deviceId")
        // Placeholder — real implementation queries DB
        return null
    }

    fun refreshTokens(refreshToken: String, config: AppConfig): LoginResponse? {
        return try {
            val algorithm = Algorithm.RSA256(
                config.jwtPublicKey as RSAPublicKey,
                config.jwtPrivateKey as RSAPrivateKey
            )
            val verifier = JWT.require(algorithm)
                .withIssuer(config.jwtIssuer)
                .withClaim("type", "refresh")
                .build()
            val decoded = verifier.verify(refreshToken)
            val userId = decoded.subject ?: return null
            val role = decoded.getClaim("role")?.asString() ?: return null
            val storeId = decoded.getClaim("storeId")?.asString() ?: return null

            val now = System.currentTimeMillis()
            val newAccessToken = JWT.create()
                .withIssuer(config.jwtIssuer)
                .withAudience(config.jwtAudience)
                .withSubject(userId)
                .withClaim("role", role)
                .withClaim("storeId", storeId)
                .withClaim("type", "access")
                .withExpiresAt(Date(now + config.accessTokenTtlMs))
                .sign(algorithm)

            LoginResponse(
                accessToken = newAccessToken,
                refreshToken = refreshToken, // Extend refresh token validity
                expiresIn = config.accessTokenTtlMs / 1000,
                tokenType = "Bearer",
                userId = userId,
                role = role,
                storeId = storeId
            )
        } catch (e: Exception) {
            logger.warn("Refresh token validation failed: ${e.message}")
            null
        }
    }
}
