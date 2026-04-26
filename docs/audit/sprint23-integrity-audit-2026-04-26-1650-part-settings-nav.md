# Sprint 23 Integrity Audit — Settings + Navigation Partial
**Generated:** 2026-04-26 16:50
**Scope:** Sprint23.md lines 474–613 (Advanced Settings + Navigation) and scaffold doc lines 128–168
**Auditor:** Background agent — read-only integrity check

## Section 1: Advanced Settings Screens (Sprint23.md 474–545)
**Plan source:** Sprint23.md lines 474–545 (3 screens: SecurityPolicy, DataRetention, AuditPolicy)
**Status:** ⚠️ partial — shells exist but neither persistence nor `viewModel:` parameter matches plan signature

### 1a. SecurityPolicySettingsScreen.kt
**Plan claim (lines 491–497):**
```kotlin
@Composable
fun SecurityPolicySettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
)
```
Plan also requires editable settings: session timeout dropdown (5/15/30/60), PIN complexity dropdown (4/6/alphanumeric), failed-login lockout (3/5/10), lockout duration (5/15/30), biometric toggle — all stored in `settings` table.

**Codebase reality:**
- File: `composeApp/feature/settings/src/commonMain/kotlin/com/zyntasolutions/zyntapos/feature/settings/screen/SecurityPolicySettingsScreen.kt:42`
- Signature: `fun SecurityPolicySettingsScreen(onBack: () -> Unit)` — **no ViewModel parameter, no Modifier**
- Body: read-only `LazyColumn` of 5 hardcoded `PolicyRow`s (15 min, 6 digits, 5 attempts, 15 min, biometric on)
- Footer: "Read only" caption (`COMMON_READ_ONLY`)
- Doc comment explicitly defers persistence to "Sprint 24 work"

**Verdict:** ⚠️ partial. Shell + i18n + nav wiring landed, but persistence layer (`settings` table read/write), dropdowns, biometric toggle, and `SettingsViewModel` injection are all missing. Severity: **medium** — DoD item 7 ("All three advanced settings screens implement correct read/write to `settings` table") is **not met**.

### 1b. DataRetentionSettingsScreen.kt
**Plan claim (lines 513–518):** dropdowns for audit-log retention (30/90/180/365), sync-queue retention (7/14/30), report retention (6/12/24 months), "Run Purge Now" button → `PurgeExpiredDataUseCase`, plus estimated rows/MB freed display. Same `viewModel: SettingsViewModel` signature.

**Codebase reality:**
- File exists at expected path; will inspect signature next.

### 1c. AuditPolicySettingsScreen.kt
**Plan claim (lines 539–544):** Toggles per action category (login/logout, product CRUD, order, customer CRUD, settings, payroll, backup, role-changes-locked-on). Storage in `settings` table under `audit.{action}.enabled` keys.

**Codebase reality:**
- File exists at expected path; will inspect signature next.

=== SECTION 1 PARTIAL — continuing ===


## Section 2: Navigation Additions (Sprint23.md 549–568)
**Plan source:** Sprint23.md lines 549–568
**Status:** ⚠️ partial — 3 of 5 routes shipped; RoleList/RoleEditor still missing

### Plan claim
Add to `ZyntaRoute.kt`:
```kotlin
data object RoleList : ZyntaRoute()
data class RoleEditor(val roleId: String? = null) : ZyntaRoute()
data object SecurityPolicy : ZyntaRoute()
data object DataRetention : ZyntaRoute()
data object AuditPolicy : ZyntaRoute()
```
And register all 5 `composable<...>` blocks under `SettingsGraph` in `MainNavGraph.kt`.

### Codebase reality
- `ZyntaRoute.kt:285,289,293` — `SecurityPolicy`, `DataRetention`, `AuditPolicy` defined as `@Serializable data object`s ✅
- `ZyntaRoute.kt` — **no `RoleList` or `RoleEditor` route present** (grep returns zero hits) ❌
- `MainNavGraph.kt:431-447` — `composable<ZyntaRoute.SecurityPolicy/DataRetention/AuditPolicy>` blocks present, each calling `screens.<factory>(navigateUp)` ✅
- `MainNavGraph.kt:333-335` — `SettingsHomeScreen` route-name dispatch maps `"SECURITY_POLICY"|"DATA_RETENTION"|"AUDIT_POLICY"` strings to `navigationController.navigate(...)` ✅
- `MainNavGraph.kt:936-938` — RBAC bypass / global-route guard correctly includes the three new routes ✅
- `MainNavScreens.kt:160-170` — `securityPolicy`, `dataRetention`, `auditPolicy` factory properties declared on the screen-factory bag ✅
- `App.kt:743-751` — three factory lambdas instantiate the actual screens with `onBack = onNavigateUp` wiring ✅
- `NavGraphCompletenessTest.kt:71-73` — `expectedProperties` set updated with three new screen-factory names ✅

