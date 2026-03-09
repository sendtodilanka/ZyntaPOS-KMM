// ============================================================
// ZyntaPOS — Root Build Script
// ============================================================
// All plugins declared here with `apply false` so each sub-module
// opts in explicitly. This avoids classloader conflicts on Gradle 8+.
// ============================================================

buildscript {
    dependencies {
        // Detekt loaded via buildscript classpath so it resolves from
        // dependency repositories (mavenCentral) rather than the plugin
        // portal, which may be unavailable in restricted CI environments.
        classpath("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:${libs.versions.detekt.get()}")
    }
}

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
}

apply(plugin = "io.gitlab.arturbosch.detekt")

// ─── Detekt — Static Analysis ─────────────────────────────────────────────
// Applied at root level to scan all Kotlin sources across all modules.
// Config: config/detekt/detekt.yml
// Run:    ./gradlew detekt
// ──────────────────────────────────────────────────────────────────────────
configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
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
    // Phase 1: violations are reported as warnings but don't fail CI.
    // Phase 2 gate: remove this to enforce zero-issue policy before merging.
    ignoreFailures = true
}

// Enable SARIF report output so results are uploaded to GitHub Security tab.
// HTML is retained for local review; XML disabled (redundant with SARIF).
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        html.required.set(true)
        xml.required.set(false)
        sarif.required.set(true)
        sarif.outputLocation.set(file("${rootProject.layout.buildDirectory.get()}/reports/detekt/detekt.sarif"))
    }
}

// ─── Version Properties ───────────────────────────────────────────────────
// Single source of truth for version information across all modules.
// Loaded from version.properties and exposed as extra properties.
// ──────────────────────────────────────────────────────────────────────────
val versionProps = java.util.Properties().apply {
    file("version.properties").inputStream().use { load(it) }
}
val versionMajor = versionProps.getProperty("VERSION_MAJOR", "1").toInt()
val versionMinor = versionProps.getProperty("VERSION_MINOR", "0").toInt()
val versionPatch = versionProps.getProperty("VERSION_PATCH", "0").toInt()
val versionLabel = versionProps.getProperty("VERSION_LABEL", "").trim()
val versionBuild = versionProps.getProperty("VERSION_BUILD", "1").toInt()

val versionName = buildString {
    append("$versionMajor.$versionMinor.$versionPatch")
    if (versionLabel.isNotEmpty()) append("-$versionLabel")
}
val versionCode = versionMajor * 10_000 + versionMinor * 100 + versionPatch

// Expose to all subprojects via extra properties
allprojects {
    extra["appVersionName"]  = versionName
    extra["appVersionCode"]  = versionCode
    extra["appVersionBuild"] = versionBuild
    extra["appVersionMajor"] = versionMajor
    extra["appVersionMinor"] = versionMinor
    extra["appVersionPatch"] = versionPatch
}

// ─── Dependency Resolution ─────────────────────────────────────────────────
// Pin kotlinx-datetime to 0.7.1 — this is the version CMP 1.10.0 was compiled
// against. In 0.7.1, kotlinx.datetime.Instant became a typealias for
// kotlin.time.Instant (added in Kotlin 2.1). Forcing 0.6.1 at runtime breaks
// CMP's pre-compiled JARs that call toLocalDateTime(kotlin.time.Instant, ...)
// causing NoSuchMethodError on screens that use datetime (Reports, Attendance, etc.).
// DO NOT downgrade to 0.6.x — it is incompatible with CMP 1.10.0 + Kotlin 2.3.0.
// ────────────────────────────────────────────────────────────────────────────
subprojects {
    configurations.configureEach {
        resolutionStrategy {
            force("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
            force("org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.7.1")
        }
    }
}

// ─── Compose Compiler Stability Configuration (S-02) ──────────────────────
// Domain model classes in :shared:domain are immutable `data class` types but
// contain `List<T>` fields that the Compose compiler conservatively treats as
// unstable. This global config marks all domain model package classes as stable
// without adding a Compose dependency to :shared:domain (framework-free ADR-002).
// Applied to every module that has the Compose Compiler plugin.
// Config file: config/compose/stability_config.conf
// ──────────────────────────────────────────────────────────────────────────
subprojects {
    pluginManager.withPlugin("org.jetbrains.kotlin.plugin.compose") {
        extensions.configure<org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension> {
            stabilityConfigurationFile.set(rootProject.file("config/compose/stability_config.conf"))
        }
    }
}
