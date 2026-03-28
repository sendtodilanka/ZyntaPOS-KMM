# ADR-011: TLS Certificate Pinning Strategy

**Date:** 2026-03-28
**Status:** ACCEPTED
**Author:** Security Audit (AI-assisted)

---

## Context

ZyntaPOS is a financial Point of Sale system that transmits payment data, inventory records,
and customer information between the KMM app (Android tablet + Desktop JVM) and the backend
API at `api.zyntapos.com`. The app is deployed on managed enterprise devices where an IT
administrator controls update rollouts.

Standard TLS validation alone relies on a global pool of 100+ trusted Certificate Authorities
(CAs). Any compromised or rogue CA in that pool can issue a fraudulent certificate for
`api.zyntapos.com`, enabling a Man-in-the-Middle (MITM) attack that passes standard CA
chain validation. This is a known, documented attack class — particularly relevant on
corporate networks, hotel WiFi, and other environments where traffic may be intercepted.

The question was: **what additional transport-layer protection should be applied beyond
standard CA validation?**

---

## Alternatives Considered

### Option A — Certificate Transparency (CT) + CAA DNS

Certificate Transparency publishes all issued certificates to public, auditable logs.
CAA DNS records restrict which CAs may issue certificates for a domain.

**Rejected because:**
- CT enforcement is performed by **browsers and operating systems only**. Ktor's HTTP
  client (used on both Android via OkHttp and Desktop via CIO) has no built-in CT
  checking mechanism. Neither does the Java `SSLSocket` stack.
- CAA DNS records are meaningful only if the CA honours them. An attacker who has
  already compromised DNS (the scenario we are protecting against) can suppress or
  alter CAA records before the CA checks them.
- Neither mechanism provides **app-layer enforcement** — they rely entirely on
  third-party infrastructure that the app cannot verify at runtime.

### Option B — HSTS Preloading

HTTP Strict Transport Security preloading forces HTTPS at the browser/OS level.

**Rejected because:**
- HSTS preloading is enforced by browsers only; it has no effect on native KMM apps
  communicating via Ktor/OkHttp.

### Option C — Root CA Pinning (ISRG Root X1)

Pin the ISRG Root X1 root CA, which signs Let's Encrypt intermediates.

**Rejected because:**
- Pinning a root CA is too broad. Every domain that uses a Let's Encrypt certificate
  shares the same root. An attacker with any LE certificate could perform an MITM
  attack against a client that pins only the root.

### Option D — No Pinning

Rely solely on standard CA chain validation.

**Rejected because:**
- ZyntaPOS handles financial transactions on enterprise networks where MITM risks are
  elevated. Standard CA validation does not protect against compromised CAs or
  network-level interception.
- Acceptable for consumer apps; insufficient for a financial POS system.

### Option E — SPKI SHA-256 Certificate Pinning ✅ Selected

Pin the SHA-256 hash of the certificate's Subject Public Key Info (SPKI) — the public
key itself, independent of the certificate wrapper. A TLS handshake is rejected at the
app layer if no certificate in the server's chain matches a known pin.

**Selected because:**
- Enforcement happens **inside the app** — no dependency on browser, OS, or third-party
  infrastructure.
- SPKI pinning survives certificate renewal (same key pair → same pin) and is
  independent of CA.
- Supported natively by OkHttp (`CertificatePinner`) on Android and implementable via
  a custom `X509TrustManager` on JVM/Desktop (Ktor CIO engine).
- Industry-standard approach for high-security native applications (used by banking
  apps, enterprise MDM clients, etc.).

---

## Decision

> **ZyntaPOS uses SPKI SHA-256 certificate pinning enforced at the app layer.**
>
> Pinning is active in **production builds only** (`AppConfig.IS_DEBUG == false`).
> Debug builds skip pinning to allow HTTP inspection proxies and local servers.

### Phase 1 (Interim — Dual-Pin): REPLACED