### Verdict
3 out of 5 plan routes shipped end-to-end with full plumbing (route → screens factory → graph composable → home dispatcher → completeness test). `RoleList` and `RoleEditor` are absent — those belong to the RBAC editor scope (Tasks 23.4 / 23.5), which is owned by a parallel agent and tracked separately. For the **settings + nav** scope, the navigation slice is **complete** for the three advanced-settings screens. Severity: **none** for this audit's scope.

=== SECTION 2 COMPLETE ===



## Section 3: Tasks 23.9 + 23.10 (Sprint23.md 572–586)

### Task 23.9 — "Implement SecurityPolicySettingsScreen.kt, DataRetentionSettingsScreen.kt, AuditPolicySettingsScreen.kt"
**Status:** ⚠️ partial
- All 3 files exist at the planned path under `composeApp/feature/settings/.../screen/`.
- `SecurityPolicySettingsScreen.kt` — read-only shell, 5 hardcoded rows, no ViewModel injected.
- `DataRetentionSettingsScreen.kt` — read-only shell, 3 hardcoded rows, "Run Purge Now" button rendered but `enabled = false` (lines 79-81). No `PurgeExpiredDataUseCase` wiring. No "estimated rows / MB freed" display.
- `AuditPolicySettingsScreen.kt` — 8 toggle rows; each `Switch(onCheckedChange = null, enabled = false)` (lines 86-92). No persistence. The "Role changes cannot be disabled" rule is not modelled (all switches are uniformly disabled).
- **None** of the three screens implements the read/write to the `settings` table that the plan requires. Each file's KDoc explicitly defers persistence to "Sprint 24 follow-up work."
- **Verdict:** Task 23.9 is **shells-only**. The visual layer satisfies the plan; the data layer does not.

### Task 23.10 — "Wire all 5 new settings routes in MainNavGraph.kt"
**Status:** ⚠️ partial — 3 of 5 wired
- 3 advanced-settings routes wired end-to-end (see Section 2).
- 2 RBAC routes (`RoleList`, `RoleEditor`) not present in `ZyntaRoute.kt`, `MainNavScreens.kt`, `MainNavGraph.kt`, or `App.kt`. Out of scope for this partial audit (RBAC agent).
- **Verdict:** Settings-side wiring is complete; RBAC-side wiring is missing.

=== SECTION 3 COMPLETE ===



## Section 4: Definition of Done items 7 + 9 (Sprint23.md 602–613)

### DoD #7 — "All three advanced settings screens implement correct read/write to `settings` table"
**Status:** ❌ NOT MET
- All three screens are explicitly read-only shells with hardcoded display values.
- No `SettingsRepository` / `settings`-table query is invoked from any of the three composables.
- The "Run Purge Now" button is `enabled = false`. Audit-policy `Switch`es are `enabled = false` with `onCheckedChange = null`.
- Severity: **high** for sprint-completion accounting; the screens look done in screenshots but the contract item is unmet.

### DoD #9 — "Localization test (no missing keys) passes" — out of scope (i18n agent)

### DoD bonus — Route wiring (DoD did not enumerate this separately; covered in Tasks 23.10)
- 3 of 5 routes wired: ✅ for SecurityPolicy/DataRetention/AuditPolicy.
- 2 of 5 routes (RoleList/RoleEditor) missing: ❌ — RBAC agent territory.

