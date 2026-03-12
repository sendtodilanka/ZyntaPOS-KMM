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
                val jti = credential.payload.id
                if (jti != null) {
                    val isRevoked = transaction {
                        RevokedTokens.selectAll()
                            .where { RevokedTokens.jti eq jti }
                            .count() > 0
                    }
                    if (isRevoked) return@validate null
                }

                JWTPrincipal(credential.payload)
            }
        }

    }
}
