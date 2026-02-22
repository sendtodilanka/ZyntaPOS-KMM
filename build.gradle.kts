// ============================================================
// ZyntaPOS — Root Build Script
// ============================================================
// All plugins declared here with `apply false` so each sub-module
// opts in explicitly. This avoids classloader conflicts on Gradle 8+.
// ============================================================

plugins {
    // ── Android ────────────────────────────────────────────────
    alias(libs.plugins.androidApplication)     apply false
    alias(libs.plugins.androidLibrary)         apply false

    // ── Kotlin ─────────────────────────────────────────────────
    alias(libs.plugins.kotlinMultiplatform)    apply false
    alias(libs.plugins.kotlinAndroid)          apply false
    alias(libs.plugins.kotlinSerialization)    apply false   // kotlinx-serialization plugin
    alias(libs.plugins.composeCompiler)        apply false

    // ── Compose ────────────────────────────────────────────────
    alias(libs.plugins.composeMultiplatform)   apply false
    alias(libs.plugins.composeHotReload)       apply false

    // ── Persistence ────────────────────────────────────────────
    alias(libs.plugins.sqldelight)             apply false   // SQLDelight Gradle plugin

    // ── Build Tooling ──────────────────────────────────────────
    alias(libs.plugins.buildkonfig)            apply false   // BuildKonfig (typed config per flavor)
    alias(libs.plugins.secretsGradle)          apply false   // Secrets Gradle Plugin (API key injection)
    alias(libs.plugins.mockative)              apply false   // Mockative KSP processor

    // ── Static Analysis ──────────────────────────────────────
    alias(libs.plugins.detekt)
}

// ─── Detekt — Static Analysis ─────────────────────────────────────────────
// Applied at root level to scan all Kotlin sources across all modules.
// Config: config/detekt/detekt.yml
// Run:    ./gradlew detekt
// ──────────────────────────────────────────────────────────────────────────
detekt {
    buildUponDefaultConfig = true            // use built-in rules as baseline
    config.setFrom(files("config/detekt/detekt.yml"))
    source.setFrom(
        fileTree(".") {
            include("**/src/commonMain/kotlin/**/*.kt")
            include("**/src/androidMain/kotlin/**/*.kt")
            include("**/src/jvmMain/kotlin/**/*.kt")
        }
    )
    parallel = true
}

// ─── Dependency Resolution ─────────────────────────────────────────────────
// Force kotlinx-datetime to 0.6.1 across all modules.
// Compose Material3 1.9.0 pulls in 0.7.1 which has binary-incompatible JVM
// class removals (Clock.class, Instant.class absent). Pinning to 0.6.1
// ensures domain bytecode matches the runtime JAR.
// ────────────────────────────────────────────────────────────────────────────
subprojects {
    configurations.configureEach {
        resolutionStrategy {
            force("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
            force("org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.6.1")
        }
    }
}