The original dual-pin approach hardcoded both a leaf pin (`API_SPKI_PIN_PRIMARY`) and
an intermediate CA backup pin (`API_SPKI_PIN_BACKUP`) in the binary. This required an
app update every ~60 days when Caddy renewed the Let's Encrypt leaf certificate.

**Status: Superseded by Phase 2 (Signed Pin List).**

### Phase 2 (Current — Signed Pin List): ✅ IMPLEMENTED (2026-03-28)

Routine TLS leaf certificate renewals no longer require an app update. The only binary
constant that changes is the emergency `API_SPKI_PIN_BACKUP` — and only if Let's Encrypt
retires the E7 intermediate CA (a multi-year event).

---

## Current Implementation — Signed Pin List

### Architecture

```
Server side (Caddy renewal hook):
  1. Extract new leaf SPKI SHA-256 pin
  2. Sign pin list with Ed25519 private key (offline — not in Docker env)
  3. Publish pre-signed JSON to GET /.well-known/tls-pins.json
     (set via TLS_PINS_JSON env var or TLS_PINS_JSON_PATH file)

App side (startup flow):
  1. PinListFetcher.resolveActivePins(prefs) — fetch /.well-known/tls-pins.json
     using CA validation ONLY (no pinning on this request)
  2. Verify Ed25519 signature using API_PIN_SIGNING_PUBLIC_KEY (hardcoded)
  3. Check expires_at is in the future
  4. Store verified pins in SecurePreferences (KEY_TLS_PINS)
  5. buildApiClient(prefs) reads stored pins; falls back to API_SPKI_PIN_BACKUP
```

### JSON Response Format (`/.well-known/tls-pins.json`)

```json
{
  "pins": ["sha256/LEAF_PIN=", "sha256/BACKUP_PIN="],
  "expires_at": "2026-09-01T00:00:00Z",
  "signature": "<Base64 Ed25519 signature — 64 bytes / 88 Base64 chars>"
}
```

### Canonical Signed Message

The Ed25519 signature covers the UTF-8 encoding of:
```
<pin[0]>\n<pin[1]>\n...\n<expires_at>
```
where pins are sorted lexicographically before joining. This deterministic format
ensures server and client construct identical byte sequences.

### Fallback Chain

```
resolveActivePins() result is non-null?  → use fetched pins (Ed25519 verified)
                          ↓ null
loadStored(prefs) is non-null?           → use stored pins (from last successful fetch)
                          ↓ null
                          → use API_SPKI_PIN_BACKUP (emergency hardcoded fallback)
```

### Why the Pin Fetch Is Secure Without Pinning

The pin list fetch uses CA-only TLS validation (no certificate pinning). This is
intentional — we cannot pin before we have fresh pins. Security is provided by the
Ed25519 signature: a MITM can intercept the HTTP response body, but cannot forge a
valid Ed25519 signature without the offline signing key. The app rejects any response
that fails signature verification.

### Key Files

| File | Role |
|---|---|
| `shared/data/src/commonMain/…/data/remote/api/CertificatePinConstants.kt` | `API_PIN_SIGNING_PUBLIC_KEY` + `API_SPKI_PIN_BACKUP` |
| `shared/data/src/commonMain/…/data/remote/api/Ed25519Verifier.kt` | `expect` declaration |
| `shared/data/src/androidMain/…/data/remote/api/Ed25519Verifier.kt` | Android actual (API 28+ EdDSA) |
| `shared/data/src/jvmMain/…/data/remote/api/Ed25519Verifier.kt` | JVM actual (Java 15+ native) |
| `shared/data/src/commonMain/…/data/remote/dto/TlsPinsDto.kt` | JSON DTO for pin list response |
| `shared/data/src/commonMain/…/data/remote/api/PinListFetcher.kt` | Fetch + verify + store |
| `shared/data/src/commonMain/…/data/remote/api/ApiClient.kt` | Reads stored pins from prefs |
| `shared/data/src/commonMain/…/data/remote/api/CertificatePinning.kt` | `expect` declaration |
| `shared/data/src/androidMain/…/data/remote/api/CertificatePinning.kt` | OkHttp `CertificatePinner` actual |
| `shared/data/src/jvmMain/…/data/remote/api/CertificatePinning.kt` | Custom `SpkiPinnedTrustManager` actual |
| `backend/api/src/main/kotlin/…/api/routes/WellKnownRoutes.kt` | `GET /.well-known/tls-pins.json` |
| `backend/api/src/main/kotlin/…/api/config/AppConfig.kt` | `tlsPinsJson` field |
| `scripts/generate-tls-signing-key.sh` | Keypair generation + pin list signing |
| `shared/domain/src/commonMain/…/domain/port/SecureStorageKeys.kt` | `KEY_TLS_PINS`, `KEY_TLS_PINS_EXPIRES_AT` |

