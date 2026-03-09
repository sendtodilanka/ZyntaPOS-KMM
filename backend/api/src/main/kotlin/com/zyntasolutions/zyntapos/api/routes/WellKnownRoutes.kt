package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.config.AppConfig
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.util.Base64

@Serializable
private data class PublicKeyResponse(val public_key: String)

/**
 * RFC 5785 well-known endpoint for the RS256 public key.
 *
 * `GET /.well-known/public-key` returns the standard Base64-encoded DER
 * (SubjectPublicKeyInfo) of the RS256 public key currently used to sign JWTs.
 *
 * ## Usage by KMP clients (ADR-008)
 * After every successful login or token refresh, the client fetches this endpoint
 * and caches the key in [SecurePreferences] via `JwtManager.cachePublicKey()`.
 * The cached key is used by `JwtManager.verifyOfflineRole()` in preference to the
 * BuildConfig-bundled fallback, enabling key rotation without an app update.
 *
 * ## Security
 * The RSA public key is NOT a secret — it is intentionally public.
 * No authentication is required. Rate-limited at the standard API tier.
 */
fun Route.wellKnownRoutes() {
    val config: AppConfig by inject()

    route("/.well-known") {
        get("/public-key") {
            val derBase64 = Base64.getEncoder().encodeToString(config.jwtPublicKey.encoded)
            call.respond(HttpStatusCode.OK, PublicKeyResponse(public_key = derBase64))
        }
    }
}