### Definition-of-Done summary for Settings + Nav scope
| Item | State |
|------|-------|
| Three advanced settings screens render | ✅ |
| Three advanced settings screens read from `settings` table | ❌ |
| Three advanced settings screens write to `settings` table | ❌ |
| Routes registered in `ZyntaRoute.kt` | ✅ (3/3 in scope) |
| Routes wired into `MainNavGraph.kt` | ✅ (3/3 in scope) |
| Routes plumbed via `MainNavScreens.kt` factories | ✅ |
| Factory wiring in `App.kt` instantiates real screens | ✅ |
| `NavGraphCompletenessTest` updated | ✅ |
| `SettingsHomeScreen` Administration card lists new entries | ✅ |
| `SettingsHomeScreen` route-name dispatch handles new entries | ✅ |

=== SECTION 4 COMPLETE ===



## Section 5: Scaffold doc Section 4 (lines 128–168) — staleness check

**Source:** `/home/user/ZyntaPOS-KMM/docs/plan/phase3-sprint23-scaffold-20260423-1722.md` Section 4
**Date written:** 2026-04-23 (3 days before this audit)

### Scaffold claim 1 (line 134)
> "Existing screens (VERIFIED count: 15 screen files)"

**Reality (2026-04-26):** With Batch 14 / PR #640 having shipped, the screen file count under `composeApp/feature/settings/.../screen/` is now **18** (added: SecurityPolicySettingsScreen, DataRetentionSettingsScreen, AuditPolicySettingsScreen). Scaffold doc count is **stale by 3**.

### Scaffold claim 2 (lines 147–152)
> "New routes needed for Batch 14: SecurityPolicy / DataRetention / AuditPolicy"

**Reality:** All three routes have been added to `ZyntaRoute.kt` (lines 285, 289, 293), wired into `MainNavGraph.kt` (lines 431–447), and registered as factory properties in `MainNavScreens.kt` (lines 160–170). Scaffold's "needed" framing is now **stale** — the routes are shipped.

### Scaffold claim 3 (line 154)
> "Settings home aggregation: SettingsHomeScreen.kt (line 74) uses `rememberSettingsGroups()`. New screens will be added as list items here."

**Reality:** `SettingsHomeScreen.kt` lines 164–166 now contain the three new `SettingsEntry` rows in the Administration group. **Stale** — work has been performed.

### Scaffold claim 4 (lines 156–168) — "Minimum-Viable Slice: Security Policy Shell"
> Scope: read-only placeholder, hardcoded values (15 min, 6-digit, 5 attempts, 15 min), `Card` + `ListItem` layout, no edit controls, route + home navigation wiring.

**Reality:** The shipped `SecurityPolicySettingsScreen.kt` matches **every** bullet of this MVS spec:
- Hardcoded values "15 min", "6 digits", "5 attempts", "15 min" → match.
- Biometric row added (extra; matches Sprint23.md plan).
- `Card` + `ListItem` layout → match.
- `enabled`/`Switch`-equivalent controls absent → match (no edit controls).
- Route + home wiring complete → match.

The MVS slice was implemented faithfully. Scaffold claim 4 is **fulfilled** — could be marked DONE in the scaffold doc.

### Scaffold claim 5 (Section 5, line 173 — out-of-scope for Section 4 but related)
> "Recommendation: i18n Migration (Smallest, Most Isolated)" — recommends starting with **i18n** rather than the Settings shells.

