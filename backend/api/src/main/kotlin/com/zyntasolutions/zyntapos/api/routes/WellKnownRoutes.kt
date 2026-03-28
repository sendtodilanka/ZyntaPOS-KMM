package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.config.AppConfig
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.util.Base64

@Serializable
private data class PublicKeyResponse(val public_key: String)

/**
 * RFC 5785 well-known endpoints.
 *
 * ### `GET /.well-known/public-key`
 * Returns the standard Base64-encoded DER (SubjectPublicKeyInfo) of the RS256 public
 * key currently used to sign JWTs.
 *
 * Usage by KMP clients (ADR-008): After every successful login the client caches the
 * key in SecurePreferences, enabling offline role verification without an app update.
 *
 * ### `GET /.well-known/tls-pins.json`
 * Returns the Ed25519-signed TLS pin list (ADR-011 — Signed Pin List).
 *
 * The JSON body was pre-generated offline by `scripts/generate-tls-signing-key.sh`
 * after the last Caddy certificate renewal. The app verifies the Ed25519 signature
 * using the public key hardcoded in `CertificatePinConstants.kt` before trusting the
 * pins. Returns **404** when no pin list has been configured (clients fall back to
 * stored or emergency backup pins).
 *
 * Both endpoints are public (no authentication) and rate-limited at the API tier.
 */
fun Route.wellKnownRoutes() {
    val config: AppConfig by inject()

    route("/.well-known") {

        // ── RS256 public key (ADR-008) ──────────────────────────────────
        get("/public-key") {
            val derBase64 = Base64.getEncoder().encodeToString(config.jwtPublicKey.encoded)
            call.respond(HttpStatusCode.OK, PublicKeyResponse(public_key = derBase64))
        }

        // ── Signed TLS pin list (ADR-011) ───────────────────────────────
        // Serves the pre-signed JSON blob verbatim. Pinning the response
        // Content-Type to application/json prevents content-sniffing attacks.
        get("/tls-pins.json") {
            val json = config.tlsPinsJson
            if (json != null) {
                call.respondText(json, ContentType.Application.Json, HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }
}
