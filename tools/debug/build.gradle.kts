// ============================================================
// :tools:debug — In-App Developer Console (debug-only)
// Tab-based debug tooling: Seeds, Database, Auth, Network,
// Diagnostics, UI/UX. Zero functional production footprint —
// all features gated behind AppInfoProvider.isDebug + ADMIN role.
// ============================================================
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    android {
        namespace  = "com.zyntasolutions.zyntapos.debug"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk     = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        commonMain.dependencies {
            // MVI infrastructure
            implementation(project(":composeApp:core"))
            // Design system — ZyntaButton, ZyntaTextField, ZyntaDialog, ZyntaStatCard, etc.
            implementation(project(":composeApp:designsystem"))
            // Core utilities — AppInfoProvider, ZyntaLogger, Result
            implementation(project(":shared:core"))
            // Domain interfaces — repositories, models (User, AuditEntry, Role, SyncOperation)
            implementation(project(":shared:domain"))
            // Seed runner and DefaultSeedDataSet
            implementation(project(":shared:seed"))
            // Data layer — DatabaseFactory for reset/vacuum/export (debug tooling exception)
            implementation(project(":shared:data"))

            implementation(libs.bundles.koin.common)
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.material3)
            api(libs.compose.ui)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.datetime)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
        commonTest.dependencies {
            implementation(libs.bundles.testing.common)
            implementation(libs.kotlinx.datetime)
        }
    }
}
