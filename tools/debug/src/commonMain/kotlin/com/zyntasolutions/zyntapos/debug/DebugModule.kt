package com.zyntasolutions.zyntapos.debug

import com.zyntasolutions.zyntapos.debug.actions.AuthActionHandler
import com.zyntasolutions.zyntapos.debug.actions.AuthActionHandlerImpl
import com.zyntasolutions.zyntapos.debug.actions.DatabaseActionHandler
import com.zyntasolutions.zyntapos.debug.actions.DatabaseActionHandlerImpl
import com.zyntasolutions.zyntapos.debug.actions.DiagnosticsActionHandler
import com.zyntasolutions.zyntapos.debug.actions.DiagnosticsActionHandlerImpl
import com.zyntasolutions.zyntapos.debug.actions.NetworkActionHandler
import com.zyntasolutions.zyntapos.debug.actions.NetworkActionHandlerImpl
import com.zyntasolutions.zyntapos.debug.actions.SeedActionHandler
import com.zyntasolutions.zyntapos.debug.actions.SeedActionHandlerImpl
import com.zyntasolutions.zyntapos.data.local.db.DatabaseFactory
import com.zyntasolutions.zyntapos.domain.repository.AuditRepository
import com.zyntasolutions.zyntapos.domain.repository.AuthRepository
import com.zyntasolutions.zyntapos.domain.repository.SyncRepository
import com.zyntasolutions.zyntapos.domain.repository.UserRepository
import com.zyntasolutions.zyntapos.seed.SeedRunner
import org.koin.compose.viewmodel.dsl.viewModelOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Koin DI module for the Debug Console.
 *
 * ### Load order requirement
 * This module MUST be loaded AFTER [dataModule] and [seedModule] so that
 * [DatabaseFactory], [SeedRunner], and all repository bindings are already
 * registered in the Koin graph before this module resolves them.
 *
 * ### Production gate
 * This module is loaded conditionally in entry points:
 * - Android: `ZyntaApplication.onCreate()` — guarded by `AppInfoProvider.isDebug`
 * - Desktop: `main()` — guarded by `AppInfoProvider.isDebug`
 *
 * The `debugModule` symbol is compiled into all builds (it is a regular
 * Kotlin `val`), but its Koin bindings are only registered at runtime
 * when `isDebug == true`. This keeps the module dormant in production.
 */
val debugModule = module {

    // ── Action handlers ──────────────────────────────────────────────────────

    single<SeedActionHandler> {
        SeedActionHandlerImpl(seedRunner = get<SeedRunner>())
    }

    single<DatabaseActionHandler> {
        DatabaseActionHandlerImpl(databaseFactory = get<DatabaseFactory>())
    }

    single<AuthActionHandler> {
        AuthActionHandlerImpl(
            authRepository = get<AuthRepository>(),
            userRepository = get<UserRepository>(),
        )
    }

    single<NetworkActionHandler> {
        NetworkActionHandlerImpl(syncRepository = get<SyncRepository>())
    }

    single<DiagnosticsActionHandler> {
        DiagnosticsActionHandlerImpl(auditRepository = get<AuditRepository>())
    }

    // ── ViewModel ────────────────────────────────────────────────────────────

    viewModelOf(::DebugViewModel)
}
