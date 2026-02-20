package com.zyntasolutions.zyntapos.data.di

import com.zyntasolutions.zyntapos.data.local.db.DatabaseDriverFactory
import com.zyntasolutions.zyntapos.data.local.db.DatabaseKeyProvider
import org.koin.dsl.module
import java.io.File

/**
 * ZentaPOS — Desktop (JVM) Data Koin Module
 *
 * Provides platform-specific actual bindings for the Desktop JVM target:
 * - [DatabaseKeyProvider]: JCE PKCS12 KeyStore-backed AES-256 key management
 * - [DatabaseDriverFactory]: SQLCipher JdbcSqliteDriver (WAL mode, hex-key PRAGMA)
 *
 * The application data directory is resolved from the OS-specific user home:
 * - macOS/Linux: `~/.zyntapos/data/`
 * - Windows:     `%APPDATA%/ZyntaPOS/data/`
 *
 * Include this module alongside [dataModule] in the Desktop application's
 * `startKoin { modules(desktopDataModule, dataModule, ...) }`.
 *
 * ## Production Deployment Note — SQLCipher Native Libs
 * For full AES-256 encryption on the JVM target, bundle the platform-native
 * SQLCipher shared library with the desktop distribution:
 * - macOS  → `libsqlcipher.dylib` (universal binary) in `lib/` next to JAR
 * - Linux  → `libsqlcipher.so` in `lib/`
 * - Windows → `sqlcipher.dll` in app root
 * Set JVM system property: `-Dorg.xerial.sqlite.lib.path=/path/to/lib`
 */
val desktopDataModule = module {

    single { resolveAppDataDir() }

    single { DatabaseKeyProvider(appDataDir = get()) }

    single { DatabaseDriverFactory(appDataDir = get()) }
}

/**
 * Resolves the OS-appropriate application data directory for ZentaPOS.
 * Creates the directory (and parents) if it does not already exist.
 */
private fun resolveAppDataDir(): String {
    val base = when {
        System.getProperty("os.name")?.lowercase()?.contains("win") == true ->
            System.getenv("APPDATA") ?: System.getProperty("user.home")
        else ->
            System.getProperty("user.home")
    }
    val dir = File(base, if (base == System.getenv("APPDATA")) "ZyntaPOS/data" else ".zyntapos/data")
    dir.mkdirs()
    return dir.absolutePath
}
