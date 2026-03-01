# TODO-010: Security Monitoring & Automated Response Layer

**Status:** Pending
**Priority:** HIGH — Active detection for risks that hardening alone cannot prevent
**Phase:** Phase 2 (Growth)
**Depends on:**
- CF Zero Trust, Snyk Monitor, Canary Tokens: No blockers — start on Day 1 of Phase 2
- Falco + Falcosidekick: Blocked on TODO-007 Step 5 (VPS provisioned with Docker + ufw)
- Cloudflare Tunnel (panel): Blocked on TODO-007 Step 6 (Caddy running)

**Must complete before:** Backend goes live (run concurrently with TODO-007 Steps 5–10)
**Created:** 2026-03-01

---

## Overview

TODO-009 hardens the backend so attacks are harder to execute. This TODO adds the **detection and automated response layer** — so when an attack succeeds or is in progress, ZyntaPOS is alerted immediately and automated countermeasures fire within seconds, without requiring a technical person on call.

The 4 accepted risks documented in TODO-009 (JVM CVE velocity, bytecode reversibility, GC heap secret copies, class loading surface) cannot be fully closed by code hardening alone. The tools in this TODO actively detect exploitation of those risks and respond automatically.

**Principle:** TODO-009 makes attacks harder. TODO-010 detects them faster and responds automatically.

### 4-Layer Coverage Map

| Layer | Tool | What It Detects / Automates |
|-------|------|-----------------------------|
| Network / Access | **Cloudflare Zero Trust** | Unauthorised access to admin panel, bot attacks, DDoS, geo-anomalies |
| Runtime / JVM | **Falco + Falcosidekick** | Shell spawn from JVM, heap dump creation, ptrace attach, unexpected JVM outbound connections |
| Dependency CVEs | **Snyk Monitor** | New CVEs published between Dependabot weekly scans; alerts within hours |
| Credential Leak | **Canary Tokens** | Detects if `.jar` bytecode was decompiled and stolen fake credentials were used |

### Automation vs Human Decision

| Event | Auto-response | Human needed? |
|-------|--------------|---------------|
| Falco: container compromise (shell from JVM) | Container restart + attacker IP blocked via `ufw` | Only if false-positive investigation needed |
| Falco: heap dump created | Heap dump file deleted immediately | Investigate root cause |
| New CVE in dependency | Dependabot + Snyk both raise a PR | Review and merge PR |
| Canary token triggered | GitHub Actions rotates real credentials | Investigate scope of breach |
| CF: DDoS / bot traffic surge | CF blocks automatically at edge | Review CF dashboard |
| System fully taken offline | — | Yes — legal + business judgment |
| Customer data breach notification | — | Yes — legal obligation |

---

## Tool 1: Cloudflare Zero Trust

### What it covers

Network/access layer. Does not directly address JVM runtime risks, but:
- Locks `panel.zyntapos.com` behind identity-verified access — invisible to the public internet
- Adds WAF + bot protection to all ZyntaPOS subdomains at the CF edge (before traffic reaches VPS)
- Replaces open TCP port exposure for the admin panel with a Cloudflare Tunnel

### Setup Steps

**Step 1 — Enable Zero Trust (free tier)**

Cloudflare Dashboard → Zero Trust → Create organisation `zyntasolutions`.
Free tier covers up to 50 users — sufficient for Zynta Solutions team.

**Step 2 — Protect admin panel with CF Access**

Create a CF Access Application for `panel.zyntapos.com`:
- Auth method: One-time PIN to `@zyntasolutions.com` email (or Google Workspace SSO)
- Policy: Allow only `*@zyntasolutions.com` email domain
- Effect: `panel.zyntapos.com` returns 403 to any request without a valid CF Access JWT cookie

**Step 3 — Cloudflare Tunnel (replace open port with tunnel)**

On the VPS:
```bash
cloudflared tunnel create zyntapos-vps
cloudflared tunnel route dns zyntapos-vps panel.zyntapos.com
```

