package com.zyntasolutions.zyntapos

import org.koin.core.module.Module

/**
 * Release variant of [devModules] — always empty.
 *
 * The debug variant (see `src/debug/DevModules.kt`) registers [DevApiService]
 * to stub out network calls during local dev testing. In a release build this
 * file is compiled instead, providing an empty list so no dev overrides are
 * applied and the production [KtorApiService] binding is used as normal.
 *
 * [ZyntaApplication] calls `devModules` inside the [BuildConfig.DEBUG] gate,
 * so this val is unreachable at runtime in release builds. It exists solely to
 * satisfy the compiler when `main/ZyntaApplication.kt` references `devModules`.
 */
val devModules: List<Module> = emptyList()
