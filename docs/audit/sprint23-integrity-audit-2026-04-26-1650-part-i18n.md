# Sprint 23 Integrity Audit — i18n / Localization Partial
**Generated:** 2026-04-26 16:50
**Scope:** i18n / Localization claims in Sprint23.md (lines 385–472, tasks 23.6/23.7/23.8/23.12) and scaffold doc Section 3 (lines 73–125).
**Sources inspected:** SupportedLocale.kt, LocalizationManager.kt, StringResource.kt, EnglishStrings.kt; spot-checks in MediaLibraryScreen, StoreTransferDashboardScreen, WarehouseListScreen.

---

## Section 1: Sinhala / Tamil translation tables
**Plan source:** Sprint23.md L387–438 (file path `shared/core/src/commonMain/resources/strings_si.json` and `strings_ta.json`); scaffold L77–80, L121–124.
**Status:** ❌ MISSING

### Plan claim
> "Files: `strings_si.json`, `strings_ta.json`. These files contain the complete translation for all 800+ string keys used in the app." (Sprint23.md L391–393)
> Scaffold L124: "Sinhala and Tamil JSON translation files already exist with Phase 2 stubs."

### Codebase reality
- `find /home/user/ZyntaPOS-KMM/shared -name 'SinhalaStrings*' -o -name 'TamilStrings*' -o -name 'strings_si*' -o -name 'strings_ta*'` → **zero results**.
- `find shared/core/src -path '*resources*' -name '*.json'` → **zero results**. No `commonMain/resources/` directory exists at all.
- Only translation table on disk is `EnglishStrings.kt` (2104 lines, registered automatically by `LocalizationManager.init`).
- No production code path calls `localizationManager.registerStrings(SupportedLocale.SI, …)` or `…TA, …)`. Only `LocalizationManagerTest.kt` registers stub maps.

### Verdict
The plan and the scaffold doc both claim Sinhala/Tamil tables exist (or will exist as Phase 2 stubs). Neither is true. Additionally, the chosen format (JSON resource files) is **inconsistent with the existing architecture** — the codebase ships `EnglishStrings.kt` as a Kotlin map keyed by the `StringResource` enum, not a runtime JSON load. Sprint 23 effectively has to choose between (a) authoring `SinhalaStrings.kt` / `TamilStrings.kt` to match the existing pattern, or (b) refactoring to a JSON-loader pipeline. The plan picks option (b) without acknowledging the migration cost.

=== SECTION 1 COMPLETE ===

---

## Section 2: LocalizationManager upgrades (loadFontForLanguage + validateKeys)
**Plan source:** Sprint23.md L442–466; tasks 23.8, 23.12.
**Status:** ✅ ALREADY IMPLEMENTED (signatures differ from plan)

### Plan claim
> Add `fun loadFontForLanguage(language: String): FontFamily { … FontFamily(Font("fonts/NotoSansSinhala-Regular.ttf")) … }` and `fun validateKeys(targetLanguage: String): List<String>` (Sprint23.md L448–465).

### Codebase reality
`LocalizationManager.kt` already exposes both capabilities, with **slightly different signatures**:

- `fun fontFamilyForLanguage(languageCode: String): String?` (L118–126) — returns a font *family name* string ("Noto Sans Sinhala", "Noto Sans Tamil", "Noto Sans Devanagari", "Noto Sans Arabic", "Noto Sans JP", "Noto Sans SC"). Method name uses `fontFamilyForLanguage`, not `loadFontForLanguage`. Returns `String?` not Compose `FontFamily` — the platform layer is expected to map the name to a real font.
- `fun validateKeys(locale: SupportedLocale): List<String>` (L135–138) — takes a `SupportedLocale` (not raw `String`), delegates to `missingKeys()`, returns the list of missing key *names*. Uses Kermit `Logger.w` inside `getString()` for missing-key warnings, not a centralized log line.
- `fun missingKeys(locale: SupportedLocale): Set<StringResource>` (L104–107) is the underlying validator.

