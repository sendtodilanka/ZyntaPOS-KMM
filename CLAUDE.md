# CLAUDE.md — ZyntaPOS KMM

This file gives AI assistants the context needed to work effectively in this codebase. Read it before making any changes.

---

## 🔴 CRITICAL: Sync Branch with Main Before Every Commit (MANDATORY)

**Before making ANY commit, Claude MUST sync the feature branch with `origin/main` to prevent PR merge conflicts.**

This is a **non-negotiable, blocking requirement**. Skipping this step causes dirty PRs that stall the auto-merge pipeline and require manual conflict resolution.

### Required Pre-Commit Sync Ritual

Run these commands **before every `git commit`**, no exceptions:

```bash
# 1. Fetch latest main from remote
git fetch origin main

# 2. Check if main has moved ahead of current branch
git log HEAD..origin/main --oneline

# 3. If output is non-empty (main has new commits) — merge it in
git merge origin/main --no-edit

# 4. Resolve any conflicts (see PR Conflict Resolution section below)
# 5. Only then proceed to commit and push your changes
```

### When to Sync

| Trigger | Action |
|---------|--------|
| Before the **first** commit of a session | Always sync |
| Before **every subsequent** commit | Always sync |
| After any pipeline failure on `main` | Re-sync before fixing |
| When GitHub reports PR as `dirty` / `mergeable: false` | Sync immediately |

### Why This Is Mandatory

- `main` is updated frequently by merged PRs from other sessions
- A single unsynced commit creates a conflict that blocks the entire 7-step pipeline
- Conflict resolution costs more time than a pre-commit sync ever will
- Auto-merge cannot squash a dirty PR — a human push is required to unblock it

> **Rule:** `git fetch origin main && git merge origin/main --no-edit` is the first command of every commit sequence. Treat it like `git add` — it is not optional.

---

## CRITICAL: Session End Protocol (MANDATORY)

**Every Claude session MUST end with a `git push` to the remote branch.**

Commits that are not pushed are invisible to GitHub Actions. The entire auto-merge pipeline (`auto-merge.yml`) depends on a push event to trigger. Local-only commits break the automation.

**Required before ending any session:**
```bash
git push -u origin <current-branch>
```

If nothing new was committed, a push is still safe (it will be a no-op). There is NO valid reason to skip this step. Leaving unpushed commits is a bug in the workflow.

---

## Session Start Protocol — Local Environment Setup (MANDATORY)

**At the start of every Claude Code session, run these checks before touching any code.**

### Step 1 — Verify PAT Token

```bash
echo $PAT   # Must print github_pat_... — if empty, pipeline monitoring is impossible
```

### Step 2 — Verify JDK Version

```bash
java -version   # Must be JDK 21 (Temurin recommended)
```

If wrong version: set `JAVA_HOME` to a JDK 21 installation before running any Gradle command.

### Step 3 — Verify Android SDK

```bash
echo $ANDROID_HOME   # Must point to a valid SDK directory
ls $ANDROID_HOME/platforms | grep android-36   # compileSdk 36 must be installed
```

If missing, install via Android Studio SDK Manager: API 36, Build Tools 35+.

### Step 4 — Verify `local.properties` Exists

```bash
ls local.properties   # Must exist — copy from template if missing
```

If missing:
```bash
cp local.properties.template local.properties
# Fill in at minimum: sdk.dir, ZYNTA_API_BASE_URL, ZYNTA_DB_PASSPHRASE
```

`local.properties` is git-ignored — **never commit it.**

### Step 5 — Verify Gradle Wrapper

```bash
./gradlew --version   # Must succeed — confirms wrapper JAR is intact
```

If it fails, re-download:
```bash
./gradlew wrapper --gradle-version 8.9
```

### Step 6 — Confirm Current Branch

```bash
git branch --show-current   # Should be claude/<task-id>
git status                  # Should be clean or show only intended changes
```

Create branch if not yet on the correct one:
```bash
git checkout -b claude/<task-id>
```

---

### What Runs Locally vs. CI-Only

| Step | Locally? | Command |
|------|----------|---------|
| Shared module tests | ✅ Yes | `./gradlew :shared:core:test :shared:domain:test :shared:security:test --parallel` |
| Full test suite | ✅ Yes | `./gradlew test --parallel --continue` |
| Android debug APK build | ✅ Yes | `./gradlew :composeApp:assembleDebug` |
| Desktop JAR build | ✅ Yes | `./gradlew :composeApp:packageUberJarForCurrentOS` |
| Detekt static analysis | ✅ Yes | `./gradlew detekt` |
| Android Lint | ✅ Yes | `./gradlew lint` |
| SQLDelight code generation | ✅ Yes | `./gradlew generateSqlDelightInterface` |
| Backend Docker services | ❌ CI/VPS only | Images built by `ci-gate.yml`, run on VPS |
| Deploy / Smoke / Verify | ❌ CI/VPS only | Triggered via GitHub Actions after merge |

### Recommended Local Pipeline Run (mirrors Step[1] Branch Validate)

Run this before the first commit of any session to confirm the branch is buildable:

```bash
# Fast pre-commit check (~2 min) — shared module tests only
./gradlew :shared:core:test \
          :shared:domain:test \
          :shared:security:test \
          --parallel --continue

# Full local CI mirror (~15 min) — run before pushing if you made structural changes
./gradlew clean \
          test \
          testDebugUnitTest \
          jvmTest \
          lint \
          assembleDebug \
          :composeApp:packageUberJarForCurrentOS \
          --parallel --continue --stacktrace
```

> **Rule:** If `./gradlew :shared:core:test :shared:domain:test :shared:security:test` fails locally, **do not push**. Fix the failure first. A broken local build will fail Step[1] and stall the entire pipeline.

---

## 🔴 RED ALERT: CI/CD Pipeline Monitoring (MANDATORY)

**After EVERY commit and push, Claude MUST actively monitor the CI/CD pipeline end-to-end BEFORE making any further commits.**

This is a **blocking requirement**. Do NOT proceed with additional code changes until the full pipeline chain completes and all steps are green.

---

### Pipeline Architecture (7-Step Chain)

This project uses a **chained repository_dispatch pipeline** — each step triggers the next only on success:

```
Push to claude/* branch
        │
        ▼
Step[1]: Branch Validate   (ci-branch-validate.yml)
  • Build-only: shared modules + Android APK + Desktop JAR
  • NO tests or lint (fast ~10 min)
  • On success → dispatches "auto-pr-trigger"
        │
        ▼
Step[2]: Auto PR           (ci-auto-pr.yml)
  • Creates PR targeting main (idempotent — reuses existing PR)
  • Enables auto-merge (squash + delete branch)
  • PAT_TOKEN required to trigger CI Gate on the PR
        │
        ▼
Step[3+4]: CI Gate         (ci-gate.yml)
  • Full build + Android Lint + Detekt + allTests
  • Backend compile + unit tests + Kover coverage (api, license, sync)
  • Admin panel TypeScript + ESLint + Vitest + Playwright
  • Flyway migration validation, docker-compose validation
  • OWASP security scan (advisory — continue-on-error)
  • Publishes JUnit XML as PR check annotations
  • "CI Gate Status" job aggregates ALL jobs → single required branch protection check
  • Builds & pushes backend Docker images to GHCR (push-to-main only)
  • On push-to-main success (all jobs green) → dispatches "deploy-trigger"
  • Blocks PR merge until "CI Gate Status" passes
        │ (after auto-merge squashes PR into main)
        ▼
Step[5]: Deploy to VPS     (cd-deploy.yml)
  • SSH into Contabo VPS
  • git reset --hard <exact-SHA>
  • docker compose pull + up -d
  • On success → dispatches "smoke-trigger"
        │
        ▼
Step[6]: Smoke Test        (cd-smoke-rollback.yml)
  • Hits live endpoints to verify deployment
  • Auto-rollback on failure
        │
        ▼
Step[7]: Verify Endpoints  (cd-verify-endpoints.yml)
  • Deep endpoint validation post-smoke
```

---

### Cloudflare API Access (use Global API Key for Email Routing)

For Cloudflare Email Routing operations, use the Global API Key (not the scoped API Token which lacks Email Routing permissions):

```bash
# Auth format: X-Auth-Email + X-Auth-Key (Global API Key)
# These are stored as GitHub Secrets: CF_AUTH_EMAIL, CF_GLOBAL_API_KEY
# Use them in GitHub Actions workflows — they are NOT available locally.

# To manage Email Routing, use the cf-email-fix.yml workflow:
curl -s -X POST -H "Authorization: token $PAT" \
  -H "Accept: application/vnd.github.v3+json" \
  "https://api.github.com/repos/sendtodilanka/ZyntaPOS-KMM/actions/workflows/cf-email-fix.yml/dispatches" \
  -d '{"ref":"main","inputs":{"action":"debug"}}'

# Actions: debug (read-only), setup-rules, deploy-worker, full-fix
```

