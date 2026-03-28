# 011 — Firebase Analytics, Crash Reporting & Monitoring

**Decision Date:** 2026-03-08
**Status:** ✅ ~98% COMPLETE (code) — Phase 2 code items completed 2026-03-27. Firebase BOM deps + google-services plugin + ZyntaApplication init (Analytics + Crashlytics) implemented. Sentry SDK in all 3 backend services + Android + Desktop JVM. AnalyticsService KMP expect/actual complete. RemoteConfigService KMP expect/actual complete (androidMain Firebase RC SDK, jvmMain stub). Firebase JS SDK added to admin-panel (src/lib/firebase.ts). RemoteConfigProvider interface in shared:core with RemoteConfigKeys constants. Koin wiring complete in androidDataModule + desktopDataModule. ViewModel event wiring complete (16 VMs). Remaining (external only): GA4 property + Firebase project creation (Firebase Console), google-services.json (CI secret). Verified 2026-03-27.
**Phases:** Phase 1 (partial) → Phase 2 → Phase 3

---

## Decision Summary

Full architectural decision covering Analytics and Crash Reporting
for all ZyntaPOS platforms: Android, Desktop JVM, Ktor Backend, Web Admin Panel.

---

## 1. Authentication

> **Google SSO — NOT REQUIRED (REMOVED 2026-03-14)**
>
> ZyntaPOS uses PIN-based authentication for POS staff (offline-first, no internet required).
> Google OAuth2 PKCE was originally planned for Phase 2 owner/admin login but was dropped —
> the existing email + PIN + RBAC system covers all use cases. No SSO integration is needed.
>
> **Auth architecture (current, final):**
> - POS app: `PinManager` (SHA-256 + salt) for staff daily login — offline, no internet
> - `JwtManager` + `TokenStorage` — session tokens from backend login
> - `RbacEngine` — ADMIN/MANAGER/CASHIER/CUSTOMER_SERVICE/REPORTER roles
> - `SessionManager` — idle timeout / auto-lock
> - Admin panel: email + TOTP MFA (see TODO-007f)

---

## 2. Analytics — Firebase GA4 + Measurement Protocol

**Decision: Single GA4 property via Firebase, with Measurement Protocol for non-Firebase platforms**

### Platform Coverage

| Platform       | Method                        | Phase |
|----------------|-------------------------------|-------|
| Android App    | Firebase Analytics SDK        | 1     |
| Web Admin Panel| Firebase JS SDK               | 2     |
| Desktop JVM    | GA4 Measurement Protocol (HTTP)| 2    |
| Ktor Backend   | GA4 Measurement Protocol (HTTP)| 3    |

### Key Points
- Single Firebase project → GA4 property auto-linked → unified dashboard all platforms
- GA4 Measurement Protocol: simple HTTP POST, no SDK, same GA4 property receives events
- Phase 3: GA4 BigQuery export for deep business intelligence analytics
- Firebase Remote Config (Phase 2): edition feature flags (STARTER / PROFESSIONAL / ENTERPRISE)
- **Push notifications:** FCM/VAPID removed. SMS gateway integration planned for Phase 4 instead.

---

## 3. Crash Reporting — Sentry (Primary)

**Decision: Sentry as primary crash reporter across all platforms**

### Platform Coverage

| Platform       | Tool                              | Phase |
|----------------|-----------------------------------|-------|
| Android App    | Sentry (Phase 1) + Crashlytics (Phase 3) | 1 / 3 |
| Desktop JVM    | Sentry                            | 1     |
| Ktor Backend   | Sentry Ktor plugin                | 1     |
| Web Admin Panel| Sentry (Phase 2) + Crashlytics (Phase 2) | 2 |

### Rationale
- Firebase Crashlytics: Android + Web only — no Desktop JVM, no backend
- Sentry: official KMP support, Ktor plugin, Desktop JVM, Android, Web — full coverage
- Phase 3 IRD e-invoice submission failures and sync errors require backend crash context
  → Sentry Ktor plugin is the only viable tool for this
- Phase 3: Add Firebase Crashlytics to Android alongside Sentry for richer Android stack traces

---

## 4. Firebase — Exact Scope (What to Use / What to Avoid)

### USE Firebase FOR
- Android Analytics (GA4 events: screens, transactions, POS actions, cart events)
- Android Performance Monitoring (Phase 2 — SQLCipher query perf, startup time)
- Android Remote Config (Phase 2 — edition feature flags)
- Web Admin Panel (Analytics + Crashlytics + optionally Firebase Hosting)
### DO NOT USE Firebase FOR
- Authentication/SSO (Desktop JVM gap, vendor lock-in risk)
- Firestore or Realtime Database (conflicts with SQLDelight offline-first architecture)
- Cloud Functions (Ktor backend already handles all server logic)
- Anything targeting Desktop JVM directly

---

## 5. Implementation Checklist

### Phase 1 — Immediate (after MVP stabilizes)
- [ ] Create Firebase project → link GA4 property
- [ ] Add Firebase Analytics SDK to Android app (`composeApp/androidMain`)
- [x] Add Sentry SDK to Android app — `sentry-android:8.8.0` in `androidApp/build.gradle.kts`; init in `ZyntaApplication.onCreate()` before Koin
- [x] Add Sentry SDK to Desktop JVM — `sentry:8.8.0` in `composeApp/build.gradle.kts` jvmMain; init in `main.kt` before Koin
- [x] Add Sentry to `backend/api`, `backend/license`, `backend/sync` — `sentry:8.8.0` in each build.gradle.kts; init in each `Application.main()` before embeddedServer
- [x] Add `SENTRY_DSN` to `local.properties.template` (Android) + `docker-compose.yml` env vars (`SENTRY_DSN_API`, `SENTRY_DSN_LICENSE`, `SENTRY_DSN_SYNC`)

### Phase 2
- [x] Add Firebase JS SDK to Web Admin Panel (React) — `admin-panel/src/lib/firebase.ts`, `firebase ^11.6.0` dep, `initFirebase()` in main.tsx | 2026-03-27
- [x] Add Firebase Remote Config (edition feature flags) — `RemoteConfigProvider` interface in shared:core, `RemoteConfigService` expect/actual in shared:data (androidMain Firebase RC actual, jvmMain stub), Koin wiring in both platform modules | 2026-03-27
- [x] Add GA4 Measurement Protocol calls from Desktop JVM — implemented in jvmMain AnalyticsService | 2026-03-13

### Phase 3
- [ ] Add Firebase Crashlytics to Android (dual with Sentry)
- [ ] Add Sentry performance tracing to Ktor
- [ ] Enable GA4 BigQuery export for business intelligence

### Phase 4
- [ ] Implement SMS gateway for push notifications (replaces FCM — low-stock alerts, shift reminders, staff notifications)

---

## 6. New Secrets Required

| Secret | Where | Phase |
|--------|-------|-------|
| `SENTRY_DSN` | local.properties + GitHub Secrets + Docker env | 1 |
| `FIREBASE_GOOGLE_SERVICES_JSON` | Android build — `google-services.json` (gitignored) | 1 |

---

## 7. Architecture Constraints (Do Not Violate)

1. Firebase SDK must only be used in `androidMain` or web — never in `commonMain`
2. GA4 Measurement Protocol calls from Desktop/Backend go through a wrapper in `:shared:data`
   (behind a repository interface — no direct HTTP in feature modules)
3. Sentry init must happen before Koin init (crash reporter must be up before DI)
4. `google-services.json` is gitignored — provide via CI secret / local only
