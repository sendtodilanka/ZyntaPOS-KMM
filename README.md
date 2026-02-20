# ZyntaPOS

**Enterprise-Grade Point of Sale — Kotlin Multiplatform (KMP)**  
Targets: Android (minSdk 24) · Desktop JVM (macOS, Windows, Linux)

---

## Executive Summary

ZyntaPOS is a fully offline-capable, multi-store POS system built with Kotlin Multiplatform and Compose Multiplatform. It shares 100 % of its business logic across Android tablets and JVM desktop machines while delivering a native-feeling UI on each platform. The system is designed for retail and food-service businesses that require reliable, encrypted local operation with optional cloud synchronisation.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                    Compose Multiplatform UI                     │
│   :composeApp:feature:*   :composeApp:designsystem              │
│              :composeApp:navigation                             │
├─────────────────────────────────────────────────────────────────┤
│               MVI State Management (per feature)                │
│         UiState ◄── ViewModel ──► UiEffect / UiIntent          │
├──────────────┬──────────────────────────────────┬──────────────┤
│ :shared:core │      :shared:domain              │:shared:security│
│ Base MVI     │ Models · UseCases · Repo Ifaces  │ Crypto · RBAC │
├──────────────┴──────────────────────────────────┴──────────────┤
│                       :shared:data                              │
│   SQLDelight (SQLite+SQLCipher) · Ktor HTTP Client · Sync Engine│
├─────────────────────────────────────────────────────────────────┤
│                       :shared:hal                               │
│   expect/actual: Thermal Printer · Barcode Scanner · Cash Drawer│
├────────────────────────┬────────────────────────────────────────┤
│    androidMain         │           jvmMain                      │
│  USB ESC/POS · ML Kit  │  TCP/IP ESC/POS · HID Scanner          │
│  Android Keystore      │  JCE KeyStore                          │
│  WorkManager Sync      │  Coroutine-based Sync                  │
└────────────────────────┴────────────────────────────────────────┘
```

### Key Principles
- **Clean Architecture**: Strict layer separation — UI → Domain → Data, no cross-layer leakage.
- **Offline-First**: SQLDelight (AES-256 via SQLCipher) is the source of truth. The UI always reads from local cache; sync runs in the background.
- **MVI**: Unidirectional data flow. Every screen has `State`, `Intent`, and `Effect` contracts.
- **Koin DI**: Single cross-platform DI graph. Platform-specific bindings registered in `androidMain` / `jvmMain` modules.
- **HAL**: All hardware I/O is abstracted behind interfaces. Business logic never imports a USB or socket library directly.

---

## Module Map

| Module | Role |
|--------|------|
| `:shared:core` | Pure Kotlin utilities, MVI base classes, `Result<T>`, `CurrencyUtils`, `DateTimeUtils`, `ValidationUtils`, Koin `coreModule` |
| `:shared:domain` | Domain models (`Order`, `Product`, `Payment`, …), repository interfaces, all use-case classes, business-rule validators |
| `:shared:data` | SQLDelight schema + DAOs, Ktor HTTP client, repository implementations, offline sync engine, `ConflictResolver` |
| `:shared:hal` | `PrinterManager`, `BarcodeScanner`, `CashDrawerController` interfaces + `expect/actual` platform drivers, `EscPosEncoder` |
| `:shared:security` | `SecureKeyStorage` (Keystore/JCE), `CryptoManager` (AES-256-GCM), `PinHasher` (PBKDF2), `TokenManager`, `RbacEngine`, `SessionManager` |
| `:composeApp:designsystem` | `ZentaTheme`, Material 3 tokens, `ZentaButton/Card/TextField`, `NumericKeypad`, `ReceiptPreview`, responsive breakpoints |
| `:composeApp:navigation` | Type-safe `NavRoute` sealed hierarchy, `ZentaNavHost`, adaptive nav shell (Rail vs Bottom Bar), RBAC route gating |
| `:composeApp:feature:auth` | Login, PIN quick-switch, biometric, auto-lock screen |
| `:composeApp:feature:pos` | Product grid, cart, discounts, payment (split/cash/card), receipt, hold orders, refund |
| `:composeApp:feature:inventory` | Product CRUD, category management, stock levels, adjustments, barcode label print |
| `:composeApp:feature:register` | Cash register open/close, cash in/out, EOD Z-report |
| `:composeApp:feature:reports` | Sales summary, product performance, stock report, CSV/PDF export |
| `:composeApp:feature:settings` | Store profile, tax config, printer setup, user management, security policy, appearance, backup/restore |
| `:composeApp:feature:customers` | Customer directory, loyalty accounts, GDPR export |
| `:composeApp:feature:coupons` | Coupon CRUD, promotion rule engine (BOGO / % / threshold) |
| `:composeApp:feature:expenses` | Expense log, P&L statement, cash-flow view |
| `:composeApp:feature:staff` | Employee profiles, shift scheduling, attendance, payroll |
| `:composeApp:feature:multistore` | Store selector, central KPI dashboard, inter-store transfers |
| `:composeApp:feature:admin` | System health, audit-log viewer, DB maintenance, backup management |
| `:composeApp:feature:media` | Product image picker, crop, compression pipeline |

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.3.0 — 100 % (no Java) |
| Multiplatform | Kotlin Multiplatform + Compose Multiplatform 1.10.0 |
| UI | Compose Multiplatform · Material 3 |
| State | MVI (StateFlow / SharedFlow / Coroutines) |
| DI | Koin 4.0+ |
| Local DB | SQLDelight 2.0+ on SQLite (encrypted via SQLCipher 4.5) |
| Networking | Ktor 3.0+ |
| Serialization | kotlinx.serialization 1.8 |
| Date/Time | kotlinx-datetime 0.6+ |
| Image Loading | Coil 3.0+ |
| Logging | Kermit 2.0 |
| Testing | Kotlin Test · Mockative 3 · Turbine · Koin-test |
| Secrets | Secrets Gradle Plugin 2.0 |
| Build | Gradle 8.x · Version Catalog (`libs.versions.toml`) |

---

## Setup Guide

### Prerequisites
- **JDK 17+** (required for Gradle and JVM desktop target)
- **Android Studio Meerkat+** (recommended) or IntelliJ IDEA 2025+
- **Android SDK** installed with API 36 build tools
- **Gradle** — the wrapper (`./gradlew`) downloads the correct version automatically

### 1. Clone the repository
```bash
git clone https://github.com/your-org/ZyntaPOS.git
cd ZyntaPOS
```

### 2. Configure local secrets
```bash
cp local.properties.template local.properties
```
Open `local.properties` and fill in the required values:

| Key | Description |
|-----|-------------|
| `sdk.dir` | Android SDK path (set automatically by Android Studio) |
| `ZYNTA_API_BASE_URL` | Backend REST API base URL |
| `ZYNTA_API_CLIENT_ID` | OAuth2 client ID |
| `ZYNTA_API_CLIENT_SECRET` | OAuth2 client secret |
| `ZYNTA_DB_PASSPHRASE` | AES-256 passphrase for SQLCipher (64 hex chars) |
| `ZYNTA_FCM_SERVER_KEY` | Firebase Cloud Messaging server key (Android) |
| `ZYNTA_MAPS_API_KEY` | Google Maps API key (optional) |
| `ZYNTA_SENTRY_DSN` | Sentry crash reporting DSN |
| `ZYNTA_IRD_API_ENDPOINT` | IRD e-invoice API endpoint (Sri Lanka) |

Generate a secure DB passphrase:
```bash
openssl rand -hex 32
```

### 3. Build & Run

**Android (debug APK)**
```bash
./gradlew :composeApp:assembleDebug
```

**Android (install on connected device)**
```bash
./gradlew :composeApp:installDebug
```

**Desktop JVM (run locally)**
```bash
./gradlew :composeApp:run
```

**Desktop JVM (package distributable)**
```bash
# macOS → .dmg
./gradlew :composeApp:packageDmg

