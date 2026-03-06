# GitHub Workflows & Repository Settings Audit

**Date:** 2026-03-04
**Auditor:** Claude (Senior KMP Architect & Systems Engineer)
**Scope:** All 5 GitHub Actions workflow files + full live repository settings
**Branch:** `claude/audit-github-workflows-L1uHH`
**Base commit:** `89a2a2d` (main)
**API status:** ✅ Full access — all findings verified against live GitHub REST API
**Pipeline test:** 2026-03-04 — end-to-end pipeline smoke test commit (auto-merge → CI → deploy)

---

## 1. Executive Summary

### Workflow File Audit (commit `64f6875` — previous session)
Five workflow files were audited and **11 issues** were fixed:
`workflow_dispatch` triggers, `permissions` blocks, JDK inconsistency,
`curl | sh` supply chain vulnerability, schedule frequency, and secret
interpolation security anti-pattern. See Section 3.

### Live Repository Settings Audit (this commit)
**9 additional findings** from the GitHub REST API. All actionable items
have been remediated in this session. See Section 4.

### Changes Applied (this session)
| Action | Method | Result |
|--------|--------|--------|
| `PAT_TOKEN` secret updated with new PAT | GitHub API `PUT /actions/secrets/PAT_TOKEN` | ✅ 204 |
| Repository visibility → **private** | GitHub API `PATCH /repos` | ✅ 200 |
| `allow_update_branch` → enabled | GitHub API `PATCH /repos` | ✅ 200 |
| Production environment → protected branches only | GitHub API `PUT /environments/production` | ✅ 200 |
| `release.yml` push trigger disabled | File edit | ✅ |
| Secret scanning | GitHub API | ❌ Requires GitHub Advanced Security (paid) |
| Required PR before merging on `main` | GitHub API | ❌ Requires GitHub Pro (paid) |

---

## 2. Repository State — Verified Good ✅

These settings were confirmed correct against the live API and require **no
changes**:

| Setting | Verified Value | Notes |
|---------|---------------|-------|
| `allow_auto_merge` | `true` | Required for auto-merge pipeline |
| `allow_squash_merge` | `true` | Pipeline uses `--squash` |
| `allow_merge_commit` | `false` | Squash-only history — clean |
| `allow_rebase_merge` | `false` | Squash-only — consistent |
| `delete_branch_on_merge` | `true` | Branch hygiene |
| Required status check | `"Build & Test"` | Exact match to `ci.yml` job name |
| `strict` (require up-to-date) | `true` | PRs must be current before merge |
| Required status check `app_id` | `15368` (GitHub Actions) | Correct app |
| `required_signatures` | `false` | CI commits are unsigned — correct |
| `allow_force_pushes` | `false` | main is protected |
| `allow_deletions` | `false` | main cannot be deleted |
| `enforce_admins` | `true` | Owner also subject to protection rules |
| `default_workflow_permissions` | `"read"` | GITHUB_TOKEN read-only by default |
| `can_approve_pull_request_reviews` | `false` | Bot cannot self-approve |
| Collaborators | `sendtodilanka` (admin only) | Single-owner repo |
| Teams | none | Personal repo |
| Webhooks | none | No external integrations configured |
| `PAT_TOKEN` secret | present | Updated 2026-03-04 |
| `VPS_HOST`, `VPS_USER`, `VPS_PORT` | present | VPS SSH access |
| `VPS_USER_KEY` | present | VPS deploy key |
| `SENTRY_AUTH_TOKEN` | present | Sentry error tracking |
| All 5 workflow files | `state: "active"` | All workflows enabled |
| Latest release | `v1.0.0-build.75` | Created by `github-actions[bot]` ✅ |

---

## 3. Workflow File Audit (Issues Fixed in Previous Session)

### Summary Table

