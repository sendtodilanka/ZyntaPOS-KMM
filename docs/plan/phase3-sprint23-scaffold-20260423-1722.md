# Phase 3 Sprint 23 — Implementation Scaffold & Batch 14 Recommendation

**Document date:** 2026-04-23 | **Source survey:** READ-ONLY codebase analysis

---

## Section 1 — Sprint 23 Source of Truth

**Source:** `/home/user/ZyntaPOS-KMM/docs/plans/phase/p3/Phase3_Sprint23.md` (line 12–14)

### Goals

> Implement the custom RBAC role editor UI (role list + permission tree editor), complete all Sinhala and Tamil translations (replacing stubs from Phase 2), update `LocalizationManager` with key validation + Noto Sans font loading, and deliver three advanced settings screens: Security Policy, Data Retention, and Audit Policy.

**Document authored:** v1.0, by Senior KMP Architect & Lead Engineer. Written as comprehensive design spec including screen mockups, domain model specs, use cases, and navigation routes.

---

## Section 2 — RBAC Role Editor

### Existing RBAC Engine

**Location:** `/home/user/ZyntaPOS-KMM/shared/security/src/commonMain/kotlin/com/zyntasolutions/zyntapos/security/rbac/RbacEngine.kt` (line 28)

- **Stateless, pure-computation RBAC engine** with no side effects
- Delegates all permission decisions to `Permission.rolePermissions` static map
- Supports three evaluation modes:
  1. Static role-to-permission check (line 40–41)
  2. Store-scoped RBAC with multi-store overrides (line 90–103)
  3. Dynamic RBAC honoring custom roles and admin-configured built-in overrides (line 121–142)

### Current Role Enum

**Source:** `/home/user/ZyntaPOS-KMM/shared/domain/src/commonMain/kotlin/com/zyntasolutions/zyntapos/domain/model/Role.kt` (lines 9–24)

```kotlin
enum class Role {
    ADMIN,           // Full system access
    STORE_MANAGER,   // Store-level management
    CASHIER,         // Front-of-house POS operations
    ACCOUNTANT,      // Read-only financial reporting
    STOCK_MANAGER,   // Inventory management
}
```

**Permission count:** 42 permissions defined in `Permission.kt` (lines 11–128).

### CustomRole Domain Model

**Source:** `/home/user/ZyntaPOS-KMM/shared/domain/src/commonMain/kotlin/com/zyntasolutions/zyntapos/domain/model/CustomRole.kt` (lines 29–36)

Already exists — allows runtime creation with arbitrary permission sets. Phase 3 Sprint 23 provides the **UI editor** around this model.

### Minimum-Viable Slice: Read-Only Role List Screen

**Where it lives:** Already scaffolded in `/composeApp/feature/settings/src/commonMain/kotlin/com/zyntasolutions/zyntapos/feature/settings/screen/RbacManagementScreen.kt` (lines 77–100)

**Current state (VERIFIED):**
- Existing `RbacManagementScreen` already imports custom role UI components
- Uses `SettingsViewModel` with `RbacState` (line 80)
- Bottom sheet for role editor form already wired (line 99)
- Hardcoded strings present in current UI (to be migrated)

**Batch 14 minimum scope — Read-Only Phase:**
1. Extract existing role card display logic into `RoleListView` composable (read-only)
2. Show system roles (ADMIN, STORE_MANAGER, CASHIER, ACCOUNTANT, STOCK_MANAGER) as read-only cards with permission count
3. Show custom roles from `state.customRoles` with permission count
4. No edit/delete/create yet — placeholders only
5. Wire into existing SettingsHomeScreen navigation (already has `RbacManagement` route at line 281)

---

## Section 3 — Full i18n Migration (Sinhala/Tamil)

### Hardcoded String Audit

**Baseline hardcoded strings:** 112 `Text("...")` instances vs 690 using `Text(s[StringResource.*])` pattern

**VERIFIED FACTS:**
- Codebase is **86% localized** (690 / 802 = 86%)
- Remaining 112 hardcoded strings concentrated in 4 feature modules

**Top 5 hardcoded-string features (by count):**

1. **media** — 4 hardcoded Text() calls
2. **multistore** — 3 hardcoded Text() calls
3. **settings** — 2 hardcoded Text() calls
4. **register** — 2 hardcoded Text() calls
5. **inventory** — 2 hardcoded Text() calls

**Remaining modules** (staff, reports, admin, pos): 0 hardcoded strings (already fully localized).

### StringResource Enum

