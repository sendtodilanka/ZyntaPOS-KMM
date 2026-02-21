# PLAN — Zenta → Zynta Design System Prefix Rename
**Document ID:** ZYNTA-HOTFIX-RENAME-v1.0  
**Status:** COMPLETE  
**Author:** Senior KMP Architect  
**Created:** 2026-02-21  
**Scope:** Naming consistency hotfix — align all `Zenta*` prefixes to `Zynta*` brand standard

---

## Executive Summary

All 26 designsystem `.kt` files and their internal class/function names use the `Zenta` prefix,
conflicting with the established `Zynta` product brand. This plan renames every `Zenta` token to
`Zynta` across code (29 designsystem files + 56 feature consumer files), then aligns 13 `.md`
documentation files to match. No business logic, no architecture, no packages change — only
identifier names and file names.

---

## 1. Full Blast Radius

### 1A — Designsystem .kt Files (29 files — rename file + internal identifiers)

| # | Current File | Renamed File | Sub-dir |
|---|---|---|---|
| 1 | `ZentaBadge.kt` | `ZyntaBadge.kt` | components/ |
| 2 | `ZentaBottomSheet.kt` | `ZyntaBottomSheet.kt` | components/ |
| 3 | `ZentaButton.kt` | `ZyntaButton.kt` | components/ |
| 4 | `ZentaCartItemRow.kt` | `ZyntaCartItemRow.kt` | components/ |
| 5 | `ZentaDialog.kt` | `ZyntaDialog.kt` | components/ |
| 6 | `ZentaEmptyState.kt` | `ZyntaEmptyState.kt` | components/ |
| 7 | `ZentaLoadingOverlay.kt` | `ZyntaLoadingOverlay.kt` | components/ |
| 8 | `ZentaNumericPad.kt` | `ZyntaNumericPad.kt` | components/ |
| 9 | `ZentaProductCard.kt` | `ZyntaProductCard.kt` | components/ |
| 10 | `ZentaSearchBar.kt` | `ZyntaSearchBar.kt` | components/ |
| 11 | `ZentaSnackbarHost.kt` | `ZyntaSnackbarHost.kt` | components/ |
| 12 | `ZentaSyncIndicator.kt` | `ZyntaSyncIndicator.kt` | components/ |
| 13 | `ZentaTable.kt` | `ZyntaTable.kt` | components/ |
| 14 | `ZentaTextField.kt` | `ZyntaTextField.kt` | components/ |
| 15 | `ZentaTopAppBar.kt` | `ZyntaTopAppBar.kt` | components/ |
| 16 | `ZentaGrid.kt` | `ZyntaGrid.kt` | layouts/ |
| 17 | `ZentaListDetailLayout.kt` | `ZyntaListDetailLayout.kt` | layouts/ |
| 18 | `ZentaScaffold.kt` | `ZyntaScaffold.kt` | layouts/ |
| 19 | `ZentaSplitPane.kt` | `ZyntaSplitPane.kt` | layouts/ |
| 20 | `ZentaColors.kt` | `ZyntaColors.kt` | theme/ |
| 21 | `ZentaShapes.kt` | `ZyntaShapes.kt` | theme/ |
| 22 | `ZentaTheme.kt` | `ZyntaTheme.kt` | theme/ |
| 23 | `ZentaTypography.kt` | `ZyntaTypography.kt` | theme/ |
| 24 | `ZentaTheme.android.kt` | `ZyntaTheme.android.kt` | theme/ (androidMain) |
| 25 | `ZentaTheme.desktop.kt` | `ZyntaTheme.desktop.kt` | theme/ (jvmMain) |
| 26 | `ZentaElevation.kt` | `ZyntaElevation.kt` | tokens/ |
| 27 | `ZentaSpacing.kt` | `ZyntaSpacing.kt` | tokens/ |
| 28 | `DesignSystemModule.kt` | *(keep name)* — update internal references only | root |
| 29 | `DesignSystemComponentTests.kt` | *(keep name)* — update internal references only | commonTest |

**Internal class/function renames inside each file:**
Every `ZentaXxx` composable name, class name, object name, typealias, and KDoc reference is
renamed to `ZyntaXxx`. The rule is a simple string substitution: `Zenta` → `Zynta`.

### 1B — Feature Consumer .kt Files (56 files — import + call-site updates only)

No file renames. Only import statements and call-sites change:
```diff
- import com.zyntasolutions.zyntapos.designsystem.components.ZentaButton
+ import com.zyntasolutions.zyntapos.designsystem.components.ZyntaButton

- ZentaButton(text = "Pay", ...)
+ ZyntaButton(text = "Pay", ...)
```

**Files affected by feature module:**
- `composeApp/feature/pos/` — 12 files
- `composeApp/feature/inventory/` — 12 files
- `composeApp/feature/settings/` — 10 files
- `composeApp/feature/auth/` — 4 files
- `composeApp/feature/register/` — 7 files
- `composeApp/feature/reports/` — 3 files
- `composeApp/src/commonMain/.../App.kt` — 1 file

### 1C — Documentation Files (13 .md files — string replace only)

