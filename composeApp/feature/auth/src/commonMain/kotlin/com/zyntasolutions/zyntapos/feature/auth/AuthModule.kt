package com.zyntasolutions.zyntapos.feature.auth

import com.zyntasolutions.zyntapos.domain.usecase.auth.CheckPermissionUseCase
import com.zyntasolutions.zyntapos.domain.usecase.auth.LoginUseCase
import com.zyntasolutions.zyntapos.domain.usecase.auth.LogoutUseCase
import com.zyntasolutions.zyntapos.domain.usecase.auth.ValidatePinUseCase
import com.zyntasolutions.zyntapos.feature.auth.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Koin DI module for `:composeApp:feature:auth`.
 *
 * ## Provided bindings
 * | Type | Scope | Notes |
 * |------|-------|-------|
 * | [AuthViewModel] | ViewModel (scoped to nav back-stack entry) | Injected via `koinViewModel()` |
 * | [LoginUseCase] | Singleton | Delegates to AuthRepository (data layer) |
 * | [LogoutUseCase] | Singleton | Delegates to AuthRepository |
 * | [ValidatePinUseCase] | Singleton | Local PIN hash comparison |
 * | [CheckPermissionUseCase] | Singleton | Synchronous RBAC evaluator |
 * | [SessionManager] | Singleton | Idle-timeout timer; uses [SupervisorJob] scope |
 *
 * ## Module ordering
 * This module must be loaded **after** the data module (`dataModule`) because
 * [AuthRepository] is provided there. In the application-level `startKoin` block,
 * include `authModule` after `dataModule`.
 *
 * ## Usage
 * ```kotlin
 * // Application-level (androidApp / desktopApp)
 * startKoin {
 *     modules(coreModule, domainModule, dataModule, authModule, …)
 * }
 * ```
 */
val authModule = module {

    // ── Use Cases ─────────────────────────────────────────────────────────────

    /**
     * Authenticates email + password (online or offline-cached hash fallback).
     * Depends on [AuthRepository] provided by the data module.
     */
    single { LoginUseCase(authRepository = get()) }

    /**
     * Destroys the current session. Depends on [AuthRepository].
     */
    single { LogoutUseCase(authRepository = get()) }

    /**
     * Validates the user's 4–6 digit quick-switch PIN.
     * Depends on [AuthRepository].
     */
    single { ValidatePinUseCase(authRepository = get()) }

    /**
     * Synchronous RBAC evaluator.
     * Receives the session Flow from [AuthRepository.getSession] to keep its
     * internal user snapshot up-to-date.
     */
    single { CheckPermissionUseCase(sessionFlow = get<com.zyntasolutions.zyntapos.domain.repository.AuthRepository>().getSession()) }

    // ── Session Manager ───────────────────────────────────────────────────────

    /**
     * Application-lifecycle idle timer.
     *
     * Uses a [SupervisorJob] + [Dispatchers.Main] scope so the timer survives
     * individual ViewModel lifecycles but is scoped to the authenticated session.
     * The scope outlives individual screens but is reset on logout.
     */
    single {
        SessionManager(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            sessionTimeoutMs = SessionManager.DEFAULT_TIMEOUT_MS,
        )
    }

    // ── ViewModel ─────────────────────────────────────────────────────────────

    /**
     * ViewModel for the authentication flow.
     * Scoped to the auth nav graph back-stack entry via `koinViewModel()`.
     *
     * [viewModelOf] generates the ViewModel factory automatically from the
     * primary constructor, so all dependencies are resolved via Koin.
     */
    viewModelOf(::AuthViewModel)
}
