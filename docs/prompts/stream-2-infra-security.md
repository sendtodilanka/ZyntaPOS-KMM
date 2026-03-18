# Stream 2: Infra & Security — Items A7, A6, A2

**Master Plan:** `todo/missing-features-implementation-plan.md` (Sections A2, A6, A7)
**Size:** M total (3 small-medium items combined into 1 session)
**Conflict Risk:** LOW — touches CI yml, admin panel, backend auth only
**Dependencies:** A7 first, then A6, then A2 (A7 affects admin auth used by A2)

---

## Pre-Implementation (MANDATORY — do not skip)

1. Read `CLAUDE.md` fully (codebase context, module map, tech stack, conventions)
2. Read ALL files in `docs/adr/` (ADR-001 through ADR-008) — especially:
   - ADR-008 (RS256 key distribution — relevant to A7 JWT migration)
   - ADR-007 (database-per-service — relevant to License service auth)
3. Read `docs/architecture/` (module dependency diagrams)
4. Read `todo/missing-features-implementation-plan.md` FULLY — especially:
   - Section A7 (Admin JWT Security Gap — your FIRST item)
   - Section A6 (Security Monitoring — your SECOND item)
   - Section A2 (Email Management System — your THIRD item)
   - IMPLEMENTATION COMPLIANCE RULES section
   - ERROR RECOVERY GUIDE section
   - ITEM DEPENDENCY GRAPH (A7 has no blockers; B2 depends on A7)
   - SESSION SCOPE GUIDANCE (A7=M, A6=S, A2=S)
5. Run `echo $PAT` to confirm GitHub token is available
6. Sync: `git fetch origin main && git merge origin/main --no-edit`

---

## Codebase Exploration (BEFORE writing any code)

```bash
# === A7: Admin JWT ===
# Read current admin auth (HS256)
find backend/ -name "AdminAuthService.kt" -exec cat {} \;

# Read POS auth (RS256 — the target pattern)
find backend/ -name "UserService.kt" -exec cat {} \;

# Read JWT configuration
grep -r "HS256\|RS256\|jwt\|JWT\|JwtConfig\|jwtSecret" backend/ --include="*.kt" -l

# Read License service admin validation
find backend/license/ -name "*.kt" | xargs grep -l "jwt\|JWT\|admin\|Admin\|verify\|token" 2>/dev/null

# Read ADR-008 for RS256 key distribution
cat docs/adr/ADR-008-*

# Read existing key management
grep -r "publicKey\|privateKey\|keyPair\|well-known" backend/ --include="*.kt" -l

# === A6: Security Monitoring ===
# Read current CI Gate workflow
cat .github/workflows/ci-gate.yml

# Check existing security scanning
grep -r "snyk\|owasp\|dependency-check\|falco" .github/ --include="*.yml" -l

# Check Slack webhook secret
grep -r "SLACK_WEBHOOK" .github/ --include="*.yml"

# === A2: Email System ===
# Read existing admin panel routes
ls admin-panel/src/routes/settings/ 2>/dev/null

# Read existing email hooks
grep -r "useEmail\|email" admin-panel/src/ --include="*.ts" --include="*.tsx" -l

# Read backend email routes
find backend/ -name "*Email*" -o -name "*email*" | grep -v node_modules | sort

# Read existing email service
find backend/ -name "EmailService.kt" -exec cat {} \;
find backend/ -name "AdminEmailRoutes.kt" -exec cat {} \;
```

---

## Item 1: A7 — Admin JWT Security Gap (do FIRST)

### Problem
Admin panel uses HS256 (symmetric shared secret) while POS uses RS256 (asymmetric).
HS256 means the secret must be shared with License service — security risk.

### Implementation Steps

1. **Read current admin token generation** in `AdminAuthService.kt`
   - Understand current HS256 flow: secret shared between API and License service
   - Note all JWT claims (userId, role, permissions, exp, iat)

2. **Modify `AdminAuthService.kt`** to use RS256:
   - Import existing RS256 private key (same key POS auth uses — check `JwtConfig`)
   - Change token signing from `Algorithm.HMAC256(secret)` to `Algorithm.RSA256(publicKey, privateKey)`
   - Keep all existing claims unchanged
   - Add `typ: "admin"` claim to distinguish from POS tokens (if not already present)

