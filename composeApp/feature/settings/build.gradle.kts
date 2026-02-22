// ============================================================
// :composeApp:feature:settings — Settings — Store config, tax, printer, user management
// ============================================================
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    android {
        namespace  = "com.zyntasolutions.zyntapos.feature.settings"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk     = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":composeApp:designsystem"))
            implementation(project(":composeApp:core"))
            implementation(project(":shared:core"))
            implementation(project(":shared:domain"))
            // :shared:hal is required by PrintTestPageUseCaseImpl (HAL orchestrator).
            // ViewModels must NOT import HAL types directly — they call the
            // PrintTestPageUseCase interface (in :shared:domain) with domain types only.
            // To fully remove this dep, relocate PrintTestPageUseCaseImpl to a dedicated
            // HAL-orchestration module (e.g. :composeApp:hal) and inject via DI.
            implementation(project(":shared:hal"))
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
        }
    }
}
