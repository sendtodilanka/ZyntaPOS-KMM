// ============================================================
// :composeApp:navigation — Type-safe Navigation Graph
// Contains: NavRoute sealed hierarchy, ZyntaNavHost,
//           platform-adaptive shell (Rail vs BottomBar),
//           NavigationViewModel with RBAC route filtering,
//           deep link scheme: zyntapos://
// ============================================================
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)          // replaces com.android.library (AGP 9.0 compat)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    android {
        namespace  = "com.zyntasolutions.zyntapos.navigation"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk     = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":composeApp:designsystem"))
            api(project(":shared:domain"))           // Role, Permission, domain models
            api(project(":shared:security"))         // RBAC route filtering (Phase 2 expansion)
            // Explicit Compose Multiplatform artifact coordinates (accessors deprecated in CMP 1.8+)
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.material3)
            api(libs.compose.ui)
            api(libs.compose.navigation)             // Type-safe KMP navigation
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.bundles.koin.common)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
        commonTest.dependencies {
            implementation(libs.bundles.testing.common)
        }
    }
}