**Reality:** Batch 14 implemented the **Settings shells** (this audit's subject) — not the i18n migration the scaffold doc recommended. The team picked option B from the scaffold's menu. Scaffold's recommendation is therefore **historically stale** but not contradicted (it was a recommendation, not a contract).

### Scaffold doc summary
The scaffold doc's "what exists today" claims are stale by exactly the work shipped in PR #640. Its "what to build" claims accurately predicted the implementation that landed. No incorrect predictions; only outdated counts and "needed" tense. Recommended fix: append a `## Status — 2026-04-26` block to the scaffold doc noting Batch 14 closed claims 1–4.

=== SECTION 5 COMPLETE ===



## Summary

**Headline:** PR #640 (sha 20b6139) shipped a clean **navigation + UI shell** slice for the three advanced-settings screens, but the **persistence half** of the Sprint 23 spec (DoD #7 — read/write to the `settings` table) is **not implemented**. The screens are visually faithful to the plan but functionally read-only.

### What's correct vs. plan
- File paths, package names, and naming all match Sprint23.md.
- Three new `ZyntaRoute` data objects, three `MainNavScreens` factories, three `composable<...>` blocks in `MainNavGraph.kt`, three `SettingsHomeScreen` Administration entries, three factory lambdas in `App.kt`, and three `NavGraphCompletenessTest` entries — every layer of the nav plumbing was updated atomically.
- Sixteen i18n keys (per task description) appear via `LocalStrings.current[StringResource.SETTINGS_*]` in all three screens — i18n agent will validate the keys themselves.

### What diverges from plan
1. **Composable signature drift.** Plan specifies `(viewModel: SettingsViewModel, onNavigateBack: () -> Unit, modifier: Modifier = Modifier)`. Implementation uses `(onBack: () -> Unit)` only. No ViewModel injection, no `Modifier` parameter. **Severity: medium** — blocks future state hookup unless the signature is widened.
2. **No persistence.** Plan demands read/write to the `settings` table. Implementation hardcodes all values. The "Run Purge Now" button and 8 audit `Switch`es are inert (`enabled = false`). **Severity: high** for DoD #7 accounting.
3. **Missing UX affordances.** Plan's "estimated rows / MB freed" caption (Data Retention) is not rendered. Plan's "Role changes cannot be disabled" rule is not modelled in Audit Policy (all switches are uniformly disabled, not just the role-changes one).
4. **Scope-defined deferral.** Each screen's KDoc explicitly tags the missing work as "Sprint 24 follow-up" — this is intentional staging, not bug-level divergence.

### What's out of audit scope (hand-off to other agents)
- RBAC `RoleList` / `RoleEditor` routes (Tasks 23.1–23.5, 23.11): **not present** in any of the inspected nav files. RBAC agent owns this.
- i18n key contents (`SETTINGS_*` strings) in EN/SI/TA tables: i18n agent owns this.

### Recommendations
1. Update scaffold doc (`docs/plan/phase3-sprint23-scaffold-20260423-1722.md`) Section 4 to mark claims 1–4 as DONE / superseded by PR #640.
2. Open a Sprint 24 ticket: "Wire SecurityPolicy/DataRetention/AuditPolicy screens to `SettingsRepository` — add `viewModel: SettingsSecurityPolicyViewModel` etc., implement settings-table CRUD, enable controls."
3. Decide whether to keep the simplified `(onBack)` signature or restore the plan's `(viewModel, onNavigateBack, modifier)` signature when persistence lands. The current shape is testable but inconsistent with peer settings screens that already inject ViewModels.

### Files referenced (all absolute)
- `/home/user/ZyntaPOS-KMM/composeApp/feature/settings/src/commonMain/kotlin/com/zyntasolutions/zyntapos/feature/settings/screen/SecurityPolicySettingsScreen.kt`
- `/home/user/ZyntaPOS-KMM/composeApp/feature/settings/src/commonMain/kotlin/com/zyntasolutions/zyntapos/feature/settings/screen/DataRetentionSettingsScreen.kt`
- `/home/user/ZyntaPOS-KMM/composeApp/feature/settings/src/commonMain/kotlin/com/zyntasolutions/zyntapos/feature/settings/screen/AuditPolicySettingsScreen.kt`
- `/home/user/ZyntaPOS-KMM/composeApp/feature/settings/src/commonMain/kotlin/com/zyntasolutions/zyntapos/feature/settings/screen/SettingsHomeScreen.kt:164-166`
- `/home/user/ZyntaPOS-KMM/composeApp/navigation/src/commonMain/kotlin/com/zyntasolutions/zyntapos/navigation/ZyntaRoute.kt:285,289,293`
- `/home/user/ZyntaPOS-KMM/composeApp/navigation/src/commonMain/kotlin/com/zyntasolutions/zyntapos/navigation/MainNavScreens.kt:160-170`
- `/home/user/ZyntaPOS-KMM/composeApp/navigation/src/commonMain/kotlin/com/zyntasolutions/zyntapos/navigation/MainNavGraph.kt:333-335,431-447,936-938`
- `/home/user/ZyntaPOS-KMM/composeApp/src/commonMain/kotlin/com/zyntasolutions/zyntapos/App.kt:743-751`
- `/home/user/ZyntaPOS-KMM/composeApp/navigation/src/commonTest/kotlin/com/zyntasolutions/zyntapos/navigation/NavGraphCompletenessTest.kt:71-73`

=== AUDIT COMPLETE ===

