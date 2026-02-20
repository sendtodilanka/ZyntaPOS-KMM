// ============================================================
// :composeApp — Compose Multiplatform KMP Library
// Targets: Android (library) + JVM Desktop (application entry).
// Contains: App() root composable, platform expect/actual stubs,
//           JVM main.kt desktop entry point.
//
// NOTE: com.android.application has been extracted to :androidApp
// to comply with AGP 9.0.0+ / KMP plugin compatibility rules.
// ============================================================
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)          // replaces com.android.library (AGP 9.0 compat)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}

kotlin {
    // Android library target — configured via android {} inside kotlin {} with
    // com.android.kotlin.multiplatform.library plugin (no top-level android {} block).
    android {
        namespace  = "com.zyntasolutions.zyntapos"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk     = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    jvm()

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiTooling)
            implementation(libs.androidx.activity.compose)
        }
        commonMain.dependencies {
            // ── Shared modules ────────────────────────────────────────────
            implementation(project(":shared:core"))   // Platform, ZentaLogger, Result, etc.

            // Explicit Compose Multiplatform artifact coordinates (accessors deprecated in CMP 1.8+)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            // AndroidX Lifecycle (KMP-compatible)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
        }
    }
}

// ── Desktop Application Configuration ────────────────────────
compose.desktop {
    application {
        mainClass = "com.zyntasolutions.zyntapos.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName    = "com.zyntasolutions.zyntapos"
            packageVersion = "1.0.0"
        }
    }
}
