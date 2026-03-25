package com.zyntasolutions.zyntapos.feature.auth.mvi

/**
 * One-shot side-effects emitted by [com.zyntasolutions.zyntapos.feature.auth.AuthViewModel].
 *
 * Unlike [AuthState], effects are consumed exactly once and are not retained across
 * recompositions. They are delivered via a `SharedFlow` with `replay = 0`.
 *
 * The UI layer observes these effects to perform navigation or show transient UI
 * (e.g., Snackbars) that cannot be modelled as persistent state.
 */
sealed class AuthEffect {

    /**
     * Login succeeded and the user should be taken to the main dashboard.
     * NavigationController.navigateAndClear(ZyntaRoute.Dashboard) should be called.
     */
    data object NavigateToDashboard : AuthEffect()

    /**
     * Login succeeded but no register session is open.
     * User must open the cash register before accessing the POS.
     * NavigationController.navigate(ZyntaRoute.OpenRegister) should be called.
     */
    data object NavigateToRegisterGuard : AuthEffect()

    /**
     * A transient error occurred that cannot be represented inline in the form.
     * Typically displayed via a Snackbar or an error banner above the form.
     *
     * @property message Human-readable error description.
     */
    data class ShowError(val message: String) : AuthEffect()

    /**
     * The session has timed out due to inactivity. The PIN lock screen must be shown.
     * Emitted by [com.zyntasolutions.zyntapos.feature.auth.session.SessionManager].
     */
    data object ShowPinLock : AuthEffect()

    /** PIN validated successfully — dismiss the PIN lock screen. */
    data object PinUnlocked : AuthEffect()

    /** Quick-switch succeeded — dismiss PIN lock + switch session. */
    data class QuickSwitchCompleted(val userName: String) : AuthEffect()

    /** Quick-switch or PIN lock failed — navigate to full login. */
    data object NavigateToLogin : AuthEffect()
}
