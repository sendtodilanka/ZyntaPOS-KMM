# GitHub Workflows & Repository Settings Audit

**Date:** 2026-03-04
**Auditor:** Claude (Senior KMP Architect & Systems Engineer)
**Scope:** All 5 GitHub Actions workflow files + repository settings review
**Branch:** `claude/audit-github-workflows-L1uHH`
**Base commit:** `89a2a2d` (main)

---

## 1. Executive Summary

Five workflow files were audited: `ci.yml`, `release.yml`, `auto-merge.yml`,
`deploy.yml`, and `verify.yml`. A total of **11 distinct issues** were found
spanning security vulnerabilities, configuration errors, and operational risks.
All fixable issues have been corrected in this commit.

**The provided GitHub PAT (`github_pat_11AE6GLDY0h…`) returned HTTP 401 on every
API call.** This blocked live auditing of branch protection rules, repository
settings, secrets, and environment configuration. See Section 6 for the full
checklist of settings you must verify manually.

---

## 2. Token Status

| Item | Status |
|------|--------|
| Token format | ✅ Correct format (`github_pat_`, 93 chars) |
| Token validity | ❌ Returns `401 Bad credentials` on all GitHub REST API calls |
| Git remote access | ✅ Works (routed through local git proxy) |

**Action required:** Regenerate the PAT at
`GitHub → Settings → Developer settings → Personal access tokens → Fine-grained tokens`
and set it as the `PAT_TOKEN` repository secret (see Section 6.3).

---

## 3. Workflow File Audit

### 3.1 `ci.yml` — Continuous Integration

**Trigger:** push to `main`/`develop`, PR targeting `main`

| # | Severity | Issue | Fixed |
|---|----------|-------|-------|
| 1 | Medium | No `workflow_dispatch` trigger — CI cannot be run manually from the Actions tab | ✅ |
| 2 | Medium | No `permissions` block — GITHUB_TOKEN defaulted to read/write for all scopes | ✅ |
| 3 | Low | `if: success()` on APK artifact upload step is redundant (that is the implicit default) | ✅ |

**No logic bugs found.** Concurrency group, Gradle cache strategy, and test
pipeline are all correct.

---

### 3.2 `release.yml` — Cross-Platform Release

**Trigger:** push to `main`

| # | Severity | Issue | Fixed |
|---|----------|-------|-------|
| 4 | High | `JAVA_DISTRIBUTION: 'temurin'` — differs from `ci.yml` and `auto-merge.yml` which use `'jetbrains'`. Inconsistent JDK toolchains produce subtly different build artefacts and Gradle daemon behaviour. | ✅ |
| 5 | Medium | No `workflow_dispatch` trigger — cannot manually initiate a release build | ✅ |
| 6 | Medium | No `concurrency` guard — two simultaneous pushes to `main` could race and produce duplicate releases with conflicting tags | ✅ |
| 7 | Medium | No `permissions` block on any build job — all four build jobs inherited implicit read/write GITHUB_TOKEN | ✅ |
| 8 | Informational | APK is signed with a CI-generated debug keystore. The comment acknowledges this as a known TODO ("replace with signed config once release.jks is created"). No action needed for Phase 1. | — |
| 9 | Informational | `softprops/action-gh-release@v2` and all `actions/*` are pinned to mutable tags (`@v4`, `@v3`, `@v2`) rather than immutable commit SHAs. This is a supply-chain risk. Recommended: pin to SHA (e.g., `actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683 # v4`). Not changed in this commit to avoid large diff — address in a dedicated hardening sprint. | — |

---

### 3.3 `auto-merge.yml` — Branch CI → Auto PR → Auto Merge

**Trigger:** push to any `feature/**`, `fix/**`, `hotfix/**`, `chore/**`,
`refactor/**`, `docs/**`, `claude/**`, `test/**`, `build/**`, `ci/**`,
`perf/**`, `style/**` branch

