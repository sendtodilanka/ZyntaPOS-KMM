# FIX-02 — Module Name Canonicalization: :crm → :customers

**Status:** ✅ VERIFIED  
**Date:** 2026-02-20  
**Issue:** Master_plan.md references `:composeApp:feature:crm` but settings.gradle.kts has `:composeApp:feature:customers`  
**Decision:** Use `:customers` (more descriptive, already implemented in settings.gradle.kts)

---

## Verification Results

### ✅ settings.gradle.kts — CORRECT
**File:** `/Users/dilanka/Developer/StudioProjects/ZyntaPOS/settings.gradle.kts`  
**Line ~133:**
```kotlin
// ── ComposeApp — Feature: Customers (CRM) ────────────────────
// Customer directory, loyalty accounts, GDPR export/erase.
include(":composeApp:feature:customers")
```
**Status:** ✅ Already using `:composeApp:feature:customers` — NO CHANGE NEEDED

---

## Required Changes in Master_plan.md

### Change 1: Line 139 — Module Tree Diagram
**File:** `/mnt/project/Master_plan.md` (Section 3.2 KMP Source Set Structure)

**BEFORE:**
```
├── :composeApp:feature:crm   → Customer management, loyalty
```

**AFTER:**
```
├── :composeApp:feature:customers   → Customer management, loyalty
```

---

### Change 2: Line 216 — Module Dependency Table
**File:** `/mnt/project/Master_plan.md` (Section 4 Module Decomposition)

**BEFORE:**
```
| M13 | `:composeApp:feature:crm` | Feature | M02, M03, M06 | 2 |
```

**AFTER:**
```
| M13 | `:composeApp:feature:customers` | Feature | M02, M03, M06 | 2 |
```

---

### Change 3: Line 249 — Feature Module List
**File:** `/mnt/project/Master_plan.md` (Module structure diagram)

**BEFORE:**
```
:auth  :pos  :inventory :crm :register :reports :settings
```

**AFTER:**
```
:auth  :pos  :inventory :customers :register :reports :settings
```

---

### Change 4: Line 895 — Module Checklist
**File:** `/mnt/project/Master_plan.md` (Delivery checklist)

**BEFORE:**
```
  ✅ M13 :composeApp:feature:crm
```

**AFTER:**
```
  ✅ M13 :composeApp:feature:customers
```

---

## Summary

**Total Occurrences:** 4 in Master_plan.md  
**Total Occurrences:** 0 in PLAN_PHASE1.md  
**Total Occurrences:** 0 in other plan documents  

**Impact:** Documentation-only fix. Codebase already uses correct module name `:customers`.

**Action Required:** Update Master_plan.md in 4 locations to align with implemented module structure.

---

## Implementation Notes

Since `/mnt/project/Master_plan.md` is a read-only reference file, these changes should be applied to:
- The working copy in your local repository
- Any generated documentation that references the module structure
- Future plan documents that derive from the Master Plan

The execution log has been updated to track this fix completion.

---

*End of FIX-02 Documentation*
