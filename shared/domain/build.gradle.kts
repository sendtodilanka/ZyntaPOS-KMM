// ============================================================
// :shared:domain — Business domain, zero framework deps
// Contains: domain models, repository interfaces, use cases,
//           business rule validators.
// Depends only on :shared:core.
// ============================================================
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)          // replaces com.android.library (AGP 9.0 compat)
}

kotlin {
    android {
        namespace  = "com.zyntasolutions.zyntapos.domain"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk     = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core"))
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.collections.immutable)
        }
        commonTest.dependencies {
            implementation(libs.bundles.testing.common)
        }
    }
}
