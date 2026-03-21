// ============================================================
// :composeApp — Compose Multiplatform KMP Library
// Targets: Android (library) + JVM Desktop (application entry).
// Contains: App() root composable, platform expect/actual stubs,
//           JVM main.kt desktop entry point.
//
// NOTE: com.android.application has been extracted to :androidApp
// to comply with AGP 9.0.0+ / KMP plugin compatibility rules.
// ============================================================
import java.time.LocalDate
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
            implementation(project(":composeApp:core"))     // BaseViewModel — needed to resolve VM supertypes

            // ── Navigation (App.kt root composable needs ZyntaNavGraph) ──
            // Exposed via api() so :androidApp can see navigation transitively.
            api(project(":composeApp:navigation"))

            // Explicit Compose Multiplatform artifact coordinates (accessors deprecated in CMP 1.8+)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            // AndroidX Lifecycle (KMP-compatible)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            // Koin Compose — koinInject() / koinViewModel() used in App.kt wiring
            implementation(libs.bundles.koin.common)

            // ── Feature modules ─────────────────────────────────────────
            // App.kt wires all screen composables into ZyntaNavGraph;
            // compile-time visibility of every feature module is required.
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
            implementation(project(":composeApp:feature:dashboard"))
            implementation(project(":composeApp:feature:onboarding"))
            implementation(project(":composeApp:feature:accounting"))
            implementation(project(":composeApp:feature:diagnostic"))
            // Debug Console — always compiled in; Koin bindings loaded only when isDebug == true
            implementation(project(":tools:debug"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)

            // ── JVM Composition Root (main.kt) ────────────────────────────
            // main.kt imports Koin modules from these shared modules.
            // Feature modules moved to commonMain (App.kt screen wiring).
            implementation(project(":shared:security"))
            implementation(project(":shared:hal"))
            implementation(project(":shared:data"))
            // main.kt loads seedModule + debugModule conditionally (isDebug gate)
            implementation(project(":shared:core"))
            implementation(project(":shared:seed"))

            // ── Crash Reporting (Sentry JVM) ─────────────────────────────────
            // Initialized before Koin in main() (ADR-011 rule #4).
            // DSN read from SENTRY_DSN environment variable at runtime.
            implementation("io.sentry:sentry:8.8.0")
        }
    }
}

// ── Desktop Application Configuration ────────────────────────
compose.desktop {
    application {
        mainClass = "com.zyntasolutions.zyntapos.MainKt"

        // JVM args for the packaged application — inject version info as system properties
        val appVersion = rootProject.extra["appVersionName"] as String
        val appBuild   = rootProject.extra["appVersionBuild"] as Int
        jvmArgs += listOf(
            "-Xmx512m",
            "-Dfile.encoding=UTF-8",
            "-Dapp.version=$appVersion",
            "-Dapp.build.number=$appBuild",
            "-Dapp.build.date=${LocalDate.now()}",
        )

        // ── ProGuard / R8 obfuscation (SEC-05) ───────────────────────────────
        // Applied to release distributions to protect business logic from
        // decompilation. Debug / dev builds (./gradlew run) are unaffected.
        buildTypes.release.proguard {
            isEnabled.set(true)
            obfuscate.set(true)
            configurationFiles.from(rootProject.file("config/proguard/compose-desktop.pro"))
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName    = "ZyntaPOS"
            packageVersion = appVersion
            description    = "ZyntaPOS — Cross-platform Point of Sale system"
            vendor         = "Zynta Solutions"
            copyright      = "Copyright 2026 Zynta Solutions. All rights reserved."

            // Bundled JVM runtime (JDK 17+)
            includeAllModules = true

            linux {
                debPackageVersion    = appVersion
                debMaintainer        = "dev@zyntasolutions.com"
                appCategory          = "Office"
                menuGroup            = "Office"
            }

            macOS {
                bundleID             = "com.zyntasolutions.zyntapos"
            }

            windows {
                menuGroup            = "ZyntaPOS"
                upgradeUuid          = "b2f8c3a1-7d4e-4f5a-9b6c-1e2d3f4a5b6c"
                dirChooser           = true
                perUserInstall       = false
                shortcut             = true
            }
        }
    }
}

// ── Desktop run task — enable debug mode for development ─────────────────────
// JvmAppInfoProvider reads app.debug from System properties. Setting it here
// ensures the debug console and seed tools are available in `./gradlew run`.
// This only affects the run / runDistributable tasks, NOT packaged installers.
afterEvaluate {
    tasks.withType<JavaExec>().configureEach {
        jvmArgs("-Dapp.debug=true")
    }
}
