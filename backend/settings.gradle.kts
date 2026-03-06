// ═══════════════════════════════════════════════════════════════════
// ZyntaPOS Backend — Gradle Settings
//
// This is a SEPARATE Gradle project from the KMP mobile/desktop app.
// Run from: backend/
//   ./gradlew :api:shadowJar
//   ./gradlew :license:shadowJar
//   ./gradlew :sync:shadowJar
//
// Each subproject has its own settings.gradle.kts for independent
// Docker builds. This root settings.gradle.kts is used when you
// want to build all backend services at once.
// ═══════════════════════════════════════════════════════════════════

rootProject.name = "zyntapos-backend"

// Individual subprojects (each has its own settings.gradle.kts too)
include(":api", ":license", ":sync", ":common")

project(":api").projectDir = file("api")
project(":license").projectDir = file("license")
project(":sync").projectDir = file("sync")
project(":common").projectDir = file("common")
