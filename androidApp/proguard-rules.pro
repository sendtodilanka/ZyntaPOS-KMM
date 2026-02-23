# ═══════════════════════════════════════════════════════════════
# ZyntaPOS — Android R8/ProGuard Rules
# Sprint 24 (14.1.17): Keep rules for KMP, Koin, SQLDelight,
# Ktor, and kotlinx-serialization.
# ═══════════════════════════════════════════════════════════════

# ── kotlinx-serialization ─────────────────────────────────────
# Keep @Serializable classes and their generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.zyntasolutions.zyntapos.**$$serializer { *; }
-keepclassmembers class com.zyntasolutions.zyntapos.** {
    *** Companion;
}
-keepclasseswithmembers class com.zyntasolutions.zyntapos.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Koin DI (reflective lookups) ──────────────────────────────
# Koin uses reflection to instantiate classes registered in modules.
-keep class org.koin.** { *; }
-keep class com.zyntasolutions.zyntapos.**.di.** { *; }
# Keep all ViewModel constructors (Koin resolves them reflectively)
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# ── SQLDelight generated classes ──────────────────────────────
-keep class app.cash.sqldelight.** { *; }
-keep class com.zyntasolutions.zyntapos.data.local.db.** { *; }

# ── SQLCipher ─────────────────────────────────────────────────
-keep class net.zetetic.database.** { *; }
-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

# ── Ktor client ───────────────────────────────────────────────
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
# Ktor uses SLF4J — keep bindings if present
-dontwarn org.slf4j.**

# ── Kotlin / KMP runtime ─────────────────────────────────────
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# ── kotlinx-coroutines ───────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ── kotlinx-datetime ─────────────────────────────────────────
-keep class kotlinx.datetime.** { *; }

# ── Compose runtime (stability metadata) ─────────────────────
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**

# ── Android Keystore (security module) ────────────────────────
-keep class android.security.keystore.** { *; }
-keep class java.security.** { *; }

# ── Domain models (may be used in reflection/serialization) ──
-keep class com.zyntasolutions.zyntapos.domain.model.** { *; }

# ── Suppress warnings for KMP expect/actual stubs ────────────
-dontwarn com.zyntasolutions.zyntapos.**
