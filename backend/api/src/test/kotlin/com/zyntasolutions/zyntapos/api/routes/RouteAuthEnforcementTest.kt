package com.zyntasolutions.zyntapos.api.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.zyntasolutions.zyntapos.api.auth.AdminRole
import com.zyntasolutions.zyntapos.api.config.AppConfig
import com.zyntasolutions.zyntapos.api.plugins.TokenRevocationCache
import com.zyntasolutions.zyntapos.api.plugins.configureAuthentication
import com.zyntasolutions.zyntapos.api.plugins.configureStatusPages
import com.zyntasolutions.zyntapos.api.service.AdminAuthResult
import com.zyntasolutions.zyntapos.api.service.AdminAuthService
import com.zyntasolutions.zyntapos.api.service.AdminAuditService
import com.zyntasolutions.zyntapos.api.service.AdminUserRow
import com.zyntasolutions.zyntapos.api.service.EmailService
import com.zyntasolutions.zyntapos.api.service.LicenseValidationClient
import com.zyntasolutions.zyntapos.api.service.MfaService
import com.zyntasolutions.zyntapos.api.service.ProductService
import com.zyntasolutions.zyntapos.api.service.UserService
import com.zyntasolutions.zyntapos.api.sync.DeltaEngine
import com.zyntasolutions.zyntapos.api.sync.SyncProcessor
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Date
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * C10: Route-level auth enforcement tests.
 *
 * Verifies that:
 *  1. Unauthenticated access to protected endpoints returns 401
 *  2. Expired JWTs are rejected with 401
 *  3. Wrongly-signed JWTs are rejected with 401
 *  4. POS JWT on admin routes returns 401 (admin uses cookie-based auth, not Bearer)
 *  5. Admin endpoints require valid admin cookie auth
 *  6. Protected POS endpoints require valid RS256 Bearer token
 *
 * Uses a minimal Ktor testApplication with only the auth + routing plugins installed,
 * with MockK-based service stubs. No database or Redis required.
 */
class RouteAuthEnforcementTest {

    companion object {
        private const val TEST_ISSUER = "https://api.test.local"
        private const val TEST_AUDIENCE = "test"

        private fun generateTestRsaKeyPair(): Pair<java.security.PublicKey, java.security.PrivateKey> {
            val kpg = java.security.KeyPairGenerator.getInstance("RSA")
            kpg.initialize(2048)
            val kp = kpg.generateKeyPair()
            return kp.public to kp.private
        }

        private val testKeyPair = generateTestRsaKeyPair()
        private val wrongKeyPair = generateTestRsaKeyPair()

        private fun testConfig(): AppConfig {
            val (pub, priv) = testKeyPair
            return AppConfig(
                jwtIssuer = TEST_ISSUER,
                jwtAudience = TEST_AUDIENCE,
                jwtPublicKey = pub,
                jwtPrivateKey = priv,
                accessTokenTtlMs = 3_600_000L,
                refreshTokenTtlMs = 86_400_000L,
                adminJwtPublicKey = pub,
                adminJwtPrivateKey = priv,
                adminJwtIssuer = "https://admin.test.local",
                adminAccessTokenTtlMs = 900_000L,
                adminRefreshTokenTtlDays = 7L,
                adminPanelUrl = "https://panel.test.local",
                redisUrl = "redis://localhost:6379",
                resendApiKey = "",
                emailFromAddress = "test@test.local",
                emailFromName = "Test",
                playIntegrityPackageName = "",
                playIntegrityApiKey = "",
                inboundEmailHmacSecret = "",
                chatwootApiUrl = "",
                chatwootApiToken = "",
                chatwootAccountId = "",
                chatwootInboxId = "",
            )
        }

        /**
         * Creates a valid POS RS256 JWT with given claims.
         * If [cacheAsNotRevoked] is true, pre-populates [TokenRevocationCache] so
         * the Authentication plugin's validate block never hits the DB.
         */
        private fun createPosJwt(
            subject: String = "user-test-001",
            role: String = "CASHIER",
            storeId: String = "store-001",
            expiresAtMs: Long = System.currentTimeMillis() + 3_600_000L,
            issuer: String = TEST_ISSUER,
            audience: String = TEST_AUDIENCE,
            keyPair: Pair<java.security.PublicKey, java.security.PrivateKey> = testKeyPair,
            cacheAsNotRevoked: Boolean = true,
        ): String {
            val jti = UUID.randomUUID().toString()
            val algorithm = Algorithm.RSA256(
                keyPair.first as RSAPublicKey,
                keyPair.second as RSAPrivateKey,
            )
            val token = JWT.create()
                .withIssuer(issuer)
                .withAudience(audience)
                .withSubject(subject)
                .withJWTId(jti)
                .withClaim("role", role)
                .withClaim("storeId", storeId)
                .withClaim("type", "access")
                .withIssuedAt(Date())
                .withExpiresAt(Date(expiresAtMs))
                .sign(algorithm)
            if (cacheAsNotRevoked) {
                TokenRevocationCache.put(jti, false)
            }
            return token
        }

        /** Creates a valid admin RS256 JWT (admin_access type). */
        private fun createAdminJwt(
            subject: String = UUID.randomUUID().toString(),
            role: String = "ADMIN",
            email: String = "admin@test.local",
            expiresAtMs: Long = System.currentTimeMillis() + 900_000L,
        ): String {
            val algorithm = Algorithm.RSA256(
                testKeyPair.first as RSAPublicKey,
                testKeyPair.second as RSAPrivateKey,
            )
            return JWT.create()
                .withIssuer("https://admin.test.local")
                .withSubject(subject)
                .withClaim("email", email)
                .withClaim("role", role)
                .withClaim("type", "admin_access")
                .withIssuedAt(Date())
                .withExpiresAt(Date(expiresAtMs))
                .sign(algorithm)
        }

        private fun testAdminUser(
            id: UUID = UUID.randomUUID(),
            role: AdminRole = AdminRole.ADMIN,
        ) = AdminUserRow(
            id = id,
            email = "admin@test.local",
            name = "Test Admin",
            role = role,
            passwordHash = null,
            mfaEnabled = false,
            isActive = true,
            lastLoginAt = null,
            createdAt = System.currentTimeMillis(),
        )
    }