Add to `docker-compose.yml` (run after TODO-007 Step 6 — Caddy must be running):
```yaml
cloudflared:
  image: cloudflare/cloudflared:latest
  restart: unless-stopped
  command: tunnel --no-autoupdate run --token ${CLOUDFLARE_TUNNEL_TOKEN}
  environment:
    - CLOUDFLARE_TUNNEL_TOKEN=${CLOUDFLARE_TUNNEL_TOKEN}
  networks:
    - internal
```

Add `config/cloudflare/tunnel-config.yml`:
```yaml
tunnel: zyntapos-vps
credentials-file: /etc/cloudflared/credentials.json
ingress:
  - hostname: panel.zyntapos.com
    service: http://panel:8081
  - service: http_status:404
```

After tunnel is running, remove `panel` port from `ufw` allow rules — only CF can reach it.

**Step 4 — Bot Fight Mode**

CF Dashboard → Security → Bots → Enable **Super Bot Fight Mode**.
Applies to `api.zyntapos.com` and `license.zyntapos.com`. Automatically blocks known bots and scrapers.

**Step 5 — Rate Limiting rule (CF edge, before VPS)**

Complements TODO-007's Redis rate limiting — this fires at the CF edge, before traffic hits the VPS:
- Rule: ≥ 20 requests / 10 seconds to `/api/v1/auth/**` → block for 60 seconds
- Configure in CF Dashboard → Security → WAF → Rate Limiting Rules

**Step 6 — Security Event Alerts**

CF Dashboard → Notifications → Create alert policies:
- Alert on: DDoS attack detected
- Alert on: WAF rule triggered > 100× per hour
- Alert on: Bot score spike > 1,000 bots/hour

Delivery: email to `security@zyntasolutions.com` + Slack webhook.

### Files to Create or Modify

| File | Action |
|------|--------|
| `config/cloudflare/tunnel-config.yml` | CREATE — cloudflared tunnel ingress config |
| `docker-compose.yml` (VPS) | MODIFY — add `cloudflared` service |
| `local.properties.template` | MODIFY — add `CLOUDFLARE_TUNNEL_TOKEN` key |

---

## Tool 2: Falco + Falcosidekick

### What it covers

Runtime JVM anomaly detection using eBPF syscall monitoring on the Contabo VPS host. Detects:
- **Shell spawned by JVM process** → possible RCE exploit
- **Heap dump (`.hprof`) file created** → secrets may be written to disk
- **`ptrace` called on JVM process** → possible JVM attach exploit (belt-and-suspenders alongside `DisableAttachMechanism`)
- **JVM connecting to unexpected external IP** → possible C2 beaconing post-compromise

### Architecture

- **Falco** — runs as a `systemd` service directly on the Contabo VPS host (not inside Docker — requires host kernel/eBPF access)
- **Falcosidekick** — runs as a Docker container in `docker-compose.yml`; receives Falco alerts via HTTP and routes them to Slack + auto-response webhook

### Installation: Falco (on VPS host — run during TODO-007 Step 5)

```bash
curl -fsSL https://falco.org/repo/falcosecurity-packages.asc | \
  sudo gpg --dearmor -o /usr/share/keyrings/falco-archive-keyring.gpg

echo "deb [signed-by=/usr/share/keyrings/falco-archive-keyring.gpg] \
  https://download.falco.org/packages/deb stable main" | \
  sudo tee /etc/apt/sources.list.d/falcosecurity.list

sudo apt-get update && sudo apt-get install -y falco

# Copy custom rules (committed in this repo)
sudo cp config/falco/zyntapos_rules.yaml /etc/falco/rules.d/

# Enable and start
sudo systemctl enable falco
sudo systemctl start falco
```

### Custom Rules File: `config/falco/zyntapos_rules.yaml`