# Windows → .msi
./gradlew :composeApp:packageMsi

# Linux → .deb
./gradlew :composeApp:packageDeb
```

**Full clean build (both targets)**
```bash
./gradlew clean :composeApp:assembleDebug :composeApp:jvmJar
```

### 4. Run tests
```bash
# All shared-module unit tests
./gradlew :shared:core:allTests
./gradlew :shared:domain:allTests
./gradlew :shared:data:allTests

# All tests across all modules
./gradlew allTests
```

---

## Security Notes

- `local.properties` is **git-ignored** and must **never** be committed. It contains encryption keys and API credentials.
- `local.properties.template` is the committed reference; it contains only placeholder values.
- The Secrets Gradle Plugin injects `ZYNTA_*` keys into Android `BuildConfig` fields at compile time. They are never stored in `AndroidManifest.xml` or `strings.xml`.
- SQLCipher encrypts the entire SQLite database with AES-256 using the `ZYNTA_DB_PASSPHRASE` key stored in the platform's secure key store (Android Keystore / JCE KeyStore) at runtime — the passphrase itself is never stored in plaintext on disk after the first boot.
- All JKS / `.keystore` / `.p12` signing artifacts are git-ignored.

---

## Docs

| Document | Path |
|----------|------|
| AI Execution Log | `docs/ai_workflows/execution_log.md` |
| Architecture Decisions | `docs/architecture/` |
| API Specifications | `docs/api/` |
| Compliance Notes | `docs/compliance/` |

---

## Contributing

1. Branch from `main` — follow the naming convention `feature/M##-short-description`.
2. All new public APIs must include KDoc comments.
3. Unit test coverage must stay above **85 %** for `:shared:*` modules.
4. Run `./gradlew detekt` (once configured in Phase 1 CI) before opening a PR.

---

## Roadmap

| Phase | Timeline | Scope |
|-------|----------|-------|
| Phase 0 | Foundation | Build system, module scaffold, secrets, CI skeleton |
| Phase 1 (MVP) | Months 1–6 | Single-store POS, offline sync, core features |
| Phase 2 (Growth) | Months 7–12 | Multi-store, CRM, promotions, CRDT sync |
| Phase 3 (Enterprise) | Months 13–18 | Staff/HR, admin, e-invoicing (IRD), analytics |

See `docs/ai_workflows/execution_log.md` for the granular task checklist.

---

*ZyntaPOS — Built with ❤️ using Kotlin Multiplatform*
