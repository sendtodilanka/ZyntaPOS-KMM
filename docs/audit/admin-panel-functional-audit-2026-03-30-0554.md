# ZyntaPOS Admin Panel — Functional Adversarial Audit
**Date:** 2026-03-30
**Auditor:** Claude (automated QA agent)
**Scope:** Admin Panel SPA (`admin-panel/src/`) ↔ Backend API (`backend/api/`)

---

## LIST A — Backend Endpoints

> Built by reading Routing.kt + every *Routes.kt file under backend/api/

| Method | Path | Auth | Role | Source File |
|--------|------|------|------|-------------|
| GET | /health | No | Public | HealthRoutes.kt |
| GET | /health/deep | No | Public | HealthRoutes.kt |
| GET | /health/sync | No | Public | HealthRoutes.kt |
| GET | /ping | No | Public | HealthRoutes.kt |
| GET | /.well-known/public-key | No | Public | WellKnownRoutes.kt |
| GET | /.well-known/tls-pins.json | No | Public | WellKnownRoutes.kt |
| POST | /v1/auth/login | No (rate-limited) | Public | AuthRoutes.kt |
| POST | /v1/auth/refresh | No (rate-limited) | Public | AuthRoutes.kt |
| GET | /admin/auth/status | No (CSRF+IP) | Public | AdminAuthRoutes.kt |
| POST | /admin/auth/bootstrap | No (CSRF+IP) | Public (first-run only) | AdminAuthRoutes.kt |
| POST | /admin/auth/login | No (CSRF+IP, rate-limited) | Public | AdminAuthRoutes.kt |
| POST | /admin/auth/refresh | Cookie (CSRF+IP) | Authenticated | AdminAuthRoutes.kt |
| POST | /admin/auth/logout | Cookie (CSRF+IP) | Authenticated | AdminAuthRoutes.kt |
| GET | /admin/auth/me | Cookie (CSRF+IP) | Authenticated | AdminAuthRoutes.kt |
| POST | /admin/auth/mfa/setup | Cookie (CSRF+IP) | Authenticated | AdminAuthRoutes.kt |
| POST | /admin/auth/mfa/enable | Cookie (CSRF+IP) | Authenticated | AdminAuthRoutes.kt |
| POST | /admin/auth/mfa/disable | Cookie (CSRF+IP) | Authenticated | AdminAuthRoutes.kt |
| POST | /admin/auth/mfa/verify | Cookie (CSRF+IP) | Authenticated (pending token) | AdminAuthRoutes.kt |
| POST | /admin/auth/forgot-password | No (CSRF+IP) | Public | AdminAuthRoutes.kt |
| POST | /admin/auth/reset-password | No (CSRF+IP) | Public | AdminAuthRoutes.kt |
| POST | /admin/auth/change-password | Cookie (CSRF+IP) | Authenticated | AdminAuthRoutes.kt |
| GET | /admin/users | Cookie (CSRF+IP) | users:read | AdminAuthRoutes.kt |
| POST | /admin/users | Cookie (CSRF+IP) | users:write | AdminAuthRoutes.kt |
| PATCH | /admin/users/{id} | Cookie (CSRF+IP) | users:write | AdminAuthRoutes.kt |
| GET | /admin/users/{id}/sessions | Cookie (CSRF+IP) | users:read | AdminAuthRoutes.kt |
| DELETE | /admin/users/{id}/sessions | Cookie (CSRF+IP) | users:sessions:revoke | AdminAuthRoutes.kt |
| GET | /v1/products | JWT RS256 | Any POS role | ProductRoutes.kt |
| GET | /v1/orders | JWT RS256 | Any POS role | OrderRoutes.kt |
| GET | /v1/orders/{orderId} | JWT RS256 | Any POS role | OrderRoutes.kt |
| POST | /v1/sync/push | JWT RS256 (sync rate) | Any POS role | SyncRoutes.kt |
| GET | /v1/sync/pull | JWT RS256 (sync rate) | Any POS role | SyncRoutes.kt |
| GET | /v1/export/customer/{customerId} | JWT RS256 | ADMIN or MANAGER | ExportRoutes.kt |
| GET | /admin/health/system | Cookie (CSRF+IP) | Authenticated | AdminHealthRoutes.kt |
| GET | /admin/health/stores | Cookie (CSRF+IP) | Authenticated | AdminHealthRoutes.kt |
| GET | /admin/health/stores/{storeId} | Cookie (CSRF+IP) | Authenticated | AdminHealthRoutes.kt |
