# TODO-002: Remove SignUp Screen & Clarify Account Creation Flow

**Status:** Completed
**Completed:** 2026-03-01
**ADR:** ADR-005
**Priority:** HIGH — Security gap; must be resolved before production
**Phase:** Phase 1 (MVP)
**Created:** 2026-03-01
**Related:** TODO-001 (Single Admin Account Management)

---

## Problem Statement

The current `SignUpScreen` is a **standalone public signup form** that creates users with `Role.ADMIN` hardcoded. This means anyone who reaches the signup screen can create unlimited admin accounts — a critical security vulnerability.

### Why SignUp Screen Must Be Removed

1. **Onboarding handles the first admin** — The onboarding wizard (`OnboardingViewModel`) already creates the one-and-only ADMIN account during first-run setup
2. **No public account creation** — ZyntaPOS is an enterprise app, not a consumer SaaS. Users do not self-register. The ADMIN creates accounts for their staff.
3. **Security hole** — `SignUpViewModel` hardcodes `Role.ADMIN` for every account, meaning every signup creates a full admin with unrestricted access
4. **Redundant with User Management** — Settings > User Management already provides the UI for the ADMIN to create STORE_MANAGER, CASHIER, ACCOUNTANT, STOCK_MANAGER, and other roles

### Current Account Flow (Incorrect)

```
App Launch
  -> Onboarding (first run) -> Creates ADMIN
  -> Login Screen -> "Don't have an account? Sign Up" link
  -> SignUp Screen -> Creates ANOTHER ADMIN (BUG!)
```

### Correct Account Flow (After Fix)

```
App Launch
  -> Onboarding (first run only) -> Creates single ADMIN (isSystemAdmin = true)
  -> Login Screen (no signup link)
  -> After ADMIN logs in:
     -> Settings > User Management > Create User
     -> Role dropdown: STORE_MANAGER, CASHIER, ACCOUNTANT, STOCK_MANAGER, CUSTOMER_SERVICE, REPORTER
     -> ADMIN role excluded from dropdown (enforced in UI and repository layer)
```

---

## What Must Change

### 1. Remove SignUp Route from Auth Navigation

**File:** `composeApp/navigation/src/commonMain/.../ZyntaRoute.kt`

Remove or deprecate:
```kotlin
data object SignUp : ZyntaRoute()  // DELETE
```

### 2. Remove SignUp from Auth Nav Graph

**File:** `composeApp/navigation/src/commonMain/.../AuthNavGraph.kt` (or equivalent)

Remove the `composable<ZyntaRoute.SignUp>` block.

### 3. Remove "Sign Up" Link from Login Screen

**File:** `composeApp/feature/auth/src/commonMain/.../screen/LoginScreen.kt`

Remove the "Don't have an account? Sign Up" `TextButton` and its `onNavigateToSignUp` callback.

### 4. Remove SignUp Screen & ViewModel (or repurpose)

**Files to remove or archive:**
- `composeApp/feature/auth/src/commonMain/.../screen/SignUpScreen.kt` (394 lines)
- `composeApp/feature/auth/src/commonMain/.../SignUpViewModel.kt`
- `composeApp/feature/auth/src/commonMain/.../SignUpState.kt` (if separate)
- `composeApp/feature/auth/src/commonTest/.../SignUpViewModelTest.kt`

**Note:** Do NOT delete these files immediately if they contain reusable form components. The form UI (name, email, password fields with validation) can be repurposed for the **User Creation form** in Settings > User Management. Evaluate before deleting.

### 5. Remove from Koin Module

**File:** `composeApp/feature/auth/src/commonMain/.../di/AuthModule.kt`

Remove `SignUpViewModel` registration.

### 6. Remove from App.kt Screen Factory

**File:** `composeApp/src/commonMain/.../App.kt`

Remove the `signUp` lambda from `MainNavScreens` (or `AuthNavScreens`).

### 7. Update LoginScreen Wiring

Remove `onNavigateToSignUp` parameter from `LoginScreen` composable and all call sites.

---

## User Creation via Settings (Already Exists)

The ADMIN creates new user accounts through:

**Settings > User Management** (`composeApp/feature/settings/`)

This screen already provides:
- User list with roles displayed
- Create/edit user form
- Role assignment dropdown
- Password/PIN setup

### Required Enhancement (See TODO-001)

The role dropdown in User Management must **exclude `Role.ADMIN`** to prevent creating additional admin accounts:

```kotlin
// In the user creation form
val availableRoles = Role.entries.filter { it != Role.ADMIN }
```

The repository layer must also enforce this as a second guard:

```kotlin
// In UserRepositoryImpl
if (user.role == Role.ADMIN) {
    return Result.failure(ZyntaException.BusinessRule("Cannot create additional admin accounts"))
}
```

---

## Account Lifecycle Summary

| Action | Who | Where | When |
|--------|-----|-------|------|
| Create ADMIN | System | Onboarding wizard | First app launch only |
| Create staff accounts | ADMIN | Settings > User Management | After ADMIN login |
| Edit user accounts | ADMIN | Settings > User Management | Anytime |
| Deactivate accounts | ADMIN | Settings > User Management | Anytime |
| Self-service password reset | Any user | Login screen (future) | Anytime |
| Self-service PIN change | Any user | Profile settings (future) | When logged in |

---

## Validation Checklist

- [x] `ZyntaRoute.SignUp` removed from route definitions
- [x] SignUp composable removed from auth nav graph
- [x] "Sign Up" link removed from LoginScreen
- [x] `SignUpViewModel` removed from Koin
- [x] `SignUpScreen.kt` removed (or archived for form reuse)
- [x] `SignUpViewModelTest.kt` removed
- [x] App.kt no longer wires SignUp screen
- [x] LoginScreen no longer accepts `onNavigateToSignUp` callback
- [x] User Management role dropdown excludes ADMIN
- [x] Repository layer blocks ADMIN creation
- [x] App compiles and runs without errors
- [x] Login flow works correctly without signup option
- [x] Onboarding still creates ADMIN correctly
- [x] ADMIN can create staff accounts via User Management
