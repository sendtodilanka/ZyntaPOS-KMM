// ============================================================
// :composeApp:core — Shared UI Infrastructure
//
// Provides cross-feature foundation shared by every
// :composeApp:feature:* module:
//   • BaseViewModel<S, I, E>  — generic MVI ViewModel base
//   • (future) shared UI utilities, animations, accessibility helpers
//
// Dependencies are intentionally minimal:
//   ✅ lifecycle-viewmodel (ViewModel + viewModelScope)
//   ✅ kotlinx-coroutines-core (StateFlow, Channel, Flow)
//   ❌ NO Compose UI — keeps compile scope lean for non-UI tests
//   ❌ NO business logic — Clean Architecture boundary respected
// ============================================================
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
}

kotlin {
    android {
        namespace  = "com.zyntasolutions.zyntapos.ui.core"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk     = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        commonMain.dependencies {
            // ViewModel + viewModelScope (KMP-aware, resolves to correct platform)
            api(libs.androidx.lifecycle.viewmodel)
            // Coroutines — StateFlow, Channel, MutableStateFlow, Flow operators
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.bundles.testing.common)
        }
    }
}
