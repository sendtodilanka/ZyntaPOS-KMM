# Roundcube Webmail Integration

**Branch:** `claude/check-stalwart-version-4PtvA`
**Status:** Implementation complete — pending DNS record + GitHub Secret

Staff currently access `@zyntapos.com` mailboxes via desktop/mobile IMAP clients only.
Roundcube adds a browser-based webmail interface at `https://webmail.zyntapos.com`
with zero new backend code. It reuses Stalwart (IMAP/SMTP), PostgreSQL, and Caddy TLS
that are already running in the stack.

---

## 1. Architecture Overview

```
Browser → Cloudflare (orange-cloud proxy)
        → Caddy TLS (cf_tls: wildcard *.zyntapos.com Origin Cert)
        → roundcube:80 (PHP-Apache)
                │
                ├── IMAP → stalwart:143 (STARTTLS, container-to-container)
                ├── SMTP → stalwart:587 (STARTTLS, authenticated submission)
                └── DB   → postgres:5432/zyntapos_webmail
```

**Key design decisions:**
- Reuses `ZYNTAPOS_DB_PASSWORD` — no new DB secret needed
- Reuses `(cf_tls)` Caddy snippet — no new certificate workflow
- Container-to-container IMAP/SMTP on `zyntapos_net` — internal, no extra port exposure
- `ROUNDCUBE_DES_KEY` is the only new secret required (24-char session encryption key)
- Roundcube auto-migrates its own schema on first boot (no Flyway, no init SQL)

---

## 2. Infrastructure: docker-compose.yml

**Added:** `roundcube` service after the `stalwart` block.

```yaml
roundcube:
  image: roundcube/roundcubemail:1.6-apache
  restart: unless-stopped
  depends_on:
    postgres:
      condition: service_healthy
    stalwart:
      condition: service_healthy
  environment:
    ROUNDCUBEMAIL_DB_TYPE: pgsql
    ROUNDCUBEMAIL_DB_HOST: postgres
    ROUNDCUBEMAIL_DB_PORT: "5432"
    ROUNDCUBEMAIL_DB_NAME: zyntapos_webmail
    ROUNDCUBEMAIL_DB_USER: zyntapos
    ROUNDCUBEMAIL_DB_PASSWORD: "${ZYNTAPOS_DB_PASSWORD}"
    ROUNDCUBEMAIL_DEFAULT_HOST: stalwart
    ROUNDCUBEMAIL_DEFAULT_PORT: "143"
    ROUNDCUBEMAIL_SMTP_SERVER: stalwart
    ROUNDCUBEMAIL_SMTP_PORT: "587"
    ROUNDCUBEMAIL_SMTP_USER: "%u"
    ROUNDCUBEMAIL_SMTP_PASS: "%p"
    ROUNDCUBEMAIL_DES_KEY: "${ROUNDCUBE_DES_KEY}"
    ROUNDCUBEMAIL_SKIN: elastic
    ROUNDCUBEMAIL_PLUGINS: archive,zipdownload
    ROUNDCUBEMAIL_UPLOAD_MAX_FILESIZE: 25M
    ROUNDCUBEMAIL_ASPELL_DICTS: en
  volumes:
    - roundcube_temp:/tmp/roundcube-temp
  networks:
    - zyntapos_net
  mem_limit: 256m
  memswap_limit: 256m
  security_opt:
    - no-new-privileges:true
  cap_drop:
    - ALL
  cap_add:
    - SETUID
    - SETGID
    - DAC_OVERRIDE
  healthcheck:
    test: ["CMD", "wget", "-qO", "/dev/null", "http://localhost/"]
    interval: 30s
    timeout: 10s
    retries: 3
    start_period: 45s
```

**Added volume** (under `volumes:`):
```yaml
  roundcube_temp:    # PHP session temp files
```

**Why `ROUNDCUBEMAIL_SMTP_USER: "%u"` and `ROUNDCUBEMAIL_SMTP_PASS: "%p"`:**
These tokens tell Roundcube to use the currently logged-in IMAP user's credentials for
SMTP submission — correct for Stalwart where the same account handles both.

**Why `cap_add: [SETUID, SETGID, DAC_OVERRIDE]`:**
Apache master process starts as root and drops privileges to `www-data` via setuid/setgid.
`DAC_OVERRIDE` is required for PHP's session file writes in restricted temp dirs.

---

## 3. Reverse Proxy: Caddyfile

**Added** `webmail.zyntapos.com` block after the `mail.zyntapos.com` block:

