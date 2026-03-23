package com.zyntasolutions.zyntapos.core.di

import com.zyntasolutions.zyntapos.core.health.SystemHealthTracker
import com.zyntasolutions.zyntapos.core.health.createSystemHealthTracker
import com.zyntasolutions.zyntapos.core.i18n.LocalizationManager
import com.zyntasolutions.zyntapos.core.logger.ZyntaLogger
import com.zyntasolutions.zyntapos.core.platform.AppInfoProvider
import com.zyntasolutions.zyntapos.core.platform.createAppInfoProvider
import com.zyntasolutions.zyntapos.core.utils.CurrencyFormatter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.core.qualifier.named
import org.koin.dsl.module

// ── Qualifier names for typed dispatcher injection ────────────────────────────

/** Koin named qualifier for [Dispatchers.IO]. */
val IO_DISPATCHER = named("IO")

/** Koin named qualifier for [Dispatchers.Main]. */
val MAIN_DISPATCHER = named("Main")

/** Koin named qualifier for [Dispatchers.Default]. */
val DEFAULT_DISPATCHER = named("Default")

/**
 * ZyntaPOS core Koin module.
 *
 * Provides:
 * - [ZyntaLogger] — singleton Kermit-backed logger
 * - [CurrencyFormatter] — singleton currency formatter (uses app default currency)
 * - `IO`, `Main`, `Default` coroutine dispatchers — injectable via [IO_DISPATCHER] etc.
 *
 * ### Registration
 * Include in your Koin setup:
 * ```kotlin
 * startKoin {
 *     modules(coreModule)
 * }
 * ```
 *
 * ### Dispatcher injection example
 * ```kotlin
 * class ProductRepository(
 *     private val ioDispatcher: CoroutineDispatcher,
 * ) : KoinComponent
 *
 * // In Koin module:
 * single<ProductRepository> {
 *     ProductRepository(ioDispatcher = get(IO_DISPATCHER))
 * }
 * ```
 */
val coreModule = module {

    // ── Logger ────────────────────────────────────────────────────────────────
    single { ZyntaLogger(defaultTag = "ZyntaPOS") }

    // ── Currency formatter ─────────────────────────────────────────────────────
    single { CurrencyFormatter() }

    // ── Localization manager ────────────────────────────────────────────────────
    single { LocalizationManager() }

    // ── App info provider (platform-specific build metadata) ─────────────────
    single<AppInfoProvider> { createAppInfoProvider() }

    // ── System health tracker (platform-specific diagnostics) ────────────────
    single<SystemHealthTracker> { createSystemHealthTracker() }

    // ── Coroutine dispatchers ─────────────────────────────────────────────────
    single<CoroutineDispatcher>(IO_DISPATCHER)      { Dispatchers.IO }
    single<CoroutineDispatcher>(MAIN_DISPATCHER)    { Dispatchers.Main }
    single<CoroutineDispatcher>(DEFAULT_DISPATCHER) { Dispatchers.Default }
}