### Verdict
Functionally complete; ✅ for behavior. **Plan is slightly out of date**: the methods exist under different names and operate on the typed `SupportedLocale`/`StringResource` enums rather than raw strings + JSON paths. Tasks 23.8 (rename methods + adopt signatures from plan) and 23.12 (write `LocalizationTest`) become trivial — `LocalizationManagerTest.kt` already exercises `missingKeys` and locale registration. The font font-loader returns a name only — no actual `.ttf` is loaded; that work is still pending and depends on the missing fonts (see Section 5).

=== SECTION 2 COMPLETE ===

---

## Section 3: StringResource enum coverage (~800 vs ~1997 keys)
**Plan source:** Sprint23.md L393 ("800+ string keys"); scaffold L77 ("802 total"), L97 ("~1966 enum keys").
**Status:** ⚠️ PARTIAL — plan and scaffold both undercount

### Plan claim
- Sprint23.md: "all 800+ string keys"; Phase 3 additions are limited to 18 staff/admin/media/einvoice keys per language.
- Scaffold: "~1966 enum keys (line count: 2136 total file)"; "690/802 = 86% localized".

### Codebase reality
- `wc -l shared/core/src/commonMain/kotlin/.../StringResource.kt` → **2151 lines** (scaffold said 2136 — close).
- Enum-entry regex match → **1997 entries**.
- `EnglishStrings.kt` → 2104 lines, with corresponding `StringResource.X to "..."` rows (e.g., `STAFF_EMPLOYEES to "Employees"` at L320). Sample staff/admin/media keys cited in the plan (`STAFF_EMPLOYEES`, etc.) **do exist** in `StringResource.kt` (L339+) and have English values.
- **IRD / e-invoice keys** (`einvoice.title`, `einvoice.submit`, `einvoice.compliance`) — these are listed in the plan but IRD is explicitly **deferred to Phase 4** (see CLAUDE.md). Expect these enum keys may not exist; not verified.

### Verdict
Real enum size is **~1997 keys**, far more than the "800+" the plan asserts. Both documents' Phase 3 addition lists (18 keys/language) are far too narrow — the gap to localize is the full 1997-key surface, not 800. The English baseline is complete; the SI/TA gap is **100% missing**. Sprint 23 task 23.6 ("Complete `strings_si.json` and `strings_ta.json` with all 800+ Phase 1–3 keys") will need to translate ~2000 entries, not 800. The IRD-related plan rows (Sprint23 L414–416, L436–438) likely target enum entries that no longer exist after the IRD removal — those rows should be deleted from the Sprint 23 plan.

=== SECTION 3 COMPLETE ===

---

## Section 4: "112 hardcoded Text() instances in 4 modules"
**Plan source:** Scaffold L77, L81–91.
**Status:** ❌ FALSE POSITIVE — claim refuted

### Plan claim
> "Baseline hardcoded strings: 112 `Text("...")` instances vs 690 using `Text(s[StringResource.*])` pattern. 86% localized. Top 5 hardcoded-string features: media (4), multistore (3), settings (2), register (2), inventory (2)."

### Codebase reality (spot-checks)
Three exact lines the audit flagged were inspected:

| Cited line | Actual code | Verdict |
|---|---|---|
| `MediaLibraryScreen.kt:259` | `Text("${s[StringResource.MEDIA_SIZE]}: %.2f MB".format(file.fileSizeMb), …)` | False positive — interpolates a localized label |
| `MediaLibraryScreen.kt:260–263` | `Text("${s[StringResource.COMMON_STATUS]}: …")`, `Text("${s[StringResource.MEDIA_PRIMARY]}: …")`, `Text("${s[StringResource.MEDIA_URL_LABEL]}: …")` | All four flagged lines wrap a `StringResource` lookup with appended runtime data |
| `StoreTransferDashboardScreen.kt:322` | `Text("${group.activeCount}")` | Pure integer badge — no translatable text |
| `WarehouseListScreen.kt:65` | `Text("${state.pendingTransfers.size}")` | Pure integer badge — no translatable text |