```caddyfile
# ── webmail.zyntapos.com — Roundcube webmail (staff browser-based email) ─────
# Provides browser-based IMAP/SMTP access to @zyntapos.com mailboxes via Stalwart.
# Uses the wildcard Cloudflare Origin Certificate (cf_tls) — no separate cert needed.
# DNS: webmail.zyntapos.com → VPS IP, orange-cloud (Cloudflare proxied).
webmail.zyntapos.com {
    import cf_tls

    # Compress assets — Roundcube's elastic skin ships ~2MB of CSS/JS
    encode zstd gzip

    reverse_proxy roundcube:80 {
        header_up X-Real-IP {remote_host}
        header_up X-Forwarded-For {remote_host}
        health_uri /
        health_interval 30s
        health_timeout 10s
        flush_interval -1
    }
}
```

**Why `flush_interval -1`:** Roundcube streams JavaScript assets; disabling buffering
prevents HTTP/2 stream resets on slower connections (same pattern as `mail.zyntapos.com`).

**Why `(cf_tls)` not a separate LE cert:**
The Cloudflare Origin wildcard cert already covers `webmail.zyntapos.com`. Unlike
`mail.zyntapos.com` (which must be grey-cloud for direct IMAP/SMTP port access),
webmail is HTTPS-only through Caddy — orange-cloud proxy is correct and the Origin
cert is sufficient.

---

## 4. Database: init-databases.sh

**Added** `zyntapos_webmail` to the single `CREATE DATABASE` batch in
`backend/postgres/init-databases.sh`:

```sql
CREATE DATABASE zyntapos_webmail OWNER $POSTGRES_USER;
```

This runs once on first `pgdata` volume initialization. On existing deployments
(pgdata already exists), create manually:

```bash
docker compose exec postgres psql -U zyntapos -d zyntapos \
  -c "CREATE DATABASE zyntapos_webmail OWNER zyntapos;"
```

Roundcube auto-creates its own schema tables on first boot — no Flyway migration needed.

---

## 5. Secrets & Environment

### New secret required

| Variable | Purpose | Where to set |
|----------|---------|--------------|
| `ROUNDCUBE_DES_KEY` | 24-char AES session key for Roundcube cookie encryption | GitHub Secret + VPS `.env` |

**Generate:**
```bash
openssl rand -base64 18 | head -c 24
```

**Add to GitHub Secrets** (repo → Settings → Secrets → Actions):
- Name: `ROUNDCUBE_DES_KEY`
- Value: `<generated-value>`

**Add to VPS `.env`** (appended by `cd-deploy.yml`):
```
ROUNDCUBE_DES_KEY=<generated-value>
```

### Reused secrets (no changes needed)

| Variable | Source |
|----------|--------|
| `ZYNTAPOS_DB_PASSWORD` | Already in VPS `.env` and GitHub Secrets |

---

## 6. Canary & Healthcheck

**Added:** `healthcheck/webmail.zyntapos.com.html` — branded canary page.

`nginx.conf` uses `try_files /$http_host.html /index.html =404;` to route by Host header
dynamically — no changes to `nginx.conf` needed. The new HTML file is auto-served when
Host is `webmail.zyntapos.com`.

In practice, Caddy proxies `webmail.zyntapos.com` to `roundcube:80` directly, so the
canary container is never reached for this domain. The file provides a consistent
fallback if Roundcube is down and Caddy falls through to canary.

---

## 7. DNS Configuration (Cloudflare)

**Manual step — one-time, done in Cloudflare dashboard:**

| Type | Name | Content | Proxy status |
|------|------|---------|-------------|
| `A` | `webmail` | `<VPS IPv4>` | **Proxied (orange cloud) ✅** |

**Why orange-cloud (proxied):**
- Webmail is HTTPS-only — traffic flows: Browser → CF edge → VPS:443 (Caddy)
- The Cloudflare Origin cert (`cf_tls`) handles the origin leg
- Contrast with `mail.zyntapos.com` which is grey-cloud because IMAP/SMTP need direct TCP

After adding the DNS record, `webmail.zyntapos.com` typically resolves within 5 minutes.

---

## 8. Post-Deployment Verification

Run after `docker compose up -d roundcube` on VPS:

- [ ] `docker compose ps roundcube` — status `healthy` (wait up to 45s start_period)
- [ ] `docker compose logs roundcube` — no DB connection errors, Apache started
- [ ] `https://webmail.zyntapos.com` — Roundcube login page loads with `elastic` skin
- [ ] Login with `<user>@zyntapos.com` + password — inbox visible, messages load
- [ ] Send test email from Roundcube to external address — delivered via Stalwart SMTP:587
- [ ] Receive test email from external → appears in Roundcube inbox via Stalwart IMAP:143
- [ ] Uptime Kuma (`https://status.zyntapos.com`) — add `webmail.zyntapos.com` monitor
- [ ] Run `cd-smoke-rollback.yml` — verify smoke test passes end-to-end

**Existing deployment (pgdata already initialized):**
Create the database manually before `docker compose up -d roundcube`:
```bash
docker compose exec postgres psql -U zyntapos -d zyntapos \
  -c "CREATE DATABASE zyntapos_webmail OWNER zyntapos;"
```