**Email architecture (inbound):**
- MX records → Cloudflare Email Routing (`route{1,2,3}.mx.cloudflare.net`)
- Support inboxes (support@, billing@, bugs@, alerts@) → CF Worker → POST to API (HMAC-signed)
- Staff mailboxes (*@zyntapos.com catch-all) → CF Worker → HTTP-to-SMTP relay → Stalwart
- VPS port 25 is **blocked externally** (Contabo firewall) — direct SMTP delivery is impossible
- HTTP-to-SMTP relay (`email-relay` container) bridges HTTPS → local SMTP on the Docker network:
  ```
  CF Worker → HTTPS POST mail.zyntapos.com/relay/email
    → Caddy → email-relay:8025 → SMTP stalwart:25 (Docker internal)
  ```
- Relay authenticates via shared secret (`EMAIL_RELAY_SECRET` in .env + CF Worker secret)
- Stalwart handles IMAP/SMTP for staff (iOS Mail, Outlook, etc.)

**Email architecture (outbound):**
- Stalwart sends via SMTP (port 465/587)
- DKIM signed (RSA selector `202603r`, Ed25519 selector `202603e`)
- SPF: `v=spf1 include:_spf.mx.cloudflare.net a:mail.zyntapos.com -all`
- DMARC: `v=DMARC1; p=quarantine; rua=mailto:dmarc@zyntapos.com`

---

### GitHub API Access (MANDATORY — use curl with PAT, NOT gh CLI)

The git remote points to a local proxy (`127.0.0.1`), so `gh` CLI cannot authenticate with GitHub. Use `curl` with the `$PAT` environment variable instead. The PAT is always available in the session environment.

```bash
# Verify PAT is available
echo $PAT   # should print github_pat_...

# Auth format: always use "token $PAT" (NOT "Bearer $PAT")
REPO="sendtodilanka/ZyntaPOS-KMM"
BRANCH=$(git branch --show-current)

# Check workflow runs on current branch
curl -s -H "Authorization: token $PAT" \
  "https://api.github.com/repos/$REPO/actions/runs?branch=$BRANCH&per_page=5" \
  | python3 -c "
import sys,json
for r in json.load(sys.stdin).get('workflow_runs',[]):
  print(f'[{r[\"status\"]:10}] [{(r[\"conclusion\"] or \"pending\"):10}] {r[\"name\"]} (#{r[\"run_number\"]})')
"

# Check open PRs for current branch
curl -s -H "Authorization: token $PAT" \
  "https://api.github.com/repos/$REPO/pulls?head=sendtodilanka:$BRANCH&state=open" \
  | python3 -c "
import sys,json
prs=json.load(sys.stdin)
if not prs: print('No open PRs')
for pr in prs:
  print(f'PR #{pr[\"number\"]}: mergeable={pr.get(\"mergeable\")} state={pr.get(\"mergeable_state\")}')
"

# Check PR checks/status by PR number
PR=<number>
SHA=$(curl -s -H "Authorization: token $PAT" \
  "https://api.github.com/repos/$REPO/pulls/$PR" | python3 -c "import sys,json; print(json.load(sys.stdin)['head']['sha'])")
curl -s -H "Authorization: token $PAT" \
  "https://api.github.com/repos/$REPO/commits/$SHA/check-runs" \
  | python3 -c "
import sys,json
for r in json.load(sys.stdin).get('check_runs',[]):
  print(f'[{r[\"status\"]:10}] [{(r[\"conclusion\"] or \"pending\"):10}] {r[\"name\"]}')
"
```

> **Session start checklist:** At the start of every session, run `echo $PAT` to confirm the token is present before attempting any pipeline monitoring.

---

### Live Monitoring Protocol (MANDATORY after every push)

> **IMPORTANT:** Claude must NEVER manually create a PR. Step[2] (ci-auto-pr.yml) creates the PR automatically after Step[1] passes. Claude's only job is to watch and wait.

```bash
REPO="sendtodilanka/ZyntaPOS-KMM"
BRANCH=$(git branch --show-current)

# Step[1] — Watch Branch Validate (triggers immediately on push)
curl -s -H "Authorization: token $PAT" \
  "https://api.github.com/repos/$REPO/actions/runs?branch=$BRANCH&per_page=5" \
  | python3 -c "import sys,json; [print(f'[{r[\"status\"]:10}][{(r[\"conclusion\"] or \"pending\"):10}] {r[\"name\"]}') for r in json.load(sys.stdin).get('workflow_runs',[])]"

# Step[2] — Confirm PR auto-created (do NOT create manually)
curl -s -H "Authorization: token $PAT" \
  "https://api.github.com/repos/$REPO/pulls?head=sendtodilanka:$BRANCH&state=open" \
  | python3 -c "import sys,json; prs=json.load(sys.stdin); print('PR #'+str(prs[0]['number']) if prs else 'No PR yet')"

# Step[3+4] — Watch CI Gate checks on the PR
# (use PR check-runs command above with the PR number)

# Steps 5-7 — Watch deploy chain (only after PR merges to main)
curl -s -H "Authorization: token $PAT" \
  "https://api.github.com/repos/$REPO/actions/runs?branch=main&per_page=10" \
  | python3 -c "
import sys,json
for r in json.load(sys.stdin).get('workflow_runs',[]):
  if any(x in r['name'] for x in ['Deploy','Smoke','Verify']):
    print(f'[{r[\"status\"]:10}][{(r[\"conclusion\"] or \"pending\"):10}] {r[\"name\"]}')
"
```

**Do NOT start the next implementation task until ALL applicable steps are green.**

**Claude ONLY intervenes when:**
1. The PR has merge conflicts with `main` (Step[2] created it but auto-merge is blocked)
2. A pipeline step fails (any of Steps 1–7 returns a non-green status)

**In all other cases — let the pipeline run naturally without touching it.**
---

### PR Conflict Resolution (MANDATORY — NO REBASE)

When Step[2] creates a PR and it has merge conflicts with `main`:

1. **Check for conflicts:**
   ```bash
   gh pr view <pr-number> --json mergeable,mergeStateStatus
   ```

2. **Resolve by merging main INTO the branch (NOT rebase):**
   ```bash
   git fetch origin main
   git merge origin/main --no-edit   # merge main in, resolve conflicts
   # Fix any conflict markers manually
   git add <resolved-files>
   git commit -m "merge: resolve conflicts with main"
   git push -u origin $(git branch --show-current)
   ```

3. **NEVER use `git rebase` for conflict resolution** — it rewrites history and breaks the auto-merge chain.

4. After push, re-monitor from Step[1] (pipeline restarts automatically).

---

### Failure Protocol

**If any step fails:**
- Do NOT push more commits hoping it fixes itself
- Run `gh run view <run-id> --log-failed` to read the exact failure
- Identify root cause, fix locally, then commit and push
- Re-monitor the ENTIRE pipeline from Step[1] after the fix push

**Why this matters:**
- Stacking commits on a broken pipeline creates cascading conflicts
- Each failed step blocks the entire downstream chain
- Multiple untested commits make it impossible to isolate which change broke the build

---

## Project Overview

**ZyntaPOS** is an enterprise-grade, offline-first Point of Sale system built with **Kotlin Multiplatform (KMM)** and **Compose Multiplatform**. It targets Android tablets (minSdk 24) and JVM desktop (macOS, Windows, Linux) with 100% shared business logic.

- **Company:** Zynta Solutions Pvt Ltd
- **Package:** `com.zyntasolutions.zyntapos`
- **Design system prefix:** `Zynta*`
- **Version:** 1.0.0 MVP (Phase 1 in progress)
- **Languages:** 100% Kotlin — no Java

---

## Repository Layout

