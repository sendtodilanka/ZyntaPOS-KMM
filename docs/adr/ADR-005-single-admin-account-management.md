# ADR-005: Single Admin Account Management

**Date:** 2026-03-01
**Status:** Accepted
**Deciders:** Senior KMP Architect (Dilanka)
**Closes:** TODO-001, TODO-002

---

## Context

`SignUpViewModel` hardcoded `Role.ADMIN` for every self-registration, and no guard existed
to prevent multiple ADMIN accounts from being created after onboarding. The onboarding wizard
(`OnboardingViewModel`) correctly created the first admin, but nothing stopped a second admin
from being created via the public SignUp screen or via Settings > User Management.

This created two security problems:

1. **Unlimited ADMIN creation via SignUp screen** — any person who reached the login screen
   could tap "Sign Up" and create a full admin account with unrestricted POS access.
2. **No system-owner identity** — there was no way to distinguish the original onboarding admin
   from any later admin accounts, making ownership transfer and privileged operations ambiguous.

---

## Decision

### 1. `isSystemAdmin` flag — no new OWNER role

Add `val isSystemAdmin: Boolean = false` to the `User` domain model. Exactly one user per
installation holds `isSystemAdmin = true` — the account created by the first-run onboarding
wizard. A dedicated `OWNER` role was rejected as unnecessary complexity.

**Why a flag instead of a role count check?**
- Future-proofing for ownership transfer (business sold, admin departing)
- Prevents edge cases where deleting/recreating a user bypasses a count-based guard
- Makes system-owner identity explicit and queryable at any point in the lifecycle

### 2. Limit ADMIN to exactly one account

`UserRepositoryImpl.create()` guards against a second ADMIN:

```kotlin
if (user.role == Role.ADMIN) {
    val existingAdmin = q.getSystemAdmin().executeAsOneOrNull()
    if (existingAdmin != null) {
        return@withContext Result.Error(
            ValidationException("Only one admin account is allowed", field = "role")
        )
    }
}
```

`SettingsViewModel.saveUser()` blocks ADMIN creation at the VM layer before any DB call:

```kotlin
if (!isUpdate && role == Role.ADMIN) {
    updateState { copy(users = users.copy(saveError = "Cannot create additional admin accounts")) }
    return@launch
}
```

### 3. Remove SignUp screen

ZyntaPOS is an enterprise POS — users do not self-register. The onboarding wizard creates
the one ADMIN; all subsequent staff accounts are created by the ADMIN via
Settings > User Management. The public SignUp screen was a security vulnerability with no
valid use case after onboarding.

**Deleted:**
- `composeApp/feature/auth/src/commonMain/.../screen/SignUpScreen.kt`
- `composeApp/feature/auth/src/commonMain/.../SignUpViewModel.kt`
- `composeApp/feature/auth/src/commonTest/.../SignUpViewModelTest.kt`

**Removed from:**
- `ZyntaRoute.kt` — `data object SignUp` route
- `AuthNavGraph.kt` — SignUp composable destination and `signUpScreen` parameter
- `ZyntaNavGraph.kt` — `signUpScreen` parameter
- `App.kt` — `signUpScreen` lambda
- `LoginScreen.kt` — "Don't have an account? Sign Up" button and `onNavigateToSignUp` callback
- `AuthModule.kt` — `viewModelOf(::SignUpViewModel)` Koin binding

### 4. Block ADMIN in Settings > User Management

The role dropdown in `UserManagementScreen` filters out `Role.ADMIN`:

```kotlin
Role.entries.filter { it != Role.ADMIN }.forEach { role -> ... }
```

Combined with the VM-layer and repository-layer guards, this creates a three-layer defence.

### 5. Ownership transfer support

Three new use cases were added to `:shared:domain`:

| Use Case | Purpose |
|----------|---------|
| `GetSystemAdminUseCase` | Returns the single `isSystemAdmin = true` user, or `null` |
| `TransferSystemAdminUseCase` | Atomically moves `isSystemAdmin` from one ADMIN to another (6 business-rule guards) |
| `EnsureSystemAdminGuardUseCase` | Reusable guard: call at the top of any privileged use case |

---

## Consequences

### Positive

- No second ADMIN account can be created through any path (UI, VM, or repository)
- System-owner identity is explicit, queryable, and transferable
- Login screen is simpler and no longer exposes a public signup path
- Onboarding remains the sole admin creation path — intent is clear

### Neutral

- DB migration `7.sqm` adds `is_system_admin INTEGER NOT NULL DEFAULT 0` and backfills the
  earliest active ADMIN as system admin for existing installations
- All repository fake implementations in tests required three new method stubs

### Negative / Trade-offs

- None identified. The old SignUp screen had no legitimate use case in the enterprise context.

---

## Files Modified

| File | Change |
|------|--------|
| `shared/domain/src/commonMain/.../model/User.kt` | Added `isSystemAdmin: Boolean = false` |
| `shared/domain/src/commonMain/.../repository/UserRepository.kt` | Added `getSystemAdmin()`, `adminExists()`, `transferSystemAdmin()` |
| `shared/domain/src/commonMain/.../usecase/user/GetSystemAdminUseCase.kt` | **Created** |
| `shared/domain/src/commonMain/.../usecase/user/TransferSystemAdminUseCase.kt` | **Created** |
| `shared/domain/src/commonMain/.../usecase/user/EnsureSystemAdminGuardUseCase.kt` | **Created** |
| `shared/domain/src/commonTest/.../fakes/FakeUserRepository.kt` | Added `getSystemAdmin()`, `adminExists()`, `transferSystemAdmin()` |
| `shared/data/src/commonMain/sqldelight/.../users.sq` | Added `is_system_admin` column; `getSystemAdmin`, `clearAllSystemAdmin`, `setSystemAdmin` queries; updated `insertUser` |
| `shared/data/src/commonMain/sqldelight/.../7.sqm` | **Created** — `ALTER TABLE` + backfill `UPDATE` |
| `shared/data/src/commonMain/.../repository/UserRepositoryImpl.kt` | ADMIN guard in `create()`; implemented `getSystemAdmin()`, `adminExists()`, `transferSystemAdmin()` |
| `shared/data/src/commonMain/.../mapper/UserMapper.kt` | `toDomain()` and `toInsertParams()` handle `isSystemAdmin` |
| `composeApp/feature/onboarding/.../OnboardingViewModel.kt` | `isSystemAdmin = true` on admin User creation |
| `composeApp/feature/settings/.../SettingsViewModel.kt` | ADMIN guard in `saveUser()` |
| `composeApp/feature/settings/.../screen/UserManagementScreen.kt` | `Role.entries.filter { it != Role.ADMIN }` in dropdown |
| `composeApp/feature/auth/.../screen/SignUpScreen.kt` | **Deleted** |
| `composeApp/feature/auth/.../SignUpViewModel.kt` | **Deleted** |
| `composeApp/navigation/.../ZyntaRoute.kt` | Removed `data object SignUp` |
| `composeApp/navigation/.../AuthNavGraph.kt` | Removed SignUp composable and `signUpScreen` param |
| `composeApp/navigation/.../ZyntaNavGraph.kt` | Removed `signUpScreen` param |
| `composeApp/src/commonMain/.../App.kt` | Removed `signUpScreen` lambda |
| `composeApp/feature/auth/.../screen/LoginScreen.kt` | Removed Sign Up button and `onNavigateToSignUp` |
| `composeApp/feature/auth/.../di/AuthModule.kt` | Removed `viewModelOf(::SignUpViewModel)` |
