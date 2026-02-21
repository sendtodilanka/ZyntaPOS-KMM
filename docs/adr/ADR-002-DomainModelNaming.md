# ADR-002: Domain Model Naming Convention

**Date:** 2026-02-21
**Status:** ACCEPTED — Option B adopted on 2026-02-21
**Author:** Senior KMP Architect (AI-assisted audit)

---

## Context

During a domain layer audit, the `shared/domain/src/commonMain/.../domain/model/` package
was found to contain **26 domain model files** named without the `*Entity` suffix:

| # | File | Domain Concept |
|---|------|---------------|
| 1 | `AuditEntry.kt` | Audit trail record |
| 2 | `CartItem.kt` | POS cart line item |
| 3 | `CashMovement.kt` | Cash drawer movement |
| 4 | `CashRegister.kt` | Register terminal |
| 5 | `Category.kt` | Product category |
| 6 | `Customer.kt` | CRM customer |
| 7 | `DiscountType.kt` | Discount strategy enum |
| 8 | `Order.kt` | Sales transaction |
| 9 | `OrderItem.kt` | Line item within order |
| 10 | `OrderStatus.kt` | Order lifecycle status |
| 11 | `OrderTotals.kt` | Computed order totals |
| 12 | `OrderType.kt` | Order classification |
| 13 | `PaymentMethod.kt` | Payment tender type |
| 14 | `PaymentSplit.kt` | Split payment record |
| 15 | `Permission.kt` | RBAC permission |
| 16 | `Product.kt` | Inventory product |
| 17 | `ProductVariant.kt` | Product SKU variant |
| 18 | `RegisterSession.kt` | Register open/close session |
| 19 | `Role.kt` | Staff role |
| 20 | `StockAdjustment.kt` | Stock movement record |
| 21 | `Supplier.kt` | Product supplier |
| 22 | `SyncOperation.kt` | Offline sync queue item |
| 23 | `SyncStatus.kt` | Sync state enum |
| 24 | `TaxGroup.kt` | Tax configuration |
| 25 | `UnitOfMeasure.kt` | Product unit |
| 26 | `User.kt` | Staff/system user |

No Architecture Decision Record (ADR) existed prior to this document to define whether
the omission of `*Entity` is intentional. This ambiguity risks misleading new developers
into confusing **pure domain models** (business rules, no persistence) with **ORM/DB
mapping entities** (SQLDelight-generated or Room `@Entity`-annotated classes).

---

## Decision Drivers

- **Developer clarity:** The term "Entity" carries dual meaning in software — DDD Entities
  (identity-based domain objects) vs. persistence-layer ORM rows.
- **KMP project structure:** ZyntaPOS already has a separate `shared/data` layer where
  SQLDelight schema tables live. Those generated classes use database-centric naming.
- **Refactor cost:** 26 files × (rename + import updates across all feature modules) = high
  mechanical effort with non-trivial merge risk.
- **Industry precedent:** Kotlin/Android community (e.g., Now in Android, Circuit) commonly
  reserves plain names (`Product`, `Order`) for domain models and `*Entity` for DB rows.

---

## Options

### Option A — Adopt `*Entity` suffix (DDD-aligned)

Rename all 26 domain model files to the `*Entity` convention:

```
Product.kt        → ProductEntity.kt
Order.kt          → OrderEntity.kt
Customer.kt       → CustomerEntity.kt
... (all 26 files)
```

- Update every import across all feature modules and shared layers.
- **Effort:** HIGH — estimated 2–4 hours of mechanical refactoring + full build verification.
- **Benefit:** Explicit DDD alignment; `*Entity` signals identity-based domain objects.
- **Risk:** Naming clash if SQLDelight-generated types are ever suffixed `*Entity` too.

---

### Option B — Formally reject `*Entity` suffix (recommended)

Document that domain models in ZyntaPOS use **plain, ubiquitous-language names**
(`Product`, `Order`, `Customer`) and that:

- `*Entity` suffix is **reserved exclusively** for ORM/persistence-layer mapping objects
  (SQLDelight tables, Room `@Entity` classes) in the `shared/data` layer.
- Domain model classes in `shared/domain` represent **pure business concepts** with no
  persistence coupling — plain names reinforce this separation.
- New developers MUST read this ADR before naming any class in `shared/domain/model/`.

- **Effort:** ZERO — current codebase requires no changes.
- **Benefit:** Aligns with Kotlin community norms; eliminates future naming collisions
  between domain and persistence layers; preserves existing import graph.
- **Risk:** Requires disciplined onboarding; `Entity` must be enforced via code review.

---

## Decision

> **To be confirmed by tech lead / architecture owner.**

- [ ] **Option A** — Rename all 26 files to `*Entity` suffix
  - Confirmed by: _________________________ Date: _____________
- [x] **Option B** — Reject `*Entity` suffix; document plain-name convention as canonical
  - Confirmed by: Dilanka (Tech Lead) Date: 2026-02-21

---

## Consequences

**Option B is now the accepted convention for ZyntaPOS.**

### Domain Layer (`shared/domain/model/`)
- All domain model classes use **plain, ubiquitous-language names**: `Product`, `Order`,
  `Customer`, `User`, etc.
- The `*Entity` suffix is **strictly prohibited** in the `shared/domain` layer.
- These classes are pure Kotlin data structures representing business concepts — zero
  persistence coupling, zero framework annotations.

### Persistence Layer (`shared/data/`)
- SQLDelight-generated query interfaces (e.g., `ProductsQueries`) and any hand-written
  DB mapping types **must use** `*Entity`, `*Table`, or `*Row` suffixes to make their
  persistence nature explicit.
- Example: a DB mapping wrapper would be `ProductEntity.kt` in `shared/data`, clearly
  separated from `Product.kt` in `shared/domain`.

### Enforcement
- This ADR is the authoritative reference. Link it in code reviews whenever a naming
  question arises.
- `CONTRIBUTING.md` has been updated with the naming rule and a pointer to this ADR.
- The rule to add to code review checklist:
  > *"Is this class in `shared/domain/model/`? → Plain name (e.g., `Product`).
  > Is this a DB/ORM mapping class? → Use `*Entity` or `*Table` suffix in `shared/data`."*

### If Option A is chosen:
- All 26 domain model files will be renamed.
- All imports across `composeApp`, feature modules, and shared layers will be updated.
- SQLDelight-generated types must be renamed or prefixed to avoid future collisions.
- A follow-up automated rename can be executed via a single Claude prompt.

### If Option B is chosen:
- This ADR becomes the canonical reference for domain model naming in ZyntaPOS.
- The `CONTRIBUTING.md` (or equivalent) should link to this ADR.
- Code review checklist should include: *"Is this a domain model? Use plain name. Is this
  a DB row? Use `*Entity` or `*Table` suffix."*

---

## References

- [Domain-Driven Design — Evans, 2003] — "Entity" = object with identity continuity
- [Now in Android (Google)](https://github.com/android/nowinandroid) — uses plain domain model names
- ZyntaPOS `shared/data` layer — SQLDelight schema tables (persistence boundary)
- ADR-001 (if exists) — project-level architectural decisions

---

*This ADR was generated during an automated architectural audit on 2026-02-21.*
*It is PROPOSED and has no binding effect until signed by the tech lead.*