```yaml
# ZyntaPOS custom Falco rules — JVM-specific threat detection
# Install to /etc/falco/rules.d/ on the Contabo VPS

- rule: JVM spawns shell
  desc: >
    A java process spawned a shell interpreter — likely indicates Remote Code Execution.
    Normal JVM operation never spawns bash/sh/ash.
  condition: >
    spawned_process
    and proc.pname = "java"
    and proc.name in (shell_binaries)
  output: >
    CRITICAL JVM spawned shell
    (pid=%proc.pid cmd=%proc.cmdline parent=%proc.pname user=%user.name container=%container.name)
  priority: CRITICAL
  tags: [jvm, rce, zyntapos]

- rule: Heap dump file created
  desc: >
    A .hprof heap dump was created — JVM heap dumps contain plaintext copies of all
    in-memory strings including credentials and keys. -XX:-HeapDumpOnOutOfMemoryError
    prevents automatic dumps; this rule catches manual dump attempts.
  condition: >
    open_write
    and fd.name endswith ".hprof"
  output: >
    CRITICAL Heap dump file created
    (file=%fd.name pid=%proc.pid proc=%proc.name user=%user.name container=%container.name)
  priority: CRITICAL
  tags: [jvm, secret-exposure, zyntapos]

- rule: JVM ptrace attempt
  desc: >
    ptrace syscall called on a java process — could indicate a JVM attach exploit
    attempting to extract memory contents. -XX:+DisableAttachMechanism blocks the
    standard attach; this catches low-level ptrace attempts.
  condition: >
    ptrace
    and proc.name = "java"
  output: >
    CRITICAL ptrace on JVM process
    (pid=%proc.pid caller_pid=%proc.ppid caller=%proc.pname user=%user.name container=%container.name)
  priority: CRITICAL
  tags: [jvm, attach-exploit, zyntapos]

- rule: JVM unexpected outbound connection
  desc: >
    A java process is making an outbound TCP connection to an IP not in the trusted list.
    ZyntaPOS backend should only connect to PostgreSQL, Redis, and known external APIs.
    Unexpected outbound connections may indicate post-compromise C2 beaconing.
  condition: >
    outbound
    and proc.name = "java"
    and not fd.rip in (trusted_ips)
    and not fd.rport in (5432, 6379)
  output: >
    WARNING Unexpected JVM outbound connection
    (dest=%fd.rip:%fd.rport pid=%proc.pid proc=%proc.name container=%container.name)
  priority: WARNING
  tags: [jvm, network, zyntapos]

# Macro: trusted external IPs (update when new external services are added)
- macro: trusted_ips
  condition: >
    fd.rip in ("1.1.1.1", "1.0.0.1", "8.8.8.8")
    or fd.rip startswith "10."
    or fd.rip startswith "172.16."
    or fd.rip startswith "192.168."
```

### Falcosidekick Config: `config/falco/falcosidekick.yaml`

```yaml
listenport: 2801
debug: false

slack:
  webhookurl: "${SLACK_WEBHOOK_URL}"
  channel: "#security-alerts"
  username: "Falco — ZyntaPOS VPS"
  icon: ":warning:"
  minimumpriority: "warning"
  messageformat: >
    *[{priority}]* {rule}
    > {output}

webhook:
  address: "http://localhost:9000/hooks/falco-response"
  minimumpriority: "critical"
  customHeaders:
    Authorization: "Bearer ${FALCO_WEBHOOK_SECRET}"
```

### Auto-Response Script: `config/falco/response-handler.sh`

This script is called by Falcosidekick's webhook output on CRITICAL alerts. Install as a
`webhook` listener on the VPS (e.g., using the `webhook` Go tool: `apt install webhook`).

```bash
#!/bin/bash
# ZyntaPOS Falco auto-response handler
# Called by Falcosidekick when a CRITICAL rule fires.
# Environment variables: FALCO_RULE, ATTACKER_IP (extracted by Falcosidekick)
set -euo pipefail

RULE="${FALCO_RULE:-unknown}"
LOG_FILE="/var/log/zyntapos-falco-response.log"

log() { echo "[$(date -u +%FT%TZ)] $*" | tee -a "$LOG_FILE"; }

case "$RULE" in
  "JVM spawns shell")
    log "RCE detected — restarting zyntapos-api container"
    docker restart zyntapos-api
    ;;
  "Heap dump file created")
    log "Heap dump detected — deleting .hprof files"
    find /var/lib/docker -name "*.hprof" -delete 2>/dev/null || true
    find /tmp -name "*.hprof" -delete 2>/dev/null || true
    ;;
  "JVM ptrace attempt")
    if [[ -n "${ATTACKER_IP:-}" ]]; then
      log "ptrace exploit attempt from $ATTACKER_IP — blocking via ufw"
      ufw deny from "$ATTACKER_IP" to any
    fi
    ;;
  *)
    log "Unhandled CRITICAL rule: $RULE — manual investigation required"
    ;;
esac

log "Response complete for rule: $RULE"
```