### Broader sweep
`grep -rn 'Text("[A-Z][a-zA-Z ]\{3,\}"' composeApp/feature --include='*.kt' | grep -v 'StringResource\|s\['` → **only matches are inside `*UiTest.kt` files** (test assertion strings, e.g., `onNodeWithText("Orders")` in `DashboardScreenUiTest.kt`). **Zero genuinely hardcoded English Text() calls in production composables across all feature modules.**

### Verdict
The "112 hardcoded strings" claim is **categorically refuted**. The earlier Batch 14 hypothesis was correct: nearly all flagged instances are either (a) `Text("…${s[StringResource.X]}…")` interpolations that are already localized, or (b) `Text("${someInt}")` numeric badges that need no translation. Production composables are effectively 100% StringResource-driven. The scaffold's "86% localized" figure is therefore artificially low — actual coverage is ~100% in `composeApp/feature/`. Tasks 23.6 / 23.12 should focus on **filling the English→SI/TA translation gap**, not on chasing hardcoded text.

=== SECTION 4 COMPLETE ===

---

## Section 5: Tasks 23.6 / 23.7 / 23.8 / 23.12
**Plan source:** Sprint23.md L579–586.

| Task | Description | Status | Notes |
|---|---|---|---|
| 23.6 | Complete `strings_si.json` and `strings_ta.json` with all 800+ Phase 1–3 keys | ❌ Not started | Files don't exist; format mismatch (JSON vs Kotlin map). Real gap is ~1997 keys per language, not 800. |
| 23.7 | Add Noto Sans Sinhala + Tamil font files to `shared/core/src/commonMain/resources/fonts/` | ❌ Not started | `find shared -name 'NotoSans*'` → no results. `commonMain/resources/` directory absent. |
| 23.8 | Update `LocalizationManager` with `loadFontForLanguage()` + `validateKeys()` | ✅ Effectively done | `fontFamilyForLanguage()` (returns name) and `validateKeys(locale)` already exist. Plan should reconcile method names instead of recreating them. |
| 23.12 | Write `LocalizationTest` validating SI/TA keys vs EN baseline | ⚠️ Partially done | `LocalizationManagerTest.kt` exists and tests `missingKeys`/`registerStrings`/`fromTag`, but with stub data only — not a real EN-vs-SI/TA completeness assertion. |

### Verdict
Two of four tasks (23.6, 23.7) are completely unstarted. Task 23.8 is already done under different method names — work item should be downgraded to a docs/rename pass. Task 23.12 has a test scaffold but lacks the production translation tables to validate. Until 23.6 lands, 23.12 cannot pass meaningfully.

=== SECTION 5 COMPLETE ===

---

## Summary of Findings

| # | Finding |
|---|---|
| F1 | **SI / TA translation tables are entirely absent.** No `SinhalaStrings.kt`, `TamilStrings.kt`, `strings_si.json`, or `strings_ta.json` exist on disk. |
| F2 | **Architecture mismatch in plan.** Sprint23.md prescribes JSON resource files; codebase uses Kotlin `Map<StringResource, String>` tables. The plan implicitly requires a loader refactor that is not called out. |
| F3 | **`LocalizationManager` already has `validateKeys` + `fontFamilyForLanguage`** (Section 2). Method names differ from plan (`fontFamilyForLanguage` vs `loadFontForLanguage`); signatures are typed (`SupportedLocale`/`StringResource`) instead of raw `String`. Task 23.8 is largely cosmetic. |
| F4 | **Real `StringResource` enum has ~1997 entries**, not the 800 quoted in Sprint23.md L393. Sprint 23 task 23.6 underestimates translation work by ~2.5×. |
| F5 | **Scaffold's "112 hardcoded Text() instances" claim is a false positive.** Spot-checks of all three cited lines confirmed they interpolate localized labels or render numeric badges. No genuinely hardcoded English production strings remain in `composeApp/feature/`. |
| F6 | **IRD-related plan additions (Sprint23.md L414–416, L436–438) are stale** — IRD is deferred to Phase 4 per CLAUDE.md, so those `einvoice.*` rows should be removed from the Sprint 23 i18n scope. |
| F7 | **Noto Sans font assets are absent** (Task 23.7 unstarted). `commonMain/resources/` directory does not exist anywhere under `shared/core/`. |
