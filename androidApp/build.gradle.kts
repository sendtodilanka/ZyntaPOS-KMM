// ============================================================
// :androidApp — Android Application Shell
// Pure Android module: com.android.application + kotlin.android.
// Depends on :composeApp (KMP library) for all shared UI/logic.
// This separation is required by AGP 9.0.0+ which no longer
// allows com.android.application alongside kotlinMultiplatform.
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
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    // The KMP shared module — provides App(), commonMain, androidMain code.
    implementation(project(":composeApp"))
    implementation(libs.androidx.activity.compose)
    debugImplementation(libs.compose.uiTooling)
}

// ── Secrets Gradle Plugin ────────────────────────────────────
secrets {
    propertiesFileName        = "local.properties"
    defaultPropertiesFileName = "local.properties.template"
    ignoreList.add("sdk.dir")
}
