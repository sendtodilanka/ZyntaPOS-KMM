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

A **dual-pin set** is applied to `api.zyntapos.com`:

| Constant | Role | Certificate |
|---|---|---|
| `API_SPKI_PIN_PRIMARY` | Leaf certificate | `CN=zyntapos.com`, issued by Let's Encrypt E7 |
| `API_SPKI_PIN_BACKUP` | Intermediate CA | `CN=E7` (Let's Encrypt), signed by ISRG Root X1 |

A TLS handshake succeeds if **any pin in the set matches any certificate in the server's
chain**. The backup intermediate CA pin provides a safety window during leaf certificate
renewals — connections remain alive even if the leaf pin has changed, as long as the
same Let's Encrypt intermediate signed the new leaf.

---

## Current Implementation

### Files

| File | Role |
|---|---|
| `shared/data/src/commonMain/…/data/remote/api/CertificatePinConstants.kt` | Pin values + rotation docs |
| `shared/data/src/commonMain/…/data/remote/api/CertificatePinning.kt` | `expect` declaration |
| `shared/data/src/androidMain/…/data/remote/api/CertificatePinning.kt` | OkHttp `CertificatePinner` actual |
| `shared/data/src/jvmMain/…/data/remote/api/CertificatePinning.kt` | Custom `SpkiPinnedTrustManager` actual |
| `shared/data/src/commonMain/…/data/remote/api/ApiClient.kt` | Call site — passes both pins |

### Platform Implementations

**Android (OkHttp):**
```kotlin
CertificatePinner.Builder()
    .apply { spkiPins.forEach { pin -> add(host, pin) } }
    .build()
```
OkHttp rejects any handshake where no certificate in the chain matches a registered pin.

**Desktop JVM (Ktor CIO — `SpkiPinnedTrustManager`):**
1. Delegates standard CA chain validation to the platform's default `TrustManagerFactory`
2. Computes SHA-256 of each certificate's public key in the chain
3. Rejects with `SSLPeerUnverifiedException` if no hash matches any pin in the set

### Pins Extracted (2026-03-28)

Extracted live from `api.zyntapos.com:443` via VPS OpenSSL:

```
PRIMARY  sha256/U15ycHcHA6rYMzCokiEnm+i851hmZT+RiFMlagBiKyc=
         CN=zyntapos.com | Issuer: Let's Encrypt E7 | Expires: 2026-05-31

BACKUP   sha256/y7xVm0TVJNahMr2sZydE2jQH8SquXV9yLF9seROHHHU=
         CN=E7 (Let's Encrypt) | Issuer: ISRG Root X1
```

---

## Rotation Policy

| Event | Impact | Action Required |
|---|---|---|
| Caddy leaf cert renewal (~every 60 days) | PRIMARY pin changes | Re-extract PRIMARY, ship app update ≥7 days before expiry |
| Let's Encrypt rotates E7 intermediate | BACKUP pin changes | Re-extract BACKUP immediately, ship app update |
| Both pins updated in same release | Normal | Verify both pins before release |

**Re-extraction commands:**
```bash
# PRIMARY — leaf certificate
openssl s_client -connect api.zyntapos.com:443 </dev/null 2>/dev/null \
  | openssl x509 -pubkey -noout \
  | openssl pkey -pubin -outform der \
  | openssl dgst -sha256 -binary | base64

# BACKUP — intermediate CA (cert #2 in chain)
openssl s_client -connect api.zyntapos.com:443 -showcerts </dev/null 2>/dev/null \
  | awk "/BEGIN CERTIFICATE/{n++} n==2,/END CERTIFICATE/ && n==2" \
  | openssl x509 -pubkey -noout \
  | openssl pkey -pubin -outform der \
  | openssl dgst -sha256 -binary | base64
```

---

## Future Evolution — Signed Pin List (TODO)

The current dual-pin approach still requires an app update whenever the leaf cert is
renewed and the backup intermediate pin does not cover the new leaf. The target
architecture is a **Signed Pin List** that eliminates app updates for routine TLS
renewals.

### Design

```
Server side:
  Caddy renew hook → generate new pins → sign with Ed25519 private key
  → publish to GET /.well-known/tls-pins.json

App side (startup):
  Fetch /.well-known/tls-pins.json (CA validation only — no pinning on this call)
  Verify Ed25519 signature using hardcoded public key
  Store verified pins in encrypted SecurePreferences
  Use stored pins for all subsequent API connections
```

### Why this is secure

The Ed25519 signing key is long-lived (years) and stored offline — separate from the
TLS key. An attacker intercepting the pin fetch cannot forge a valid signature without
the signing key. The app only accepts pin updates that pass Ed25519 verification.

### What still requires an app update

Only a compromise or deprecation of the Ed25519 signing key requires an app update —
a rare event measured in years, not months.

**Status:** Not yet implemented. The current dual-pin strategy is the interim solution
until the Signed Pin List is built. When implemented, `API_SPKI_PIN_PRIMARY` can be
removed from the binary and only the Ed25519 public key needs to be hardcoded.

---

## Consequences

- Production builds enforce SPKI pinning on all connections to `api.zyntapos.com`.
- Debug builds are unaffected — local servers and HTTP proxies work normally.
- Leaf certificate renewals require a coordinated app update (mitigated by BACKUP pin
  providing a safety window).
- The rotation burden is accepted as appropriate for a financial POS system on
  enterprise-managed devices where IT controls the update pipeline.
- The Signed Pin List pattern (above) is the path to eliminating the rotation burden
  entirely.

---

## References

- `shared/data/src/commonMain/…/data/remote/api/CertificatePinConstants.kt`
- `shared/data/src/commonMain/…/data/remote/api/CertificatePinning.kt`
- [OkHttp CertificatePinner documentation](https://square.github.io/okhttp/4.x/okhttp/okhttp3/-certificate-pinner/)
- [OWASP Mobile Security — Certificate Pinning](https://owasp.org/www-community/controls/Certificate_and_Public_Key_Pinning)
- ADR-008 — RS256 Key Distribution (related TOFU pattern for JWT public key)

---

*Authored during the 2026-03-28 configuration and security audit session.*
*Approved by: Dilanka (Tech Lead)*
