// ============================================================
// :composeApp:feature:auth — Authentication — Login, PIN quick-switch, biometric, session management
// ============================================================
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    //alias(libs.plugins.mockative)
}

kotlin {
    android {
        namespace  = "com.zyntasolutions.zyntapos.feature.auth"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk     = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        commonMain.dependencies {
            // MVI BaseViewModel shared foundation — promoted from feature/auth/mvi in Sprint 13b
            implementation(project(":composeApp:core"))
            implementation(project(":composeApp:designsystem"))
            implementation(project(":shared:core"))
            implementation(project(":shared:domain"))
            implementation(libs.bundles.koin.common)
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(compose.material3)
            api(libs.compose.ui)
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
            implementation(libs.kotlinx.datetime)
        }
        jvmTest.dependencies {
            implementation(libs.kotlinx.datetime)
        }
    }
}