| # | Severity | Issue | Fixed |
|---|----------|-------|-------|
| 10 | High | **Security:** `if [ -z "${{ secrets.PAT_TOKEN }}" ]` directly interpolates the secret value into the bash script source. If an attacker could craft a malicious secret value, they could break out of the string context (script injection). The safe pattern is to expose a boolean indicator via an env var and test that instead. | ✅ |
| 11 | Medium | No workflow-level `permissions` block — GITHUB_TOKEN defaulted to read/write. The `auto-pr` job has job-level permissions but the `validate` job did not. | ✅ |

**Logic is correct:** Validate → Auto-PR → Auto-Merge chain works as designed.
PAT_TOKEN fallback to GITHUB_TOKEN is correctly handled at the YAML expression
level (`${{ secrets.PAT_TOKEN || secrets.GITHUB_TOKEN }}`).

---

### 3.4 `deploy.yml` — VPS Deployment

**Trigger:** push to `main`, `workflow_dispatch`

| # | Severity | Issue | Fixed |
|---|----------|-------|-------|
| 12 | **Critical** | **Security:** `curl -sSfL https://sentry.io/get-cli/ \| sh` — fetches an unversioned installer script from the internet and executes it with full shell privileges. This is a supply-chain attack vector: if `sentry.io` is compromised or the URL redirects, arbitrary code runs on the runner as root. | ✅ |
| 13 | Medium | No workflow-level `permissions` block | ✅ |
| 14 | Informational | `appleboy/ssh-action@v1.0.3` is a third-party action pinned to a mutable tag. Recommend pinning to SHA in a hardening sprint. | — |
| 15 | Informational | Rollback (`git reset --hard HEAD~1`) only goes back exactly one commit. If `main` accumulates multiple commits before the deploy triggers (e.g., during a queue), the rollback target may not be the last-known-good state. Consider storing the previous HEAD SHA before deploy and using it for rollback. | — |

**Fix applied to issue 12:** Replaced `curl | sh` with a direct binary download
from the official `getsentry/sentry-cli` GitHub release at a pinned version
(`2.42.2`). Update `SENTRY_CLI_VERSION` in `deploy.yml` when upgrading.

---

### 3.5 `verify.yml` — Endpoint Health Monitoring

**Trigger:** `workflow_dispatch`, schedule, `workflow_run` (post-deploy)

| # | Severity | Issue | Fixed |
|---|----------|-------|-------|
| 16 | **High** | Schedule `*/15 * * * *` = **96 runs/day**. At a 10-minute timeout per run, this can consume up to 960 runner-minutes/day for a single monitoring workflow alone. GitHub's standard private-repo plan includes 2,000 min/month — this workflow alone could exhaust that in ~2 days. | ✅ |
| 17 | Medium | No `permissions` block | ✅ |
| 18 | Informational | Smoke-test logic is duplicated verbatim between `deploy.yml` and `verify.yml`. Should be extracted into a reusable workflow (`.github/workflows/smoke-test.yml`) — deferred to a refactoring sprint. | — |

**Fix applied to issue 16:** Schedule changed from `*/15` to `*/30` (48
runs/day, 480 min/day maximum). Adjust back to `*/15` only if SLA requirements
demand sub-30-minute outage detection.

---

## 4. Cross-Cutting Issues

### 4.1 JDK Distribution Inconsistency (Fixed)

Before this fix, three different JDK configurations existed:

| Workflow | `JAVA_DISTRIBUTION` |
|----------|---------------------|
| `ci.yml` | `jetbrains` |
| `auto-merge.yml` (validate job) | `jetbrains` |
| `release.yml` | **`temurin`** ← was different |

**Risk:** Different JDK toolchains can produce byte-for-byte-different class
files, different Kotlin compiler behaviour for JVM targets, and different
Compose compiler outputs. A green CI build (JetBrains JDK) does not guarantee
that the release build (Temurin JDK) will produce an equivalent artefact.

**Fix:** All workflows now use `jetbrains` as the distribution.

### 4.2 Action Version Pinning (Not Changed — Deferred)

All first-party (`actions/*`, `gradle/actions/*`) and third-party
(`softprops/*`, `appleboy/*`) actions are pinned to mutable version tags
(`@v4`, `@v3`, etc.) rather than immutable commit SHAs. If a tag is moved
(intentionally or via compromise), the workflow will silently run different
code.

