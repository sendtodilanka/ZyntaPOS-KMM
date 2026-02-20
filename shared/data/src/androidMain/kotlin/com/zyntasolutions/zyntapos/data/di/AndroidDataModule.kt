package com.zyntasolutions.zyntapos.data.di

import com.zyntasolutions.zyntapos.data.local.db.DatabaseDriverFactory
import com.zyntasolutions.zyntapos.data.local.db.DatabaseKeyProvider
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * ZentaPOS — Android Data Koin Module
 *
 * Provides platform-specific actual bindings for Android:
 * - [DatabaseKeyProvider]: Android Keystore-backed AES-256 key management
 * - [DatabaseDriverFactory]: SQLCipher-backed [AndroidSqliteDriver]
 *
 * Include this module alongside [dataModule] in the Android application's
 * `startKoin { modules(androidDataModule, dataModule, ...) }`.
 */
val androidDataModule = module {

    single { DatabaseKeyProvider(androidContext()) }

    single { DatabaseDriverFactory(context = androidContext()) }
}
