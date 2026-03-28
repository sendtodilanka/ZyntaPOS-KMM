# ADR-012 — Firebase Removal

**Date:** 2026-03-28
**Status:** ACCEPTED
**Deciders:** Engineering Lead

---

## Context

ZyntaPOS originally planned Firebase for analytics (GA4), crash reporting (Crashlytics), and feature flags (Remote Config). Sentry was adopted first as the primary crash reporter with full platform coverage (Android, Desktop JVM, Ktor backend). Firebase was subsequently implemented for analytics and Remote Config.

After review, Firebase was found to add dependency weight and complexity without sufficient benefit given the existing alternatives:

- **Crashlytics** — redundant with Sentry (which already covers all platforms including Desktop JVM and Ktor backend, which Crashlytics does not)
- **Analytics (Firebase/GA4)** — adds Google dependency; Kermit structured logging to `operational_logs` + Sentry breadcrumbs covers event capture needs without external network calls
- **Remote Config** — requires internet connectivity; `FeatureRegistryRepository` (local SQLite, already implemented) covers all feature flag use cases offline-first
- **Firebase JS SDK (Admin Panel)** — adds npm bundle weight; Sentry `@sentry/react` already active

Additionally, removing Firebase eliminates the need to manage `google-services.json` (a secret with strict gitignore requirements) and two Gradle plugins.

---

## Decision

**Remove all Firebase components from ZyntaPOS.**

| Component Removed | Replacement |
|-------------------|-------------|
| Firebase Analytics SDK (Android) | Kermit structured logging → `KermitSqliteAdapter` + Sentry breadcrumbs |
| GA4 Measurement Protocol (Desktop JVM) | Kermit structured logging |
| Firebase Remote Config (Android) | `RemoteConfigService` backed by `FeatureRegistryRepository` (SQLite) |
| Firebase Crashlytics (Android) | Sentry Android SDK (already primary crash reporter) |
| `kermit-crashlytics` bridge | Removed (no Crashlytics target) |
| Firebase JS SDK (Admin Panel) | `@sentry/react` (already active) |

**What is NOT affected by this ADR:**
- `GOOGLE_SERVICES_JSON` / `google-services.json` — removed along with Firebase
- Sentry — remains as the sole crash reporter on all platforms
- `KermitSqliteAdapter` — remains as the operational log sink

---

## Consequences

### Positive
- Zero Firebase/Google dependencies in the codebase
- No `google-services.json` secret management required (removed from CI and `.gitignore`)
- `AnalyticsService` and `RemoteConfigService` are now regular `commonMain` classes — no `expect/actual` complexity
- DI bindings consolidated from platform-specific modules into common `DataModule`
- Smaller Android APK (no Firebase BOM, Analytics, Crashlytics)
- Admin panel bundle lighter (no Firebase JS SDK)
- Feature flags work fully offline (read from local SQLite, no network call required)

### Neutral
- Analytics event granularity reduced (Kermit logs are available in `operational_logs` for in-app debugging; Sentry breadcrumbs provide crash context)
- Remote Config no longer supports server-push of flag changes — flags are updated via sync engine from the Admin Panel

### Negative
- No real-time GA4 dashboard for business events (planned mitigation: Phase 4 analytics module with custom reporting)

---

## Alternatives Considered

1. **Keep Firebase Analytics only** — rejected; the operational overhead of maintaining `google-services.json` and two Gradle plugins for analytics alone is not justified when Kermit + Sentry covers the debugging use case
2. **Keep Remote Config only** — rejected; `FeatureRegistryRepository` is a strictly better fit for an offline-first architecture
3. **Replace with Mixpanel or Amplitude** — rejected; out of scope for Phase 3; can be revisited in Phase 4

---

## Implementation

Implemented in commit on branch `claude/remove-fcm-ird-code-B4Oy9` (2026-03-28).

Files changed:
- `gradle/libs.versions.toml` — removed Firebase version/library/plugin declarations
- `build.gradle.kts` (root) — removed `googleServices` and `firebaseCrashlytics` plugin aliases
- `androidApp/build.gradle.kts` — removed Firebase plugins and dependencies
- `shared/data/build.gradle.kts` — removed Firebase androidMain dependencies
- `shared/data/src/commonMain/.../analytics/AnalyticsService.kt` — replaced `expect class` with regular Kermit-only class
- `shared/data/src/commonMain/.../remoteconfig/RemoteConfigService.kt` — replaced `expect class` with `FeatureRegistryRepository`-backed class
- `shared/data/src/androidMain/.../analytics/AnalyticsService.kt` — deleted (Firebase actual)
- `shared/data/src/jvmMain/.../analytics/AnalyticsService.kt` — deleted (GA4 Measurement Protocol actual)
- `shared/data/src/androidMain/.../remoteconfig/RemoteConfigService.kt` — deleted (Firebase RC actual)
- `shared/data/src/jvmMain/.../remoteconfig/RemoteConfigService.kt` — deleted (JVM no-op stub)
- `shared/data/src/androidMain/.../di/AndroidDataModule.kt` — removed Firebase-dependent bindings
- `shared/data/src/jvmMain/.../di/DesktopDataModule.kt` — removed Firebase-dependent bindings
- `shared/data/src/commonMain/.../di/DataModule.kt` — added common AnalyticsService + RemoteConfigService bindings
- `androidApp/src/main/kotlin/.../ZyntaApplication.kt` — removed Firebase init, Crashlytics, CrashlyticsLogWriter
- `composeApp/src/jvmMain/.../main.kt` — updated comment for RemoteConfig fetch
- `admin-panel/src/lib/firebase.ts` — deleted
- `admin-panel/src/main.tsx` — removed Firebase import and `initFirebase()` call
- `admin-panel/package.json` — removed `firebase` npm package
- `admin-panel/.env.example` — removed all `VITE_FIREBASE_*` env vars
- `.github/workflows/_reusable-build-test.yml` — removed `GOOGLE_SERVICES_JSON` secret and inject step
- `.github/workflows/ci-branch-validate.yml` — removed `GOOGLE_SERVICES_JSON` secret passthrough
- `.github/workflows/ci-pr-gate.yml` — removed `GOOGLE_SERVICES_JSON` secret passthrough
- `.github/workflows/ci-push-main.yml` — removed `GOOGLE_SERVICES_JSON` secret passthrough
- `.gitignore` — removed `google-services.json` entry
- `docs/todo/011-firebase-analytics-sentry-integration.md` — updated to reflect removal
