// ============================================================
// :shared:data — Data layer: SQLDelight, Ktor, repo impls
// Contains: SQLDelight schema + DAOs, Ktor ApiClient + DTOs,
//           repository implementations, sync engine.
// Implements interfaces from :shared:domain.
// ============================================================
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared:domain"))
            implementation(libs.bundles.ktor.common)
            implementation(libs.bundles.sqldelight.common)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.android.driver)
            implementation(libs.sqlcipher.android)
            implementation(libs.androidx.sqlite)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
            implementation(libs.sqldelight.jvm.driver)
        }
        commonTest.dependencies {
            implementation(libs.bundles.testing.common)
            implementation(libs.ktor.client.mock)
        }
    }
}

android {
    namespace   = "com.zynta.pos.data"
    compileSdk  = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// ── SQLDelight Configuration ──────────────────────────────────
sqldelight {
    databases {
        create("ZyntaDatabase") {
            packageName.set("com.zynta.pos.db")
            // Source directory for .sq schema files
            srcDirs.setFrom("src/commonMain/sqldelight")
            // Generate verify queries for compile-time SQL validation
            verifyMigrations.set(false)
            generateAsync.set(false)
        }
    }
}