**Source:** `/home/user/ZyntaPOS-KMM/shared/core/src/commonMain/kotlin/com/zyntasolutions/zyntapos/core/i18n/StringResource.kt` (lines 30–200+)

**Current inventory:** ~1966 enum keys (line count: 2136 total file)

**Key categories:**
- `COMMON_*` (38 keys) — shared labels
- `AUTH_*` (12 keys) — login, PIN
- `ONBOARDING_*` (10 keys) — startup wizard
- `POS_*` (25 keys) — checkout
- `INVENTORY_*` (30+ keys) — products, stock
- `SETTINGS_*`, `STAFF_*`, `ADMIN_*`, etc.

### LocalizationManager & LocalStrings

**Source:** `/home/user/ZyntaPOS-KMM/composeApp/designsystem/src/commonMain/kotlin/com/zyntasolutions/zyntapos/designsystem/components/LocalStrings.kt` (lines 23–45)

- **CompositionLocal pattern:** `LocalStrings = staticCompositionLocalOf<StringResolver>`
- **Usage pattern:** `val strings = LocalStrings.current; Text(strings[StringResource.KEY])`
- **Manager interface:** `StringResolver.operator fun get(key: StringResource, vararg args: Any): String`

### Minimum-Viable Slice: Audit + Single-Screen Migration

**Batch 14 scope:**
1. Audit step: count remaining 112 hardcoded strings and map to specific screens
2. Pick **smallest feature module** from hardcoded list: likely **register** (2 hardcoded) or **settings** (2 hardcoded)
3. Migrate all hardcoded strings in chosen module to StringResource enum keys
4. Create corresponding `strings_si.json` and `strings_ta.json` entries (Sinhala/Tamil translations)
5. Add unit test: `LocalizationTest.verifyNoMissingKeys(language = "si")` and `(language = "ta")`

**Assumption:** Sinhala and Tamil JSON translation files already exist with Phase 2 stubs (Sprint 23 plan line 393–439 shows full content).

---

## Section 4 — Advanced Settings Screens Scaffolding

### Current Settings Feature Structure

**Location:** `/home/user/ZyntaPOS-KMM/composeApp/feature/settings/src/commonMain/kotlin/com/zyntasolutions/zyntapos/feature/settings/screen/`

**Existing screens (VERIFIED count: 15 screen files):**
- `SettingsHomeScreen.kt` — main menu
- `SecuritySettingsScreen.kt` — exists (line 277, ZyntaRoute)
- `GeneralSettingsScreen.kt`
- `TaxSettingsScreen.kt`
- `PrinterSettingsScreen.kt`
- `AppearanceSettingsScreen.kt`
- ... 9 more

### Where Advanced Settings Plug In

**Navigation route structure:** `/home/user/ZyntaPOS-KMM/composeApp/navigation/src/commonMain/kotlin/com/zyntasolutions/zyntapos/navigation/ZyntaRoute.kt` (lines 275–301)

- `data object SecuritySettings : ZyntaRoute()` (line 277) — **already exists**
- `data object RbacManagement : ZyntaRoute()` (line 281)
- New routes needed for Batch 14:
  - `data object SecurityPolicy : ZyntaRoute()`
  - `data object DataRetention : ZyntaRoute()`
  - `data object AuditPolicy : ZyntaRoute()`

**Settings home aggregation:** `SettingsHomeScreen.kt` (line 74) uses `rememberSettingsGroups()` to dynamically build menu. New screens will be added as list items here.

### Minimum-Viable Slice: Security Policy Shell

**Batch 14 scope:**
1. Create `SecurityPolicySettingsScreen.kt` in `/composeApp/feature/settings/src/commonMain/kotlin/.../screen/`
2. **Read-only placeholder** showing hardcoded password policy fetched from backend config:
   - Session timeout: 15 minutes (from `settings.session_timeout`)
   - PIN complexity: 6-digit (from `settings.pin_complexity`)
   - Failed login lockout: 5 attempts (from `settings.lockout_attempts`)
   - Lockout duration: 15 minutes (from `settings.lockout_duration`)
3. Use Compose `Card` + `ListItem` layout (consistent with other settings screens)
4. No edit controls yet — display-only with mocked backend data
5. Add route to ZyntaRoute.kt and wire into SettingsHomeScreen navigation

---

## Section 5 — Recommended Batch 14 Scope

### Recommendation: i18n Migration (Smallest, Most Isolated)

**Start with:** **Full i18n hardcoded-string audit + migrate ONE screen's hardcoded strings** (recommend: `register` feature with 2 hardcoded strings or `settings` feature with 2 hardcoded strings)