```
ZyntaPOS-KMM/
├── androidApp/                        # Android application shell (AGP 9.0+)
├── composeApp/                        # Compose Multiplatform KMP root
│   ├── src/                           # App() root composable, platform stubs
│   ├── core/                          # BaseViewModel<S,I,E> for all features
│   ├── designsystem/                  # Material 3 theme + reusable Zynta* components
│   ├── navigation/                    # Type-safe NavHost, RBAC route gating
│   └── feature/                       # 17 feature modules (see Module Map below)
├── shared/                            # 100% cross-platform business logic
│   ├── core/                          # MVI base, Result<T>, utilities (pure Kotlin)
│   ├── domain/                        # Domain models, use-case interfaces, repo contracts
│   ├── data/                          # SQLDelight (encrypted), Ktor HTTP, sync engine
│   ├── hal/                           # Hardware Abstraction Layer (printer, scanner, cash drawer)
│   ├── security/                      # AES-256-GCM, Keystore/JCE, RBAC, session mgmt
│   └── seed/                          # Debug-only seed data (SeedRunner + JSON fixtures)
├── tools/
│   └── debug/                         # In-app 6-tab developer console
├── docs/
│   ├── adr/                           # Architecture Decision Records (ADR-001 … ADR-005)
│   ├── architecture/                  # Diagrams and module dependency graphs
│   ├── audit/                         # Phase 1–4 audit reports
│   └── ai_workflows/                  # AI execution logs
├── config/detekt/                     # Static analysis rules (detekt.yml)
├── gradle/libs.versions.toml          # Centralized version catalog (250 lines)
├── gradle_commands.md                 # Full Gradle command reference
├── build.gradle.kts                   # Root build (plugins, Detekt, version props)
├── settings.gradle.kts                # Module registry (26 modules)
├── gradle.properties                  # Build cache, parallelism, JVM memory
├── version.properties                 # Semantic version (1.0.0, BUILD=1)
├── local.properties.template          # Secrets template (local.properties is git-ignored)
├── CONTRIBUTING.md                    # Architecture conventions and code review rules
└── README.md                          # Executive summary and setup guide
```

---

## Module Map (26 Modules)

### Shared Modules (KMP — Pure Business Logic)

| Module | Purpose |
|--------|---------|
| `:shared:core` | Pure Kotlin utilities, MVI base classes, `Result<T>`, `CurrencyUtils`, `DateTimeUtils`, `ValidationUtils`, Koin `coreModule` |
| `:shared:domain` | Domain models (38+), repository interfaces, use-case classes, business-rule validators — **no framework deps** |
| `:shared:data` | SQLDelight schema + DAOs, Ktor HTTP client (GZIP), repository implementations, offline sync engine. Full C6.1 sync stack: `ConflictResolver` (LWW/FIELD_MERGE/APPEND_ONLY via `CrdtStrategy`), `SyncEngine` (priority push, store isolation, conflict detection), `SyncQueueMaintenance` (prune + dedup), `ConflictLogRepositoryImpl` audit trail, version vectors. Multi-store isolation via `store_id` column. |
| `:shared:hal` | `PrinterManager`, `BarcodeScanner` — `expect/actual` platform drivers, `EscPosEncoder`. `CashDrawerController` — NOT YET IMPLEMENTED (Phase 2 backlog) |
| `:shared:security` | `DatabaseKeyManager`/`EncryptionManager` (AES-256-GCM, Keystore/JCE), `PinManager` (SHA-256 + salt), `JwtManager` + `TokenStorage` interface, `RbacEngine` |
| `:shared:seed` | **Debug-only.** `SeedRunner` + JSON fixtures (8 categories, 5 suppliers, 25 products, 15 customers). Use as `debugImplementation` only. |

### UI Infrastructure (Compose Multiplatform)

| Module | Purpose |
|--------|---------|
| `:composeApp` | Root KMP library — `App()` composable, JVM `main.kt`, Android library target |
| `:composeApp:core` | `BaseViewModel<State, Intent, Effect>` — all feature VMs extend this |
| `:composeApp:designsystem` | `ZyntaTheme`, Material 3 tokens, `ZyntaButton/Card/TextField`, `NumericKeypad`, `ReceiptPreview`, responsive breakpoints |
| `:composeApp:navigation` | Type-safe `NavRoute` sealed hierarchy, `ZyntaNavHost`, adaptive nav shell (Rail vs Bottom Bar), RBAC route gating |

### Feature Modules (16)

| Module | Purpose |
|--------|---------|
| `:composeApp:feature:auth` | Login, PIN quick-switch, biometric, auto-lock screen |
| `:composeApp:feature:dashboard` | Home KPI dashboard (sales, orders, low-stock alerts, charts) — 3 responsive layout variants |
| `:composeApp:feature:onboarding` | First-run wizard (business name → admin account); shown exactly once |
| `:composeApp:feature:pos` | Product grid, cart, discounts, payment (split/cash/card), receipt, hold orders, refund |
| `:composeApp:feature:inventory` | Product CRUD, category mgmt, stock levels, adjustments, barcode label print |
| `:composeApp:feature:register` | Cash session open/close, cash in/out, EOD Z-report |
| `:composeApp:feature:reports` | Sales summary, product performance, stock report, CSV/PDF export |
| `:composeApp:feature:settings` | Store profile, tax config, printer setup, user mgmt, security policy, backup/restore |
| `:composeApp:feature:customers` | Customer directory, loyalty accounts, GDPR export |
| `:composeApp:feature:coupons` | Coupon CRUD, promotion rule engine (BOGO / % / threshold) |
| `:composeApp:feature:expenses` | Expense log, P&L statement, cash-flow view |
| `:composeApp:feature:staff` | Employee profiles, shift scheduling, attendance, payroll |
| `:composeApp:feature:multistore` | Store selector, central KPI dashboard, inter-store transfers |
| `:composeApp:feature:admin` | System health, audit-log viewer, DB maintenance, backup management |
| `:composeApp:feature:media` | Product image picker, crop, compression pipeline |
| `:composeApp:feature:accounting` | E-Invoice creation and IRD (Sri Lanka) submission pipeline |
| `:composeApp:feature:diagnostic` | Remote diagnostic consent flow — JIT token decode, operator accept/deny UI, WebSocket relay to technician (ENTERPRISE, TODO-006) |

### Platform Apps / Tools

| Module | Purpose |
|--------|---------|
| `:androidApp` | Android application shell (pure `com.android.application` — required by AGP 9.0+ to be separate from KMP) |
| `:tools:debug` | 6-tab Debug Console (Seeds, Database, Auth, Network, Diagnostics, UI/UX). Always compiled; Koin bindings loaded only when `BuildConfig.DEBUG = true`. |

### Dependency Direction (enforced)

```
Feature Modules → :composeApp:navigation / designsystem / core
                       ↓
               :shared:domain  (use cases, repo interfaces)
                       ↓
               :shared:data    (implementations)
                       ↓
          :shared:security  /  :shared:hal  /  :shared:core
```

**Architecture guard:** `:shared:domain` permits only `:shared:core` as a dependency. It must never import from `:shared:data`, `:shared:hal`, `:shared:security`, or any feature module.

---

## Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Kotlin (100%, no Java) | 2.3.0 |
| Multiplatform | Kotlin Multiplatform + Compose Multiplatform | 1.10.0 |
| UI | Material 3 | 1.10.0-alpha05 |
| State Management | MVI (StateFlow / SharedFlow) | Custom BaseViewModel |
| DI | Koin | 4.1.1 |
| Local DB | SQLDelight 2.0.2 on SQLite + SQLCipher 4.5 (AES-256) | 2.0.2 |
| Networking | Ktor Client | 3.4.1 |
| Serialization | kotlinx-serialization | 1.8.0 |
| Date/Time | kotlinx-datetime | **0.7.1 — pinned** (required by CMP 1.10.0; 0.6.x causes NoSuchMethodError) |
| Image Loading | Coil | 3.0.4 |
| Logging | Kermit | 2.1.0 |
| Testing | Kotlin Test, Mockative 3 (KSP), Turbine, Koin-test | Latest |
| Static Analysis | Detekt | 1.23.8 |
| Secrets | Secrets Gradle Plugin | 2.0.1 |
| Build | Gradle 8.x + Version Catalog | `libs.versions.toml` |
| Android | AGP 8.13.2, minSdk 24, compileSdk/targetSdk 36 | |
| Desktop Serial | jSerialComm | 2.10.4 |
| ML Kit (Android) | Barcode Scanning | 17.3.0 |
| KSP | Forced to 2.3.4 for Mockative 3.0.1 compat with Kotlin 2.3.0 | 2.3.4 |

---

## Architecture Patterns

### Clean Architecture (strict layering)

```
┌─────────────────────────────────────────┐
│  Presentation (Compose Multiplatform)   │
│  :composeApp:feature:* (MVI per screen) │
└────────────────────┬────────────────────┘
                     ↓ (use cases, repo interfaces)
┌─────────────────────────────────────────┐
│  Domain (Pure Business Logic)           │
│  :shared:domain                         │
│  • Models (26 plain Kotlin data classes)│
│  • Use Cases (interfaces + impls)       │
│  • Repository Contracts (interfaces)    │
└────────────────────┬────────────────────┘
                     ↓ (implements contracts)
┌─────────────────────────────────────────┐
│  Data / Infrastructure                  │
│  :shared:data   :shared:security        │
│  :shared:hal    :shared:core            │
└─────────────────────────────────────────┘
```

### MVI Pattern (mandatory for all screens)

