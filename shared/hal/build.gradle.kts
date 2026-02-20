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
    alias(libs.plugins.androidLibrary)
}

kotlin {
    androidTarget {
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

android {
    namespace   = "com.zynta.pos.hal"
    compileSdk  = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
