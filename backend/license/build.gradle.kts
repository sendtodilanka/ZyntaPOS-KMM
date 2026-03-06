plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    application
    id("org.owasp.dependencycheck") version "12.2.0"
}

group = "com.zyntasolutions.zyntapos"
version = "1.0.0"

application {
    mainClass.set("com.zyntasolutions.zyntapos.license.ApplicationKt")
}

repositories {
    mavenCentral()
}

// ── Security: force patched transitive dependency versions ───────────────────
// jackson-core/databind: transitive via logback-classic JSON encoder
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "com.fasterxml.jackson.core") {
            useVersion("2.19.0")
            because("CVE fix: upgrade Jackson core to patched version (DoS)")
        }
    }
}

val ktorVersion = "3.4.1"
val exposedVersion = "0.61.0"
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
    // ── Common validation utilities ─────────────────────────────────────
    implementation(project(":common"))

    // ── Ktor Server (CIO — not Netty) ──────────────────────────────────
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-cio:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
    implementation("io.ktor:ktor-server-rate-limit:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-default-headers:$ktorVersion")

    // ── Database ───────────────────────────────────────────────────────
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
    implementation("org.postgresql:postgresql:42.7.5")
    implementation("com.zaxxer:HikariCP:6.3.0")

    // ── Migrations ────────────────────────────────────────────────────
    implementation("org.flywaydb:flyway-core:10.22.0")
    implementation("org.flywaydb:flyway-database-postgresql:10.22.0")

    // ── Serialization ─────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")

    // ── DI ────────────────────────────────────────────────────────────
    implementation("io.insert-koin:koin-ktor:$koinVersion")
    implementation("io.insert-koin:koin-logger-slf4j:$koinVersion")

    // ── Logging ───────────────────────────────────────────────────────
    implementation("ch.qos.logback:logback-classic:1.5.18")

    // ── JWT RS256 ─────────────────────────────────────────────────────
    implementation("com.auth0:java-jwt:4.4.0")

    // ── Testing ───────────────────────────────────────────────────────
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation(kotlin("test"))
}

tasks.shadowJar {
    archiveBaseName.set("zyntapos-license")
    archiveVersion.set("")
    archiveClassifier.set("")
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
