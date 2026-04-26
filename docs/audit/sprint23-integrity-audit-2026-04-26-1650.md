---
title: Sprint 23 Plan ↔ Codebase Integrity Audit
generated: 2026-04-26 16:50
sources:
  - docs/plans/phase/p3/Phase3_Sprint23.md (613 lines, original spec)
  - docs/plan/phase3-sprint23-scaffold-20260423-1722.md (205 lines, situational scaffold)
audit-method: 3 parallel read-only agents (Settings/Nav, RBAC, i18n) + manual merge
---

# Sprint 23 — Plan ↔ Codebase Integrity Audit

## Executive Summary

Both Sprint 23 plan documents materially diverge from the live `main` branch. The most consequential gaps:

1. **The original spec (`Phase3_Sprint23.md`) references a fictional RBAC taxonomy.** It cites roles (`MANAGER`, `CUSTOMER_SERVICE`, `REPORTER`) and ~18 permission constants (`POS_ACCESS`, `VIEW_ADMIN_PANEL`, `MANAGE_BACKUPS`, `MANAGE_EINVOICE`, `MANAGE_MULTI_STORE`, …) **none of which exist** in the canonical `Role.kt` or `Permission.kt`. Implementing tasks 23.4 / 23.5 verbatim would fail to compile.
2. **The original spec's i18n architecture is wrong for this codebase.** It prescribes JSON resource files (`strings_si.json`, `strings_ta.json`); the project actually uses `Map<StringResource, String>` Kotlin tables (`EnglishStrings.kt`). Adopting the plan as written would require a runtime loader refactor that the plan does not acknowledge.
3. **The original spec undercounts the translation surface by ~2.5×.** Plan says "800+ keys"; real `StringResource` enum has **~1997 entries**.
4. **The scaffold doc's "112 hardcoded `Text()` instances" claim is a false positive.** Every spot-check resolves to either an interpolation of a localized label or a pure-numeric badge. Production composables are effectively **100% localized**.
5. **Batch 14 (PR #640) shipped only the UI shell of the three advanced-settings screens — not the persistence half.** DoD #7 (read/write to `settings` table) is **not met**.
6. **Scaffold doc is 3 days out of date.** Its "needed routes" and "15 screen files" claims have been superseded by Batch 14 but not annotated in the doc.

Net Sprint 23 progress, weighted by DoD: **~22%** (UI scaffold for advanced settings landed cleanly; RBAC editor at ~40% domain layer + ~0% UI; i18n at ~0%).

## Status table — Sprint 23 task list

| # | Task | Owner agent | Status | Notes |
|---|------|-------------|--------|-------|
| 23.1 | `PermissionGroup.kt` + `PermissionItem.kt` domain models | RBAC | ✅ Done | Both in single file at `shared/domain/.../model/PermissionGroup.kt` (15 LOC); signatures match spec. |
| 23.2 | 5 use case interfaces under `:shared:domain/.../usecase/rbac/` | RBAC | ⚠️ Partial | All 5 files exist. `GetRolesUseCase` was renamed to `GetCustomRolesUseCase` (concrete `class`, returns `Flow<List<CustomRole>>`). `SaveCustomRoleUseCase` is a `class` (not `fun interface`) with extra `isUpdate: Boolean` param + `Result<Unit>` return. Plan's `Role`-typed signatures should be `CustomRole`. |
| 23.3 | Implement `GetPermissionsTreeUseCaseImpl` returning all permissions grouped by module | RBAC | ❌ Not done | Interface is a stub `fun interface`; **no `Impl` class anywhere**; not bound in `SettingsModule` Koin DI. `CloneRoleUseCase` impl also missing. |
| 23.4 | `RoleListScreen.kt` with system/custom role separation | RBAC | ❌ Not done | File does not exist. Functionality lives inside legacy `RbacManagementScreen.kt` (481 LOC) as a single screen with a bottom-sheet form, no dedicated split. |
| 23.5 | `RoleEditorScreen.kt` with `PermissionGroupHeader` (tri-state) and `PermissionRow` | RBAC | ❌ Not done | File does not exist. **Zero matches** for `TriStateCheckbox` / `ToggleableState` / `PermissionGroup` in the entire settings module. |
| 23.6 | Complete `strings_si.json` and `strings_ta.json` with all 800+ Phase 1–3 keys | i18n | ❌ Not done | **Both files absent.** No `commonMain/resources/` directory exists. Real key count is ~1997, not 800. Format mismatch with codebase architecture. |
| 23.7 | Add Noto Sans Sinhala + Tamil font files to `shared/core/.../resources/fonts/` | i18n | ❌ Not done | `find shared -name 'NotoSans*'` returns zero results. |
| 23.8 | Update `LocalizationManager` with `loadFontForLanguage()` + `validateKeys()` | i18n | ✅ Effectively done | `fontFamilyForLanguage(code: String): String?` and `validateKeys(locale: SupportedLocale): List<String>` already implemented; method names + signatures differ from plan. Work item should be downgraded to a rename/doc pass. |
| 23.9 | Implement `SecurityPolicySettingsScreen.kt`, `DataRetentionSettingsScreen.kt`, `AuditPolicySettingsScreen.kt` | Settings/Nav | ⚠️ Partial — shells only | All 3 files at planned paths, fully wired into nav layer. **No persistence**: hardcoded values, "Run Purge Now" `enabled = false`, all 8 audit `Switch`es `onCheckedChange = null, enabled = false`. KDocs explicitly defer writes to "Sprint 24". |
| 23.10 | Wire all 5 new settings routes in `MainNavGraph.kt` | Settings/Nav | ⚠️ 3/5 done | SecurityPolicy, DataRetention, AuditPolicy fully wired through `ZyntaRoute` → `MainNavScreens` → `MainNavGraph` → `App.kt` → `SettingsHomeScreen` → `NavGraphCompletenessTest`. **`RoleList` + `RoleEditor` routes do not exist.** |
| 23.11 | Write `RoleEditorViewModelTest` | RBAC | ❌ Not done | Editor screen + ViewModel don't exist; nothing to test. |
| 23.12 | Write `LocalizationTest` validating SI/TA keys vs EN baseline | i18n | ⚠️ Stub only | `LocalizationManagerTest.kt` exercises `missingKeys` + `registerStrings` with synthetic data. No real EN-vs-SI/TA completeness assertion (no SI/TA tables to compare against). |
| 23.13 | `./gradlew :shared:core:assemble && :composeApp:feature:settings:assemble` | All | ✅ Verified by CI | Branch Validate (Step 1) + Build Images (Step 4) green on PR #640 / sha 20b6139. |

## Definition-of-Done — checklist evaluation

| DoD item | State | Source |
|----------|-------|--------|
| `RoleListScreen` distinguishes system vs custom roles | ❌ | RBAC partial §1, §6 |
| `RoleEditorScreen` permission tree has tri-state module checkboxes | ❌ | RBAC partial §1, §6 |
| `SaveCustomRoleUseCase` persists to `roles` table | ⚠️ Use case exists with diverged signature; persistence path not validated end-to-end | RBAC partial §2 |
| `strings_si.json` / `strings_ta.json` zero missing keys vs `strings_en.json` | ❌ Both files absent | i18n partial §1 |
| Noto Sans Sinhala and Tamil fonts load without errors | ❌ Font assets absent | i18n partial §5 (T23.7) |
| `LocalizationManager.validateKeys()` reports no missing keys in debug build | ⚠️ Method exists; can't validate without SI/TA tables | i18n partial §2 |
| All three advanced settings screens implement correct read/write to `settings` table | ❌ **Read/write missing** — shells only | Settings/Nav partial §4 |
| Role editor tests pass | ❌ No editor exists | RBAC partial §6 |
| Localization completeness test passes | ❌ Stub only | i18n partial §5 (T23.12) |

**Score: 0/9 fully done, 3/9 partial, 6/9 not started.** The "settings screens render" item is the closest to ✅ in spirit (Batch 14 shipped them), but the DoD specifically requires R/W behavior, which is unmet.

## Cross-document inconsistencies (plan ↔ plan)

| # | Original spec (`Phase3_Sprint23.md`) | Scaffold doc (`phase3-sprint23-scaffold-…`) | Reality |
|---|--------------------------------------|---------------------------------------------|---------|
| 1 | Roles: ADMIN, MANAGER, CASHIER, CUSTOMER_SERVICE, REPORTER | Roles: ADMIN, STORE_MANAGER, CASHIER, ACCOUNTANT, STOCK_MANAGER | Scaffold matches `Role.kt`. **Spec is wrong.** |
| 2 | Permissions: POS_ACCESS, VIEW_ADMIN_PANEL, MANAGE_BACKUPS, MANAGE_EINVOICE, … (~18 fictional names) | Says "42 permissions" | Actual `Permission.kt` has **45** entries with names like `PROCESS_SALE`, `MANAGE_PRODUCTS`, `ADJUST_STOCK`, `MANAGE_STAFF`, `MANAGE_CUSTOMERS`, `VIEW_AUDIT_LOG`, `MANAGE_BACKUP`, `MANAGE_ACCOUNTING`, `ADMIN_ACCESS`. **Spec list is fictional; scaffold count is 3 too low.** |
| 3 | "all 800+ string keys" | "~1966 enum keys (line count: 2136 total file)" | `StringResource` has **~1997 entries**, file is 2151 lines. **Spec is far off; scaffold is close.** |
| 4 | "strings_si.json / strings_ta.json … contain the complete translation" | "Sinhala and Tamil JSON translation files already exist with Phase 2 stubs" | **Neither file exists.** Both docs assert presence. |
| 5 | Method `loadFontForLanguage(language: String): FontFamily` | n/a | Actual: `fontFamilyForLanguage(languageCode: String): String?` — returns name, not Compose `FontFamily`. |
| 6 | IRD `einvoice.*` translation rows | n/a | IRD deferred to Phase 4; `einvoice.*` keys may not exist. |
| 7 | "112 hardcoded Text() instances in 4 modules" | (scaffold-only claim) | **False positive.** All spot-checks resolve to localized interpolations or numeric badges. |
| 8 | "15 screen files" under settings | (scaffold-only claim) | After Batch 14: **18** screen files (3 added). |
| 9 | "RoleList / RoleEditor routes will be added" | (scaffold-only claim) | Still absent in `ZyntaRoute.kt`. |

## What Batch 14 (PR #640) actually closed

| Sprint 23 item | Closed by Batch 14? |
|----------------|---------------------|
| Task 23.9 — settings screen files exist | ✅ Yes (shells only) |
| Task 23.10 — settings routes wired | ✅ 3/5 (advanced settings; not RBAC) |
| Scaffold §4 Claim 4 — "Minimum-Viable Slice: Security Policy Shell" | ✅ Faithfully implemented |
| Scaffold §4 Claim 1 — "15 screen files" | (count moved to 18) |
| 16 i18n keys + EN translations for new shells | ✅ Done |
| `NavGraphCompletenessTest` updated for new factories | ✅ Done |

## Recommendations

1. **Reconcile `Phase3_Sprint23.md`** with the canonical `Role.kt` and `Permission.kt`. Replace fictional names; cross-link to the actual enums. Until this is done, Tasks 23.4 / 23.5 / 23.11 should be considered blocked at the spec level.
2. **Replace JSON-resource sections** of the i18n plan with Kotlin `Map<StringResource, String>` table sections (`SinhalaStrings.kt`, `TamilStrings.kt`). Either commit to JSON loader or — recommended — keep parity with the existing `EnglishStrings.kt` pattern.
3. **Re-baseline the translation count** to ~1997 keys per language. The "800+" figure understates the work by ~2.5×.
4. **Drop the IRD `einvoice.*` translation rows** from Sprint 23 (deferred to Phase 4).
5. **Append a `## Status — 2026-04-26` block** to `phase3-sprint23-scaffold-20260423-1722.md` marking Section 4 claims 1–4 as superseded by PR #640.
6. **Open a Sprint 24 ticket**: "Wire SecurityPolicy / DataRetention / AuditPolicy screens to `SettingsRepository` — implement settings-table CRUD, restore `viewModel: SettingsViewModel` parameter, enable controls."
7. **Decide on the RBAC editor strategy.** Either:
   - (a) Build the `RoleListScreen` + `RoleEditorScreen` per spec (with reconciled enums), and migrate the existing `RbacManagementScreen` callers; or
   - (b) Keep the existing single-screen `RbacManagementScreen` and amend Sprint 23 to reflect the simpler topology.
8. **Drop or rewrite Task 23.8.** Method names in the plan don't match `LocalizationManager.kt`. The work item is largely cosmetic — either rename to match the plan or update the plan to match reality.

## Severity-ranked findings

| # | Finding | Severity | Source |
|---|---------|----------|--------|
| 1 | Spec doc references fictional `Role` / `Permission` constants | **Critical** (blocks any verbatim implementation of Tasks 23.4/23.5) | RBAC §4, §6 |
| 2 | DoD #7 (settings R/W) unmet by Batch 14 | **High** (DoD breach) | Settings/Nav §4 |
| 3 | SI / TA translation tables completely absent | **High** (Sprint 23 i18n goal at 0%) | i18n §1 |
| 4 | Plan/code architecture mismatch (JSON vs Kotlin map) | **Medium** | i18n §1 |
| 5 | Plan undercounts translation work by ~2.5× | **Medium** | i18n §3 |
| 6 | Scaffold doc stale (counts, "needed" framing) | Low | Settings/Nav §5 |
| 7 | "112 hardcoded strings" scaffold claim is a false positive | Low (informational — no actual gap) | i18n §4 |
| 8 | RBAC composable signature drift (no `viewModel:`/`modifier`) | Low–Medium | Settings/Nav §1 |
| 9 | `CloneRoleUseCase` and `GetPermissionsTreeUseCaseImpl` lack implementations | Medium | RBAC §2 |

## Files referenced

- Plan source — `/home/user/ZyntaPOS-KMM/docs/plans/phase/p3/Phase3_Sprint23.md`
- Scaffold source — `/home/user/ZyntaPOS-KMM/docs/plan/phase3-sprint23-scaffold-20260423-1722.md`
- Partial — `/home/user/ZyntaPOS-KMM/docs/audit/sprint23-integrity-audit-2026-04-26-1650-part-settings-nav.md`
- Partial — `/home/user/ZyntaPOS-KMM/docs/audit/sprint23-integrity-audit-2026-04-26-1650-part-rbac.md`
- Partial — `/home/user/ZyntaPOS-KMM/docs/audit/sprint23-integrity-audit-2026-04-26-1650-part-i18n.md`
- Canonical RBAC enums — `shared/domain/src/commonMain/kotlin/com/zyntasolutions/zyntapos/domain/model/{Role,Permission,CustomRole,PermissionGroup}.kt`
- LocalizationManager — `shared/core/src/commonMain/kotlin/com/zyntasolutions/zyntapos/core/i18n/LocalizationManager.kt`
- StringResource enum — `shared/core/src/commonMain/kotlin/com/zyntasolutions/zyntapos/core/i18n/StringResource.kt`
- EnglishStrings table — `shared/core/src/commonMain/kotlin/com/zyntasolutions/zyntapos/core/i18n/EnglishStrings.kt`
- RbacManagementScreen — `composeApp/feature/settings/src/commonMain/kotlin/com/zyntasolutions/zyntapos/feature/settings/screen/RbacManagementScreen.kt`
- 3 Batch 14 shells — `composeApp/feature/settings/src/commonMain/kotlin/com/zyntasolutions/zyntapos/feature/settings/screen/{SecurityPolicy,DataRetention,AuditPolicy}SettingsScreen.kt`
- Nav plumbing — `composeApp/navigation/src/commonMain/kotlin/com/zyntasolutions/zyntapos/navigation/{ZyntaRoute,MainNavScreens,MainNavGraph}.kt`
- App factory wiring — `composeApp/src/commonMain/kotlin/com/zyntasolutions/zyntapos/App.kt:743-751`
- NavGraph completeness test — `composeApp/navigation/src/commonTest/kotlin/com/zyntasolutions/zyntapos/navigation/NavGraphCompletenessTest.kt:71-73`
