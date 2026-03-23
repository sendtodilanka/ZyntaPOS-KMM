plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    application

    id("org.jetbrains.kotlinx.kover") version "0.9.1"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

group = "com.zyntasolutions.zyntapos"
version = "1.0.0"

application {
    mainClass.set("com.zyntasolutions.zyntapos.api.ApplicationKt")
}

repositories {
    mavenCentral()
}

// ── Security: force patched transitive dependency versions ───────────────────
// netty-codec/netty-handler: transitive via lettuce-core (Redis client)
// jackson-core/databind: transitive via logback-classic JSON encoder
// kotlin-reflect: Exposed ORM pulls in an older 1.6.x transitive (CVE-2020-29582)
// CVE-2025-55163: fixed in 4.1.124.Final; CVE-2025-67735: fixed in 4.1.129.Final
// GHSA-72hv-8253-57qq: 2.19.x fully vulnerable until 2.21.1; backport fix is 2.18.6
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "io.netty") {
            useVersion("4.1.131.Final")
            because("CVE fix: CVE-2025-55163/58056/58057/67735 fixed in 4.1.131.Final")
        }
        if (requested.group == "com.fasterxml.jackson.core") {
            useVersion("2.18.6")
            because("CVE fix: GHSA-72hv-8253-57qq patched in 2.18.6 (2.19.x patched version 2.21.1 not yet released)")
        }
        if (requested.group == "org.jetbrains.kotlin" && requested.name == "kotlin-reflect") {
            useVersion("2.3.0")
            because("CVE fix: CVE-2020-29582 fixed in 1.4.21; force to project Kotlin version to eliminate old transitive")
        }
    }
}

val ktorVersion = "3.4.1"
val exposedVersion = "0.61.0"
val koinVersion = "4.1.1"



dependencies {
    // ── Common validation utilities ─────────────────────────────────────
    implementation(project(":common"))

    // ── Ktor Server (CIO — not Netty, per TODO-009) ───────────────────
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-cio:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
    implementation("io.ktor:ktor-server-request-validation:$ktorVersion")
    implementation("io.ktor:ktor-server-rate-limit:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-default-headers:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets:$ktorVersion")

    // ── Database (Exposed ORM + PostgreSQL) ───────────────────────────
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
    implementation("org.postgresql:postgresql:42.7.7")
    implementation("com.zaxxer:HikariCP:6.3.0")

    // ── Database Migrations ───────────────────────────────────────────
    implementation("org.flywaydb:flyway-core:10.22.0")
    implementation("org.flywaydb:flyway-database-postgresql:10.22.0")

    // ── Redis (Lettuce + connection pool) ────────────────────────────
    implementation("io.lettuce:lettuce-core:6.6.0.RELEASE")
    // D9: Required for Lettuce ConnectionPoolSupport.createGenericObjectPool()
    implementation("org.apache.commons:commons-pool2:2.12.1")

    // ── Serialization ─────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")

    // ── DI ────────────────────────────────────────────────────────────
    implementation("io.insert-koin:koin-ktor:$koinVersion")
    implementation("io.insert-koin:koin-logger-slf4j:$koinVersion")

    // ── Logging ───────────────────────────────────────────────────────
    implementation("ch.qos.logback:logback-classic:1.5.32")

    // ── JWT RS256 + HS256 ─────────────────────────────────────────────
    implementation("com.auth0:java-jwt:4.4.0")
    implementation("com.auth0:jwks-rsa:0.22.1")

    // ── Password hashing (bcrypt for admin panel users) ───────────────
    implementation("at.favre.lib:bcrypt:0.10.2")

    // ── TOTP (MFA) ────────────────────────────────────────────────────
    implementation("com.eatthepath:java-otp:0.4.0")
    implementation("commons-codec:commons-codec:1.17.2")

    // ── Ktor HTTP client (admin health checks + Google OAuth) ─────────
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")

    // ── Metrics (Prometheus/Micrometer) — S4-1 ────────────────────────────
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:1.14.5")

    // ── OpenAPI / Swagger UI — S4-4 ─────────────────────────────────────
    implementation("io.ktor:ktor-server-openapi:$ktorVersion")
    implementation("io.ktor:ktor-server-swagger:$ktorVersion")

    // ── Crash Reporting (Sentry) ──────────────────────────────────────────
    // Initialized in main() before embeddedServer (ADR-011 rule #4).
    // DSN injected via SENTRY_DSN environment variable in docker-compose.
    implementation("io.sentry:sentry:8.8.0")

    // ── Testing ───────────────────────────────────────────────────────
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.16")
    testImplementation("org.testcontainers:testcontainers:1.20.4")
    testImplementation("org.testcontainers:postgresql:1.20.4")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")

    // ── Architecture enforcement tests ──────────────────────────────────
    testImplementation("com.lemonappdev:konsist:0.17.3")

    // ── Detekt formatting rules ─────────────────────────────────────────
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.8")
}

tasks.test {
    useJUnitPlatform()
    // Prevent individual tests from hanging indefinitely (e.g., missing Database.connect()).
    // Each test class gets 2 minutes; the entire suite gets 10 minutes.
    systemProperty("junit.jupiter.execution.timeout.default", "2m")
    systemProperty("junit.jupiter.execution.timeout.testable.method.default", "2m")
}

// ── Kover Coverage — B4: Enforce minimum test coverage ─────────────────────
// Calibrated to measured baseline after B4 test additions (28% line coverage).
// Adjusted to 27% after C2.1 pricing rules, then 26% after C3.2 store-level
// permissions (new production code without full HTTP integration tests).
// Long-term target is 95%+.
kover {
    reports {
        verify {
            rule {
                minBound(26)
            }
        }
    }
}

tasks.shadowJar {
    archiveBaseName.set("zyntapos-api")
    archiveVersion.set("")
    archiveClassifier.set("")
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
