# ============================================================
# ZyntaPOS — ProGuard / R8 rules for the Compose Desktop JVM distribution
# (SEC-05: Obfuscate desktop JVM target to protect business logic)
#
# Applied via compose.desktop.application.buildTypes.release.proguard
# in composeApp/build.gradle.kts.
#
# References:
#   https://www.guardsquare.com/manual/configuration/usage
#   https://github.com/JetBrains/compose-multiplatform/blob/master/tutorials/Native_distributions_and_local_execution
# ============================================================

# ── Kotlin ────────────────────────────────────────────────────────────────────
# Kotlin metadata and reflection are required by kotlinx.serialization, Koin,
# and Compose Multiplatform's internal mechanisms.
-keep class kotlin.Metadata { *; }
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature, Exceptions
-keepattributes RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations
-dontwarn kotlin.**
-dontwarn kotlinx.**

# ── Kotlin Coroutines ─────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ── kotlinx.serialization ─────────────────────────────────────────────────────
# Serialization uses reflection to access serializer companion objects.
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keep @kotlinx.serialization.Serializable class * { *; }
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

# ── Koin DI ───────────────────────────────────────────────────────────────────
# Koin uses Kotlin reflection to resolve type parameters at runtime.
-keep class org.koin.** { *; }
-keepclassmembers class * {
    @org.koin.core.annotation.* *;
}
-dontwarn org.koin.**

# ── Compose Multiplatform / Jetbrains Compose ─────────────────────────────────
-keep class androidx.compose.** { *; }
-keep class org.jetbrains.compose.** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}
-dontwarn androidx.compose.**
-dontwarn org.jetbrains.compose.**

# ── Compose Navigation / Serialization (type-safe routes) ────────────────────
# NavRoute sealed subclasses are referenced by name in the navigation graph.
-keep class com.zyntasolutions.zyntapos.navigation.** { *; }

# ── Ktor Client ───────────────────────────────────────────────────────────────
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# ── SQLDelight (generated database code) ─────────────────────────────────────
-keep class com.zyntasolutions.zyntapos.db.** { *; }
-keep class app.cash.sqldelight.** { *; }
-dontwarn app.cash.sqldelight.**

# ── SQLite / SQLCipher ────────────────────────────────────────────────────────
-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

# ── jSerialComm (hardware serial port access) ─────────────────────────────────
-keep class com.fazecast.jSerialComm.** { *; }
-dontwarn com.fazecast.jSerialComm.**

# ── Sentry (crash reporting) ──────────────────────────────────────────────────
-keep class io.sentry.** { *; }
-dontwarn io.sentry.**

# ── ZyntaPOS application classes ─────────────────────────────────────────────
# Keep the application entry point.
-keep class com.zyntasolutions.zyntapos.MainKt { *; }

# Keep domain models — they are referenced by serialization and repositories.
-keep class com.zyntasolutions.zyntapos.domain.model.** { *; }

# Keep security classes — Keystore operations use reflection on JCE provider names.
-keep class com.zyntasolutions.zyntapos.security.** { *; }

# Keep HAL port classes — loaded via Koin at runtime.
-keep class com.zyntasolutions.zyntapos.hal.** { *; }

# ── Java standard library ─────────────────────────────────────────────────────
-dontwarn java.awt.**
-dontwarn javax.swing.**
-dontwarn sun.**
-dontwarn com.sun.**

# ── Output options ────────────────────────────────────────────────────────────
-printmapping build/outputs/mapping/release/mapping.txt
-verbose
