# ZyntaPOS — Claude Action Prompts
# Source: ZENTA-AUDIT-V2-FINAL-SYNTHESIS Priority Action Plan
# 23 Atomic Prompts · Ordered by Severity · Each is Self-Contained

> **How to use:** Paste each prompt into a fresh Claude chat or Claude Code session.
> Every prompt includes: exact file paths, what to read first, what to write, and a verification command.
> Do NOT skip the "read first" step — Claude must confirm current state before writing.
>
> **Execution order respect chain:**
> - #3 must complete before #8 and #9
> - #1 must complete before #9 and #12
> - #5 must complete before #4
> - #11 + #13 + #20 can be done as one combined edit (noted in those prompts)

---

## 🔴 CRITICAL — Sprint Blockers

### PROMPT #1 — Create Koin Startup + Wire Navigation (MERGED-A1)
### PROMPT #2 — Remove SecurityAuditLogger() Compile Error from posModule (MERGED-B1)
### PROMPT #3 — Refactor 3 Domain Use Cases Off HAL — Create Port Interfaces (MERGED-C1)

## 🔴 HIGH — Fix Before Sprint 5 Integration Testing

### PROMPT #4 — Replace InMemorySecurePreferences in Production Bindings (MERGED-D2)
### PROMPT #5 — Implement TaxGroupRepositoryImpl + UnitGroupRepositoryImpl (MERGED-D1)
### PROMPT #6 — Delete Rogue feature/pos/PrintReceiptUseCase.kt (MERGED-B2)

## 🟠 MEDIUM — Fix Within Sprint 5

### PROMPT #7 — Resolve Dual SecurePreferences Types (MERGED-D3)
### PROMPT #8 — Remove HAL Dependency from SettingsViewModel (MERGED-E1)
### PROMPT #9 — Register PrintTestPageUseCase in Koin (MERGED-E2)
### PROMPT #10 — Replace Non-Cryptographic generateUuid() (MERGED-E3)
### PROMPT #11 — Update Master_plan — Remove M03 from Feature Modules (MERGED-G1)

## 🟡 LOW — Fix Within Sprint 6

### PROMPT #12 — Wire SecurePreferencesKeyMigration.migrate() (MERGED-F1)
### PROMPT #13 — Correct Master_plan Dep Columns M08, M11, M18 (MERGED-G2) [COMBINE WITH #11]
### PROMPT #14 — Close PLAN_ZENTA_TO_ZYNTA_RENAME_v1.0.md (MERGED-G3)
### PROMPT #15 — Fill or Rename zentapos-audit-final-synthesis.md (MERGED-G4)
### PROMPT #16 — Add ADR-001 to CONTRIBUTING.md ADR Table (MERGED-G5)
### PROMPT #17 — Fix Stale Brand String in ReceiptFormatter.kt (MERGED-G6)
### PROMPT #18 — Document 7 Empty Feature Modules as Scaffold (MERGED-G7)
### PROMPT #19 — Update PLAN_STRUCTURE_CROSSCHECK Module Count (MERGED-G8)
### PROMPT #20 — Add M05 to Master_plan M09 Deps Column (MERGED-B3) [COMBINE WITH #11]
### PROMPT #21 — Resolve keystore/ + token/ Scaffold Directory Ambiguity (MERGED-F2)
### PROMPT #22 — Review PasswordHasher Port Abstraction (MERGED-F3)
### PROMPT #23 — Delete Boilerplate compose-multiplatform.xml (MERGED-H1)

---

See the full prompt bodies in the downloadable file: action_prompts_v1.md
(Full content provided separately — this is the table of contents copy saved to docs/)
