// ============================================================
// :composeApp:designsystem — Material 3 Design System
// Contains: ZentaTheme, color tokens, typography, shapes,
//           spacing constants, reusable stateless Composables
//           (ZentaButton, ZentaCard, NumericKeypad, etc.).
// ============================================================
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)          // replaces com.android.library (AGP 9.0 compat)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    android {
        namespace  = "com.zyntasolutions.zyntapos.designsystem"
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
            // Explicit Compose Multiplatform artifact coordinates (accessors deprecated in CMP 1.8+)
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(compose.material3)
            api(libs.compose.ui)
            api(libs.compose.components.resources)
            api(compose.materialIconsExtended)
            api(libs.compose.adaptive)
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.coil.compose)
        }
        androidMain.dependencies {
            implementation(libs.compose.uiTooling)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.compose.uiTooling)
        }
        commonTest.dependencies {
            implementation(libs.bundles.testing.common)
        }
    }
}
