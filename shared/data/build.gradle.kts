// ============================================================
// :shared:data — Data layer: SQLDelight, Ktor, repo impls
// Contains: SQLDelight schema + DAOs, Ktor ApiClient + DTOs,
//           repository implementations, sync engine.
// Implements interfaces from :shared:domain.
// ============================================================
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)          // replaces com.android.library (AGP 9.0 compat)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    android {
        namespace  = "com.zyntasolutions.zyntapos.data"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk     = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared:domain"))
            // Note: :shared:security removed — MERGED-F3 (2026-02-22).
            // PasswordHashPort is declared in :shared:domain; PasswordHasherAdapter
            // is bound by securityModule in :shared:security. :shared:data no longer
            // has a compile-time dependency on :shared:security.
            implementation(libs.bundles.ktor.common)
            implementation(libs.bundles.sqldelight.common)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.android.driver)
            implementation(libs.sqlcipher.android)
            implementation(libs.androidx.sqlite)
            implementation(libs.androidx.work.runtime)
            // koin-android required by AndroidDataModule.androidContext() extension
            implementation(libs.koin.android)
            // Firebase Analytics SDK — used by AnalyticsService androidMain actual (TODO-011)
            // Uses versioned ref directly (platform() is removed in Kotlin 2.3 KMP)
            implementation(libs.firebase.analytics.ktx.versioned)
            // Firebase Remote Config SDK — used by RemoteConfigService androidMain actual (TODO-011)
            implementation(libs.firebase.config.ktx.versioned)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
            implementation(libs.sqldelight.jvm.driver)
        }
        commonTest.dependencies {
            implementation(libs.bundles.testing.common)
            implementation(libs.ktor.client.mock)
        }
        // JVM integration tests use the in-memory SQLite driver (JdbcSqliteDriver.IN_MEMORY)
        // for repository and SyncEngine tests that require a real SQLDelight database.
        val jvmTest by getting {
            dependencies {
                implementation(libs.sqldelight.jvm.driver)
                implementation(libs.ktor.client.mock)
                implementation(libs.bundles.testing.common)
            }
        }
    }
}

// ── SQLDelight Configuration ──────────────────────────────────
sqldelight {
    databases {
        create("ZyntaDatabase") {
            packageName.set("com.zyntasolutions.zyntapos.db")
            srcDirs.setFrom("src/commonMain/sqldelight")
            verifyMigrations.set(false)
            generateAsync.set(false)
            dialect(libs.sqldelight.sqlite.dialect)
        }
    }
}