**Recommended fix (future sprint):**
```yaml
# Instead of:
uses: actions/checkout@v4
# Use:
uses: actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683 # v4.2.2
```

Tools like [Dependabot](https://docs.github.com/en/code-security/dependabot)
(with `ecosystem: github-actions`) or
[`pin-github-action`](https://github.com/mheap/pin-github-action) can
automate this.

### 4.3 Secret Keys vs ZYNTA_* Naming

`release.yml` creates `local.properties` by copying `local.properties.template`
and appending two secrets:

```bash
echo "API_BASE_URL=${API_BASE_URL:-https://localhost/api}" >> local.properties
echo "DB_ENCRYPTION_PASSWORD=${DB_ENCRYPTION_PASSWORD:-ci-placeholder-not-for-production}" >> local.properties
```

The template placeholders for `ZYNTA_API_BASE_URL`, `ZYNTA_DB_PASSPHRASE`,
`ZYNTA_FCM_SERVER_KEY`, etc. remain as literal strings like
`YOUR_64_CHAR_HEX_PASSPHRASE_HERE`. The release APK/desktop builds will
compile but will contain non-functional placeholder credentials. This is the
current Phase 1 state (backend not yet integrated). When you add production
secrets to GitHub, add them as:

| GitHub Secret Name | local.properties key |
|--------------------|----------------------|
| `API_BASE_URL` | `API_BASE_URL` |
| `DB_ENCRYPTION_PASSWORD` | `DB_ENCRYPTION_PASSWORD` |
| `ZYNTA_API_BASE_URL` | `ZYNTA_API_BASE_URL` |
| `ZYNTA_DB_PASSPHRASE` | `ZYNTA_DB_PASSPHRASE` |
| `ZYNTA_FCM_SERVER_KEY` | `ZYNTA_FCM_SERVER_KEY` |

And update `release.yml` to inject them into `local.properties`.

---

## 5. Required Repository Secrets

The following secrets **must** exist under
`Settings → Secrets and variables → Actions → Repository secrets`:

| Secret | Required By | Purpose |
|--------|-------------|---------|
| `PAT_TOKEN` | `auto-merge.yml` | Fine-grained PAT with `repo` scope so PRs created by the workflow trigger `ci.yml` (GITHUB_TOKEN cannot do this) |
| `VPS_HOST` | `deploy.yml` | VPS hostname or IP |
| `VPS_USER` | `deploy.yml` | SSH username |
| `VPS_PORT` | `deploy.yml` | SSH port (usually 22) |
| `DEPLOY_SSH_PRIVATE_KEY` | `deploy.yml` | Private key for VPS SSH access |
| `SENTRY_AUTH_TOKEN` | `deploy.yml` | Sentry auth token for release tracking |
| `API_BASE_URL` | `release.yml` | Backend API URL for release builds |
| `DB_ENCRYPTION_PASSWORD` | `release.yml` | DB encryption password for release builds |

If `PAT_TOKEN` is missing or expired, `auto-merge.yml` falls back to
`GITHUB_TOKEN`, which **cannot** trigger `ci.yml` on the PR. Auto-merge will
be enabled but will never resolve because the required "Build & Test" check
will never appear on the PR.

---

## 6. GitHub Repository Settings Checklist

Because the GitHub API token provided was invalid (HTTP 401), the following
settings **could not be verified programmatically**. You must verify each one
manually at `https://github.com/sendtodilanka/ZyntaPOS-KMM/settings`.

### 6.1 General → Pull Requests

| Setting | Required Value | Why |
|---------|---------------|-----|
| Allow squash merging | ✅ Enabled | `auto-merge.yml` uses `--squash` |
| Allow merge commits | Any | Unused by auto-merge pipeline |
| Allow rebase merging | Any | Unused by auto-merge pipeline |
| **Allow auto-merge** | ✅ **Enabled** | Without this, `gh pr merge --auto` fails silently |
| Automatically delete head branches | ✅ Enabled (or `--delete-branch` in workflow handles it) | Clean branch hygiene |

### 6.2 Branches → Branch Protection Rule: `main`

| Setting | Required Value | Failure Mode If Wrong |
|---------|---------------|----------------------|
| Require a pull request before merging | ✅ Enabled | Direct pushes bypass the pipeline |
| Required approvals | **0** | Auto-merge bot cannot self-approve; any value > 0 blocks auto-merge |
| Dismiss stale PR approvals | Any | |
| Require status checks to pass before merging | ✅ Enabled | Auto-merge fires immediately without CI |
| **Required status check name** | **`Build & Test`** (exact, case-sensitive) | Wrong name → CI runs but check is never "required" → auto-merge fires before CI finishes |
| Require branches to be up to date before merging | ✅ Enabled | PRs could merge with stale code |
| Require signed commits | ❌ **Disabled** | CI commits are unsigned; enabling this blocks all auto-merges |
| Require linear history | ❌ **Disabled** | Conflicts with squash-merge history model |
| Lock branch | ❌ **Disabled** | Prevents all writes including auto-merge |
| Do not allow bypassing the above settings | ✅ Enabled | Admins should not bypass — it's a safety gate |
| Restrict who can push to matching branches | ❌ **Disabled** (or include the PAT owner's account and the bot user) | If enabled without including the auto-merge bot user, push is blocked |

### 6.3 Actions → General

| Setting | Required Value |
|---------|---------------|
| Actions permissions | Allow all actions, or at minimum allow the specific actions used |
| Workflow permissions (GITHUB_TOKEN default) | Read and write permissions |
| Allow GitHub Actions to create and approve pull requests | ✅ Enabled |

### 6.4 Actions → Secrets and Variables

Verify all secrets from Section 5 are present and non-expired. The `PAT_TOKEN`
in particular must be regenerated (the one provided in this session was
invalid).

---

## 7. Most Likely Causes of "Messed-Up" Settings

Based on common mistakes made via the GitHub web UI, these are the settings
most likely to be misconfigured:

1. **"Require signed commits" enabled on `main`** — This silently breaks
   all auto-merges from CI. The auto-merge commit (a squash commit created
   by GitHub) is not GPG/SSH signed, so the push is rejected.

2. **Required status check name wrong** — The exact string must be
   `Build & Test` (the `name:` of the job in `ci.yml`). Common mistakes:
   `Build and Test`, `build-and-test`, `CI / Build & Test`, etc.

3. **"Allow auto-merge" not enabled in General settings** — The branch
   protection `gh pr merge --auto` call in `auto-merge.yml` will return an
   error without this setting.

4. **Required approvals set to 1** — Auto-merge will queue but never
   execute because the GITHUB_TOKEN / PAT_TOKEN cannot approve its own PR.

5. **GITHUB_TOKEN permissions set to read-only** — Blocks auto-merge from
   writing to PRs. Set to "Read and write permissions" under
   `Settings → Actions → General → Workflow permissions`.

6. **`PAT_TOKEN` secret missing or expired** — Auto-merge falls back to
   GITHUB_TOKEN, which cannot trigger `ci.yml` on the PR. The required
   status check never appears, so auto-merge never resolves.

---

## 8. Changes Made in This Commit

| File | Changes |
|------|---------|
| `.github/workflows/ci.yml` | Added `workflow_dispatch`, `permissions` block, removed redundant `if: success()` |
| `.github/workflows/release.yml` | Fixed `JAVA_DISTRIBUTION` to `jetbrains`, added `workflow_dispatch`, added `concurrency` guard, added `permissions` blocks to all jobs |
| `.github/workflows/auto-merge.yml` | Fixed secret interpolation security anti-pattern, added workflow-level `permissions` block |
| `.github/workflows/deploy.yml` | Replaced `curl \| sh` Sentry install with pinned binary download, added `permissions` block |
| `.github/workflows/verify.yml` | Changed schedule from `*/15` to `*/30`, added `permissions` block |
| `docs/audit/github-workflows-audit.md` | This document |