### docker-compose.yml Addition (Falcosidekick)

```yaml
falcosidekick:
  image: falcosecurity/falcosidekick:latest
  restart: unless-stopped
  ports:
    - "127.0.0.1:2801:2801"   # Bind to localhost only — Falco connects internally
  environment:
    - SLACK_WEBHOOKURL=${SLACK_WEBHOOK_URL}
    - FALCO_WEBHOOK_SECRET=${FALCO_WEBHOOK_SECRET}
  volumes:
    - ./config/falco/falcosidekick.yaml:/etc/falcosidekick/config.yaml:ro
  networks:
    - internal
```

Configure Falco to send alerts to Falcosidekick by adding to `/etc/falco/falco.yaml`:
```yaml
json_output: true
http_output:
  enabled: true
  url: "http://localhost:2801/"
```

### Files to Create or Modify

| File | Action |
|------|--------|
| `config/falco/zyntapos_rules.yaml` | CREATE — 4 custom detection rules |
| `config/falco/falcosidekick.yaml` | CREATE — alert routing config |
| `config/falco/response-handler.sh` | CREATE — auto-response script |
| `docker-compose.yml` (VPS) | MODIFY — add `falcosidekick` service |
| `local.properties.template` | MODIFY — add `SLACK_WEBHOOK_URL`, `FALCO_WEBHOOK_SECRET` |

---

## Tool 3: Snyk Monitor

### What it covers

