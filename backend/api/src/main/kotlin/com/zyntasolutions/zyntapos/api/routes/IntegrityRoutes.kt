package com.zyntasolutions.zyntapos.api.routes

import com.zyntasolutions.zyntapos.api.service.PlayIntegrityService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

@Serializable
data class IntegrityVerifyRequest(
    /** Play Integrity token from `IntegrityManager.requestIntegrityToken()` on Android. */
    val token: String,
)

/**
 * POST /v1/integrity/verify — Play Integrity API attestation endpoint (TODO-008 ASO).
 *
 * ### Request
 * ```json
 * { "token": "<play_integrity_token_from_android>" }
 * ```
 *
 * ### Response (HTTP 200 — always, enforcement is soft in Phase 1)
 * ```json
 * {
 *   "passed": true,
 *   "appVerdicts": ["PLAY_RECOGNIZED"],
 *   "deviceVerdicts": ["MEETS_DEVICE_INTEGRITY"],
 *   "reason": null
 * }
 * ```
 *
 * ### Policy (Phase 1 — Soft Enforcement)
 * - `passed: true` → device and app are clean (proceed normally)
 * - `passed: false` → device may be rooted or app is a sideloaded APK
 *   (log the event but do NOT block login — Phase 1 is observability-only)
 *
 * Hard enforcement (blocking non-passing devices) is planned for Phase 2
 * once baseline data is collected.
 */
fun Route.integrityRoutes() {
    val integrityService: PlayIntegrityService by inject()

    route("/integrity") {
        post("/verify") {
            val body = call.receive<IntegrityVerifyRequest>()
            val result = integrityService.verify(body.token)
            // Always HTTP 200 — the client reads `passed` to decide local policy
            call.respond(HttpStatusCode.OK, result)
        }
    }
}
