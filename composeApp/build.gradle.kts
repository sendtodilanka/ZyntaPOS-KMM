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
            // ── Shared KMP modules ────────────────────────────────────────
            implementation(project(":shared:core"))         // Platform, ZyntaLogger, Result, etc.

            // ── Navigation (App.kt root composable needs ZyntaNavGraph) ──
            // Exposed via api() so :androidApp can see navigation transitively.
            api(project(":composeApp:navigation"))

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

            // ── JVM Composition Root (main.kt) ────────────────────────────
            // main.kt is the JVM entry point and acts as the DI composition
            // root — it must see every Koin module it registers at startup.
            // Tier 1: Core
            implementation(project(":shared:core"))
            // Tier 2: Security
            implementation(project(":shared:security"))
            // Tier 3: HAL
            implementation(project(":shared:hal"))
            // Tier 4: Data
            implementation(project(":shared:data"))
            // Tier 5: Navigation (already via commonMain api(), re-stated for clarity)
            // Tier 6: Feature modules
            implementation(project(":composeApp:feature:auth"))
            implementation(project(":composeApp:feature:pos"))
            implementation(project(":composeApp:feature:inventory"))
            implementation(project(":composeApp:feature:register"))
            implementation(project(":composeApp:feature:reports"))
            implementation(project(":composeApp:feature:settings"))
            implementation(project(":composeApp:feature:customers"))
            implementation(project(":composeApp:feature:coupons"))
            implementation(project(":composeApp:feature:expenses"))
            implementation(project(":composeApp:feature:staff"))
            implementation(project(":composeApp:feature:multistore"))
            implementation(project(":composeApp:feature:admin"))
            implementation(project(":composeApp:feature:media"))
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