Every feature screen follows strict MVI. The `BaseViewModel` lives at:
`composeApp/core/src/commonMain/.../ui/core/mvi/BaseViewModel.kt`

```kotlin
// State — immutable snapshot of screen
data class MyFeatureState(
    val items: List<Item> = emptyList(),
    val isLoading: Boolean = false
)

// Intent — sealed class of all user actions
sealed class MyFeatureIntent {
    data object LoadItems : MyFeatureIntent()
    data class SelectItem(val id: String) : MyFeatureIntent()
}

// Effect — one-shot side effects (navigation, toasts)
sealed class MyFeatureEffect {
    data class ShowError(val message: String) : MyFeatureEffect()
    data object NavigateBack : MyFeatureEffect()
}

// ViewModel — MUST extend BaseViewModel (ADR-001)
class MyFeatureViewModel(
    private val useCase: GetItemsUseCase
) : BaseViewModel<MyFeatureState, MyFeatureIntent, MyFeatureEffect>(
    initialState = MyFeatureState()
) {
    override suspend fun handleIntent(intent: MyFeatureIntent) {
        when (intent) {
            is MyFeatureIntent.LoadItems -> loadItems()
            is MyFeatureIntent.SelectItem -> selectItem(intent.id)
        }
    }

    private suspend fun loadItems() {
        updateState { it.copy(isLoading = true) }
        useCase.execute().fold(
            onSuccess = { updateState { s -> s.copy(items = it, isLoading = false) } },
            onFailure = { sendEffect(MyFeatureEffect.ShowError(it.message ?: "")) }
        )
    }
}
```

**BaseViewModel provides:**
- `StateFlow<State>` for UI observation
- `Channel<Effect>(BUFFERED)` for one-shot side effects
- `updateState { }` — atomic, thread-safe state mutation
- `viewModelScope` — structured coroutine lifecycle
- `abstract suspend fun handleIntent(I)` — single MVI entry point

---

## Critical Naming Conventions

### ADR-002: Domain Model Names (ACCEPTED)

Domain models use **plain, ubiquitous-language names** — no `*Entity` suffix. This boundary is enforced in code review.

```kotlin
// ✅ CORRECT — in :shared:domain/model/
data class Product(val id: String, val name: String, val price: Double, ...)
data class Order(val id: String, val items: List<OrderItem>, ...)

// ❌ WRONG — *Entity suffix is banned in domain layer
data class ProductEntity(...)

// ✅ OK — *Entity suffix is reserved for :shared:data ORM mapping types
// e.g., shared/data/mapper/ProductEntity.kt (hand-written DB mapper)
// SQLDelight auto-generates: Products (table type), ProductsQueries
```

**Code review rule:** If you see `*Entity` in `shared/domain/model/`, request a rename citing ADR-002.

**Domain models (38+ files):** `Product`, `Order`, `OrderItem`, `Customer`, `Category`, `User`, `Role`, `Permission`, `CashRegister`, `RegisterSession`, `CashMovement`, `PaymentMethod`, `PaymentSplit`, `Supplier`, `TaxGroup`, `UnitOfMeasure`, `StockAdjustment`, `SyncOperation`, `SyncStatus`, `OrderStatus`, `OrderType`, `DiscountType`, `CartItem`, `OrderTotals`, `ProductVariant`, `AuditEntry`, `Edition`, `Heartbeat`, `IntegrityReport`, `License`, `LicenseStatus`, `MasterProduct`, `StoreProductOverride`, `WarehouseStock`, `TransitEvent`, `ReplenishmentRule`, `PurchaseOrder`, `PurchaseOrderItem`

### ADR-001: ViewModel Base Class (ACCEPTED)

All ViewModels **MUST** extend `BaseViewModel`. Direct extension of `androidx.lifecycle.ViewModel` is **prohibited** in any feature module.

```kotlin
// ✅ CORRECT
import com.zyntasolutions.zyntapos.ui.core.mvi.BaseViewModel

class MyFeatureViewModel(...) : BaseViewModel<MyState, MyIntent, MyEffect>(MyState.Initial) { ... }

// ❌ WRONG — direct ViewModel extension is rejected in code review
class MyFeatureViewModel(...) : ViewModel() { ... }
```

### Other Naming Conventions

- **Koin DI modules:** declare in `<feature>/di/` package, named `<feature>Module`
- **Design system components:** prefix with `Zynta*` (e.g., `ZyntaButton`, `ZyntaCard`)
- **Use cases:** suffix with `UseCase` (e.g., `GetProductsUseCase`, `CalculateOrderTotalsUseCase`)
- **Repository impls:** suffix with `Impl` (e.g., `ProductRepositoryImpl`)
- **Branches:** `feature/M##-short-description`
- **Commits:** Conventional Commits format (see below)

---

## Dependency Injection (Koin)

