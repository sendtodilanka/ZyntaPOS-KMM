# 011 — Analytics, Crash Reporting & Monitoring

**Decision Date:** 2026-03-08
**Updated:** 2026-03-28 — Firebase removed (ADR-012). All Firebase/GA4 code replaced with Sentry + Kermit.
**Status:** ✅ COMPLETE — Firebase fully removed. Sentry handles crash reporting on all platforms. Kermit structured logging handles analytics events (fanned out via KermitSqliteAdapter to operational_logs + Sentry breadcrumbs in release builds).

---

## Decision Summary

Full architectural decision covering Analytics and Crash Reporting
for all ZyntaPOS platforms: Android, Desktop JVM, Ktor Backend, Web Admin Panel.

**ADR-012 (2026-03-28):** Firebase removed from ZyntaPOS entirely. See `docs/adr/ADR-012-firebase-removal.md`.

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

## 2. Analytics — Kermit Structured Logging (replaces Firebase GA4)

**Decision (ADR-012): Firebase Analytics removed. Analytics events logged via Kermit.**

### Platform Coverage

| Platform       | Method                                          | Status |
|----------------|-------------------------------------------------|--------|
| Android App    | Kermit → KermitSqliteAdapter + Sentry breadcrumbs | ✅ Active |
| Web Admin Panel| Sentry (`@sentry/react`) — page views + errors  | ✅ Active |
| Desktop JVM    | Kermit → KermitSqliteAdapter                    | ✅ Active |
| Ktor Backend   | Sentry Ktor plugin — request tracing + errors   | ✅ Active |

### Key Points
- `AnalyticsService` (commonMain) logs events via Kermit; no network calls, no external SDK
- `KermitSqliteAdapter` fans Kermit events to `operational_logs` table for in-app diagnostic queries
- Sentry breadcrumbs automatically capture Kermit log events in release builds (via sentry-android integration)
- **Push notifications:** FCM/VAPID removed. SMS gateway integration planned for Phase 4 instead.
- **Remote Config:** Firebase RC removed. Feature flags now served from local `FeatureRegistryRepository` (SQLite-backed). See ADR-012.

---

## 3. Crash Reporting — Sentry (Primary, All Platforms)

**Decision: Sentry as sole crash reporter across all platforms (ADR-012: Crashlytics removed)**

### Platform Coverage

| Platform       | Tool                              | Status |
|----------------|-----------------------------------|--------|
| Android App    | Sentry Android SDK (`sentry-android:8.8.0`) | ✅ Active |
| Desktop JVM    | Sentry JVM SDK (`sentry:8.8.0`)   | ✅ Active |
| Ktor Backend   | Sentry Ktor plugin                | ✅ Active |
| Web Admin Panel| `@sentry/react ^10.44.0`          | ✅ Active |

### Rationale for Sentry-only
- Firebase Crashlytics: Android + Web only — no Desktop JVM, no Ktor backend support
- Sentry: official KMP support, Ktor plugin, Desktop JVM, Android, Web — full coverage
- No need for dual-reporting once Sentry covers all platforms

---

## 4. Firebase — Scope (Historical)

Firebase was fully removed on 2026-03-28 (ADR-012). The following Firebase components were in use and have been replaced:

| Component | Was Used For | Replacement |
|-----------|-------------|-------------|
| Firebase Analytics (Android) | GA4 events | Kermit structured logging |
| Firebase Analytics (Web) | GA4 web events | Sentry (`@sentry/react`) |
| GA4 Measurement Protocol (Desktop) | GA4 events | Kermit structured logging |
| Firebase Remote Config (Android) | Edition feature flags | `FeatureRegistryRepository` (SQLite) |
| Firebase Crashlytics (Android) | Crash reporting | Sentry (already active) |
| Firebase JS SDK (Admin Panel) | Analytics | Sentry |

---

## 5. Implementation Checklist

### Completed
- [x] Sentry SDK in Android app — `sentry-android:8.8.0`; init in `ZyntaApplication.onCreate()` before Koin
- [x] Sentry SDK in Desktop JVM — `sentry:8.8.0`; init in `main.kt` before Koin
- [x] Sentry in `backend/api`, `backend/license`, `backend/sync`
- [x] `SENTRY_DSN` in `local.properties.template` + `docker-compose.yml` env vars
- [x] `@sentry/react` in admin panel
- [x] `AnalyticsService` (commonMain) — Kermit-only, no Firebase
- [x] `RemoteConfigService` (commonMain) — `FeatureRegistryRepository`-backed, no Firebase
- [x] Firebase fully removed (ADR-012, 2026-03-28)

### Phase 4
- [ ] Implement SMS gateway for push notifications (replaces FCM — low-stock alerts, shift reminders, staff notifications)

---

## 6. Secrets Required

| Secret | Where | Status |
|--------|-------|--------|
| `ZYNTA_SENTRY_DSN` | local.properties + GitHub Secret (`SENTRY_DSN_*`) | ✅ Active |

> `FIREBASE_GOOGLE_SERVICES_JSON` / `GOOGLE_SERVICES_JSON` — **removed** (ADR-012, 2026-03-28).

---

## 7. Architecture Constraints

1. `AnalyticsService` lives in `commonMain` — no platform-specific implementations needed
2. `RemoteConfigService` lives in `commonMain` — backed by `FeatureRegistryRepository`
3. Sentry init must happen before Koin init (crash reporter must be up before DI)
4. No Firebase SDKs anywhere in the codebase (enforced by ADR-012)