| # | File | Severity | Issue | Status |
|---|------|----------|-------|--------|
| 1 | `ci.yml` | Medium | No `workflow_dispatch` trigger | ✅ Fixed |
| 2 | `ci.yml` | Medium | No `permissions` block (defaulted to read/write) | ✅ Fixed |
| 3 | `ci.yml` | Low | Redundant `if: success()` on artifact upload | ✅ Fixed |
| 4 | `release.yml` | High | `JAVA_DISTRIBUTION: 'temurin'` vs `'jetbrains'` in all other workflows | ✅ Fixed |
| 5 | `release.yml` | Medium | No `workflow_dispatch` trigger | ✅ Fixed |
| 6 | `release.yml` | Medium | No `concurrency` guard (duplicate releases possible) | ✅ Fixed |
| 7 | `release.yml` | Medium | No `permissions` blocks on 4 build jobs | ✅ Fixed |
| 8 | `auto-merge.yml` | High | Direct secret interpolation in bash: `if [ -z "${{ secrets.PAT_TOKEN }}" ]` | ✅ Fixed |
| 9 | `auto-merge.yml` | Medium | No workflow-level `permissions` block | ✅ Fixed |
| 10 | `deploy.yml` | **Critical** | `curl sentry.io/get-cli/ \| sh` — unversioned pipe-to-shell (supply chain attack vector) | ✅ Fixed |
| 11 | `deploy.yml` | Medium | No `permissions` block | ✅ Fixed |
| 12 | `verify.yml` | **Critical** | Schedule `*/15` = 96 runs/day, up to 960 runner-min/day (exhausts free plan in ~2 days) | ✅ Fixed |
| 13 | `verify.yml` | Medium | No `permissions` block | ✅ Fixed |

### Key Fixes Detail

**Issue #10 — Sentry CLI install (deploy.yml)**
```yaml
# BEFORE (dangerous — pipes unversioned internet script to shell):
run: curl -sSfL https://sentry.io/get-cli/ | sh -s -- --install-dir /usr/local/bin

# AFTER (pinned binary download from official GitHub release):
run: |
  SENTRY_CLI_VERSION="2.42.2"
  curl -sSfL \
    "https://github.com/getsentry/sentry-cli/releases/download/${SENTRY_CLI_VERSION}/sentry-cli-Linux-x86_64" \
    -o /usr/local/bin/sentry-cli
  chmod +x /usr/local/bin/sentry-cli
```

**Issue #8 — Secret interpolation (auto-merge.yml)**
```yaml
# BEFORE (insecure — secret value embedded in bash source):
if [ -z "${{ secrets.PAT_TOKEN }}" ]; then

# AFTER (safe — boolean indicator via env var, secret never touches shell source):
env:
  HAS_PAT_TOKEN: ${{ secrets.PAT_TOKEN != '' }}
run: |
  if [ "$HAS_PAT_TOKEN" != "true" ]; then
```

**Issue #4 — JDK inconsistency**
```yaml
# BEFORE release.yml:
JAVA_DISTRIBUTION: 'temurin'   # Different from ci.yml and auto-merge.yml

# AFTER (all workflows):
JAVA_DISTRIBUTION: 'jetbrains'  # Consistent across all build types
```

---

## 4. Live Repository Settings Audit (This Session)

### R-1 — Repository Was PUBLIC [CRITICAL → FIXED]

**Finding:** The repository was `"visibility": "public"` — an enterprise POS
system with live VPS SSH deploy keys, Sentry DSN, Docker Compose deployment
scripts, and active production infrastructure was fully visible to the public.

**Root cause:** Hit GitHub free plan's private repo limit, temporarily made
public as a workaround.

**Fix applied:** `PATCH /repos` → `{"private": true}` → HTTP 200 ✅

**Verified:** `"private": true, "visibility": "private"` confirmed in response.

---

### R-2 — PAT_TOKEN Was Revoked [CRITICAL → FIXED]

**Finding:** The `PAT_TOKEN` secret contained an expired/revoked token. The
auto-merge pipeline was falling back to `GITHUB_TOKEN`, which cannot trigger
`ci.yml` on PRs. Every auto-merge run was producing a warning but no CI check
would appear — auto-merge would queue but never fire.

**Fix applied:** Secret encrypted with repo's libsodium public key and uploaded
via `PUT /repos/.../actions/secrets/PAT_TOKEN` → HTTP 204 ✅

---

### R-3 — `required_pull_request_reviews` Absent [HIGH → Acknowledged]