- Single cross-platform DI graph
- Platform-specific bindings registered in `androidMain` / `jvmMain` Koin modules
- Feature modules declare Koin modules in their `di/` package
- Never use `GlobalContext.get()` outside of DI bootstrap code
- **Recent refactor:** `loadKoinModules()` global call replaced with `koin.loadModules()` (PR #21) for better testability

---

## Database Layer

**SQLDelight 2.0.2 on SQLite, encrypted with SQLCipher 4.5 (AES-256)**

**61 `.sq` schema files** as of Phase 2 (actual count via `find shared/data/src/commonMain/sqldelight -name "*.sq" | wc -l`). Core tables include: `products`, `orders`, `order_items`, `categories`, `customers`, `suppliers`, `registers`, `settings`, `stock`, `audit_log`, `sync_queue` (outbox), `sync_state`, `version_vectors`, `conflict_log`, `e_invoices`, `employees`, `expenses`, `coupons`, `accounting_entries`, `master_products`, `store_products`, `warehouse_stock`, `stock_transfers`, `transit_tracking`, `replenishment_rules`, `purchase_orders`, and more.

**Key patterns:**
- Products table has `products_fts` (FTS5) with `AFTER INSERT/UPDATE/DELETE` triggers for full-text search
- `sync_queue` table tracks pending offline operations with `sync_status`
- All writes go to local SQLite immediately; background sync engine pushes to cloud
- DB passphrase stored in Android Keystore / JCE KeyStore — **never written to disk in plaintext**

**Code generation:** Run `./gradlew generateSqlDelightInterface` after modifying `.sq` files.

**Schema files:** `shared/data/src/commonMain/sqldelight/` — 61 `.sq` files (actual count; grew from 36 in Phase 1 to 61 in Phase 2 with multi-store features)

---

## Security Architecture

| Component | Implementation |
|-----------|---------------|
| DB Encryption | SQLCipher 4.5, AES-256, passphrase from Keystore |
| Key Storage (Android) | Android Keystore via `androidx.security.crypto` |
| Key Storage (JVM) | JCE KeyStore |
| PIN Hashing | SHA-256 + 16-byte random salt (via `PinManager` in `:shared:security`); constant-time compare |
| JWT Tokens | `JwtManager` (decodes claims, 30s clock-skew buffer) + `TokenStorage` interface backed by `SecurePreferences` (testable via `FakeSecurePreferences`) |
| RBAC | `RbacEngine` in `:shared:security`; `SessionManager` is in `:composeApp:feature:auth` (idle-timeout / PIN-lock, NOT in `:shared:security`) |
| Secrets Injection | Gradle Secrets Plugin: `ZYNTA_*` keys from `local.properties` → `BuildConfig` fields |

**ADR-003:** `SecurePreferences` is canonical in `:shared:security` only. The old `data.local.security.SecurePreferences` interface was deleted. Do not recreate it in `:shared:data`.

**ADR-004:** Keystore token scaffold was removed. Use `TokenStorage` interface backed by `SecurePreferences`.

---

## Build System

### Key Gradle Properties

```properties
# gradle.properties
kotlin.code.style=official
org.gradle.caching=true
org.gradle.parallel=true
org.gradle.configuration-cache=false    # disabled — KMP/Compose CC bugs
org.gradle.jvmargs=-Xmx4g -Xms512m -XX:MaxMetaspaceSize=1g
kotlin.incremental=true
kotlin.incremental.multiplatform=true
sqldelight.verifyMigrations=true
```

### Version Catalog

All dependency versions are in `gradle/libs.versions.toml`. Always add new deps through the catalog — never hardcode versions in module build files.

**Critical pin:** `kotlinx-datetime` is **pinned at 0.7.1** globally in `build.gradle.kts`. This version is required by CMP 1.10.0 (Instant became a typealias for kotlin.time.Instant). Do not downgrade to 0.6.x — it causes NoSuchMethodError on datetime-using screens. The root build script forces this via `resolutionStrategy`.

### Always use the Gradle wrapper

```bash
./gradlew <task>   # correct
gradle <task>      # wrong — may use wrong version
```

---

## Common Gradle Commands

### Build

```bash
./gradlew assemble                              # All modules (debug)
./gradlew :composeApp:assembleDebug             # Android debug APK
./gradlew :composeApp:run                       # Run desktop app
./gradlew :composeApp:packageDistributionForCurrentOS  # Desktop installer
```

### Test

```bash
./gradlew test                                  # All tests, all modules
./gradlew allTests                              # All KMP target tests
./gradlew jvmTest                               # JVM/Desktop tests only
./gradlew testDebugUnitTest                     # Android unit tests only

# Per-module
./gradlew :shared:domain:test
./gradlew :shared:data:jvmTest
./gradlew :composeApp:feature:pos:test

# Single test class
./gradlew :shared:domain:test --tests "com.zentapos.domain.usecase.CalculateOrderTotalsUseCaseTest"
```

### Code Quality

```bash
./gradlew detekt                                # Static analysis (full project)
./gradlew lint                                  # Android Lint
./gradlew :composeApp:feature:pos:lint          # Lint single module
```

### SQLDelight

```bash
./gradlew generateSqlDelightInterface           # Generate all DB interfaces
./gradlew :shared:data:generateCommonMainZyntaPosDatabaseInterface
./gradlew verifySqlDelightMigration
```

### CI Pipeline Commands (from `gradle_commands.md`)

```bash
# Pre-commit check (~2 min)
./gradlew :shared:core:test :shared:domain:test :shared:security:test --parallel --continue

# Full verification before PR merge (~8 min)
./gradlew clean test lint --parallel --continue --stacktrace

# Full CI pipeline (~15 min)
./gradlew clean test testDebugUnitTest jvmTest lint assembleDebug \
          :composeApp:packageUberJarForCurrentOS --parallel --continue --stacktrace
```

### Clean

```bash
./gradlew clean                                 # Clean all build outputs
./gradlew --stop                                # Stop stuck Gradle daemon
./gradlew build --refresh-dependencies          # Force re-download deps
```

---

## Testing Standards

| Layer | Coverage Target   | Tools |
|-------|-------------------|-------|
| Use Cases | 95%+              | Kotlin Test + Mockative 3 |
| Repositories | 95%+              | Kotlin Test + Mockative 3 |
| ViewModels | 95%+              | Kotlin Test + Turbine (for Flows) |
| Compose UI | Need for all flow | Compose UI Test (Phase 2) |

**Kover coverage:** All modules must maintain **95%+ line coverage** as reported by Kover. CI Gate enforces this threshold — PRs dropping below 95% will be blocked.

**Test organization:**
- `src/commonTest/` — shared cross-platform tests (domain logic, use cases)
- `src/jvmTest/` — JVM integration tests (SQLDelight with in-memory DB)
- `src/androidTest/` — reserved for Phase 2 instrumented tests

**Mocking:** Mockative 3 (KSP-based, compatible with Kotlin 2.3.0 via KSP 2.3.4). Use `mock<Interface>` — no hand-written fakes unless testing boundary contracts.

**Flow testing:** Use Turbine for `StateFlow` / `SharedFlow` assertions in ViewModel tests.

---

## Commit Style

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
feat(pos): add hold order use case
fix(inventory): correct stock adjustment calculation
refactor(data): rename mapper to align with ADR-002
docs(adr): accept ADR-003 SecurePreferences consolidation
build(gradle): pin kotlinx-datetime to 0.7.1
test(domain): add CalculateOrderTotalsUseCase coverage
```

Scopes match module names: `pos`, `inventory`, `auth`, `domain`, `data`, `security`, `hal`, `core`, `navigation`, `designsystem`, `settings`, `register`, `reports`, `customers`, `staff`, `admin`, `debug`, `gradle`, `ci`.

---

## Secrets & Local Configuration

**`local.properties` is git-ignored — never commit it.**
Copy `local.properties.template` and fill in values before first build.

| Key | Description |
|-----|-------------|
| `sdk.dir` | Android SDK path |
| `ZYNTA_API_BASE_URL` | Backend REST API base URL |
| `ZYNTA_API_CLIENT_ID` | OAuth2 client ID |
| `ZYNTA_API_CLIENT_SECRET` | OAuth2 client secret |
| `ZYNTA_DB_PASSPHRASE` | AES-256 passphrase for SQLCipher (generate: `openssl rand -hex 32`) |
| `ZYNTA_FCM_VAPID_PUBLIC_KEY` | FCM v1 VAPID public key (Web Push) |
| `ZYNTA_FCM_VAPID_PRIVATE_KEY` | FCM v1 VAPID private key (Web Push) |
| `ZYNTA_SENTRY_DSN` | Sentry crash reporting DSN (Android) |
| `ZYNTA_IRD_API_ENDPOINT` | IRD e-invoice API endpoint (Sri Lanka) |
| `ZYNTA_IRD_CLIENT_CERTIFICATE_PATH` | Absolute path to IRD `.p12` certificate |
| `ZYNTA_IRD_CERTIFICATE_PASSWORD` | IRD certificate password |
| `RESEND_API_KEY` | Resend transactional email API key (TODO-008a) |
| `EMAIL_FROM_ADDRESS` | Sender email address (e.g. `noreply@zyntapos.com`) |

> **FCM Note:** Firebase Legacy Server Key was permanently disabled by Google (June 2024).
> `ZYNTA_FCM_SERVER_KEY` is replaced by `ZYNTA_FCM_SERVICE_ACCOUNT_JSON` (GitHub Secret — too large for `local.properties`).
> Backend services use `firebase-admin` SDK with the service account JSON for FCM v1 HTTP API.

**All GitHub Secrets** (28 configured — stored in repository, not `local.properties`):

| Secret | Purpose |
|--------|---------|
| `PAT_TOKEN` | Repository dispatch + GHCR pull on VPS |
| `VPS_HOST` | Contabo VPS IP address |
| `VPS_USER` | SSH username (`deploy`) |
| `VPS_PORT` | SSH port |
| `VPS_USER_KEY` | SSH private key for `deploy` user |
| `VPS_ROOT` | VPS root username (one-time setup only) |
| `VPS_ROOT_KEY` | SSH private key for root (one-time setup only) |
| `DEPLOY_SSH_PRIVATE_KEY` | Alternate deploy key |
| `CF_ORIGIN_CERT` | Cloudflare Origin Certificate (PEM) |
| `CF_ORIGIN_KEY` | Cloudflare Origin Certificate private key (PEM) |
| `CLOUDFLARE_TUNNEL_TOKEN` | Cloudflare Tunnel token (optional Zero Trust) |
| `CLOUDFLARE_ACCOUNT_ID` | Cloudflare Account ID |
| `CLOUDFLARE_API_TOKEN` | Cloudflare API Token (scoped — no Email Routing perms) |
| `CF_GLOBAL_API_KEY` | Cloudflare Global API Key (full access — use for Email Routing) |
| `CF_AUTH_EMAIL` | Cloudflare account email (`mecduino@gmail.com`) — paired with `CF_GLOBAL_API_KEY` |
| `GOOGLE_SERVICES_JSON` | `google-services.json` for Firebase Android SDK |
| `ZYNTA_FCM_SERVICE_ACCOUNT_JSON` | Firebase Admin SDK service account (FCM v1 push notifications) |
| `ZYNTA_FCM_VAPID_PUBLIC_KEY` | VAPID public key for Web Push |
| `ZYNTA_FCM_VAPID_PRIVATE_KEY` | VAPID private key for Web Push |
| `GA4_MEASUREMENT_ID` | Google Analytics 4 Measurement ID |
| `SENTRY_AUTH_TOKEN` | Sentry CLI auth token |
| `SENTRY_DSN_API` | Sentry DSN for `zyntapos-api` |
| `SENTRY_DSN_LICENSE` | Sentry DSN for `zyntapos-license` |
| `SENTRY_DSN_SYNC` | Sentry DSN for `zyntapos-sync` |
| `SLACK_WEBHOOK_URL` | Slack webhook for Falco security alerts |
| `NVD_API_KEY` | OWASP Dependency Check NVD API key |
| `CHATWOOT_API_TOKEN` | Chatwoot API user access token (API-based integrations) |
| `CHATWOOT_ACCOUNT_ID` | Chatwoot account ID (`1`) |

See `docs/architecture/deployment.md` → "GitHub Secrets required" for full details and generation instructions.

---

## CI/CD

**Two GitHub Actions workflows:**

### `.github/workflows/ci.yml` — Continuous Integration
- Triggers: push to `main`/`develop`, PR to `main`
- Runner: `ubuntu-latest`, JDK 21 (Temurin), 60-min timeout
- Steps: build shared modules → build Android debug APK → build Desktop JVM JAR → Android Lint → Detekt → all tests → upload artifacts (APK, test reports, lint reports, 7-day retention)

### `.github/workflows/release.yml` — Release
- Triggers: push to `main`
- Runs on 4 matrix runners: ubuntu, macos, windows
- Produces: Android APK (signed), macOS DMG, Windows MSI, Linux DEB
- Creates GitHub Release tagged `v1.0.0-build.{run_number}`

### GitHub Secrets (Required for CI/CD & FTS)

All 26 secrets are configured. See "Secrets & Local Configuration" section above for the full list.

| Secret | Purpose | Used by |
|--------|---------|---------|
| `PAT_TOKEN` | Repository dispatch + GHCR pull on VPS | ci-gate, cd-deploy |
| `VPS_HOST` | Contabo VPS IP address | FTS Steps 1-6, cd-deploy |
| `VPS_USER` | SSH username (`deploy`) | FTS Steps 1-6, cd-deploy |
| `VPS_PORT` | SSH port | FTS Steps 1-6, cd-deploy |
| `VPS_USER_KEY` | SSH private key for `deploy` user | FTS Steps 1-6, cd-deploy |
| `CF_ORIGIN_CERT` | Cloudflare Origin Certificate (PEM) — TLS between CF edge and VPS | FTS Step 4 |
| `CF_ORIGIN_KEY` | Cloudflare Origin Certificate private key (PEM) | FTS Step 4 |
| `CLOUDFLARE_TUNNEL_TOKEN` | Cloudflare Tunnel token for Zero Trust access (optional) | FTS Step 4, Step 5 |
| `SLACK_WEBHOOK_URL` | Slack webhook for Falco security alerts (optional) | FTS Step 4 |
| `ZYNTA_FCM_SERVICE_ACCOUNT_JSON` | Firebase Admin SDK service account — FCM v1 push notifications | backend services |
| `CHATWOOT_API_TOKEN` | Chatwoot API user access token | Chatwoot API integrations |
| `CHATWOOT_ACCOUNT_ID` | Chatwoot account ID (`1`) | Chatwoot API integrations |

---

## 🔴 VPS SSH Access (MANDATORY — GitHub Actions Only)

**Claude CANNOT SSH directly into the VPS from a local terminal session.** There is no direct SSH access from the Claude Code environment. All VPS operations MUST be performed via GitHub Actions workflows.

### Why

The VPS SSH private key (`VPS_USER_KEY`), host (`VPS_HOST`), port (`VPS_PORT`), and user (`VPS_USER`) are stored **exclusively as GitHub Secrets**. They are never exposed in the local environment or `local.properties`. The only way to run commands on the VPS is to trigger a GitHub Actions workflow that uses these secrets.

### How to Run Commands on the VPS

**Option 1 — Use an existing workflow (preferred)**

Trigger the relevant workflow via `repository_dispatch` or `workflow_dispatch`:

```bash
# Trigger cd-deploy manually (re-deploys current main SHA)
curl -s -X POST -H "Authorization: token $PAT" \
  -H "Accept: application/vnd.github.v3+json" \
  "https://api.github.com/repos/sendtodilanka/ZyntaPOS-KMM/actions/workflows/cd-deploy.yml/dispatches" \
  -d '{"ref":"main"}'
```

**Option 2 — Create a one-off workflow**

For custom VPS commands (e.g., check logs, restart a container, reset the DB), create a temporary workflow file that uses the stored secrets:

```yaml
# .github/workflows/vps-adhoc.yml  (delete after use)
name: VPS Ad-hoc
on:
  workflow_dispatch:
    inputs:
      command:
        description: 'Shell command to run on VPS'
        required: true

jobs:
  run:
    runs-on: ubuntu-latest
    steps:
      - uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.VPS_HOST }}
          username: ${{ secrets.VPS_USER }}
          port: ${{ secrets.VPS_PORT }}
          key: ${{ secrets.VPS_USER_KEY }}
          script: ${{ github.event.inputs.command }}
