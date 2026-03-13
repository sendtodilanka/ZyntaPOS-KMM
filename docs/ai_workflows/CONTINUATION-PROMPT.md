# ZyntaPOS-KMM — Continuation Prompt for New Claude Code Sessions

**Last Updated:** 2026-03-13
**Purpose:** Copy-paste this prompt into a new Claude Code session to continue implementation work. Each session should pick up where the last left off.

---

## THE PROMPT

Copy everything below the line into a new Claude Code session:

---

```
You are continuing implementation work on ZyntaPOS-KMM, a Kotlin Multiplatform POS system. Read CLAUDE.md first for full architecture context.

## Current Status (as of 2026-03-13)

Phase 1: COMPLETE (5/5 TODOs done)
Phase 2: ~97% complete

### Completed Sprints
- Sprint 1 (Admin Panel Session Stability): COMPLETE
- Sprint 2 (Backend Cross-Module Alignment): COMPLETE (15/15 tasks done)
- Sprint 3 (Test Coverage & Performance): COMPLETE (14/14 tasks done)
- Sprint 4 (Documentation & Compliance): COMPLETE (11/11 tasks done)

### Completed Blocks (do NOT re-implement)
- BLOCK 0: TaxGroupRepositoryImpl + UnitGroupRepositoryImpl fully implemented
- BLOCK 1: S2-3 (timestamp standardization), S2-12 (license validation in auth) done
- BLOCK 2: All Sprint 3 items done (S3-1 through S3-15)
- BLOCK 3: All Sprint 4 items done (S4-1 through S4-11)
- BLOCK 4: DiagnosticRelay.kt, AnalyticsService expect/actual, email delivery log UI, zyntapos-docs site all done
- BLOCK 5: BackupFileManager expect/actual + backups.sq; ReportRepositoryImpl stubs + 4 new .sq tables; EInvoiceRepositoryImpl.submitToIrd() with IrdApiClient expect/actual; AppConfig IRD vars; platform DI wiring DONE
- BLOCK 6: DiagnosticSession.VisitType enum + visitType/siteVisitToken fields; V15 Flyway migration; DiagnosticSessionService.createSiteVisitToken() DONE

### Feature Status
- TODO-006 (~85%): WebSocket diagnostic relay DONE. Remaining: site visit token support (Phase 3)
- TODO-007e: API docs site DONE (zyntapos-docs/ with Scalar, docker-compose, Caddyfile)
- TODO-008a (~85%): Email delivery log UI DONE
- TODO-010 (~80%): Canary tokens embedded in source. Remaining: Snyk/CF Zero Trust (external)
- TODO-011 (~95%): AnalyticsService expect/actual + Koin wiring + ViewModel event wiring DONE

## YOUR TASK: Complete remaining Phase 3 deferred items below

Work through these items sequentially. For each item: implement, run `./gradlew assemble` to verify compilation, commit with conventional commits format, and push. Follow the CLAUDE.md pre-commit sync ritual (fetch + merge origin/main) before EVERY commit. Monitor the 7-step CI/CD pipeline after each push before starting the next item.

Use `curl` with `$PAT` for GitHub API — NOT `gh` CLI (git remote is a local proxy).

---

### BLOCK 5: KMM Client Stubs to Complete (Phase 3 deferred) — ✅ COMPLETE 2026-03-13

**BackupRepositoryImpl** — `shared/data/src/commonMain/kotlin/.../repository/BackupRepositoryImpl.kt`
- Currently in-memory only — needs platform expect/actual `BackupFileManager` for real file I/O
- Android: copy encrypted SQLite DB file to external storage / app-specific dir
- Desktop: file copy to `~/.zyntapos/backups/` directory
- Add `shared/data/src/commonMain/.../backup/BackupFileManager.kt` (expect class)
- Add `shared/data/src/androidMain/.../backup/BackupFileManager.kt` (actual class using File API)
- Add `shared/data/src/jvmMain/.../backup/BackupFileManager.kt` (actual class using java.nio.file)
- Wire into `BackupRepositoryImpl.createBackup()` and `restoreBackup()`
- Add `backups` SQLDelight table to persist backup metadata across sessions

**ReportRepositoryImpl stubs** — `shared/data/src/commonMain/.../repository/ReportRepositoryImpl.kt`
- `getPurchaseOrders()` returns emptyList() — needs `purchase_orders` SQLDelight table
- `getWarehouseInventory()` returns placeholder data — needs `rack_products` join table
- `getSupplierPurchases()` returns zero totals — needs purchase_orders aggregation
- `getMultiStoreComparison()` uses store ID as name — needs `stores` registry table
- `getLeaveBalances()` uses hardcoded allotments — needs `leave_allotments` table
- Add the required SQLDelight schema files, regenerate interface, implement methods

**EInvoiceRepositoryImpl.submitToIrd()** — `shared/data/src/commonMain/.../repository/EInvoiceRepositoryImpl.kt`
- Currently marks as SUBMITTED without calling IRD API
- Needs actual HTTP call to Sri Lanka IRD API endpoint
- Configuration: `ZYNTA_IRD_API_ENDPOINT`, `ZYNTA_IRD_CLIENT_CERTIFICATE_PATH`, `ZYNTA_IRD_CERTIFICATE_PASSWORD`
- Create `IrdApiClient` expect/actual (Ktor-based, with client certificate support for mTLS)
- Android actual: OkHttp with KeyStore for client certificate
- JVM actual: Ktor CIO with SSLContext for client certificate
- Wire the result back to update invoice status to ACCEPTED/REJECTED

---

### BLOCK 6: TODO-006 Remaining — Site Visit Token Support (Phase 3) — ✅ COMPLETE 2026-03-13

**Site Visit Token** — for on-site technician hardware access
- Domain model: extend `DiagnosticSession` with `visitType: VisitType` (REMOTE | ON_SITE)
- Backend: add `site_visit_token` column to `diagnostic_sessions` table (new Flyway migration)
- Backend service: `DiagnosticSessionService.createSiteVisitToken()` — HMAC-SHA256 token scoped to specific hardware components
- Hardware validation: technician must present physical hardware token at customer site
- Implementation is architecturally complete for remote access; site visit is the next layer

---

## VERIFICATION after completing each block

```bash
# KMM modules
./gradlew assemble
./gradlew allTests
./gradlew detekt