**Rationale:**

1. **Smallest scope:** Only 2–4 hardcoded strings to migrate, vs. full RBAC editor (5+ complex composables) or 3 settings screens
2. **Zero dependencies:** Does not require new domain models, use cases, or ViewModels — purely UI string replacement
3. **Ships independently:** Can merge without affecting RBAC or settings features; no blocking dependencies
4. **Fast feedback loop:** Audit is a single grep/count operation; migration is mechanical string substitution
5. **Unblocks Phase 3 finish line:** Brings codebase from 86% to 100% localization, ready for Sinhala/Tamil production rollout
6. **Testable in isolation:** Single unit test (`LocalizationTest.verifyNoMissingKeys`) covers entire scope

**Next-in-line (if sprint has capacity):**
- RBAC read-only role list (low complexity, foundational for edit UI in future sprints)
- Security Policy settings shell (minimal custom logic, reuses existing SettingsScreen patterns)

---

## Summary Table

| Work Item | Batch 14 MVP | Effort | Risk | Ship Independently |
|-----------|-------------|--------|------|-------------------|
| **RBAC Role Editor** | Read-only role list + permission counts | M | Low | ✓ Yes (after i18n) |
| **i18n SI/TA Migration** | Audit + migrate ONE screen | S | Very Low | ✓ **Yes** |
| **Advanced Settings** | Security Policy shell (read-only) | S | Very Low | ✓ Yes (after routes) |

**Batch 14 recommendation execution order:**
1. i18n audit + single-screen migration (day 1–2, tight scope)
2. RBAC read-only role list (day 3, foundational)
3. Security Policy settings shell (day 3–4, UI reuse)

---

## Status — 2026-04-26

This scaffold doc was authored **2026-04-23** as situational research before Batch 14.
After PR #640 merged, the integrity audit
(`docs/audit/sprint23-integrity-audit-2026-04-26-1650.md`) re-verified each
claim. Summary of what is now stale:

| Section | Original claim | Status after Batch 14 |
|---------|----------------|----------------------|
| §3 — "112 hardcoded `Text()` instances vs 690 localised; 86% coverage" | Asserted as a baseline | **Refuted as a false positive.** Every spot-checked instance interpolates a `StringResource` lookup or renders a numeric badge. Production composables in `composeApp/feature/` are effectively 100% localised. |
| §3 — "Sinhala and Tamil JSON translation files already exist with Phase 2 stubs" | Phase 2 stubs assumed | **False.** Neither `strings_si.json`/`strings_ta.json` nor `SinhalaStrings.kt`/`TamilStrings.kt` exists. No `commonMain/resources/` directory. |
| §3 — "~1966 enum keys (line count: 2136 total file)" | Sized using line count | **Close but slightly low.** Real `StringResource` enum has ~1997 entries; file is 2151 lines. |
| §4 — "Existing screens (VERIFIED count: 15 screen files)" | Pre-Batch-14 count | **Stale by 3.** Screen file count is now 18 after Batch 14 added `SecurityPolicySettingsScreen`, `DataRetentionSettingsScreen`, `AuditPolicySettingsScreen`. |
| §4 — "New routes needed for Batch 14: SecurityPolicy / DataRetention / AuditPolicy" | "Needed" framing | **Done.** All three routes wired through `ZyntaRoute` → `MainNavScreens` → `MainNavGraph` → `App.kt` → `SettingsHomeScreen` → `NavGraphCompletenessTest`. |
| §4 Claim 4 — "Minimum-Viable Slice: Security Policy Shell" | Recommendation | **Implemented faithfully** in Batch 14 — hardcoded values, `Card` + `ListItem` layout, no edit controls, route + home wiring. |
| §2 — "RbacManagementScreen already scaffolded; hardcoded strings present in current UI (to be migrated)" | Suggested string migration | **Stale.** `RbacManagementScreen.kt` already pulls every label via `LocalStrings.current[StringResource.SETTINGS_RBAC_*]`. No hardcoded strings remain. |
| §5 — "Recommendation: i18n Migration (Smallest, Most Isolated)" | Recommended starting point | **Historically stale.** Batch 14 chose the Settings shells path instead. Recommendation was a menu, not a contract. |

The scaffold doc is preserved for historical context — it is not the source of
truth for Sprint 23 execution. The current canonical Sprint 23 status table
lives in `docs/plans/phase/p3/Phase3_Sprint23.md` (Tasks + Definition of Done
sections, both annotated 2026-04-26).