```

Then trigger it via curl:
```bash
curl -s -X POST -H "Authorization: token $PAT" \
  -H "Accept: application/vnd.github.v3+json" \
  "https://api.github.com/repos/sendtodilanka/ZyntaPOS-KMM/actions/workflows/vps-adhoc.yml/dispatches" \
  -d '{"ref":"main","inputs":{"command":"cd /opt/zyntapos && docker compose ps"}}'
```

### Available VPS Workflows

| Workflow | File | Purpose |
|----------|------|---------|
| Deploy | `cd-deploy.yml` | git reset + docker compose pull + up |
| Smoke Test | `cd-smoke-rollback.yml` | Hit live endpoints, auto-rollback on failure |
| Verify Endpoints | `cd-verify-endpoints.yml` | Deep endpoint validation post-smoke |

> **Session start rule:** If you need to run anything on the VPS, do NOT attempt direct SSH. Identify the right workflow above or create a one-off `vps-adhoc.yml`. All required credentials are already in GitHub Secrets — nothing needs to be provided locally.

---

## Architecture Decision Records (ADRs)

All structural decisions are documented in `docs/adr/`. Create a new ADR before introducing new conventions, modules, or tech.

| ADR | Title | Status |
|-----|-------|--------|
| ADR-001 | ViewModel Base Class Policy — all VMs extend `BaseViewModel<S,I,E>` | ACCEPTED |
| ADR-002 | Domain Model Naming — no `*Entity` suffix in `:shared:domain` | ACCEPTED |
| ADR-003 | `SecurePreferences` Interface Consolidation — canonical in `:shared:security` only | ACCEPTED |
| ADR-004 | Keystore Token Scaffold Removal — use `TokenStorage` interface | ACCEPTED |
| ADR-005 | Single Admin Account Management — one ADMIN via `isSystemAdmin` flag; SignUp removed | ACCEPTED |
| ADR-006 | Backend Docker Build in CI — images built by CI Gate, pushed to GHCR | ACCEPTED |
| ADR-007 | Database-Per-Service — API uses `zyntapos_api`, License uses `zyntapos_license` | ACCEPTED |
| ADR-008 | RS256 Key Distribution — Bundle default key + SecurePreferences cache (TOFU); `GET /.well-known/public-key` | ACCEPTED |
| ADR-009 | Admin Panel / POS App Feature Boundary — admin panel MUST NOT contain store-operational write features | ACCEPTED |

---

## Koin Module Loading Order (Critical)

`ZyntaApplication` initializes Koin in this exact 7-tier order. Changing the order will cause missing binding errors at runtime.

1. `coreModule` — Logger, CurrencyFormatter, Dispatchers (IO, Main, Default)
2. `securityModule` — Encryption, JWT, PIN hashing, RBAC
3. `halModule()` — Printer port, Barcode scanner port
4. `androidDataModule` / `desktopDataModule` — Platform DB driver (SQLCipher/JVM SQLite), Keystore, Network monitor, SecurePreferences
5. `dataModule` — Repositories, SyncEngine, ApiService
6. `navigationModule` — Navigation graph bindings (RBAC logic inlined via `RbacEngine`)
7. Feature modules (16) + `debugModule` / `seedModule` (debug builds only)

`SecurePreferencesKeyMigration.migrate()` is called before any auth operations during startup.

**Named dispatcher qualifiers:**
```kotlin
val IO_DISPATCHER = named("IO")      // For repository/database work
val MAIN_DISPATCHER = named("Main")  // For UI updates
val DEFAULT_DISPATCHER = named("Default")  // For CPU-bound computation
```

---

## Navigation Routes

Type-safe navigation using `kotlinx.serialization`. Routes are defined in `:composeApp:navigation` as a sealed class hierarchy.

```kotlin
sealed class ZyntaRoute {
    // Graph nodes
    data object AuthGraph : ZyntaRoute()
    data object MainGraph : ZyntaRoute()
    data object InventoryGraph : ZyntaRoute()
    data object RegisterGraph : ZyntaRoute()
    data object ReportsGraph : ZyntaRoute()
    data object SettingsGraph : ZyntaRoute()

    // Auth
    data object Login : ZyntaRoute()
    data object PinLock : ZyntaRoute()

    // Main
    data object Dashboard : ZyntaRoute()
    data object Pos : ZyntaRoute()
    data class Payment(val orderId: String) : ZyntaRoute()

    // Inventory
    data object ProductList : ZyntaRoute()
    data class ProductDetail(val productId: String? = null) : ZyntaRoute()
    data object CategoryList : ZyntaRoute()
    // ... additional routes
}
```

**Deep link scheme:** `zyntapos://`
- Product: `zyntapos://product/{barcode}`
- Order: `zyntapos://order/{orderId}`

