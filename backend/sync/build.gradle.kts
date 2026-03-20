plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    application
    id("org.owasp.dependencycheck") version "12.2.0"
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

group = "com.zyntasolutions.zyntapos"
version = "1.0.0"

application {
    mainClass.set("com.zyntasolutions.zyntapos.sync.ApplicationKt")
}

repositories {
    mavenCentral()
}

// ── Security: force patched transitive dependency versions ───────────────────
// netty-codec/netty-handler: transitive via lettuce-core (Redis client)
// kotlin-reflect: older transitive via Ktor/Koin (CVE-2020-29582)
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
val koinVersion = "4.1.1"

// ── OWASP Dependency Check (TODO-009 Level 4) ─────────────────────────────
// CI sets OWASP_FAIL_CVSS=11 to report-only (never fail); local dev uses 7.0
dependencyCheck {
    failBuildOnCVSS = (System.getenv("OWASP_FAIL_CVSS")?.toFloatOrNull() ?: 7.0f)
    suppressionFile = "owasp-suppressions.xml"
    formats = listOf("HTML", "JSON", "SARIF")
    nvd {
        apiKey = System.getenv("NVD_API_KEY") ?: ""
        delay = if (System.getenv("NVD_API_KEY").isNullOrBlank()) 3500 else 500
    }
}

dependencies {
    // ── Common validation utilities + JWT defaults ───────────────────
    implementation(project(":common"))

    // ── Ktor Server (CIO) ──────────────────────────────────────────────
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-cio:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
    implementation("io.ktor:ktor-server-rate-limit:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-default-headers:$ktorVersion")

    // ── Ktor Client (for SyncForwarder → API service forwarding) ──────
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")

    // ── Redis pub/sub (Lettuce) ────────────────────────────────────────
    implementation("io.lettuce:lettuce-core:6.6.0.RELEASE")

    // ── Serialization ─────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // ── DI ────────────────────────────────────────────────────────────
    implementation("io.insert-koin:koin-ktor:$koinVersion")
    implementation("io.insert-koin:koin-logger-slf4j:$koinVersion")

    // ── Logging ───────────────────────────────────────────────────────
    implementation("ch.qos.logback:logback-classic:1.5.32")

    // ── JWT RS256 (for validating tokens from API) ────────────────────
    implementation("com.auth0:java-jwt:4.4.0")

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

    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.16")
    testImplementation("org.testcontainers:testcontainers:1.20.4")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")

    // ── Architecture enforcement tests ──────────────────────────────────
    testImplementation("com.lemonappdev:konsist:0.17.3")

    // ── Detekt formatting rules ─────────────────────────────────────────
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.8")
}

// ── Kover Coverage — B4: Enforce minimum test coverage ─────────────────────
// Target: 95%+ line coverage. CI Gate blocks PRs below this threshold.
kover {
    reports {
        verify {
            rule {
                minBound(95)
            }
        }
    }
}

tasks.shadowJar {
    archiveBaseName.set("zyntapos-sync")
    archiveVersion.set("")
    archiveClassifier.set("")
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