    // ── Helper: create minimal test app with auth + POS routes ──────────────

    /**
     * Sets up a Ktor test application with:
     *  - ContentNegotiation (JSON)
     *  - JWT Authentication (jwt-rs256 for POS routes)
     *  - StatusPages (error handling)
     *  - Koin DI with mocked services
     *  - POS routes under /v1 with JWT authentication
     *  - Admin auth routes (cookie-based)
     */
    private fun testAppWithRoutes(
        productService: ProductService = mockk(relaxed = true),
        syncProcessor: SyncProcessor = mockk(relaxed = true),
        deltaEngine: DeltaEngine = mockk(relaxed = true),
        adminAuthService: AdminAuthService = mockk(relaxed = true),
        userService: UserService = mockk(relaxed = true),
        licenseClient: LicenseValidationClient = mockk(relaxed = true),
        block: suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        application {
            install(Koin) {
                modules(module {
                    single { testConfig() }
                    single { productService }
                    single { syncProcessor }
                    single { deltaEngine }
                    single { adminAuthService }
                    single { userService }
                    single { licenseClient }
                    single { mockk<AdminAuditService>(relaxed = true) }
                    single { mockk<MfaService>(relaxed = true) }
                    single { mockk<EmailService>(relaxed = true) }
                })
            }

            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = false
                    prettyPrint = false
                    encodeDefaults = true
                })
            }

            configureAuthentication()
            configureStatusPages()

            routing {
                // Admin auth routes (cookie-based, no CSRF for test simplicity)
                adminAuthRoutes()

                // POS routes under /v1 with JWT auth
                route("/v1") {
                    authRoutes()

                    authenticate("jwt-rs256") {
                        productRoutes()
                        syncRoutes()
                    }
                }
            }
        }

        block()
    }

    // ════════════════════════════════════════════════════════════════════════
    // 1. Unauthenticated access to protected POS endpoints → 401
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `GET v1 products without auth returns 401`() = testAppWithRoutes {
        val response = client.get("/v1/products")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `POST v1 sync push without auth returns 401`() = testAppWithRoutes {
        val response = client.post("/v1/sync/push") {
            contentType(ContentType.Application.Json)
            setBody("""{"device_id":"d1","operations":[]}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET v1 sync pull without auth returns 401`() = testAppWithRoutes {
        val response = client.get("/v1/sync/pull")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 2. Expired JWT → 401
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `GET v1 products with expired JWT returns 401`() = testAppWithRoutes {
        val expiredToken = createPosJwt(
            expiresAtMs = System.currentTimeMillis() - 60_000L  // expired 1 min ago
        )

        val response = client.get("/v1/products") {
            header(HttpHeaders.Authorization, "Bearer $expiredToken")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `POST v1 sync push with expired JWT returns 401`() = testAppWithRoutes {
        val expiredToken = createPosJwt(
            expiresAtMs = System.currentTimeMillis() - 60_000L
        )

        val response = client.post("/v1/sync/push") {
            header(HttpHeaders.Authorization, "Bearer $expiredToken")
            contentType(ContentType.Application.Json)
            setBody("""{"device_id":"d1","operations":[]}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 3. JWT signed with wrong key → 401
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `GET v1 products with wrong-key JWT returns 401`() = testAppWithRoutes {
        val wrongKeyToken = createPosJwt(keyPair = wrongKeyPair)

        val response = client.get("/v1/products") {
            header(HttpHeaders.Authorization, "Bearer $wrongKeyToken")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `POST v1 sync push with wrong-key JWT returns 401`() = testAppWithRoutes {
        val wrongKeyToken = createPosJwt(keyPair = wrongKeyPair)

        val response = client.post("/v1/sync/push") {
            header(HttpHeaders.Authorization, "Bearer $wrongKeyToken")
            contentType(ContentType.Application.Json)
            setBody("""{"device_id":"d1","operations":[]}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 4. Tampered / malformed JWT → 401
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `GET v1 products with tampered JWT returns 401`() = testAppWithRoutes {
        val response = client.get("/v1/products") {
            header(HttpHeaders.Authorization, "Bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJoYWNrZXIifQ.fakesig")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET v1 products with random string as Bearer returns 401`() = testAppWithRoutes {
        val response = client.get("/v1/products") {
            header(HttpHeaders.Authorization, "Bearer not-a-jwt-at-all")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET v1 products with empty Bearer returns 401`() = testAppWithRoutes {
        val response = client.get("/v1/products") {
            header(HttpHeaders.Authorization, "Bearer ")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 5. JWT with wrong issuer or audience → 401
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `GET v1 products with wrong issuer JWT returns 401`() = testAppWithRoutes {
        val wrongIssuerToken = createPosJwt(issuer = "https://evil.example.com")

        val response = client.get("/v1/products") {
            header(HttpHeaders.Authorization, "Bearer $wrongIssuerToken")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET v1 products with wrong audience JWT returns 401`() = testAppWithRoutes {
        val wrongAudienceToken = createPosJwt(audience = "wrong-audience")

        val response = client.get("/v1/products") {
            header(HttpHeaders.Authorization, "Bearer $wrongAudienceToken")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 6. JWT missing required claims (role or subject) → 401
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `GET v1 products with JWT missing role claim returns 401`() = testAppWithRoutes {
        val algorithm = Algorithm.RSA256(
            testKeyPair.first as RSAPublicKey,
            testKeyPair.second as RSAPrivateKey,
        )
        val noRoleToken = JWT.create()
            .withIssuer(TEST_ISSUER)
            .withAudience(TEST_AUDIENCE)
            .withSubject("user-test")
            .withJWTId(UUID.randomUUID().toString())
            // No role claim
            .withClaim("storeId", "store-001")
            .withClaim("type", "access")
            .withIssuedAt(Date())
            .withExpiresAt(Date(System.currentTimeMillis() + 3_600_000L))
            .sign(algorithm)

        val response = client.get("/v1/products") {
            header(HttpHeaders.Authorization, "Bearer $noRoleToken")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET v1 products with JWT missing subject returns 401`() = testAppWithRoutes {
        val algorithm = Algorithm.RSA256(
            testKeyPair.first as RSAPublicKey,
            testKeyPair.second as RSAPrivateKey,
        )
        val noSubjectToken = JWT.create()
            .withIssuer(TEST_ISSUER)
            .withAudience(TEST_AUDIENCE)
            // No subject
            .withJWTId(UUID.randomUUID().toString())
            .withClaim("role", "CASHIER")
            .withClaim("storeId", "store-001")
            .withClaim("type", "access")
            .withIssuedAt(Date())
            .withExpiresAt(Date(System.currentTimeMillis() + 3_600_000L))
            .sign(algorithm)

        val response = client.get("/v1/products") {
            header(HttpHeaders.Authorization, "Bearer $noSubjectToken")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 7. HS256 token (wrong algorithm) → 401
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `GET v1 products with HS256 JWT returns 401`() = testAppWithRoutes {
        val hs256Token = JWT.create()
            .withIssuer(TEST_ISSUER)
            .withAudience(TEST_AUDIENCE)
            .withSubject("user-test")
            .withClaim("role", "ADMIN")
            .withClaim("storeId", "store-001")
            .withClaim("type", "access")
            .withIssuedAt(Date())
            .withExpiresAt(Date(System.currentTimeMillis() + 3_600_000L))
            .sign(Algorithm.HMAC256("symmetric-secret"))

        val response = client.get("/v1/products") {
            header(HttpHeaders.Authorization, "Bearer $hs256Token")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status,
            "HS256 tokens must be rejected on RS256-only endpoints")
    }

    // ════════════════════════════════════════════════════════════════════════
    // 8. Admin endpoints without cookie auth → 401
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `GET admin users without cookie returns 401`() {
        val adminAuthService = mockk<AdminAuthService>(relaxed = true)
        // verifyAccessToken returns null for missing cookie
        coEvery { adminAuthService.verifyAccessToken(any()) } returns null

        testAppWithRoutes(adminAuthService = adminAuthService) {
            val response = client.get("/admin/users")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    @Test
    fun `GET admin auth me without cookie returns 401`() {
        val adminAuthService = mockk<AdminAuthService>(relaxed = true)
        coEvery { adminAuthService.verifyAccessToken(any()) } returns null

        testAppWithRoutes(adminAuthService = adminAuthService) {
            val response = client.get("/admin/auth/me")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 9. Admin endpoints with POS JWT in Bearer header → 401
    //    (Admin routes use cookie auth, not Bearer)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `GET admin users with POS Bearer JWT returns 401`() {
        val adminAuthService = mockk<AdminAuthService>(relaxed = true)
        coEvery { adminAuthService.verifyAccessToken(any()) } returns null

        testAppWithRoutes(adminAuthService = adminAuthService) {
            val posToken = createPosJwt()
            val response = client.get("/admin/users") {
                header(HttpHeaders.Authorization, "Bearer $posToken")
            }
            // Admin routes use cookie-based auth, not Bearer — POS JWT in header is ignored
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 10. Admin endpoints with valid admin cookie → role enforcement
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `GET admin users with HELPDESK role returns 403`() {
        val helpdeskUserId = UUID.randomUUID()
        val helpdeskUser = testAdminUser(id = helpdeskUserId, role = AdminRole.HELPDESK)
        val adminToken = createAdminJwt(subject = helpdeskUserId.toString(), role = "HELPDESK")

        val adminAuthService = mockk<AdminAuthService>(relaxed = true)
        coEvery { adminAuthService.verifyAccessToken(adminToken) } returns helpdeskUserId
        coEvery { adminAuthService.findById(helpdeskUserId) } returns helpdeskUser

        testAppWithRoutes(adminAuthService = adminAuthService) {
            val response = client.get("/admin/users") {
                header("Cookie", "admin_access_token=$adminToken")
            }
            // HELPDESK does not have users:read permission → 403
            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertTrue(response.bodyAsText().contains("FORBIDDEN") || response.bodyAsText().contains("does not have permission"))
        }
    }

    @Test
    fun `GET admin users with ADMIN role succeeds`() {
        val adminUserId = UUID.randomUUID()
        val adminUser = testAdminUser(id = adminUserId, role = AdminRole.ADMIN)
        val adminToken = createAdminJwt(subject = adminUserId.toString(), role = "ADMIN")

        val adminAuthService = mockk<AdminAuthService>(relaxed = true)
        coEvery { adminAuthService.verifyAccessToken(adminToken) } returns adminUserId
        coEvery { adminAuthService.findById(adminUserId) } returns adminUser
        coEvery { adminAuthService.listUsers(any(), any(), any(), any(), any()) } returns
            com.zyntasolutions.zyntapos.api.models.AdminPagedResponse(
                data = emptyList(), page = 1, size = 20, total = 0, totalPages = 0
            )

        testAppWithRoutes(adminAuthService = adminAuthService) {
            val response = client.get("/admin/users") {
                header("Cookie", "admin_access_token=$adminToken")
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun `GET admin users with AUDITOR role returns 403`() {
        val auditorUserId = UUID.randomUUID()
        val auditorUser = testAdminUser(id = auditorUserId, role = AdminRole.AUDITOR)
        val adminToken = createAdminJwt(subject = auditorUserId.toString(), role = "AUDITOR")

        val adminAuthService = mockk<AdminAuthService>(relaxed = true)
        coEvery { adminAuthService.verifyAccessToken(adminToken) } returns auditorUserId
        coEvery { adminAuthService.findById(auditorUserId) } returns auditorUser

        testAppWithRoutes(adminAuthService = adminAuthService) {
            val response = client.get("/admin/users") {
                header("Cookie", "admin_access_token=$adminToken")
            }
            // AUDITOR does not have users:read permission → 403
            assertEquals(HttpStatusCode.Forbidden, response.status)
        }
    }

    @Test
    fun `GET admin users with FINANCE role returns 403`() {
        val financeUserId = UUID.randomUUID()
        val financeUser = testAdminUser(id = financeUserId, role = AdminRole.FINANCE)
        val adminToken = createAdminJwt(subject = financeUserId.toString(), role = "FINANCE")

        val adminAuthService = mockk<AdminAuthService>(relaxed = true)
        coEvery { adminAuthService.verifyAccessToken(adminToken) } returns financeUserId
        coEvery { adminAuthService.findById(financeUserId) } returns financeUser

        testAppWithRoutes(adminAuthService = adminAuthService) {
            val response = client.get("/admin/users") {
                header("Cookie", "admin_access_token=$adminToken")
            }
            assertEquals(HttpStatusCode.Forbidden, response.status)
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 11. Admin endpoint with expired admin cookie token → 401
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `GET admin users with expired admin cookie returns 401`() {
        val adminAuthService = mockk<AdminAuthService>(relaxed = true)
        val expiredToken = createAdminJwt(
            expiresAtMs = System.currentTimeMillis() - 60_000L
        )
        coEvery { adminAuthService.verifyAccessToken(expiredToken) } returns null

        testAppWithRoutes(adminAuthService = adminAuthService) {
            val response = client.get("/admin/users") {
                header("Cookie", "admin_access_token=$expiredToken")
            }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 12. Valid POS JWT accesses POS endpoints successfully
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `GET v1 products with valid POS JWT succeeds`() {
        val productService = mockk<ProductService>(relaxed = true)
        coEvery { productService.list(any(), any(), any(), any()) } returns
            com.zyntasolutions.zyntapos.api.models.PagedResponse(
                data = emptyList(),
                page = 0,
                size = 50,
                total = 0L,
                hasMore = false,
            )

        testAppWithRoutes(productService = productService) {
            val validToken = createPosJwt()

            val response = client.get("/v1/products") {
                header(HttpHeaders.Authorization, "Bearer $validToken")
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 13. Public auth endpoints are accessible without JWT
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `POST v1 auth login is accessible without JWT`() {
        val userService = mockk<UserService>(relaxed = true)
        val licenseClient = mockk<LicenseValidationClient>(relaxed = true)
        coEvery { userService.authenticate(any(), any(), any()) } returns null

        testAppWithRoutes(userService = userService, licenseClient = licenseClient) {
            val response = client.post("/v1/auth/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"email":"test@test.com","password":"test123"}""")
            }
            // 401 means the route was reached and auth logic ran (not a 404 or route-not-found)
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    @Test
    fun `POST v1 auth refresh is accessible without JWT`() {
        val userService = mockk<UserService>(relaxed = true)
        coEvery { userService.refreshTokens(any(), any(), any(), any()) } returns null

        testAppWithRoutes(userService = userService) {
            val response = client.post("/v1/auth/refresh") {
                contentType(ContentType.Application.Json)
                setBody("""{"refresh_token":"some-token"}""")
            }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    @Test
    fun `POST admin auth login is accessible without cookie`() {
        val adminAuthService = mockk<AdminAuthService>(relaxed = true)
        coEvery { adminAuthService.login(any(), any(), any(), any()) } returns AdminAuthResult.InvalidCredentials

        testAppWithRoutes(adminAuthService = adminAuthService) {
            val response = client.post("/admin/auth/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"email":"admin@test.local","password":"test123"}""")
            }
            // 401 means login logic ran and rejected — route was accessible
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }

    @Test
    fun `GET admin auth status is accessible without auth`() {
        val adminAuthService = mockk<AdminAuthService>(relaxed = true)
        coEvery { adminAuthService.needsBootstrap() } returns false

        testAppWithRoutes(adminAuthService = adminAuthService) {
            val response = client.get("/admin/auth/status")
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // 14. Admin JWT in Bearer header does NOT grant POS endpoint access
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `GET v1 products with admin JWT returns 401`() = testAppWithRoutes {
        // Admin JWT has different issuer — POS verifier should reject it
        val adminToken = createAdminJwt()

        val response = client.get("/v1/products") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status,
            "Admin JWT should not be accepted by POS jwt-rs256 verifier (different issuer)")
    }

    // ════════════════════════════════════════════════════════════════════════
    // 15. Admin endpoint deactivated user → 401
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun `GET admin users with deactivated admin returns 401`() {
        val userId = UUID.randomUUID()
        val adminToken = createAdminJwt(subject = userId.toString())

        val adminAuthService = mockk<AdminAuthService>(relaxed = true)
        coEvery { adminAuthService.verifyAccessToken(adminToken) } returns userId
        // findById returns null for deactivated users
        coEvery { adminAuthService.findById(userId) } returns null

        testAppWithRoutes(adminAuthService = adminAuthService) {
            val response = client.get("/admin/users") {
                header("Cookie", "admin_access_token=$adminToken")
            }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }
}