**RBAC gating:** Navigation items are filtered by role within `ZyntaNavGraph.kt` / `NavigationItems.kt` in `:composeApp:navigation`. A standalone `RbacNavFilter` class does not exist in the codebase; RBAC gating logic is inlined in the nav graph composables using `RbacEngine` from `:shared:security`.

---

## Network Layer

**Ktor HttpClient configuration** (`ApiClient.kt` in `:shared:data`):

```kotlin
install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
install(Auth) {
    bearer {
        loadTokens { BearerTokens(accessToken, refreshToken) }
        refreshTokens { /* calls refresh endpoint on 401 */ }
    }
}
install(HttpTimeout) {
    connectTimeoutMillis = 10_000L
    requestTimeoutMillis = 30_000L
}
install(HttpRequestRetry) {
    retryOnServerErrors(maxRetries = 3)
    exponentialDelay(base = 2.0, maxDelayMs = 4_000L)  // delays: 1s, 2s, 4s
}
```

**API Service interface** (`ApiService.kt`):
- `login()`, `refreshToken()`, `getProducts()`, `pushOperations()`, `pullOperations()`
- HTTP errors are mapped to typed domain exceptions: `AuthException`, `NetworkException`, `SyncException`

---

## Reactive Patterns

**POS product search (canonical reactive pipeline):**

```kotlin
combine(_searchQuery.debounce(300L), _selectedCategoryId)
    .distinctUntilChanged()
    .flatMapLatest { (query, categoryId) ->
        if (query.isBlank() && categoryId == null) {
            productRepository.getAll()
        } else {
            productRepository.search(query, categoryId)
        }
    }
    .onEach { products -> updateState { copy(products = products) } }
    .launchIn(viewModelScope)
```

Use `debounce(300L)` on search queries to avoid excessive DB/network calls. Use `flatMapLatest` (not `flatMapMerge`) for queries so previous in-flight searches are cancelled.

---

## Hardware Abstraction Layer (HAL)

All hardware I/O is behind `expect/actual` interfaces in `:shared:hal`. Business logic **never** imports USB or socket libraries directly.

**Port interfaces:**
```kotlin
interface ReceiptPrinterPort {
    suspend fun printReceipt(order: Order, cashierId: String): Result<Unit>
    suspend fun printZReport(report: ZReportData): Result<Unit>
    suspend fun testPrint(): Result<Unit>
}

interface BarcodeScanner {
    val scanEvents: Flow<ScanResult>
    suspend fun startListening(): Result<Unit>
    suspend fun stopListening()
}
```

**Android implementations:** USB Host API (hardware scanners), ML Kit Vision (camera barcode), ESC/POS over USB
**JVM implementations:** HID keyboard emulation, TCP/IP socket (network printers), jSerialComm (serial port)

---

## Security Details

| Component | Implementation | Notes |
|-----------|---------------|-------|
| JWT | `JwtManager` | Decodes claims only (no local sig verification); 30s clock-skew buffer |
| PIN | `PinManager` (object) | SHA-256 + 16-byte random salt; format `<base64url-salt>:<hex-hash>`; constant-time compare |
| DB Key | `DatabaseKeyManager` (expect/actual) | Android: envelope encryption — DEK wrapped by non-extractable KEK in Android Keystore; Desktop: 256-bit AES key in PKCS12 KeyStore at `~/.zyntapos/.db_keystore.p12`; passphrase never written to disk in plaintext |
| Prefs | `SecurePreferences` (expect/actual) | Android: EncryptedSharedPreferences (AES-256-SIV keys, AES-256-GCM values); JVM: AES-GCM encrypted properties file |
| Roles (KMM app) | `RbacEngine` | Stateless; roles: ADMIN, MANAGER, CASHIER, CUSTOMER_SERVICE, REPORTER — these are **POS store user roles** |
| Roles (admin panel) | `admin-panel/src/hooks/use-auth.ts` | ADMIN, OPERATOR, FINANCE, AUDITOR, HELPDESK — these are **Zynta Solutions internal staff roles** (separate system) |
| Session | `SessionManager` | In `:composeApp:feature:auth` (NOT `:shared:security`); idle-timeout countdown, emits `AuthEffect.ShowPinLock` |

---

## Common Pitfalls to Avoid