**Finding:** The `main` branch protection had no `required_pull_request_reviews`
rule. Direct pushes to `main` bypass CI entirely (only PR merges gate on "Build
& Test").

**API response:** Full protection rule returned no `required_pull_request_reviews`
object — confirmed absent.

**Attempted fix:** `PUT /branches/main/protection` with
`required_pull_request_reviews: {required_approving_review_count: 0}` → **HTTP
403: "Upgrade to GitHub Pro or make this repository public to enable this
feature."**

**Status:** Not fixable on free private repo plan.

**Mitigation:** The auto-merge pipeline is the only intended path to `main`.
Since the repo has a single owner (`sendtodilanka`) who controls all local
pushes, direct push risk is low. Re-enable this rule if upgrading to GitHub Pro.

---

### R-4 — Production Environment Had No Protection Rules [HIGH → FIXED]

**Finding:** The `production` environment had:
- `protection_rules: []` — no deployment protection whatsoever
- `can_admins_bypass: true`
- `deployment_branch_policy: null`

Any workflow run on any branch could deploy to production. An accidental push
to a `feature/` branch that somehow triggered `deploy.yml` could reach the VPS.

**Fix applied:** `PUT /environments/production` →
```json
{
  "deployment_branch_policy": {
    "protected_branches": true,
    "custom_branch_policies": false
  }
}
```

**Verified response:**
```json
"deployment_branch_policy": { "protected_branches": true, "custom_branch_policies": false },
"protection_rules": [{ "id": 49143081, "type": "branch_policy" }]
```
✅ Production now only deploys from `main` (the only protected branch).

---

### R-5 — `allow_update_branch` Was Disabled [MEDIUM → FIXED]

**Finding:** `allow_update_branch: false` while `strict: true` is required for
status checks. When `main` advances while a PR is open (e.g., another PR merges
first), the pending PR branch falls behind. GitHub blocks the auto-merge until
the branch is manually updated — breaking the fully-autonomous pipeline.

**Fix applied:** `PATCH /repos` → `{"allow_update_branch": true}` → HTTP 200 ✅

**Verified:** `allow_update_branch: True` confirmed in response.

**Pipeline impact:** Auto-merge now automatically keeps PR branches up-to-date
with `main` when required, without human intervention.

---

### R-6 — Secret Scanning Disabled [MEDIUM → Not Available on Free Plan]

**Finding:** On the public repo, GitHub natively scans for leaked secrets.
After making the repo private, `secret_scanning` and
`secret_scanning_push_protection` are only available with GitHub Advanced
Security (GHAS), which requires a paid plan.

**Attempted fix:** `PATCH /repos` with `security_and_analysis` →
**HTTP 422: "Secret scanning is not available for this repository."**

**Status:** Not available on free private repo.

**Mitigation:** Commit hygiene is the primary defence:
- `local.properties` is in `.gitignore`
- `local.properties.template` contains only placeholder strings
- Workflow files use `secrets.*` references — no hardcoded credentials

---

### R-7 — Missing Release Secrets [MEDIUM → Acknowledged]

**Finding:** `API_BASE_URL` and `DB_ENCRYPTION_PASSWORD` secrets are absent.
`release.yml` falls back to:
- `API_BASE_URL=https://localhost/api`
- `DB_ENCRYPTION_PASSWORD=ci-placeholder-not-for-production`

Release APKs/JARs will compile but have non-functional placeholder credentials.

**Status:** Acceptable for Phase 1 (backend not yet integrated into release
builds). When ready, add these secrets under
`Settings → Secrets and variables → Actions`.

| Secret name to add | `local.properties` key | Value source |
|-------------------|----------------------|-------------|
| `API_BASE_URL` | `API_BASE_URL` | Backend URL |
| `DB_ENCRYPTION_PASSWORD` | `DB_ENCRYPTION_PASSWORD` | `openssl rand -hex 32` |

---

### R-8 — Release Workflow Fires on Every Push to `main` [MEDIUM → FIXED]

**Finding:** `release.yml` had `push: branches: [main]`, meaning every single
`claude/` branch merge triggered a 60-minute, 4-runner cross-platform release
build (Android APK + macOS DMG + Windows MSI + Linux DEB). With active
development generating many merges per day, this is wasteful and consumes
Actions minutes rapidly.

**Fix applied:** Removed `push: branches: [main]` trigger from `release.yml`.
The workflow now only runs when manually triggered via `workflow_dispatch`.

**Pipeline impact:** Zero. `deploy.yml` is completely independent — VPS
deployments continue automatically on every push to `main` as before.
Cross-platform release builds can be triggered manually when a version is ready
for distribution.

---

### R-9 — Informational Findings

| # | Finding | Impact |
|---|---------|--------|
| R-9a | `sha_pinning_required: false` for Actions — actions like `actions/checkout@v4` use mutable tags, not commit SHAs. A compromised action tag could run arbitrary code on runners. Recommend a hardening sprint to pin all actions to SHA. | Low (trusted publishers) |
| R-9b | No Dependabot configured. Kotlin/Gradle dependencies may accumulate CVEs silently. Add `.github/dependabot.yml` to enable automated dependency PRs. | Low |
| R-9c | 284 total workflow runs in history — pipeline is actively used and healthy. Latest runs are all `conclusion: "success"`. | Informational |
| R-9d | `squash_merge_commit_title: "COMMIT_OR_PR_TITLE"` — squash commit title uses the PR title, which is auto-derived from branch name in `auto-merge.yml`. Resulting main history is readable. | Informational |
| R-9e | `web_commit_signoff_required: false` — DCO sign-off not required. Appropriate for a closed-source commercial project. | Informational |

---

## 5. Automation Pipeline Health Check

After all changes, the fully-autonomous pipeline is:

```
Developer / Claude pushes to claude/* branch
        │
        ▼
auto-merge.yml (validate job)
  • Runs ci.yml equivalent: tests, detekt, lint, APK build
  • JAVA_DISTRIBUTION: jetbrains (consistent ✅)
        │
        ▼
auto-merge.yml (auto-pr job)
  • Creates PR using PAT_TOKEN ← now valid again ✅
  • Enables auto-merge (--squash)
  • Deletes branch after merge
        │
        ▼
ci.yml triggered on PR by PAT_TOKEN
  • "Build & Test" job runs
  • Required status check: "Build & Test" ← correct name ✅
        │
        ▼
Auto-merge fires when check passes
  • allow_auto_merge: true ✅
  • allow_update_branch: true ✅ (now auto-updates stale PRs)
  • required_approving_review_count: N/A (free plan)
        │
        ▼
deploy.yml triggered on push to main
  • Deploys to Contabo VPS via SSH
  • Docker Compose pull + up -d
  • Smoke tests 6 endpoints
  • Auto-rollback on smoke test failure
  • Sentry release notification (pinned CLI v2.42.2 ✅)
  • Environment: production ← now restricted to protected branches ✅
        │
        ▼
verify.yml (post-deploy + every 30 min)
  • Independently validates all 6 endpoints
  • 30-min schedule ← reduced from 15 min ✅
```

**Release workflow** is decoupled and runs only on `workflow_dispatch` — will
not interfere with the continuous delivery pipeline.

---

## 6. Required Secrets — Current Status

| Secret | Present | Purpose | Notes |
|--------|---------|---------|-------|
| `PAT_TOKEN` | ✅ Updated 2026-03-04 | Auto-merge pipeline creates PRs | Renewed this session |
| `VPS_HOST` | ✅ | SSH deploy target | |
| `VPS_USER` | ✅ | SSH username | |
| `VPS_PORT` | ✅ | SSH port | |
| `VPS_USER_KEY` | ✅ | SSH private key | |
| `SENTRY_AUTH_TOKEN` | ✅ | Sentry release tracking | |
| `API_BASE_URL` | ❌ Missing | Release build API URL | Phase 2 |
| `DB_ENCRYPTION_PASSWORD` | ❌ Missing | Release build DB key | Phase 2 |

---

## 7. Free Plan Limitations (Cannot Fix Without Upgrade)

| Feature | Requires | Impact |
|---------|----------|--------|
| Required PR before merging | GitHub Pro | Direct pushes to `main` not blocked |
| Secret scanning on private repos | GitHub Advanced Security | No automated credential leak detection |
| Code scanning (CodeQL) | GitHub Advanced Security | No automated vulnerability scanning |
| More than 1 environment protection reviewer | GitHub Pro | Single reviewer only |

**Recommendation:** If the project upgrades to GitHub Pro ($4/user/month), re-enable:
1. `required_pull_request_reviews` with `required_approving_review_count: 0`
   (PRs required before merging, no human approval needed — fully autonomous)
2. Secret scanning + push protection

---

## 8. Commit Log

| Commit | Change |
|--------|--------|
| `64f6875` | ci(workflows): 13 fixes across all 5 workflow files (previous session) |
| This commit | ci(audit): live API remediations — private repo, PAT_TOKEN, env policy, release trigger |


---

## 9. Sprint F — Backend Docker Image Pipeline (2026-03-05)

**Commit:** `05438ca`, `d44ae10`
**Branch:** `claude/audit-kmp-roadmap-AjuNk`
**Trigger:** GitHub Actions run `22704980409` — `Step[3+4]: CI Gate` failed

---

### 9.1 Root Cause Analysis (via GitHub REST API)

Fetched job logs using PAT-authenticated API call:
```
GET /repos/sendtodilanka/ZyntaPOS-KMM/actions/jobs/65830176340/logs
```

**Error in all 3 `build-backend-images` matrix jobs:**
```
e: file:///build/build.gradle.kts:30:9: Unresolved reference: apiDelay
FAILURE: Build failed with an exception.
Script compilation error:
  Line 30:         apiDelay = 3500
                   ^ Unresolved reference: apiDelay
BUILD FAILED in 51s
```

**Cause:** OWASP Dependency Check Gradle plugin 10.0.4 renamed `nvd.apiDelay` → `nvd.delay`.
The Sprint E implementation used the v9.x property name.

---

### 9.2 Fixes Applied

#### Fix 1 — OWASP nvd.apiDelay renamed (commit `d44ae10`)

| File | Change |
|------|--------|
| `backend/api/build.gradle.kts:30` | `apiDelay = 3500` → `delay = 3500` |
| `backend/license/build.gradle.kts:30` | same |
| `backend/sync/build.gradle.kts:29` | same |

Also fixes `sec-backend-scan.yml` OWASP jobs — `./gradlew dependencyCheckAnalyze` was
failing with the same error (suppressed by `|| true` in the Docker dependencies layer,
but fatal in the `shadowJar` step).

#### Fix 2 — Architecture change: CI builds Docker images, VPS pulls (commit `05438ca`)

The deploy was using `docker compose up -d --build` on the VPS. This required `gradlew`
and `gradle/wrapper/` inside the Docker build context — files that were either absent or
gitignored. Root cause: VPS should never build from source.

| # | File | Change | Why |
|---|------|--------|-----|
| 1 | `.gitignore` | Remove `backend/*/gradle/` line; add `!backend/*/gradle/wrapper/gradle-wrapper.jar` exception | Allow gradle wrapper to be committed |
| 2–4 | `backend/api/gradlew` + `gradle/wrapper/*` | New files (copy from root Gradle 8.14.3) | Dockerfile COPY + `sec-backend-scan.yml` |
| 5–7 | `backend/license/gradlew` + `gradle/wrapper/*` | Same | Same |
| 8–10 | `backend/sync/gradlew` + `gradle/wrapper/*` | Same | Same |
| 11 | `.github/workflows/ci-gate.yml` | Add `build-backend-images` matrix job; add `packages: write`; change `trigger-deploy.needs` to `[build-and-test, build-backend-images]` | Build + push images to GHCR before deploy |
| 12 | `docker-compose.yml` | Replace `build: context: ./backend/{service}` with `image: ghcr.io/sendtodilanka/zyntapos-{service}:latest` | VPS pulls pre-built images |
| 13 | `.github/workflows/cd-deploy.yml` | Add `docker login ghcr.io`; remove `--build` from `docker compose up` | VPS no longer builds from source |

---

### 9.3 Updated Pipeline Diagram

```
Developer / Claude pushes to claude/* branch
        │
        ▼  ci-branch-validate.yml — Step[1]
        │
        ▼  ci-auto-pr.yml — Step[2]
        │
        ▼  ci-gate.yml — Step[3+4] on push to main
        │    ├── build-and-test (KMM: build + lint + tests)
        │    └── build-backend-images (matrix: api / license / sync)
        │          └── docker/build-push-action@v6
        │                → ghcr.io/sendtodilanka/zyntapos-{service}:latest
        │                → ghcr.io/sendtodilanka/zyntapos-{service}:<sha>
        │
        ▼  trigger-deploy (needs: [build-and-test, build-backend-images])
        │
        ▼  cd-deploy.yml — Step[5]
        │    docker login ghcr.io (PAT_TOKEN — needs read:packages scope)
        │    docker compose pull  ← pre-built images
        │    docker compose up -d ← no --build
        │
        ▼  cd-smoke.yml — Step[6]
        │
        ▼  cd-verify-endpoints.yml — Step[7]
```

---

### 9.4 Updated Required Secrets

| Secret | Present | Purpose | Notes |
|--------|---------|---------|-------|
| `PAT_TOKEN` | ✅ | Auto-merge pipeline + GHCR pull on VPS | Must have **`read:packages`** scope in addition to `repo` |
| `VPS_HOST` | ✅ | SSH deploy target | |
| `VPS_USER` | ✅ | SSH username | |
| `VPS_PORT` | ✅ | SSH port | |
| `VPS_USER_KEY` | ✅ | SSH private key | |
| `SENTRY_AUTH_TOKEN` | ✅ | Sentry release tracking | |
| `GITHUB_TOKEN` | Auto | GHCR image push | Auto-provided by Actions; `packages: write` granted in `ci-gate.yml` |
| `NVD_API_KEY` | ❌ Optional | OWASP NVD higher rate limit | Not required; `nvd.delay=3500` limits requests without key |

---

### 9.5 ADR Created

**ADR-006** (`docs/adr/ADR-006-backend-docker-build-in-ci.md`) — ACCEPTED

Documents the decision to build Docker images in CI instead of on the VPS, including
context, consequences, and rollback procedure.


