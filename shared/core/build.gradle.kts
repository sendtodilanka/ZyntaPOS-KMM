// ============================================================
// :shared:core — Pure Kotlin Multiplatform utilities
// No platform-specific imports. Compiles to all targets.
// Contains: MVI base, Result types, extensions, dispatchers,
//           CurrencyUtils, DateTimeUtils, ValidationUtils.
// ============================================================
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)          // replaces com.android.library (AGP 9.0 compat)
}

kotlin {
    android {
        namespace  = "com.zyntasolutions.zyntapos.core"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk     = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.bundles.kotlinx.common)
            api(libs.kermit)
            api(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.koin.android)
        }
        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutinesSwing)
        }
        commonTest.dependencies {
            implementation(libs.bundles.testing.common)
        }
    }
}