---

## Platform Notes

### Android API Coverage

| API level | Ed25519 support | Behaviour |
|---|---|---|
| API 31+ | `"Ed25519"` and `"EdDSA"` (standard) | ✅ Full support |
| API 28–30 | `"EdDSA"` via Conscrypt | ✅ Falls back to EdDSA |
| API 24–27 | Neither algorithm in standard providers | ⚠️ Returns `false` → fallback chain |

On API 24–27, signature verification returns `false`, so `PinListFetcher.refresh()`
returns `null`. The fallback chain (stored pins → `API_SPKI_PIN_BACKUP`) ensures
connectivity. Since ZyntaPOS targets enterprise POS tablets, API 28+ is the realistic
minimum in production deployments.

### JVM/Desktop

Java 15+ (used in the `:shared:data` JVM target of JVM 17) provides native Ed25519
support via `Signature.getInstance("Ed25519")`.

---

## Rotation Policy

| Event | Impact | Action Required |
|---|---|---|
| Caddy leaf cert renewal (~every 60 days) | Leaf SPKI changes | Run `generate-tls-signing-key.sh --sign` with new leaf pin; update `TLS_PINS_JSON` in `.env`; redeploy. **No app update needed.** |
| Let's Encrypt retires E7 intermediate | `API_SPKI_PIN_BACKUP` changes | Update `API_SPKI_PIN_BACKUP` in `CertificatePinConstants.kt`, ship app update |
| Ed25519 signing key compromise | `API_PIN_SIGNING_PUBLIC_KEY` changes | Generate new keypair via `--keygen`; update `CertificatePinConstants.kt`, ship app update |

The Ed25519 signing key is measured in years, not months — the rotation burden is
dramatically lower than the original dual-pin approach.

---

## Consequences

- Routine TLS certificate renewals no longer require app updates.
- App updates are only needed for two rare events: intermediate CA rotation (years)
  and Ed25519 signing key rotation (years).
- Debug builds are unaffected — local servers and HTTP proxies work normally.
- Android API 24–27 devices degrade gracefully to `API_SPKI_PIN_BACKUP` (still secure
  against MITM attacks as long as the E7 intermediate is trusted).

---

## References

- `shared/data/src/commonMain/…/data/remote/api/CertificatePinConstants.kt`
- `shared/data/src/commonMain/…/data/remote/api/PinListFetcher.kt`
- `scripts/generate-tls-signing-key.sh`
- [OkHttp CertificatePinner documentation](https://square.github.io/okhttp/4.x/okhttp/okhttp3/-certificate-pinner/)
- [OWASP Mobile Security — Certificate Pinning](https://owasp.org/www-community/controls/Certificate_and_Public_Key_Pinning)
- ADR-008 — RS256 Key Distribution (TOFU pattern, same concept applied to JWT key)

---

*Phase 1 authored during the 2026-03-28 configuration and security audit session.*
*Phase 2 (Signed Pin List) implemented 2026-03-28.*
*Approved by: Dilanka (Tech Lead)*
