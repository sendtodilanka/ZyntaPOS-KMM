// ============================================================
// :shared:security — Encryption, key storage, RBAC, sessions
// Contains: AES-256-GCM crypto (expect/actual), SecureKeyStorage
//           (Android Keystore / JVM JCE), PBKDF2 PIN hashing,
//           JWT TokenManager, RbacEngine, SessionManager,
//           BiometricAuthHelper (expect/actual).
// ============================================================
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)          // replaces com.android.library (AGP 9.0 compat)
}

kotlin {
    android {
        namespace  = "com.zyntasolutions.zyntapos.security"
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
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.datastore.preferences.core)
            implementation(libs.datastore.core.okio)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.androidx.security.crypto)
        }
        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutinesSwing)
        }
        commonTest.dependencies {
            implementation(libs.bundles.testing.common)
        }
    }
}