1. **Do not extend `androidx.lifecycle.ViewModel` directly.** Always extend `BaseViewModel<S,I,E>` (ADR-001).
2. **Do not add `*Entity` suffix to domain models.** Use plain names in `shared/domain/model/` (ADR-002).
3. **Do not import `:shared:data`, `:shared:hal`, or `:shared:security` from `:shared:domain`.** Only `:shared:core` is permitted.
4. **Do not hardcode `kotlinx-datetime` version.** It is globally pinned to 0.7.1 in root `build.gradle.kts`.
5. **Do not put security primitives in `:shared:data`.** Encrypted key-value storage belongs only in `:shared:security` (ADR-003).
6. **Do not commit `local.properties`.** It contains encryption keys and API credentials.
7. **Do not use `loadKoinModules(global=true)`.** Use `koin.loadModules()` instead (PR #21 migration).
8. **Do not use `GlobalContext.get()` outside DI bootstrap code.**
9. **Do not add `:shared:seed` as a regular dependency.** It must be `debugImplementation` only.
10. **Do not run bare `gradle` — always use `./gradlew`** to ensure the correct Gradle wrapper version.
11. **Do not add cross-database FK constraints in backend migrations.** API and License use separate databases (`zyntapos_api`, `zyntapos_license`) — validate references at app layer (ADR-007).
12. **Do not share Flyway migrations between services.** Each service owns its own `db/migration/` directory and schema history.
13. **Do not add store-operational write features to the admin panel.** Stock transfers, replenishment rules, pricing rules, tax rate CRUD — all store-level business operations belong in the KMM app with POS JWT auth (`/v1/*` endpoints). The admin panel is for Zynta Solutions platform operations only (licenses, monitoring, support, master catalog). If support needs store data for debugging, use the remote diagnostic system (TODO-006), not admin panel views (ADR-009).

---

## Backend Database Architecture (ADR-007)

Each backend service uses its own PostgreSQL database on the same instance:

| Service | Database | Flyway migrations |
|---------|----------|-------------------|
| API (`zyntapos-api`) | `zyntapos_api` | `backend/api/src/main/resources/db/migration/` |
| License (`zyntapos-license`) | `zyntapos_license` | `backend/license/src/main/resources/db/migration/` |
| Sync (`zyntapos-sync`) | _(none — Redis only)_ | N/A |

**Init script:** `backend/postgres/init-databases.sh` creates per-service databases on first PostgreSQL volume init.

**To reset databases:** Remove the `pgdata` volume so the init script runs again:
```bash
docker compose down && docker volume rm zyntapos_pgdata && docker compose up -d
```

Or use the "VPS Full Fix" workflow with `reset_db=yes`.

---

## Backend Architecture (3 Microservices)

### Service Topology

| Service | Port | Database | Purpose |
|---------|------|----------|---------|
| `zyntapos-api` | 8081 | `zyntapos_api` (PostgreSQL) | REST API for POS app + Admin panel |
| `zyntapos-sync` | 8082 | None (stateless) | WebSocket real-time sync relay |
| `zyntapos-license` | 8083 | `zyntapos_license` (PostgreSQL) | License activation, heartbeat, device management |

### Backend Tech Stack

| Layer | Technology |
|-------|-----------|
| Framework | Ktor 3.4.1 (CIO transport) |
| ORM | Exposed 0.61.0 |
| Migrations | Flyway (API: 31 migrations, License: 4 migrations) |
| DI | Koin 4.1.1 |
| Auth (POS) | RS256 JWT (asymmetric) — API signs, all services verify |
| Auth (Admin) | HS256 JWT (symmetric) — shared secret between API and License |
| Redis | Lettuce 6.6.0 — pub/sub for sync notifications |
| Password (POS) | SHA-256 + salt (PinManager format) with brute-force lockout |
| Password (Admin) | BCrypt cost 12 with account lockout |
| Crash Reporting | Sentry |
| Containers | Docker Compose (10 containers: api, license, sync, postgres, redis, caddy, etc.) |

### Key Backend Files

| What | Where |
|------|-------|
| API entry point | `backend/api/src/main/kotlin/.../Application.kt` |
| API route registration | `backend/api/src/main/kotlin/.../plugins/Routing.kt` |
| POS user auth + brute-force | `backend/api/src/main/kotlin/.../service/UserService.kt` |
| Admin auth (bcrypt, MFA) | `backend/api/src/main/kotlin/.../service/AdminAuthService.kt` |
| License validation client | `backend/api/src/main/kotlin/.../service/LicenseValidationClient.kt` |
| Admin user repository | `backend/api/src/main/kotlin/.../repository/AdminUserRepository.kt` |
| POS user repository | `backend/api/src/main/kotlin/.../repository/PosUserRepository.kt` |
| Product repository | `backend/api/src/main/kotlin/.../repository/ProductRepository.kt` |
| Sync push/pull processor | `backend/api/src/main/kotlin/.../sync/SyncProcessor.kt` |
| Entity applier (normalized tables) | `backend/api/src/main/kotlin/.../sync/EntityApplier.kt` |
| Conflict resolver (LWW) | `backend/api/src/main/kotlin/.../sync/ServerConflictResolver.kt` |
| GDPR customer data export | `backend/api/src/main/kotlin/.../routes/ExportRoutes.kt` |
| Audit trail service | `backend/api/src/main/kotlin/.../service/AdminAuditService.kt` |
| API Flyway migrations | `backend/api/src/main/resources/db/migration/V1-V31` |
| Admin inventory routes | `backend/api/src/main/kotlin/.../routes/AdminInventoryRoutes.kt` |
| Admin transfer routes (IST) | `backend/api/src/main/kotlin/.../routes/AdminTransferRoutes.kt` |
| Admin replenishment routes | `backend/api/src/main/kotlin/.../routes/AdminReplenishmentRoutes.kt` |
| Warehouse stock repository | `backend/api/src/main/kotlin/.../repository/WarehouseStockRepository.kt` |
| Replenishment repository | `backend/api/src/main/kotlin/.../repository/ReplenishmentRepository.kt` |
| License service entry | `backend/license/src/main/kotlin/.../Application.kt` |
| License Flyway migrations | `backend/license/src/main/resources/db/migration/V1-V4` |
| Sync WebSocket hub | `backend/sync/src/main/kotlin/.../hub/WebSocketHub.kt` |
| Diagnostic WebSocket | `backend/sync/src/main/kotlin/.../routes/DiagnosticWebSocketRoutes.kt` |
| Redis pub/sub listener | `backend/sync/src/main/kotlin/.../hub/RedisPubSubListener.kt` |
| Shared validation DSL | `backend/common/src/main/kotlin/.../ValidationScope.kt` |
| Koin DI module (API) | `backend/api/src/main/kotlin/.../di/AppModule.kt` |
| Docker Compose | `docker-compose.yml` |

### Backend Audit Status

A comprehensive audit was completed on 2026-03-12. See `docs/audit/backend-modules-audit-2026-03-12.md` for the full 78-finding report with 6-phase remediation plan.

| Phase | Status |
|-------|--------|
| Phase A: Critical Security | **COMPLETED** (PR #285) |
| Phase B: Cross-Module Alignment | **COMPLETED** (S2-3 timestamps, S2-12 license validation) |
| Phase C: Test Coverage (52 files) | **PARTIAL** (S3-6 admin auth tests, S3-11 indexes, S3-14 pool tuning, S3-15 repository extraction) |
| Phase D: Code Quality & Performance | Pending |
| Phase E: Documentation & API Spec | **PARTIAL** (S4-5/S4-6/S4-7 backend docs, S4-10 GDPR export, S4-11 audit sync) |
| Phase F: Advanced Security Hardening | **PARTIAL** (S4-9 CSP nonce) |

### Backend Common Pitfalls

1. **Do not create cross-database FK constraints** — API and License use separate databases (ADR-007). Validate references at app layer.
2. **Do not share Flyway migrations between services** — each service owns its own `db/migration/` directory.
3. **Do not use `SyncSessionManager`** — it was removed. Use `WebSocketHub` for all WS connection management.
4. **Do not add `ForceSyncSubscriber`** — `RedisPubSubListener` handles both `sync:delta:*` and `sync:commands`.
5. **Do not issue POS refresh tokens as JWTs** — use opaque tokens stored in `pos_sessions` table.
6. **Do not interpolate user values in email templates without HTML-escaping** — use `htmlEscape()`.
7. **Do not add `/admin/*` endpoints for store-level business operations** — transfers, replenishment, pricing, tax rates belong under `/v1/*` with POS JWT (RS256) auth. Admin endpoints are for platform operations only (ADR-009).

---

## Development Phases

| Phase | Status | Scope |
|-------|--------|-------|
| Phase 0 — Foundation | Complete | Build system, module scaffold, secrets, CI skeleton |
| Phase 1 — MVP | Complete | Single-store POS, offline sync, core features |
| Phase 2 — Growth | ✅ 100% Complete | Multi-store (C1.1–C1.5), CRM, promotions, CRDT sync (C6.1), centralized inventory, full sync pipeline, admin panel replenishment dashboard |
| Phase 3 — Enterprise | ~80% In Progress | Staff/HR, admin, e-invoicing (IRD), analytics |

See `docs/ai_workflows/execution_log.md` for the granular task checklist.

---

## Quick Reference: Where Things Live

| What you need | Where to look |
|---------------|---------------|
| Domain model definitions | `shared/domain/src/commonMain/.../model/` |
| Repository interfaces | `shared/domain/src/commonMain/.../repository/` |
| Repository implementations | `shared/data/src/commonMain/.../repository/` |
| SQLDelight schema files | `shared/data/src/commonMain/sqldelight/` |
| BaseViewModel | `composeApp/core/src/commonMain/.../ui/core/mvi/BaseViewModel.kt` |
| Design system components | `composeApp/designsystem/src/commonMain/.../` |
| Navigation routes | `composeApp/navigation/src/commonMain/.../` |
| RBAC nav gating | `composeApp/navigation/src/commonMain/.../ZyntaNavGraph.kt` + `NavigationItems.kt` (no separate `RbacNavFilter` class exists) |
| Session manager (auto-lock) | `composeApp/feature/auth/src/commonMain/.../session/SessionManager.kt` |
| Koin DI modules | `<feature>/src/commonMain/.../di/<Feature>Module.kt` |
| Security utilities | `shared/security/src/commonMain/.../` |
| HAL interfaces | `shared/hal/src/commonMain/.../` |
| Seed/test data | `shared/seed/src/commonMain/.../` |
| Debug console | `tools/debug/src/commonMain/.../` |
| Gradle commands | `gradle_commands.md` |
| Architecture decisions | `docs/adr/ADR-NNN-*.md` |
| Version catalog | `gradle/libs.versions.toml` |
| **Admin panel role definitions** | `admin-panel/src/types/user.ts` — `AdminRole` type (ADMIN/OPERATOR/FINANCE/AUDITOR/HELPDESK) |
| **Admin panel permission map (40 permissions)** | `admin-panel/src/hooks/use-auth.ts` — `PERMISSIONS` record (incl. `inventory:read`) |
| **Admin panel inventory view** | `admin-panel/src/routes/inventory/index.tsx` — cross-store/warehouse stock comparison (C1.2) |
| **Admin panel auth store** | `admin-panel/src/stores/auth-store.ts` — Zustand store for `AdminUser \| null` |
| **Admin panel API hooks** | `admin-panel/src/api/auth.ts`, `users.ts` — TanStack Query mutations |
| **Admin panel routes** | `admin-panel/src/routes/` — login, users, tickets, master-products, settings/profile, settings/mfa |
| **Admin panel diagnostic viewer** | `admin-panel/src/routes/diagnostic/index.tsx` — technician session management (create JIT token, per-store status, revoke); `admin-panel/src/api/diagnostic.ts` + `admin-panel/src/types/diagnostic.ts` (TODO-006) |
| **API documentation site** | `zyntapos-docs/` — Scalar multi-spec viewer (4 OpenAPI specs), deployed to Cloudflare Pages via `cd-docs.yml`; guides in `zyntapos-docs/guides/` |
| **OpenAPI specs (docs site)** | `zyntapos-docs/openapi/api-v1.yaml`, `admin-v1.yaml`, `license-v1.yaml`, `sync-v1.yaml` |
| **OpenAPI specs (embedded)** | `backend/api/src/main/resources/openapi/api-spec.yaml`, `backend/license/.../license-spec.yaml`, `backend/sync/.../sync-spec.yaml` — served as Swagger UI at `/docs` per service |

---

## Strict Rules & Standards

1. **Architectural Integrity**: Must strictly follow **Clean Architecture** (Data, Domain, and UI separation).
2. **State Management**: Must follow the **MVI (Model-View-Intent)** pattern for all UI logic.
3. **Consistency**: All new code/updates must match existing coding styles, naming conventions, and file structures.
4. **DRY Principle**: Do not reinvent the wheel; reuse existing functions, components, and utility classes.
5. **Best Practices**: Follow industry-leading standards for KMP, including dependency injection (Hilt/Koin), concurrency (Coroutines/Flow), and memory management.
