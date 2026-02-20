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
}