3. **Update License service admin JWT validation:**
   - Change verification from HS256 to RS256
   - Use public key only (no private key needed for verification)
   - Follow ADR-008: load key from `/.well-known/public-key` endpoint or bundled default

4. **Session rotation:**
   - Existing HS256 tokens will fail RS256 validation — this is expected
   - Add graceful handling: if RS256 validation fails, return 401 with "Session expired, please re-login"
   - Do NOT add backwards-compatible HS256 fallback (clean migration)

5. **Remove HS256 secret** from configuration where no longer needed

6. **Write tests:**
   - `AdminAuthServiceTest.kt` — token generation produces valid RS256 JWT
   - `AdminAuthServiceTest.kt` — RS256 token validates correctly with public key
   - `AdminAuthServiceTest.kt` — old HS256 token is rejected

### Commit after A7:
```bash
git fetch origin main && git merge origin/main --no-edit
git add backend/ todo/missing-features-implementation-plan.md
git commit -m "fix(security): migrate admin JWT from HS256 to RS256 [A7]

- AdminAuthService now signs admin tokens with RS256 (same keypair as POS)
- License service validates admin tokens with RS256 public key
- Removed shared HS256 secret dependency between services
- Existing admin sessions will require re-login (expected)

Plan file updated: A7 marked complete"
git push -u origin $(git branch --show-current)
# Monitor pipeline until green before proceeding to A6
```

---

## Item 2: A6 — Security Monitoring (after A7 pipeline is green)

### What's Missing (~85% complete)
- Snyk Monitor step in CI
- OWASP dependency check
- Falcosidekick → Slack wiring

### Implementation Steps

1. **Add OWASP dependency-check** to `build.gradle.kts`:
   - Add plugin: `org.owasp.dependencycheck` (use version catalog)
   - Configure: NVD API key from `NVD_API_KEY` secret
   - Add Gradle task `dependencyCheckAnalyze`

