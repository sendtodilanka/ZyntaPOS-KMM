package com.zyntasolutions.zyntapos

import com.zyntasolutions.zyntapos.data.remote.api.ApiService
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin module that replaces the production [ApiService] binding with [DevApiService].
 *
 * Loaded only inside the [BuildConfig.DEBUG] gate in [ZyntaApplication].
 * The `override = true` flag replaces the [KtorApiService] already registered
 * by [dataModule] with the local no-op stub.
 *
 * Source set: `src/debug/` — excluded from release builds by the Android build system.
 */
private val devDataModule: Module = module {
    single<ApiService> { DevApiService() }
}

/**
 * List of dev-only Koin modules for this build type.
 *
 * Debug: contains [devDataModule] to wire in [DevApiService].
 * Release: see `src/release/DevModules.kt` — returns an empty list.
 *
 * [ZyntaApplication] appends this list to the debug module load so the
 * caller never needs to know which build type is active.
 */
val devModules: List<Module> = listOf(devDataModule)
