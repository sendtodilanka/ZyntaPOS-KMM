package com.zyntasolutions.zyntapos.api.plugins

// CANARY:ZyntaPOS-api-auth-e5f6g7h8

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.zyntasolutions.zyntapos.api.config.AppConfig
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import org.koin.ktor.ext.inject
import java.security.interfaces.RSAPublicKey

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
                if (subject != null && role != null) JWTPrincipal(credential.payload) else null
            }
        }

        // ── Admin panel tokens (HS256 — httpOnly cookie) ───────────────────
        // Admin routes read the access token from the cookie via
        // AdminAuthService.verifyAccessToken() because Ktor's jwt() plugin
        // only reads Bearer headers. This named config exists for completeness.
        jwt("admin-jwt-hs256") {
            realm = "ZyntaPOS Admin Panel"
            verifier(
                JWT.require(Algorithm.HMAC256(config.adminJwtSecret))
                    .withIssuer(config.adminJwtIssuer)
                    .withClaim("type", "admin_access")
                    .build()
            )
            validate { credential ->
                val subject = credential.payload.subject
                val role = credential.payload.getClaim("role")?.asString()
                if (subject != null && role != null) JWTPrincipal(credential.payload) else null
            }
        }
    }
}
