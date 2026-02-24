// ============================================================
// :shared:seed — Debug-only Seed Data Framework
// JSON-backed seed data for UI/UX testing.
// SeedRunner calls existing domain repositories directly —
// no new domain logic is introduced here.
//
// IMPORTANT: This module must only be included in DEBUG builds.
// It has ZERO production footprint when not included.
// ============================================================
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    android {
        namespace  = "com.zyntasolutions.zyntapos.seed"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk     = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:core"))
            implementation(project(":shared:domain"))
            implementation(libs.bundles.kotlinx.common)
            implementation(libs.bundles.koin.common)
        }
        commonTest.dependencies {
            implementation(libs.bundles.testing.common)
            implementation(libs.kotlinx.datetime)
        }
        jvmTest.dependencies {
            implementation(libs.kotlinx.datetime)
        }
    }
}