# Backend
cd backend/api && ./gradlew compileKotlin test
cd backend/license && ./gradlew compileKotlin test
cd backend/sync && ./gradlew compileKotlin test

# Admin panel
cd admin-panel && npm run build && npm test

# Full CI
./gradlew clean test lint --parallel --continue --stacktrace
```

## RULES
1. Follow CLAUDE.md exactly — especially pre-commit sync ritual
2. Use conventional commits: feat/fix/refactor/test/docs/build(scope): message
3. One logical change per commit — don't bundle unrelated work
4. Push after each commit and monitor the full 7-step pipeline
5. Use `curl` with `$PAT` for GitHub API, NOT `gh` CLI
6. Never push to main directly — always use the feature branch
7. If any CI step fails, stop and fix before continuing
8. Read existing code before modifying — understand before changing
9. No Java — 100% Kotlin
10. All ViewModels MUST extend BaseViewModel<S,I,E> (ADR-001)
11. No *Entity suffix in shared/domain/model/ (ADR-002)
```

---

## NOTES FOR THE OPERATOR

- **Session length:** Each block is roughly one session's worth of work. If a session times out, start a new one with this same prompt — it will pick up from where it left off by checking git log.
- **Priority:** Block 5 items are Phase 3 deferred. Block 6 is the remaining TODO-006 piece.
- **Tracking:** After each session, update `docs/todo/GAP-ANALYSIS-AND-FILL-PLAN.md` with current completion percentages.
- **Branch naming:** Each session should create its own branch: `claude/<descriptive-name>-<sessionId>`
