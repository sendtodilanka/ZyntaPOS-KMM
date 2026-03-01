# TODO-001: Single Admin Account Management

**Status:** Pending
**Priority:** HIGH — Immediate security gap
**Phase:** Phase 1 (MVP)
**Created:** 2026-03-01

---

## Problem Statement

The current codebase has no guard preventing creation of multiple ADMIN accounts. `SignUpViewModel` hardcodes `Role.ADMIN` for every signup, and there is no mechanism to limit admin accounts to one. The onboarding wizard correctly creates the first admin, but nothing prevents additional admins from being created afterward.

---

## Decision

- **No OWNER role** — unnecessary complexity
- **Limit ADMIN to exactly one account** — the account created during onboarding
- **Remove SignUp screen** — onboarding handles admin creation; after that, admin creates other users via Settings > User Management
- **Use `isSystemAdmin` flag** — explicit marker on the onboarding-created admin user
- **Block ADMIN role in user creation** — the role dropdown in user management must exclude `Role.ADMIN`

---

## Implementation Plan

### 1. Add `isSystemAdmin` Flag to User Model

**File:** `shared/domain/src/commonMain/.../model/User.kt`

```kotlin
data class User(
    // ... existing fields
    val isSystemAdmin: Boolean = false,  // Only true for onboarding-created admin
)
```

**Why `isSystemAdmin` instead of checking role count:**
- Future-proofing for admin ownership transfer (e.g., business sold)
- Prevents edge cases where deleting/recreating users bypasses a count-based check
- Makes system owner identity explicit and queryable

### 2. Set Flag During Onboarding

**File:** `composeApp/feature/onboarding/src/commonMain/.../OnboardingViewModel.kt`

```kotlin
User(
    role = Role.ADMIN,
    isSystemAdmin = true,  // This user owns the system
    // ...
)
```

### 3. Block ADMIN Creation in User Management

**File:** `composeApp/feature/settings/src/commonMain/.../SettingsViewModel.kt`

```kotlin
// In SaveUser intent handler
if (formRole == Role.ADMIN) {
    sendEffect(SettingsEffect.ShowError("Cannot create additional admin accounts"))
    return
}
```

### 4. Hide ADMIN from Role Dropdown

**File:** `composeApp/feature/settings/src/commonMain/.../RbacManagementScreen.kt` (or equivalent user creation form)

```kotlin
Role.entries.filter { it != Role.ADMIN }
```

### 5. Remove SignUp Screen and Route

- Remove `SignUpViewModel` or repurpose it
- Remove `ZyntaRoute.SignUp` from `AuthNavGraph`
- Remove the "Sign Up" button/link from `LoginScreen`

### 6. Update SQLDelight Schema

**File:** `shared/data/src/commonMain/sqldelight/.../users.sq`

Add `is_system_admin` column (INTEGER 0/1, default 0) to the `users` table.

### 7. Add Guard in Repository Layer

**File:** `shared/data/src/commonMain/.../repository/UserRepositoryImpl.kt`

```kotlin
override suspend fun createUser(user: User): Result<User> {
    if (user.role == Role.ADMIN) {
        val existingAdmin = queries.getSystemAdmin().executeAsOneOrNull()
        if (existingAdmin != null) {
            return Result.failure(ZyntaException.BusinessRule("Only one admin account is allowed"))
        }
    }
    // ... proceed with creation
}
```

---

## Validation Checklist

- [ ] Onboarding creates admin with `isSystemAdmin = true`
- [ ] User management form excludes ADMIN from role dropdown
- [ ] Repository layer rejects ADMIN creation if one already exists
- [ ] SignUp screen and route removed
- [ ] Login screen no longer shows "Sign Up" link
- [ ] SQLDelight schema includes `is_system_admin` column
- [ ] Existing tests updated
- [ ] New test: verify ADMIN creation blocked after onboarding
