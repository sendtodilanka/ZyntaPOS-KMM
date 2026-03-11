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

import java.time.LocalDate

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeCompiler)
    // Secrets Gradle Plugin — injects local.properties keys into BuildConfig.
    alias(libs.plugins.secretsGradle)
    // Firebase — google-services.json processed at build time (injected by CI from GOOGLE_SERVICES_JSON secret)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.firebaseCrashlytics)
}

android {
    namespace  = "com.zyntasolutions.zyntapos"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.zyntasolutions.zyntapos"
        minSdk        = libs.versions.android.minSdk.get().toInt()
        targetSdk     = libs.versions.android.targetSdk.get().toInt()
        versionCode   = rootProject.extra["appVersionCode"] as Int
        versionName   = rootProject.extra["appVersionName"] as String

        // Inject version info into BuildConfig for runtime access
        buildConfigField("String", "APP_VERSION_NAME", "\"${rootProject.extra["appVersionName"]}\"")
        buildConfigField("int", "APP_VERSION_CODE", "${rootProject.extra["appVersionCode"]}")
        buildConfigField("int", "APP_BUILD_NUMBER", "${rootProject.extra["appVersionBuild"]}")
        buildConfigField("String", "BUILD_DATE", "\"${LocalDate.now()}\"")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildFeatures {
        buildConfig = true   // required for Secrets Gradle Plugin to inject API keys
    }

    signingConfigs {
        create("release") {
            // CI: read from environment variables (GitHub Secrets)
            // Local: fallback to debug keystore for testing release builds
            val ksFile    = System.getenv("RELEASE_KEYSTORE_PATH")
            val ksPass    = System.getenv("RELEASE_KEYSTORE_PASSWORD")
            val keyAlias  = System.getenv("RELEASE_KEY_ALIAS")
            val keyPass   = System.getenv("RELEASE_KEY_PASSWORD")

            if (ksFile != null && file(ksFile).exists()) {
                storeFile     = file(ksFile)
                storePassword = ksPass
                this.keyAlias      = keyAlias
                keyPassword   = keyPass
            } else {
                // Fallback to debug keystore so `assembleRelease` works locally
                storeFile     = file("${System.getProperty("user.home")}/.android/debug.keystore")
                storePassword = "android"
                this.keyAlias      = "androiddebugkey"
                keyPassword   = "android"
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    lint {
        // GradleDependency demoted to informational: kotlinx-datetime MUST be 0.7.1
        // (required by CMP 1.10.0). In 0.7.1, Instant is a typealias for kotlin.time.Instant.
        // Downgrading to 0.6.1 causes NoSuchMethodError on datetime-using screens.
        // Root build.gradle.kts enforces this version via resolutionStrategy.force().
        informational += "GradleDependency"
    }
}

kotlin {
    jvmToolchain(21)
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
    implementation(project(":composeApp:feature:dashboard"))
    implementation(project(":composeApp:feature:onboarding"))
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
    implementation(project(":composeApp:feature:accounting"))

    // ── Tier 8: Debug tools ────────────────────────────────────────────────
    // Always compiled in (compile-time import in ZyntaApplication required).
    // Koin bindings loaded only in debug builds via BuildConfig.DEBUG gate.
    // ProGuard/R8 eliminates unreachable debug classes in release builds.
    implementation(project(":shared:seed"))
    implementation(project(":tools:debug"))

    // ── Crash Reporting (Sentry) ───────────────────────────────────────────
    // Initialized before Koin in ZyntaApplication.onCreate() (ADR-011 rule #4).
    // DSN injected via Secrets Gradle Plugin: ZYNTA_SENTRY_DSN → BuildConfig.ZYNTA_SENTRY_DSN.
    implementation("io.sentry:sentry-android:8.8.0")

    // ── Feature: Diagnostic module ────────────────────────────────────────
    implementation(project(":composeApp:feature:diagnostic"))

    // ── Firebase Analytics + Crashlytics (TODO-011) ───────────────────────
    // google-services.json is CI-injected from GOOGLE_SERVICES_JSON secret.
    // Placeholder file must exist at androidApp/google-services.json for local builds.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics.ktx)
    implementation(libs.firebase.crashlytics.ktx)
}

// ── Secrets Gradle Plugin ────────────────────────────────────
secrets {
    propertiesFileName        = "local.properties"
    defaultPropertiesFileName = "local.properties.template"
    ignoreList.add("sdk.dir")
}
