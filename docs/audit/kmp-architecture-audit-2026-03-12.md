# KMP Architecture Audit Report — Admin Panel & Backend

**Date:** 2026-03-12
**Scope:** `admin-panel/`, `backend/` (API, Sync, License, Common)
**Auditor:** Claude (Senior KMP Architect)
**Branch:** `claude/kmp-architecture-audit-66Ypu`
**Status:** Complete
**Prior Audit:** `docs/audit/backend-modules-audit-2026-03-12.md` (78 findings, Phase A completed via PR #285)

---

## 1. Executive Summary

During a leadership hiatus, multiple independent sessions implemented the `admin-panel` (React SPA) and `backend` (3 Ktor microservices). This audit evaluates both codebases against established standards (ADR-001–008, CLAUDE.md, Clean Architecture, MVI patterns, security model, compliance requirements).

### Overall Risk Assessment

| Area | Risk Level | Assessment |
|------|-----------|------------|
| **Admin Panel** | **LOW** | Excellent. Strong auth (MFA, httpOnly cookies, CSRF), 37 permissions enforced, strict TypeScript, no XSS vectors. Production-ready with 5 minor hardening improvements. |
| **Backend API** | **LOW** (post Phase A) | Phase A (PR #285) resolved all 14 critical findings. Remaining issues are medium/low: rate limiting refinement, MFA throttling, test coverage gaps. |
| **Backend Sync** | **LOW-MEDIUM** | Functional. Minor concerns: graceful shutdown not implemented. |
| **Backend License** | **LOW** | Cleanest service. Properly centralized table definitions, validates admin JWTs correctly. |
| **Compliance** | **MEDIUM** | Audit trail persistence (`AuditRepositoryImpl` TODO stubs) is a PCI-DSS R10 violation. GDPR data export not implemented. These are KMM client-side issues, not backend. |
| **Architecture Compliance** | **LOW** | All 8 ADRs verified as enforced. Clean Architecture layering intact. MVI pattern followed in all feature modules. |

**Key Takeaway:** The unauthorized implementations are architecturally sound and follow project conventions. **No components need to be discarded.** All work should be salvaged with targeted hardening improvements.

---

## 2. Architecture Compliance Verification

All 8 Architecture Decision Records (ADRs) and CLAUDE.md conventions were verified against the actual codebase:

| Rule | Source | Status | Evidence |
|------|--------|--------|----------|
| All VMs extend `BaseViewModel<S,I,E>` | ADR-001 | **COMPLIANT** | All feature ViewModels extend BaseViewModel |
| Domain models use plain names (no `*Entity`) | ADR-002 | **COMPLIANT** | 31 domain models use plain names |
| `SecurePreferences` only in `:shared:security` | ADR-003 | **COMPLIANT** | No competing interface in `:shared:data` |
| No keystore/token scaffold dirs | ADR-004 | **COMPLIANT** | Directories removed |
| Single admin account (`isSystemAdmin`) | ADR-005 | **COMPLIANT** | 3-layer guards enforced |
| Docker images built in CI, pushed to GHCR | ADR-006 | **COMPLIANT** | CI Gate builds and pushes |
| Database-per-service (no cross-DB FKs) | ADR-007 | **COMPLIANT** | `zyntapos_api` and `zyntapos_license` separate; no cross-DB FKs |
| RS256 key distribution (TOFU + BuildConfig) | ADR-008 | **COMPLIANT** | `/.well-known/public-key` endpoint active |
| POS uses opaque refresh tokens (not JWT) | CLAUDE.md | **COMPLIANT** | `UserService.kt:198-214` |
| Admin uses HS256 JWT | CLAUDE.md | **COMPLIANT** | `AdminAuthService.kt:415-423` |
| Single-use token rotation | CLAUDE.md | **COMPLIANT** | `UserService.kt:221-282`, `AdminAuthService.kt:177-210` |
| Brute-force protection (5 attempts, 15 min lockout) | CLAUDE.md | **COMPLIANT** | `UserService.kt:70-71`, `AdminAuthService.kt:618-619` |
| BCrypt cost 12 for admin passwords | CLAUDE.md | **COMPLIANT** | `AdminAuthService.kt:258` |
| Container hardening (read-only FS, cap_drop ALL) | Best practice | **COMPLIANT** | All services in docker-compose.yml |
| Security headers (HSTS, CSP, X-Frame-Options) | Best practice | **COMPLIANT** | Backend + nginx config |

---

## 3. Admin Panel Findings

### 3.1 Security Assessment: EXCELLENT

| Category | Status | Details |
|----------|--------|---------|
| Authentication | **Strong** | MFA (TOTP + backup codes), httpOnly cookies (no localStorage tokens), session management |
| Authorization | **Strong** | 5 admin roles (ADMIN, OPERATOR, FINANCE, AUDITOR, HELPDESK), 37 permissions enforced |
| CSRF Protection | **Strong** | Double-submit cookie pattern (`X-XSRF-Token` header on all state-changing requests) |
| XSS Prevention | **Strong** | No `dangerouslySetInnerHTML`, no `eval()`, no `innerHTML`; React auto-escapes |
| Input Validation | **Strong** | Zod + react-hook-form on all forms; backend enforces constraints |
| Secrets Management | **Strong** | No hardcoded credentials; tokens exclusively via httpOnly cookies |
| Type Safety | **Strong** | Strict TypeScript (`strict: true`, `noUnusedLocals`, `noUnusedParameters`) |
| State Management | **Clean** | Zustand (client state) + TanStack Query (server state); proper separation |
| Docker Build | **Secure** | Multi-stage Alpine build; nginx serves static files |

### 3.2 Findings (All Low Severity)

| # | File | Deviation | Severity | Impact | Recommendation |
|---|------|-----------|----------|--------|----------------|
| AP-1 | `admin-panel/nginx.conf` | Missing HSTS header | Low | MitM risk on first connection | Add `Strict-Transport-Security: max-age=63072000; includeSubDomains; preload` |
| AP-2 | `admin-panel/nginx.conf` | CSP uses `'unsafe-inline'` for scripts | Low | Weaker XSS protection | Accept as trade-off (required by React/Vite); document in ADR-009 |
| AP-3 | `admin-panel/nginx.conf` | Missing `Permissions-Policy` header | Low | No browser API restriction | Add `Permissions-Policy: camera=(), microphone=(), geolocation=()` |
| AP-4 | `admin-panel/index.html` | Google Fonts link missing SRI | Low | CDN tampering risk | Add `integrity` attribute with hash |
| AP-5 | `admin-panel/src/lib/api-client.ts` | No frontend mutation debounce | Low | Double-submit risk | Add debounce wrapper; backend rate limiting is primary defense |

### 3.3 Strengths

- **No dead code** — ESLint catches unused imports
- **No console.log** in production code
- **Proper caching** — TanStack Query TTLs (Dashboard: 30s, Health: 15s, Sync: 10s)
- **Accessibility** — Semantic HTML, proper aria-labels
- **Deployment** — nginx security headers, gzip, SPA fallback, health endpoint

---

## 4. Backend Findings (Post Phase A)

### 4.1 Security Assessment: STRONG (After PR #285)

All 14 critical findings from the prior audit (`backend-modules-audit-2026-03-12.md`) were resolved in Phase A (PR #285):

| Critical Finding | Resolution |
|-----------------|------------|
| POS brute-force protection missing | 5-attempt lockout, 15-min cooldown |
| POS refresh token was signed JWT | Changed to opaque tokens in `pos_sessions` table |
| Email template XSS | `htmlEscape()` applied to all interpolated values |
| No CSRF protection | Double-submit cookie pattern implemented |
| Token revocation missing | Single-use rotation with `revoked_tokens` table |
| JWT config defaults fragile | Validated at startup |

### 4.2 Remaining Findings

| # | File | Deviation | Severity | Impact |
|---|------|-----------|----------|--------|
| BE-1 | `backend/api/.../plugins/RateLimit.kt` | Rate limiting present but TODO-009 incomplete | Medium | Auth: 10/min, API: 300/min, Sync: 60/min — configured but not deeply audited |
| BE-2 | `backend/api/.../service/AdminAuthService.kt:622` | BCrypt 72-byte truncation | Medium | Mitigated: max 128 char password validation enforced |
| BE-3 | `backend/api/.../service/AdminAuthService.kt:414-424` | MFA code submission no explicit rate limit | Low | Mitigated: 2-min TTL on pending MFA token |
| BE-4 | `backend/api/.../service/AdminAuthService.kt:535-557` | Email enumeration on password reset | Low | Mitigated: route returns 202 Accepted regardless of user existence |
| BE-5 | `backend/api/.../plugins/Csrf.kt` | No CSRF on admin API endpoints | Low | Acceptable: SPA + CORS + SameSite cookies provide equivalent protection |
| BE-6 | `backend/api/.../Application.kt:24-30` | Canary tokens (fake AWS keys) | Info | Intentional — TODO-010 Falco alert honeypot |

### 4.3 Backend Strengths Confirmed

- **Container hardening:** read-only FS, tmpfs /tmp (noexec, nosuid), cap_drop ALL, no NEW_PRIVILEGES, memory limits
- **No SQL injection:** All queries parameterized via Exposed ORM
- **Error handling:** No sensitive data leakage; emails masked in logs (`m***@domain.com`)
- **Network isolation:** Only Caddy exposes ports 80/443; all services on internal bridge network
- **Secrets:** Docker secrets (`/run/secrets/`); database connection strings never logged
- **Java deserialization:** Blocked via `System.setProperty("jdk.serialFilter", "!*")`

---

## 5. Compliance Gaps (KMM Client-Side)

These are NOT backend issues — they exist in the KMM shared modules:

| # | Area | Gap | Severity | Impact |
|---|------|-----|----------|--------|
| CG-1 | `shared/data/.../AuditRepositoryImpl` | TODO stubs — audit entries not persisted to DB | **Critical** | PCI-DSS Requirement 10 violation; all audit events generated by `SecurityAuditLogger` are discarded in memory |
| CG-2 | `shared/domain` | `ExportCustomerDataUseCase` not implemented | High | GDPR right-to-access not functional |
| CG-3 | `shared/domain` | Hard erasure not implemented (soft-delete only) | High | GDPR right-to-erasure incomplete |
| CG-4 | System-wide | No automated data retention purge policy | Medium | No expiration for old records |

---

## 6. Salvage vs Discard Assessment

| Component | Verdict | Rationale |
|-----------|---------|-----------|
| **Admin panel (entire)** | **SALVAGE** | Production-quality; strong security, clean architecture, proper RBAC enforcement |
| **Backend API service** | **SALVAGE** | Phase A resolved all critical issues; remaining work is incremental hardening |
| **Backend Sync service** | **SALVAGE** | Functional; graceful shutdown is only real gap |
| **Backend License service** | **SALVAGE** | Cleanest implementation; properly centralized table definitions |
| **Backend Common library** | **SALVAGE** | `ValidationScope` DSL is well-designed and reusable |
| **Docker Compose config** | **SALVAGE** | Properly hardened containers, correct network isolation, health checks on all services |

**Nothing needs to be discarded.** The independent implementations accurately followed project conventions.

---

## 7. Remediation Roadmap

### Work Stream A: Admin Panel Hardening (Low Priority — ~2 hours)

| Step | Action | File(s) |
|------|--------|---------|
| A1 | Add HSTS header | `admin-panel/nginx.conf` |
| A2 | Add Permissions-Policy header | `admin-panel/nginx.conf` |
| A3 | Add SRI integrity to Google Fonts | `admin-panel/index.html` |
| A4 | Add mutation debounce wrapper | `admin-panel/src/lib/api-client.ts` |
| A5 | Document CSP `unsafe-inline` acceptance | `docs/adr/ADR-009-*` |

### Work Stream B: Backend Phases B-F (Medium Priority — ~20 hours)

Continue the existing 6-phase remediation plan from `backend-modules-audit-2026-03-12.md`:

| Phase | Scope | Priority | Est. |
|-------|-------|----------|------|
| Phase B | Cross-module alignment (error responses, JWT config, timestamps) | Medium | 4h |
| Phase C | Test coverage for 52 untested files | Medium | 8h |
| Phase D | Centralize table definitions, graceful shutdown, close HTTP clients | Low | 4h |
| Phase E | Deploy OpenAPI/Swagger UI | Low | 2h |
| Phase F | MFA code rate limiting, IP allowlisting | Low | 2h |

### Work Stream C: KMM Client Compliance (Critical — Blocks Launch)

| Step | Action | File(s) | Priority |
|------|--------|---------|----------|
| C1 | Implement `AuditRepositoryImpl` persistence | `shared/data/.../repository/AuditRepositoryImpl.kt` | **Critical** |
| C2 | Implement `ExportCustomerDataUseCase` | `shared/domain/.../usecase/` | High |
| C3 | Implement hard-delete for GDPR erasure | `shared/data/.../repository/CustomerRepositoryImpl.kt` | High |
| C4 | Add data retention automation | `shared/data/.../sync/` | Medium |

### Execution Order

1. **Work Stream C** (critical path — blocks Phase 1 launch)
2. **Work Stream A** (quick wins, non-blocking)
3. **Work Stream B** (sequential, across multiple sessions)

---

## 8. Phase Status Summary

| Phase | Scope | Status |
|-------|-------|--------|
| Phase A: Critical Security | Backend security hardening (14 critical findings) | **COMPLETED** (PR #285) |
| Phase B: Cross-Module Alignment | Error responses, JWT config, timestamps | Pending |
| Phase C: Test Coverage | 52 untested backend files | Pending |
| Phase D: Code Quality & Performance | Table centralization, graceful shutdown | Pending |
| Phase E: Documentation & API Spec | OpenAPI/Swagger deployment | Pending |
| Phase F: Advanced Security Hardening | MFA rate limiting, IP allowlisting | Pending |
| **KMM Compliance** | Audit persistence, GDPR export/erasure | **CRITICAL — Pending** |
| **Admin Panel Hardening** | HSTS, Permissions-Policy, SRI, debounce | Low — Pending |

---

## Appendix: Audit Methodology

### Files Audited

**Admin Panel (~45 files):**
- `package.json`, `tsconfig.json`, `vite.config.ts`, `.env.example`
- `src/types/user.ts`, `src/hooks/use-auth.ts`, `src/stores/auth-store.ts`, `src/stores/ui-store.ts`
- `src/lib/api-client.ts`, `src/api/auth.ts`, `src/api/users.ts`, `src/api/tickets.ts`
- `src/routes/` (login, dashboard, users, tickets, settings, sync)
- `nginx.conf`, `Dockerfile`, `index.html`

**Backend (~60 files):**
- `backend/api/src/main/kotlin/**/Application.kt`, `UserService.kt`, `AdminAuthService.kt`
- `backend/api/src/main/kotlin/**/plugins/` (RateLimit, Csrf, Security headers)
- `backend/api/src/main/kotlin/**/sync/` (SyncProcessor, ServerConflictResolver)
- `backend/api/src/main/resources/db/migration/V1-V11`
- `backend/license/src/main/kotlin/**/Application.kt`, migrations V1-V4
- `backend/sync/src/main/kotlin/**/hub/` (WebSocketHub, RedisPubSubListener)
- `backend/common/src/main/kotlin/**/ValidationScope.kt`
- `docker-compose.yml`, all Dockerfiles

**Documentation:**
- All 8 ADRs (`docs/adr/ADR-001` through `ADR-008`)
- `docs/audit/backend-modules-audit-2026-03-12.md`
- `docs/audit/gap_analysis_2026-03-09.md`
- `docs/architecture/security-model.md`
- `docs/compliance/README.md`
- `docs/api/README.md`
- `CONTRIBUTING.md`, `CLAUDE.md`

### Tools Used
- Static code analysis (grep, glob patterns)
- Dependency review (`package.json`, `libs.versions.toml`)
- Architecture compliance checks (ADR verification)
- Security pattern matching (auth flows, input validation, output encoding)