2. **Add Snyk Monitor step** to `.github/workflows/ci-gate.yml`:
   - After test step, before artifact upload
   - Use `snyk/actions/gradle@master` or CLI-based approach
   - Continue on error (don't block builds for advisory-level vulns)

3. **Wire Falcosidekick → Slack:**
   - Check if Falco is deployed on VPS (read `docker-compose.yml`)
   - If Falcosidekick config exists, add `SLACK_WEBHOOK_URL` output
   - If not deployed, create config placeholder in `docker-compose.yml`

4. **Add OWASP check to CI Gate workflow:**
   - New job or step in `ci-gate.yml`
   - Upload report as artifact

### Commit after A6:
```bash
git fetch origin main && git merge origin/main --no-edit
git add .github/ build.gradle.kts gradle/libs.versions.toml docker-compose.yml todo/missing-features-implementation-plan.md
git commit -m "feat(ci): add OWASP dependency check and Snyk security scanning [A6]

- OWASP dependencyCheckAnalyze Gradle plugin added
- Snyk monitor step in ci-gate.yml
- Falcosidekick Slack webhook configuration

Plan file updated: A6 status ~85% → ~95%"
git push -u origin $(git branch --show-current)
# Monitor pipeline until green before proceeding to A2
```

---

## Item 3: A2 — Email Management System (after A6 pipeline is green)

### What's Missing (~95% complete)
- Admin panel email delivery log UI page
- Bounce/complaint webhook handler

### Implementation Steps

1. **Search existing email components:**
   ```bash
   grep -r "useEmailLogs\|useEmailPreferences\|email" admin-panel/src/api/ --include="*.ts" -l
   grep -r "EmailLog\|DeliveryLog" admin-panel/src/ --include="*.ts" --include="*.tsx" -l
   ls admin-panel/src/routes/settings/
   ```

2. **Create `admin-panel/src/routes/settings/email.tsx`:**
   - Email delivery log table (date, recipient, subject, status, template)
   - Wire to existing `useEmailLogs()` hook
   - Filter by status (SENT, FAILED, BOUNCED, QUEUED)
   - Filter by date range
   - Follow existing admin panel page patterns (check other settings pages)

3. **Add Resend bounce/complaint webhook endpoint:**
   - Location: `backend/api/src/main/kotlin/.../routes/WebhookRoutes.kt` (or add to existing)
   - `POST /webhooks/resend` — receives bounce/complaint events
   - Update `email_delivery_log` status to BOUNCED or COMPLAINED
   - Validate Resend webhook signature (if available)

4. **Add email retry logic:**
   - For QUEUED → SENDING failures, add retry with exponential backoff
   - Max 3 retries, then mark as FAILED
   - Can be a simple coroutine-based retry in `EmailService.kt`

### Commit after A2:
```bash
git fetch origin main && git merge origin/main --no-edit
git add admin-panel/ backend/ todo/missing-features-implementation-plan.md
git commit -m "feat(email): add delivery log UI and bounce webhook handler [A2]

- Admin panel email delivery log page at /settings/email
- Resend bounce/complaint webhook endpoint
- Email retry logic for failed deliveries

Plan file updated: A2 status ~95% → ~100%"
git push -u origin $(git branch --show-current)
# Monitor pipeline until green
```

---

## Post-Implementation Updates (MANDATORY — per item, in SAME commit)

### Update `todo/missing-features-implementation-plan.md` after EACH item:

**A7:**
- Mark all A7 checkboxes `[x]`
- Update FEATURE COVERAGE MATRIX: remove A7 from "missing" or mark COMPLETE

**A6:**
- Mark completed A6 checkboxes `[x]`
- Update status: `~85% Complete` → `~95% Complete` (or 100% if all done)
- Note: CF Zero Trust + WAF rules are dashboard actions, not code — mark as out of scope

**A2:**
- Mark completed A2 checkboxes `[x]`
- Update status: `~95% Complete` → `~100% Complete` (or note remaining items)
- Update FEATURE COVERAGE MATRIX

### Update `CLAUDE.md` if needed:
- If new CI workflow steps added → update CI/CD section
- If new webhook endpoint added → note in Backend section

---

## Pipeline Monitoring (after EVERY push)

```bash
REPO="sendtodilanka/ZyntaPOS-KMM"
BRANCH=$(git branch --show-current)

# Step[1] — Watch Branch Validate
curl -s -H "Authorization: token $PAT" \
  "https://api.github.com/repos/$REPO/actions/runs?branch=$BRANCH&per_page=5" \
  | python3 -c "import sys,json; [print(f'[{r[\"status\"]:10}][{(r[\"conclusion\"] or \"pending\"):10}] {r[\"name\"]}') for r in json.load(sys.stdin).get('workflow_runs',[])]"

# Step[2] — Confirm PR auto-created
curl -s -H "Authorization: token $PAT" \
  "https://api.github.com/repos/$REPO/pulls?head=sendtodilanka:$BRANCH&state=open" \
  | python3 -c "import sys,json; prs=json.load(sys.stdin); print('PR #'+str(prs[0]['number']) if prs else 'No PR yet')"

# Step[3+4] — CI Gate
PR=<number>
SHA=$(curl -s -H "Authorization: token $PAT" \
  "https://api.github.com/repos/$REPO/pulls/$PR" | python3 -c "import sys,json; print(json.load(sys.stdin)['head']['sha'])")
curl -s -H "Authorization: token $PAT" \
  "https://api.github.com/repos/$REPO/commits/$SHA/check-runs" \
  | python3 -c "import sys,json; [print(f'[{r[\"status\"]:10}][{(r[\"conclusion\"] or \"pending\"):10}] {r[\"name\"]}') for r in json.load(sys.stdin).get('check_runs',[])]"
```

**CRITICAL:** Do NOT start next item until current item's pipeline is green.
For CI workflow changes (.yml files), be extra careful — syntax errors break ALL pipelines.

---

## Important Notes

- A7 MUST be completed before A2 (admin auth changes affect all admin endpoints)
- These items do NOT touch `:shared:domain`, `:shared:data`, or KMM feature modules
  (minimal conflict risk with Stream 1, 3, 4)
- For `.yml` workflow changes: validate YAML syntax before committing
- For admin panel changes: check `admin-panel/package.json` for build/lint commands
- If Resend webhook signature validation docs are needed, check https://resend.com/docs/webhooks
