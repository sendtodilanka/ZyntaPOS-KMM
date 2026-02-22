// ============================================================
// :androidApp — Android Application Shell
// Pure Android module: com.android.application + kotlin.android.
// Depends on :composeApp (KMP library) for all shared UI/logic.
// This separation is required by AGP 9.0.0+ which no longer
// allows com.android.application alongside kotlinMultiplatform.
//
// ZyntaApplication is the Koin composition root for the Android
// platform. It must have compile-time visibility of EVERY module
// it registers at startup. All shared and feature modules are
// therefore declared as implementation() dependencies here.
// ============================================================

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeCompiler)
    // Secrets Gradle Plugin — injects local.properties keys into BuildConfig.
    alias(libs.plugins.secretsGradle)
}

android {
    namespace  = "com.zyntasolutions.zyntapos"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.zyntasolutions.zyntapos"
        minSdk        = libs.versions.android.minSdk.get().toInt()
        targetSdk     = libs.versions.android.targetSdk.get().toInt()
        versionCode   = 1
        versionName   = "1.0"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildFeatures {
        buildConfig = true   // required for Secrets Gradle Plugin to inject API keys
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    lint {
        // GradleDependency demoted to informational: kotlinx-datetime is intentionally
        // pinned to 0.6.1 — 0.7.1 has binary-incompatible JVM class removals.
        // See root build.gradle.kts resolutionStrategy force block for details.
        informational += "GradleDependency"
    }
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    // ── Compose Multiplatform KMP library (App() composable root) ─────────
    implementation(project(":composeApp"))
    implementation(libs.androidx.activity.compose)

    // ── Compose tooling: preview annotation available in all build types ──
    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)

    // ── Koin Android context bootstrap (androidContext()) ─────────────────
    implementation(libs.koin.android)

    // ── Tier 1: Core ──────────────────────────────────────────────────────
    implementation(project(":shared:core"))

    // ── Tier 2: Security ──────────────────────────────────────────────────
    implementation(project(":shared:security"))

    // ── Tier 3: Hardware Abstraction Layer ────────────────────────────────
    implementation(project(":shared:hal"))

    // ── Tier 4: Data ──────────────────────────────────────────────────────
    implementation(project(":shared:data"))

    // ── Tier 5: Domain (use cases / repositories used by data) ───────────
    implementation(project(":shared:domain"))

    // ── Tier 6: Navigation ────────────────────────────────────────────────
    implementation(project(":composeApp:navigation"))

    // ── Tier 7: Feature modules ───────────────────────────────────────────
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

// ── Secrets Gradle Plugin ────────────────────────────────────
secrets {
    propertiesFileName        = "local.properties"
    defaultPropertiesFileName = "local.properties.template"
    ignoreList.add("sdk.dir")
}
