# ZyntaPOS — Dev Testing Guide

> **Audience:** Engineers and QA testers who need to run the full app **without** a live backend API,
> Firebase, Sentry DSN, or IRD certificate.
> **Scope:** Android (emulator/device) and Desktop JVM.
> **Last updated:** 2026-03-18

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Initial Setup](#2-initial-setup)
3. [How the Dev Environment Works](#3-how-the-dev-environment-works)
4. [First Run — Onboarding Wizard](#4-first-run--onboarding-wizard)
5. [Loading Seed Data (Debug Console)](#5-loading-seed-data-debug-console)
6. [Debug Console Reference](#6-debug-console-reference)
7. [Feature Testing Checklist](#7-feature-testing-checklist)
8. [Automated Tests](#8-automated-tests)
9. [Build Commands](#9-build-commands)
10. [Code Quality](#10-code-quality)
11. [Known Dev Limitations](#11-known-dev-limitations)
12. [Troubleshooting](#12-troubleshooting)

---

## 1. Prerequisites

| Requirement | Minimum | Recommended |
|-------------|---------|-------------|
| JDK | 21 | 21 (Temurin) |
| IDE | IntelliJ IDEA 2024+ | Android Studio Meerkat+ |
| Android SDK | API 24 (build tools 36) | API 36 |
| Gradle | via wrapper only — `./gradlew` | — |
| OS | macOS 12+, Windows 10+, Ubuntu 22.04+ | — |

> **Important:** Always use `./gradlew` — never invoke `gradle` directly. The wrapper downloads the
> exact version pinned in `gradle/wrapper/gradle-wrapper.properties` and ensures build reproducibility.

---

## 2. Initial Setup

### Step 1 — Copy the dev properties file

```bash
cp local.properties.dev local.properties
```

`local.properties.dev` is committed to the repo and contains safe, pre-filled placeholder values for
every required build config key. The only value you **must** change is `sdk.dir`.

> `local.properties` is **git-ignored** and must **never** be committed — it can hold real credentials
> in production setups. The `.dev` variant intentionally uses all-placeholder values.

### Step 2 — Set your Android SDK path

Open `local.properties` and update `sdk.dir` to match your local SDK installation:

```properties
# Linux
sdk.dir=/home/<your-username>/Android/Sdk

# macOS
sdk.dir=/Users/<your-username>/Library/Android/sdk

# Windows
sdk.dir=C\:\\Users\\<your-username>\\AppData\\Local\\Android\\Sdk
```

Android Studio sets this automatically when you open the project. If you are using IntelliJ or a
headless CI agent, set it manually.

### Step 3 — Build and run

**Android debug APK (emulator or physical device)**
```bash
./gradlew :composeApp:assembleDebug
./gradlew :androidApp:installDebug     # install on connected device/emulator
```

**Desktop JVM (development run)**
```bash
./gradlew :composeApp:run
```

### Dev properties reference

`local.properties.dev` ships with these values — all are safe, non-functional placeholders:

| Key | Dev Value | Notes |
|-----|-----------|-------|
| `sdk.dir` | `/home/user/Android/Sdk` | **Must update to your path** |
| `ZYNTA_API_BASE_URL` | `http://localhost:8080` | Stubbed by `DevApiService` — never called |
| `ZYNTA_API_CLIENT_ID` | `dev-client-id` | Placeholder |
| `ZYNTA_API_CLIENT_SECRET` | `dev-client-secret` | Placeholder |
| `ZYNTA_DB_PASSPHRASE` | 64 zeroes | Intentional dev key — never use in production |
| `ZYNTA_FCM_SERVER_KEY` | `dev-placeholder-fcm-key` | FCM not used in dev |
| `ZYNTA_MAPS_API_KEY` | `dev-placeholder-maps-key` | Maps not used in dev |
| `ZYNTA_SENTRY_DSN` | `dev-placeholder-sentry-dsn` | Crash reporting disabled in dev |
| `ZYNTA_IRD_API_ENDPOINT` | `http://localhost:8080/ird` | IRD submission stubbed |
| `ZYNTA_IRD_CLIENT_CERTIFICATE_PATH` | `/dev/null` | No real cert needed |
| `ZYNTA_IRD_CERTIFICATE_PASSWORD` | `dev-placeholder-cert-password` | Placeholder |

---

## 3. How the Dev Environment Works

The dev build replaces the production network layer with a no-op stub via Koin dependency injection.
No production code is modified — the stub is physically limited to the `src/debug/` Android source set
and is excluded from release builds at compile time.

### Architecture overview

```
Debug Build                        Release Build
───────────────────────────────    ──────────────────────────────
ZyntaApplication (src/main/)       ZyntaApplication (src/main/)
 └─ loads devModules               └─ loads emptyList()
     └─ DevApiService
         (src/debug/ only)         KtorApiService
                                   (bound by dataModule)
```

### Key properties

| Property | Behaviour |
|----------|-----------|
| **Authentication** | 100% local. `AuthRepositoryImpl.login()` validates email + BCrypt-hashed password directly from SQLite. `DevApiService.login()` exists as a fallback stub but is never reached in normal auth flows. |
| **Sync engine** | `SyncEngine` checks `networkMonitor.isConnected` before every sync attempt. It skips silently when offline — no stubbing needed. |
| **API stubs** | `DevApiService.pushOperations()` marks all pending sync operations as accepted, keeping the sync queue clean. `pullOperations()` returns an empty response. |
| **Koin override** | `DevModules` uses `allowOverride = true` to replace the production `KtorApiService` binding registered by `dataModule`. The release variant of `DevModules.kt` (in `src/release/`) returns `emptyList()` — zero overhead. |
| **No hardcoded credentials** | The dev setup never stores admin credentials in source code. First-run creates the admin account through the standard Onboarding wizard. |

### Files involved

| File | Location | Purpose |
|------|----------|---------|
| `local.properties.dev` | `/` (repo root) | Committed dev placeholder for all build config keys |
| `DevApiService.kt` | `androidApp/src/debug/kotlin/…` | No-op API stub (debug source set only) |
| `DevModules.kt` | `androidApp/src/debug/kotlin/…` | Koin module override (debug) |
| `DevModules.kt` | `androidApp/src/release/kotlin/…` | No-op release variant (returns `emptyList()`) |
| `ZyntaApplication.kt` | `androidApp/src/main/kotlin/…` | Loads `devModules` in the debug block |

---

## 4. First Run — Onboarding Wizard

On a fresh install (or after clearing app data), the app shows a two-step onboarding wizard before
the login screen. This is the correct way to create the first ADMIN account — no credentials are
hardcoded anywhere.

### Step 1 — Business Information

| Field | Validation |
|-------|-----------|
| Business name | Required · 2–100 characters |

Enter your test business name (e.g. `Zynta Demo Café`). Tap **Next**.

### Step 2 — Admin Account

| Field | Validation |
|-------|-----------|
| Admin name | Required · min 2 characters |
| Admin email | Must contain `@` and `.`, min 5 characters |
| Password | Min 8 characters |
| Confirm password | Must match password |

The ViewModel passes the plain-text password directly to `UserRepository.create()` for hashing. The
password is never persisted by the wizard itself.

### What happens on completion

1. Business name saved: `settingsRepository.set("general.business_name", …)`
2. Admin user created in SQLite with role `ADMIN`
3. Onboarding gated: `settingsRepository.set("onboarding.completed", "true")`
4. App navigates to Login screen

### Re-running onboarding

Clear app data (Android: Settings → Apps → ZyntaPOS → Clear Data) to reset `onboarding.completed`
and trigger the wizard again on next launch.

---

## 5. Loading Seed Data (Debug Console)

Before testing most features, populate the local database with realistic test data via the Debug
Console → **Seeds** tab.

### Quick start

1. Open the Debug Console (debug-only button in the navigation)
2. Select the **Seeds** tab
3. Tap **Run Seed**
4. Tap **Setup Admin Account** (or use credentials created during Onboarding)
5. Navigate to Login and sign in

### What the seed data contains

#### Categories (8)

| # | Name |
|---|------|
| 1 | Coffee & Espresso |
| 2 | Tea & Herbal |
| 3 | Pastries & Bakery |
| 4 | Sandwiches & Wraps |
| 5 | Cold Drinks |
| 6 | Merchandise |
| 7 | Desserts |
| 8 | Breakfast |

#### Suppliers (5)

| Supplier | Contact | Email |
|----------|---------|-------|
| Arabica Premium Roasters | Carlos Mendez | carlos@arabica.com |
| Golden Grain Bakery Supply | Emma Wilson | emma@goldengrain.com |
| Fresh Valley Farms | Tom Green | tom@freshvalley.com |
| Global Tea Imports | Li Wei | li.wei@gti.com |
| Zyntara Merchandise Co. | Sarah Park | sarah@zyntara-merch.com |

#### Products (25)

| ID | Name | SKU | Price | Category | Stock | Min | Status |
|----|------|-----|-------|----------|-------|-----|--------|
| seed-prod-001 | Espresso Single Shot | SKU-ESP-01 | $2.50 | Coffee & Espresso | 999 | 0 | OK |
| seed-prod-002 | Espresso Double Shot | SKU-ESP-02 | $3.50 | Coffee & Espresso | 999 | 0 | OK |
| seed-prod-003 | Flat White | SKU-FW-01 | $4.20 | Coffee & Espresso | 999 | 0 | OK |
| seed-prod-004 | Cappuccino | SKU-CAP-01 | $4.00 | Coffee & Espresso | 999 | 0 | OK |
| seed-prod-005 | Caramel Macchiato | SKU-MAC-01 | $5.20 | Coffee & Espresso | 999 | 0 | OK |
| seed-prod-006 | English Breakfast Tea | SKU-TEA-01 | $3.00 | Tea & Herbal | 80 | 20 | OK |
| seed-prod-007 | Chamomile Herbal Tea | SKU-TEA-02 | $3.20 | Tea & Herbal | 3 | 10 | **LOW STOCK** |
| seed-prod-008 | Butter Croissant | SKU-BAK-01 | $3.50 | Pastries & Bakery | 24 | 5 | OK |
| seed-prod-009 | Blueberry Muffin | SKU-BAK-02 | $3.80 | Pastries & Bakery | 2 | 8 | **LOW STOCK** |
| seed-prod-010 | Almond Danish | SKU-BAK-03 | $4.20 | Pastries & Bakery | 18 | 5 | OK |
| seed-prod-011 | BLT Sandwich | SKU-SAN-01 | $7.50 | Sandwiches & Wraps | 15 | 5 | OK |
| seed-prod-012 | Veggie Wrap | SKU-SAN-02 | $6.90 | Sandwiches & Wraps | 4 | 5 | **LOW STOCK** |
| seed-prod-013 | Club Sandwich | SKU-SAN-03 | $8.50 | Sandwiches & Wraps | 10 | 3 | OK |
| seed-prod-014 | Sparkling Water 500ml | SKU-DRK-01 | $2.00 | Cold Drinks | 48 | 12 | OK |
| seed-prod-015 | Fresh Orange Juice | SKU-DRK-02 | $4.50 | Cold Drinks | 20 | 8 | OK |
| seed-prod-016 | Iced Coffee Frappe | SKU-DRK-03 | $5.90 | Cold Drinks | 999 | 0 | OK |
| seed-prod-017 | ZyntaPOS Branded Mug 12oz | SKU-MRC-01 | $14.99 | Merchandise | 30 | 5 | OK |
| seed-prod-018 | Reusable Coffee Cup 16oz | SKU-MRC-02 | $19.99 | Merchandise | 0 | 5 | **OUT OF STOCK** |
| seed-prod-019 | Chocolate Brownie | SKU-DES-01 | $4.50 | Desserts | 12 | 5 | OK |
| seed-prod-020 | New York Cheesecake Slice | SKU-DES-02 | $6.90 | Desserts | 8 | 3 | OK |
| seed-prod-021 | Macaron Assortment 3pc | SKU-DES-03 | $5.50 | Desserts | 1 | 4 | **LOW STOCK** |
| seed-prod-022 | Full English Breakfast | SKU-BRK-01 | $12.90 | Breakfast | 20 | 5 | OK |
| seed-prod-023 | Avocado Toast | SKU-BRK-02 | $9.50 | Breakfast | 15 | 5 | OK |
| seed-prod-024 | Granola Bowl | SKU-BRK-03 | $7.50 | Breakfast | 3 | 5 | **LOW STOCK** |
| seed-prod-025 | Pancake Stack | SKU-BRK-04 | $10.50 | Breakfast | 10 | 3 | OK |

**Low-stock products (useful for testing Dashboard alerts):**
- Chamomile Herbal Tea — 3 in stock, min 10
- Blueberry Muffin — 2 in stock, min 8
- Veggie Wrap — 4 in stock, min 5
- Reusable Coffee Cup — **0 in stock** (out of stock), min 5
- Macaron Assortment — 1 in stock, min 4
- Granola Bowl — 3 in stock, min 5

#### Customers (15)

| ID | Name | Phone | Email | Loyalty Points |
|----|------|-------|-------|---------------|
| seed-cust-001 | Alice Johnson | +1-555-1001 | alice.j@email.com | 250 |
| seed-cust-002 | Bob Martinez | +1-555-1002 | bob.m@email.com | 150 |
| seed-cust-003 | Carol Chen | +1-555-1003 | carol.c@email.com | 500 |
| seed-cust-004 | David Kim | +1-555-1004 | david.k@email.com | 75 |
| seed-cust-005 | Eva Garcia | +1-555-1005 | eva.g@email.com | 320 |
| seed-cust-006 | Frank Lee | +1-555-1006 | frank.l@email.com | 0 |
| seed-cust-007 | Grace Williams | +1-555-1007 | grace.w@email.com | 180 |
| seed-cust-008 | Henry Brown | +1-555-1008 | *(no email)* | 0 |
| seed-cust-009 | Isabel Taylor | +1-555-1009 | isabel.t@email.com | 90 |
| seed-cust-010 | James Wilson | +1-555-1010 | james.w@email.com | 440 |
| seed-cust-011 | Karen Anderson | +1-555-1011 | karen.a@email.com | 220 |
| seed-cust-012 | Liam Thomas | +1-555-1012 | *(no email)* | 0 |
| seed-cust-013 | Mia Jackson | +1-555-1013 | mia.j@email.com | 380 |
| seed-cust-014 | Noah White | +1-555-1014 | noah.w@email.com | 110 |
| seed-cust-015 | Olivia Harris | +1-555-1015 | olivia.h@email.com | 670 |

> Seeding is **idempotent** — calling "Run Seed" multiple times inserts new records only if they don't
> already exist (checked by ID). Safe to re-run after partial runs.

---

## 6. Debug Console Reference

The Debug Console is compiled into all builds but its Koin bindings are only loaded when
`BuildConfig.DEBUG = true`. It provides six tabs:

### Tab 1 — Seeds

| Control | Action |
|---------|--------|
| Seed profile dropdown | Select which data profile to load |
| **Run Seed** | Executes seeding; shows result card (inserted / skipped / failed per entity) |
| **Clear Seeds** | Deletes all seeded records |
| **Setup Admin Account** | Dialog to create or overwrite the ADMIN user — accepts name, email (default: `admin@debug.local`), password (min 8 chars). Password is passed directly to `UserRepository.create()` for hashing; never stored in plaintext. |

### Tab 2 — Database

| Control | Action |
|---------|--------|
| DB file size display | Shows current database file size in KB |
| Inspection tools | Query and inspect DB records |
| Destructive clear | Wipe all database contents (use with caution) |

### Tab 3 — Auth

| Section | Controls |
|---------|---------|
| **Current Session** | Displays email, role, PIN configured status. Shows "No active session" if logged out. |
| **Session Actions** | Reload Session · Clear Session (danger) |
| **All Users** | Lists every user (name, email, role). Allows deactivating non-current users. Reload Users button. |

> If no users appear, go to **Seeds → Setup Admin Account** first.

### Tab 4 — Network

| Control | Action |
|---------|--------|
| Force Offline Mode toggle | UI flag that simulates offline state (full network interception planned for Phase 2) |
| Pending operations counter | Count of unsynced items in the sync queue (red if > 0) |
| **Force Sync** | Triggers a sync attempt immediately |
| **Clear Sync Queue** | Marks all pending operations as synced without pushing to server. Warning: data will not reach the backend. |

### Tab 5 — Diagnostics

| Section | Details |
|---------|---------|
| **System Health** | DB file size · pending sync ops count · audit entries count |
| **Audit Log** | Up to 50 latest audit entries (event type, timestamp, user ID, payload). Colour-coded: primary = success, error = failed. |
| **Session Logs** | Last 30 log lines in monospace font. Export Logs button. |

### Tab 6 — UI / UX

| Control | Behaviour |
|---------|-----------|
| Theme toggle | Light / Dark / System — overrides theme for the current session only (not persisted to `SettingsRepository`) |
| Font scale slider | 0.75× to 1.50× with step presets. Presets: Small (0.85×), Normal (1.0×), Large (1.15×), XL (1.30×). Reset to 1.0× button. |

> All UI/UX overrides reset when the debug session ends.

---

## 7. Feature Testing Checklist

After seeding, use this checklist to exercise all 17 feature modules manually.

### Auth
- [ ] Log in with ADMIN credentials created during Onboarding or via Seeds tab
- [ ] Log out and log back in
- [ ] Set a PIN (Settings → Security) and use PIN quick-switch on lock screen
- [ ] Trigger auto-lock by leaving the app idle beyond the configured timeout
- [ ] Test wrong password — verify login is rejected without any crashes

### Dashboard
- [ ] KPI cards render (Today's Sales, Order Count, Low-Stock Alerts)
- [ ] Low-stock alert badges appear for the 6 products below minimum stock level (see seed data)
- [ ] Tap a KPI card to navigate to the corresponding detail screen
- [ ] Rotate tablet or resize desktop window — verify 3 responsive layout variants switch correctly

### Onboarding
- [ ] Clear app data and relaunch — wizard appears on first run
- [ ] Step 1: leave business name blank — verify validation error
- [ ] Step 1: enter 1-character name — verify "min 2 chars" error
- [ ] Step 2: mismatched passwords — verify error
- [ ] Step 2: password fewer than 8 chars — verify error
- [ ] Complete wizard — verify navigation to Login screen

### POS
- [ ] Browse product grid; filter by category
- [ ] Search for "espresso" — verify FTS5 search returns relevant products
- [ ] Add items to cart; adjust quantity; remove item
- [ ] Apply a percentage discount to a cart line
- [ ] Complete a cash payment — verify receipt preview
- [ ] Split payment (cash + card) — verify totals add up
- [ ] Hold an order; retrieve it from the hold queue
- [ ] Process a refund on a completed order

### Inventory
- [ ] Browse product list; filter by category
- [ ] Add a new product (name, SKU, barcode, price, category, stock)
- [ ] Edit an existing product — verify changes persist
- [ ] Record a stock adjustment (add / remove / count)
- [ ] Search by product name and by barcode
- [ ] Delete a product — verify it disappears from POS grid

### Register
- [ ] Open a cash register session (enter opening float)
- [ ] Record a cash-in entry
- [ ] Record a cash-out entry
- [ ] View running cash balance
- [ ] Close the session — verify Z-report generates

### Reports
- [ ] Sales summary — select a date range and verify data loads
- [ ] Product performance report
- [ ] Stock report (verify low-stock products appear highlighted)
- [ ] Trigger CSV export — verify file is created
- [ ] Print preview renders (printer hardware not required — preview only)

### Settings
- [ ] Update store profile (name, address, phone)
- [ ] Configure a tax group (rate, name)
- [ ] Printer setup — test print (no physical printer needed; verify no crash)
- [ ] Add a new user with CASHIER role; verify login works with new credentials
- [ ] Change security policy (auto-lock timeout)
- [ ] Appearance settings (theme toggle)

### Customers
- [ ] Browse customer directory
- [ ] Add a new customer (name, phone, email)
- [ ] Attach a customer to a POS sale; verify loyalty points accrue
- [ ] GDPR export — verify customer data export is triggered

### Coupons
- [ ] Create a percentage-off coupon (e.g. 10% off all items)
- [ ] Create a BOGO coupon
- [ ] Create a threshold coupon (e.g. $50 minimum)
- [ ] Apply a coupon at POS checkout — verify discount is applied correctly
- [ ] Apply an expired coupon — verify rejection

### Expenses
- [ ] Log an expense (amount, category, description, date)
- [ ] View the expense list
- [ ] Check the P&L report — verify expense appears
- [ ] View cash-flow summary

### Staff
- [ ] Add an employee profile (name, role, contact)
- [ ] Assign a shift
- [ ] Mark attendance for today
- [ ] View payroll summary (expected: UI renders; calculations are placeholder for Phase 3 completion)

### Multistore
- [ ] View store selector
- [ ] Switch between stores (requires at least 2 stores in seed data)
- [ ] View central KPI dashboard aggregating all stores

### Admin
- [ ] System health dashboard renders (DB size, pending sync, audit count)
- [ ] Audit log viewer — verify recent actions appear
- [ ] DB maintenance actions (vacuum — note: `VacuumDatabaseUseCase` wiring is pending, see Known Limitations)
- [ ] Backup / restore flow

### Media
- [ ] Open product detail in Inventory
- [ ] Tap "Add Image" — verify image picker opens
- [ ] Select an image, crop, and confirm — verify product thumbnail updates

### Accounting
- [ ] Create an e-invoice for a completed order
- [ ] Review submission status — verify it remains in "Pending" (IRD endpoint is stubbed in dev)
- [ ] Verify no crash when attempting submission

### Diagnostic
- [ ] Open the Diagnostic screen (requires ADMIN or MANAGER role)
- [ ] View active diagnostic sessions list
- [ ] Verify no crash when requesting a remote session token

---

## 8. Automated Tests

### Run all tests

```bash
./gradlew test --parallel --continue
```

### Run tests for a specific module

```bash
# Shared layer (critical path — run these before every commit)
./gradlew :shared:core:test
./gradlew :shared:domain:test
./gradlew :shared:security:test

# Data layer (JVM integration tests with in-memory SQLite)
./gradlew :shared:data:jvmTest

# Feature modules
./gradlew :composeApp:feature:auth:test
./gradlew :composeApp:feature:pos:test
./gradlew :composeApp:feature:inventory:test
./gradlew :composeApp:feature:dashboard:test
./gradlew :composeApp:feature:onboarding:test
./gradlew :composeApp:feature:reports:test
```

### Platform-specific tests

```bash
./gradlew jvmTest                 # Desktop/JVM target tests only
./gradlew testDebugUnitTest       # Android unit tests only
./gradlew allTests                # All KMP targets
```

### Run a single test class

```bash
./gradlew :shared:domain:test --tests "com.zyntasolutions.zyntapos.domain.usecase.CalculateOrderTotalsUseCaseTest"
```

### CI pipeline commands

```bash
# Pre-commit check (~2 min)
./gradlew :shared:core:test :shared:domain:test :shared:security:test --parallel --continue

# Full verification before opening a PR (~8 min)
./gradlew clean test lint --parallel --continue --stacktrace

# Full CI pipeline (~15 min)
./gradlew clean test testDebugUnitTest jvmTest lint assembleDebug \
          :composeApp:packageUberJarForCurrentOS --parallel --continue --stacktrace
```

### Generate SQLDelight interfaces (after modifying .sq files)

```bash
./gradlew generateSqlDelightInterface
```

---

## 9. Build Commands

### Android

```bash
# Debug APK
./gradlew :composeApp:assembleDebug

# Install on connected device / running emulator
./gradlew :androidApp:installDebug

# Clean rebuild
./gradlew clean :composeApp:assembleDebug
```

### Desktop JVM

```bash
# Run directly (for active development)
./gradlew :composeApp:run

# Package for current OS (macOS → .dmg, Windows → .msi, Linux → .deb)
./gradlew :composeApp:packageDistributionForCurrentOS

# Standalone JAR (useful for quick distribution)
./gradlew :composeApp:packageUberJarForCurrentOS
```

---

## 10. Code Quality

```bash
# Static analysis (Detekt — all modules)
./gradlew detekt

# Android Lint (all modules)
./gradlew lint

# Lint a single module
./gradlew :composeApp:feature:pos:lint
```

---

## 11. Known Dev Limitations

| Limitation | Detail |
|-----------|--------|
| Cloud sync stubbed | `DevApiService` accepts all push operations without sending to a server. Multi-store sync and conflict resolution are not testable offline. |
| IRD e-invoice submission | `DevApiService.pushEInvoice()` is a no-op. The accounting feature renders correctly but submission always stays "Pending". |
| Cash drawer | Hardware pulse not available in emulator. The HAL interface logs a no-op on Android and JVM when no device is connected. |
| Barcode scanner (physical) | Use keyboard-wedge simulation: focus the barcode input field, type the barcode string (see seed data), and press Enter. |
| Admin DB vacuum | `VacuumDatabaseUseCase` exists in domain + data but is not yet wired into `AdminModule.kt` or `AdminViewModel`. Tapping "Vacuum" in Admin may have no effect. |
| Register session guard | `AuthViewModel` has a `TODO (Sprint 20)` for checking open register session on login. The guard is not enforced yet. |
| CRDT conflict resolution | `ConflictResolver` is fully integrated into `SyncEngine` (C6.1). LWW with deviceId tiebreak + PRODUCT field-level merge. Conflicts are auto-resolved and logged to `conflict_log` table. See `ConflictResolverTest` for 10 unit tests. |
| `./gradlew test` failures | Some fake repository classes in `commonTest` have known type-signature mismatches documented in `execution_log.md` under "Doing". Run `./gradlew :shared:domain:test` to test the most stable module. |

---

## 12. Troubleshooting

| Symptom | Likely Cause | Fix |
|---------|-------------|-----|
| Build fails: "sdk.dir is missing" | `local.properties` not created | `cp local.properties.dev local.properties` then set `sdk.dir` |
| Build fails: "ZYNTA_* key not found" | `local.properties` missing keys | Ensure all keys from `local.properties.dev` are present |
| App crashes immediately on launch | Koin missing binding or DB passphrase mismatch | Verify `local.properties` has `ZYNTA_DB_PASSPHRASE` set; check Logcat for Koin errors |
| Onboarding shows again after first run | App data cleared or `onboarding.completed` flag missing | Expected after clearing data — complete the wizard again |
| POS product grid is empty | Seed data not loaded | Open Debug Console → Seeds → Run Seed |
| Debug Console not visible | Running a release build | Build and run a debug variant (`assembleDebug` / `installDebug`) |
| Login rejected after Seeds → Setup Admin | Password fewer than 8 characters was entered | Re-run Setup Admin Account with a longer password |
| `./gradlew test` fails with type errors | Known fake-repository mismatches | See `docs/ai_workflows/execution_log.md` "Doing" section for details; run per-module tests instead |
| Koin "already declared" binding error | Module load order changed in `ZyntaApplication.kt` | Restore the 7-tier Koin load order documented in `CLAUDE.md` |
| Desktop window does not appear | `./gradlew :composeApp:run` still compiling | Wait for the `BUILD SUCCESSFUL` message; the window opens after compilation completes |
| Sync queue keeps growing | Force Sync fails silently (no server) | Normal behaviour in dev. Use Debug Console → Network → Clear Sync Queue to reset. |

---

*Last updated: 2026-03-18*
