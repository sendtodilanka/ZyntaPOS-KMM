// ============================================================
// :shared:hal — Hardware Abstraction Layer
// Uses expect/actual to abstract POS hardware:
//   - Thermal printers  (Android: USB ESC/POS | JVM: TCP/IP)
//   - Barcode scanners  (Android: ML Kit CameraX | JVM: HID)
//   - Cash drawers      (Android: USB pulse | JVM: serial/GPIO)
// ============================================================
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)          // replaces com.android.library (AGP 9.0 compat)
}

kotlin {
    android {
        namespace  = "com.zyntasolutions.zyntapos.hal"
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
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            // ML Kit BarcodeScanning added here when integrated
        }
        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutinesSwing)
        }
        commonTest.dependencies {
            implementation(libs.bundles.testing.common)
        }
    }
}
