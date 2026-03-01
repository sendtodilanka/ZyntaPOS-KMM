package com.zyntasolutions.zyntapos.seed

import org.koin.dsl.module

/**
 * Koin module for the debug-only `:shared:seed` framework.
 *
 * ### Usage in debug builds only
 * Include this module in the application-level `startKoin` block **only**
 * in debug build types:
 *
 * ```kotlin
 * // Android (ZyntaApplication.onCreate)
 * if (BuildConfig.DEBUG) {
 *     modules(seedModule)
 * }
 *
 * // Desktop (main.kt)
 * // conditional block around inclusion
 * ```
 *
 * ### Zero production footprint
 * This module is declared in `:shared:seed` which must only be added as
 * `debugImplementation` in consuming modules. The module object itself
 * contains no side-effects — it only registers Koin bindings.
 */
val seedModule = module {
    /**
     * [SeedRunner] singleton — wired to production repository interfaces
     * so it exercises the full data stack (SQLDelight DAOs, sync enqueue, etc.).
     *
     * New repositories (unitGroup, taxGroup, user, register, expense, employee, coupon)
     * are injected with `getOrNull()` so the runner gracefully degrades when
     * any of these repositories are not yet registered in the DI graph.
     */
    single {
        SeedRunner(
            categoryRepository = get(),
            supplierRepository = get(),
            productRepository = get(),
            customerRepository = get(),
            unitGroupRepository = getOrNull(),
            taxGroupRepository = getOrNull(),
            userRepository = getOrNull(),
            registerRepository = getOrNull(),
            expenseRepository = getOrNull(),
            employeeRepository = getOrNull(),
            couponRepository = getOrNull(),
        )
    }
}