Continuous CVE scanning between Dependabot's weekly scans. Snyk monitors the Gradle dependency
tree against its own advisory database (separate from GitHub's) and alerts within hours of a
new CVE being published — often before Dependabot's next scheduled check.

**Snyk vs Dependabot — complementary, not redundant:**

| | Snyk Monitor | Dependabot |
|-|-------------|------------|
| Alert speed | Hours after CVE publication | Up to 7 days (weekly schedule) |
| Advisory database | Snyk DB (independent) | GitHub Advisory DB |
| CVEs with no fix yet | ✅ Reports and monitors | ❌ Only reports when fix is available |
| Auto PR for fix | ✅ Optional "Fix PR" | ✅ Default behaviour |
| Licence compliance | ✅ | ❌ |
| Cost | Free tier (unlimited OSS scans) | Free (built into GitHub) |

Keep both — they catch different CVEs at different speeds.

### Setup (no repo files required — SaaS configuration only)

1. Sign up at `snyk.io` → free tier → connect GitHub organisation `zyntasolutions`
2. Import `ZyntaPOS-KMM` repository → Snyk auto-detects all Gradle build files
3. Set monitoring frequency: **daily** (free tier supports this)
4. Configure notifications:
   - Email: `security@zyntasolutions.com` for NEW CVEs only (severity: HIGH + CRITICAL)
   - Slack: `#security-alerts` channel via webhook (same webhook as Falcosidekick)
5. Enable "Fix PRs" — Snyk raises a PR with the patched version when a fix is available

### No repo files to create

Snyk is configured entirely in the Snyk dashboard. The only repo artefact is the implicit
Gradle build file structure Snyk reads.

---

## Tool 4: Canary Tokens

### What it covers

Detects if the Ktor service `.jar` bytecode was decompiled and fake credentials found inside
were used. This closes the accepted risk of "JVM bytecode reversibility" documented in TODO-009
— not by preventing decompilation, but by detecting it within seconds of the stolen credential
being used.

**Attack scenario this defends against:**
1. Attacker obtains the `.jar` (e.g., via a misconfigured Docker registry or insider threat)
2. Attacker decompiles with IntelliJ or `jadx` → finds what looks like an API key or URL
3. Attacker uses the "credential" → canary fires immediately → real credentials are rotated
   before any real damage is done

### Setup

**Step 1 — Create two Canary tokens at canarytokens.org**

Token A — "Custom URL / Web token":
- Alert email: `security@zyntasolutions.com`
- Alert Slack: same `#security-alerts` webhook
- Note: "Possible .jar decompile — token A found in zyntapos-api bytecode"

Token B — "AWS API key":
- Same alert destinations
- Note: "Possible .jar decompile — fake AWS key found in zyntapos-api config"

**Step 2 — Embed Token A in Application.kt source (as a comment)**

In `zyntapos-api/src/main/kotlin/Application.kt`:
```kotlin
// Internal: ZyntaPOS License Verification endpoint
// Support API reference: https://[CANARY_TOKEN_A_URL]
// For integration support contact: api-support@zyntapos.com
```

The URL looks like a plausible internal documentation or support endpoint to someone reading
decompiled bytecode. Store the real token URL in `local.properties` (gitignored) and substitute
it in the source file before each build — OR embed it directly in the comment (the token URL
itself is not a secret; only the email/Slack alert destinations are sensitive).

**Step 3 — Embed Token B as a fake AWS config comment**

In `zyntapos-sync/src/main/kotlin/config/SyncConfig.kt` (or similar config file):
```kotlin
// Backup sync storage (disabled — reserved for future use)
// AWS_ACCESS_KEY_ID = AKIA[CANARY_TOKEN_B_KEY]
// AWS_SECRET_ACCESS_KEY = [CANARY_TOKEN_B_SECRET]
```

**Step 4 — GitHub Actions auto-response workflow**

Create `.github/workflows/canary-response.yml`:
```yaml
name: Canary Token Response

on:
  repository_dispatch:
    types: [canary-triggered]

jobs:
  rotate-credentials:
    runs-on: ubuntu-latest
    steps:
      - name: Notify security channel
        run: |
          curl -X POST "${{ secrets.SLACK_WEBHOOK_URL }}" \
            -H "Content-Type: application/json" \
            -d '{"text": ":rotating_light: *CANARY TOKEN FIRED* — possible .jar decompile. Rotating API credentials now."}'

      - name: Rotate ZYNTA_API_CLIENT_SECRET
        uses: actions/github-script@v7
        with:
          github-token: ${{ secrets.ADMIN_TOKEN }}
          script: |
            // Trigger credential rotation workflow
            await github.rest.actions.createWorkflowDispatch({
              owner: context.repo.owner,
              repo: context.repo.repo,
              workflow_id: 'rotate-secrets.yml',
              ref: 'main'
            });
```

**Step 5 — Document canary token renewal**

Add to `local.properties.template`:
```properties
# Canary tokens (renew annually or after any suspected exposure)
# Create new tokens at canarytokens.org — update embedded URLs in Application.kt + SyncConfig.kt
# Token A URL: https://canarytokens.org/...  (set this to the token URL, not shown here)
# Token B key: AKIA...  (the fake AWS key value from canarytokens.org)
CANARY_TOKEN_A_URL=
CANARY_TOKEN_B_KEY=
```

### Files to Create or Modify

| File | Action |
|------|--------|
| `.github/workflows/canary-response.yml` | CREATE — auto-rotate credentials on canary trigger |
| `local.properties.template` | MODIFY — add canary token documentation and placeholder keys |

---

## Implementation Order

| Priority | Action | Effort | Blocker |
|----------|--------|--------|---------|
| **1** | Create Canary tokens at canarytokens.org + embed in source | 5 min | None — do this on Day 1 |
| **2** | Enable Snyk Monitor (connect GitHub org) | 15 min | None |
| **3** | Set up CF Zero Trust + protect `panel.zyntapos.com` | 1 hr | None (CF account exists) |
| **4** | Add Cloudflare Tunnel for panel subdomain | 1 hr | TODO-007 Step 6 (Caddy running) |
| **5** | Install Falco on VPS as systemd service | 1 hr | TODO-007 Step 5 (VPS provisioned) |
| **6** | Deploy custom Falco rules (`zyntapos_rules.yaml`) | 1 hr | Step 5 complete |
| **7** | Set up Falcosidekick + `response-handler.sh` webhook | 1 hr | Step 5 complete |
| **8** | Test all 4 Falco rules fire correctly (simulate each scenario) | 1 hr | Steps 5–7 complete |

**Items 1–3** can be done on Day 1 of Phase 2 with zero infrastructure dependencies. Total effort for items 1–3: under 2 hours.

---

## Files to Create or Modify (Summary)

### New Files

| File | Purpose |
|------|---------|
| `config/falco/zyntapos_rules.yaml` | 4 custom Falco detection rules (JVM-specific) |
| `config/falco/falcosidekick.yaml` | Falcosidekick alert routing (Slack + auto-response webhook) |
| `config/falco/response-handler.sh` | Auto-response: container restart, heap dump deletion, IP block |
| `config/cloudflare/tunnel-config.yml` | Cloudflare Tunnel ingress config for panel subdomain |
| `.github/workflows/canary-response.yml` | Auto-rotate credentials when canary token fires |

### Files to Modify

| File | Change |
|------|--------|
| `docker-compose.yml` (VPS) | Add `falcosidekick` service + `cloudflared` tunnel service |
| `local.properties.template` | Add `CLOUDFLARE_TUNNEL_TOKEN`, `SLACK_WEBHOOK_URL`, `FALCO_WEBHOOK_SECRET`, `CANARY_TOKEN_A_URL`, `CANARY_TOKEN_B_KEY` |

### No Repo Changes Needed (SaaS configuration only)

- Snyk Monitor — configured in Snyk dashboard
- CF Zero Trust rules, Bot Fight Mode, Rate Limiting — configured in CF dashboard
- Canary token creation — done at canarytokens.org

---

## Validation Checklist (14 items)

### Cloudflare Zero Trust (4 items)

- [ ] `panel.zyntapos.com` returns HTTP 403 to `curl -I https://panel.zyntapos.com` without a CF Access cookie
- [ ] CF Access email OTP login works for `@zyntasolutions.com` addresses (test by logging in)
- [ ] Cloudflare Tunnel active: `cloudflared tunnel list` shows `zyntapos-vps` with status `healthy`
- [ ] Bot Fight Mode enabled: CF Dashboard → Security → Bots shows "Super Bot Fight Mode: ON"

### Falco + Falcosidekick (5 items)

- [ ] `sudo falco --version` returns a version string on the Contabo VPS
- [ ] `systemctl status falco` shows `active (running)`
- [ ] Custom rules loaded: `sudo falco -L 2>&1 | grep "JVM spawns shell"` returns the rule name
- [ ] Falcosidekick container running: `docker ps --filter name=falcosidekick --format "{{.Status}}"` shows `Up`
- [ ] Slack alert received in `#security-alerts` when test rule fires: run `sh -c "exec bash"` from inside a test Java container and verify Slack message arrives within 30 seconds

### Snyk Monitor (2 items)

- [ ] `ZyntaPOS-KMM` repo visible in Snyk Projects dashboard with Gradle dependency count shown
- [ ] Alert fires for known-vulnerable dep: temporarily add `compile("log4j:log4j:1.2.17")` to any `build.gradle.kts`, run `snyk test` locally, verify CRITICAL severity reported

### Canary Tokens (3 items)

- [ ] Token A fires: visit the embedded URL in a browser → verify alert email + Slack arrive within 60 seconds
- [ ] Token B fires: run `aws sts get-caller-identity --access-key [TOKEN_B_KEY]` → verify alert arrives within 60 seconds
- [ ] `.github/workflows/canary-response.yml` is listed in GitHub → Actions tab and can be triggered manually