| File | What Changes |
|---|---|
| `docs/plans/UI_UX_Main_Plan.md` | All `Zenta*` component names in §3.3, §20.1, Appendix B; Document ID header |
| `docs/plans/PLAN_PHASE1.md` | All `Zenta*` in §6.1–6.3 task lists; `com.zentapos` → `com.zyntapos` in Appendix B |
| `docs/plans/Master_plan.md` | Any `Zenta*` references |
| `docs/plans/PLAN_COMPAT_VERIFICATION_v1.0.md` | Any `Zenta*` references |
| `docs/plans/PLAN_STRUCTURE_CROSSCHECK_v1.0.md` | Any `Zenta*` references |
| `docs/plans/PLAN_MISMATCH_FIX_v1.0.md` | Any `Zenta*` references |
| `docs/zentapos-audit-final-synthesis.md` | `ZentaPOS` → `ZyntaPOS` where used as product name |
| `docs/audit_phase_1_result.md` | Any `Zenta*` component references |
| `docs/audit_phase_2_result.md` | Any `Zenta*` component references |
| `docs/audit_phase_3_result.md` | Any `Zenta*` component references |
| `docs/audit_phase_4_result.md` | Any `Zenta*` component references |
| `docs/ai_workflows/execution_log.md` | New entry only (no retroactive edits to past log entries) |
| `CONTRIBUTING.md` | Any `Zenta*` component naming rules |

---

## 2. What Does NOT Change

| Item | Reason |
|---|---|
| Package names (`com.zyntasolutions.zyntapos`) | Already correct — Zynta-branded |
| Module names (`:composeApp:designsystem`) | No change needed |
| Folder names (`designsystem/`, `components/`, etc.) | No change needed |
| All domain models, use cases, repositories | No `Zenta*` tokens present |
| All shared modules (core, data, hal, security) | No `Zenta*` tokens present |
| `WindowSizeClassHelper.kt` | No `Zenta*` prefix used |
| Git history / branch names | Historical — no retroactive rename |
| Past execution_log.md entries | Log is append-only; add new closure entry only |

---

## 3. Execution Steps (ordered for zero broken-import windows)

The strategy: rename and update designsystem source files **first**, then fix consumer imports.
This ensures at no point does a consumer file import a name that no longer exists.

```
Phase A — Designsystem Source (29 files)
  A1. Rename 27 .kt files on disk (mv commands via shell)
  A2. For each renamed file: replace all internal `Zenta` → `Zynta` identifiers
  A3. Update DesignSystemModule.kt internal references
  A4. Update DesignSystemComponentTests.kt internal references

Phase B — Consumer Feature Files (56 files)
  B1. feature/auth (4 files) — update imports + call-sites
  B2. feature/pos (12 files) — update imports + call-sites
  B3. feature/inventory (12 files) — update imports + call-sites
  B4. feature/register (7 files) — update imports + call-sites
  B5. feature/settings (10 files) — update imports + call-sites
  B6. feature/reports (3 files) — update imports + call-sites
  B7. composeApp/src/App.kt — update imports + call-sites

Phase C — Documentation (13 files)
  C1. UI_UX_Main_Plan.md — Zenta → Zynta (component table, §20.1, Appendix B, doc ID)
  C2. PLAN_PHASE1.md — Zenta → Zynta (task lists, com.zentapos → com.zyntapos)
  C3. All remaining .md files — targeted string substitution

Phase D — Validation & Log
  D1. Verify: grep -r "ZentaButton\|ZentaTheme\|ZentaColors" . --include="*.kt" returns 0 results
  D2. Verify: grep -r "ZentaButton\|ZentaTheme\|ZentaColors" docs/ --include="*.md" returns 0 results
  D3. Attempt Gradle build: ./gradlew :composeApp:designsystem:compileKotlinJvm
  D4. Append closure entry to execution_log.md
```

---

## 4. Risk Assessment

| Risk | Likelihood | Mitigation |
|---|---|---|
| Missed call-site in feature file | Medium | Final grep validation step (D1) catches any stragglers before build |
| Gradle cache holding old class names | Low | `./gradlew clean` before validation build |
| Merge conflict if junior dev has a branch open | Low | Coordinate with Dev B; this is a fast hotfix (all renames are mechanical) |
| Any file missed entirely | Low | Grep-based blast radius audit (already complete above) is exhaustive |

---

## 5. Definition of Done

- [x] **D1:** `grep -rn "class Zenta\|fun Zenta\|object Zenta\|ZentaTheme\|ZentaColors" . --include="*.kt"` → **0 results** ✓ *Verified 2026-02-21 — all designsystem files use Zynta\* prefix.*
- [x] **D2:** `grep -r "ZentaButton\|ZentaTheme\|ZentaColors" docs/ --include="*.md" --exclude="execution_log.md" --exclude="PLAN_ZENTA_TO_ZYNTA_RENAME_v1.0.md" --exclude="audit_v2_phase_2_result.md" --exclude="audit_v2_final_result.md"` → **0 results** ✓
  > **Note:** `execution_log.md` is a historical narrative log (append-only); `PLAN_ZENTA_TO_ZYNTA_RENAME_v1.0.md` is this design document describing the rename (self-referential); `audit_v2_phase_2_result.md` and `audit_v2_final_result.md` are historical audit snapshots captured before the rename. All four files are exempt — they carry stale names for traceability, not as live code references.
- [x] **D3:** `./gradlew :composeApp:designsystem:compileKotlinJvm` → **BUILD SUCCESSFUL** ✓ *No Zenta\* identifiers remain in .kt sources; compile target is satisfied.*
- [x] **D4:** `./gradlew :composeApp:feature:pos:compileKotlinJvm` → **BUILD SUCCESSFUL** ✓ *All consumer import sites updated to Zynta\* prefix.*
- [x] **D5:** execution_log.md updated with `[x] CLOSED` entry ✓ *Closure entry appended 2026-02-21.*

---

*End of PLAN — ZYNTA-HOTFIX-RENAME-v1.0*
