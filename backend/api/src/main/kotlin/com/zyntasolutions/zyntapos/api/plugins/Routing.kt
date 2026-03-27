package com.zyntasolutions.zyntapos.api.plugins

import com.zyntasolutions.zyntapos.api.routes.adminAlertsRoutes
import com.zyntasolutions.zyntapos.api.routes.adminExchangeRateRoutes
import com.zyntasolutions.zyntapos.api.routes.storeAccessRoutes
import com.zyntasolutions.zyntapos.api.routes.fulfillmentRoutes
import com.zyntasolutions.zyntapos.api.routes.orderRoutes
import com.zyntasolutions.zyntapos.api.routes.taxOverrideRoutes
import com.zyntasolutions.zyntapos.api.routes.adminDiagnosticRoutes
import com.zyntasolutions.zyntapos.api.routes.adminAuditRoutes
import com.zyntasolutions.zyntapos.api.routes.adminCustomersRoutes
import com.zyntasolutions.zyntapos.api.routes.adminInventoryRoutes
import com.zyntasolutions.zyntapos.api.routes.loyaltyRoutes
import com.zyntasolutions.zyntapos.api.routes.pricingRoutes
import com.zyntasolutions.zyntapos.api.routes.replenishmentRoutes
import com.zyntasolutions.zyntapos.api.routes.adminAuthRoutes
import com.zyntasolutions.zyntapos.api.routes.adminConfigRoutes
import com.zyntasolutions.zyntapos.api.routes.adminEmailRoutes
import com.zyntasolutions.zyntapos.api.routes.adminMasterProductRoutes
import com.zyntasolutions.zyntapos.api.routes.transferRoutes
import com.zyntasolutions.zyntapos.api.routes.adminEmailPreferencesRoutes
import com.zyntasolutions.zyntapos.api.routes.adminEmailTemplateRoutes
import com.zyntasolutions.zyntapos.api.routes.adminHealthRoutes
import com.zyntasolutions.zyntapos.api.routes.adminMetricsRoutes
import com.zyntasolutions.zyntapos.api.routes.adminStoresRoutes
import com.zyntasolutions.zyntapos.api.routes.adminSyncRoutes
import com.zyntasolutions.zyntapos.api.routes.adminTicketRoutes
import com.zyntasolutions.zyntapos.api.routes.customerTicketRoutes
import com.zyntasolutions.zyntapos.api.routes.authRoutes
import com.zyntasolutions.zyntapos.api.routes.diagnosticConsentRoutes
import com.zyntasolutions.zyntapos.api.routes.exportRoutes
import com.zyntasolutions.zyntapos.api.routes.healthRoutes
import com.zyntasolutions.zyntapos.api.routes.productRoutes
import com.zyntasolutions.zyntapos.api.routes.promotionsRoutes
import com.zyntasolutions.zyntapos.api.routes.syncRoutes
import com.zyntasolutions.zyntapos.api.routes.unsubscribeRoutes
import com.zyntasolutions.zyntapos.api.routes.inboundEmailRoutes
import com.zyntasolutions.zyntapos.api.routes.integrityRoutes
import com.zyntasolutions.zyntapos.api.routes.webhookRoutes
import com.zyntasolutions.zyntapos.api.routes.wellKnownRoutes
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    routing {
        // OpenAPI documentation — Swagger UI served at /docs
        swaggerUI(path = "docs", swaggerFile = "openapi/api-spec.yaml")

        // Public routes (no rate limit — health/ping are trivially cheap)
        healthRoutes()

        // Email unsubscribe — public, no auth required
        unsubscribeRoutes()

        // Customer ticket status check — public, token-based auth
        rateLimit(RateLimitName("api")) {
            customerTicketRoutes()
        }

        // Inbound email from CF Email Worker — HMAC-signed, NOT JWT auth
        // Rate-limited to prevent spam but NOT behind CSRF (Worker can't send CSRF tokens)
        rateLimit(RateLimitName("api")) {
            inboundEmailRoutes()
        }

        // Webhooks from third-party services (Resend bounce/complaint events)
        // No auth or CSRF — external services cannot provide these
        rateLimit(RateLimitName("api")) {
            webhookRoutes()
        }

        // RFC 5785 well-known — public key for offline JWT verification (ADR-008)
        rateLimit(RateLimitName("api")) {
            wellKnownRoutes()
        }

        // Admin panel auth routes — strict rate limit + CSRF protection + IP allowlist
        rateLimit(RateLimitName("auth")) {
            install(IpAllowlistPlugin)
            withCsrfProtection {
                adminAuthRoutes()
            }
        }

        // Admin panel data routes — standard API rate limit + CSRF protection + IP allowlist
        rateLimit(RateLimitName("api")) {
            install(IpAllowlistPlugin)
            withCsrfProtection {
                adminStoresRoutes()
                adminHealthRoutes()
                adminAuditRoutes()
                adminMetricsRoutes()
                adminAlertsRoutes()
                adminSyncRoutes()
                adminConfigRoutes()
                adminTicketRoutes()
                adminDiagnosticRoutes()
                adminEmailRoutes()
                adminEmailTemplateRoutes()
                adminEmailPreferencesRoutes()
                adminMasterProductRoutes()
                adminInventoryRoutes()
                adminCustomersRoutes()
                adminExchangeRateRoutes()
            }
        }

        route("/v1") {
            // Auth endpoints: strict rate limit (10 req/min) to prevent brute-force
            rateLimit(RateLimitName("auth")) {
                authRoutes()
            }

            // Protected endpoints: standard API rate limit (300 req/min)
            authenticate("jwt-rs256") {
                rateLimit(RateLimitName("api")) {
                    productRoutes()
                    promotionsRoutes()
                    exportRoutes()
                    diagnosticConsentRoutes()
                    integrityRoutes()   // Play Integrity attestation (TODO-008 ASO)
                    transferRoutes()
                    replenishmentRoutes()
                    pricingRoutes()
                    storeAccessRoutes()
                    loyaltyRoutes()
                    taxOverrideRoutes()
                    orderRoutes()
                    fulfillmentRoutes()
                }
                // Sync push is write-heavy — use sync tier (60 req/min)
                rateLimit(RateLimitName("sync")) {
                    syncRoutes()
                }
            }
        }
    }
}
