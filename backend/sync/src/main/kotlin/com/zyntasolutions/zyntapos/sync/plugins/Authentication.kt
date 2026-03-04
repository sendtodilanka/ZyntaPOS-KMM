package com.zyntasolutions.zyntapos.sync.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.zyntasolutions.zyntapos.sync.config.SyncConfig
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import org.koin.ktor.ext.inject
import java.security.interfaces.RSAPublicKey

fun Application.configureAuthentication() {
    val config: SyncConfig by inject()

    install(Authentication) {
        jwt("jwt-rs256") {
            realm = "ZyntaPOS Sync"
            verifier(
                JWT.require(Algorithm.RSA256(config.jwtPublicKey as RSAPublicKey, null))
                    .withIssuer(config.jwtIssuer)
                    .build()
            )
            validate { credential ->
                if (credential.payload.subject != null) JWTPrincipal(credential.payload) else null
            }
        }
    }
}
