# Authentication Guide

ZyntaPOS uses two separate authentication systems for POS terminals and the admin panel.

## POS Terminal Authentication (RS256 JWT)

POS terminals authenticate with email/password and receive RS256-signed JWT tokens.

### Flow

1. **Login** — `POST /v1/auth/login` with email + password
2. **Receive tokens** — Access token (15 min TTL) + opaque refresh token
3. **Use access token** — `Authorization: Bearer <token>` on all `/v1/*` endpoints
4. **Refresh** — `POST /v1/auth/refresh` when the access token expires
5. **Logout** — `POST /v1/auth/logout` to invalidate the refresh token

### JWT Claims

| Claim | Description |
|-------|-------------|
| `sub` | User ID |
| `iss` | `https://api.zyntapos.com` |
| `aud` | `zyntapos-app` |
| `exp` | Expiration timestamp |
| `role` | User role (ADMIN, MANAGER, CASHIER, etc.) |
| `storeId` | Store the user belongs to |

### Public Key Distribution

The RS256 public key is available at:

```
GET /.well-known/public-key
```

POS apps use a TOFU (Trust On First Use) model: the default key is bundled in the app, and the latest key is cached in SecurePreferences (ADR-008).

### Code Sample — POS Login (curl)

```bash
# Step 1: Login
curl -s -X POST https://api.zyntapos.com/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "cashier@store.com",
    "password": "your-password",
    "license_key": "ZYNTA-ABCD-1234-EFGH-5678",
    "device_id": "android-tablet-001"
  }'

# Response:
# {
#   "access_token": "eyJhbGciOiJSUzI1NiIs...",
#   "refresh_token": "opaque-refresh-token-value",
#   "expires_in": 900,
#   "user": { "id": "...", "name": "Cashier", "role": "CASHIER", "store_id": "..." }
# }

# Step 2: Use access token on subsequent requests
curl -s https://api.zyntapos.com/v1/products \
  -H "Authorization: Bearer eyJhbGciOiJSUzI1NiIs..."

# Step 3: Refresh when token expires (HTTP 401)
curl -s -X POST https://api.zyntapos.com/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refresh_token": "opaque-refresh-token-value"}'
```

### Code Sample — POS Login (Kotlin)

```kotlin
val client = HttpClient {
    install(ContentNegotiation) { json() }
    install(Auth) {
        bearer {
            loadTokens { BearerTokens(accessToken, refreshToken) }
            refreshTokens {
                val response = client.post("/v1/auth/refresh") {
                    setBody(RefreshRequest(refreshToken = oldTokens.refreshToken))
                }
                val tokens = response.body<RefreshResponse>()
                BearerTokens(tokens.accessToken, tokens.refreshToken ?: oldTokens.refreshToken)
            }
        }
    }
}

// Login
val loginResponse = client.post("/v1/auth/login") {
    setBody(LoginRequest(email = "cashier@store.com", password = "pass"))
}
```

## Admin Panel Authentication (RS256 JWT)

The admin panel uses HttpOnly cookies with CSRF protection. Admin JWT tokens are
signed with the same RS256 key pair as POS tokens (migrated from HS256 in A7).

### Flow

1. **Login** — `POST /admin/auth/login` with email + password
2. **Cookie set** — Server sets `admin_access_token` HttpOnly cookie + `csrf_token` cookie
3. **CSRF token** — Include `X-CSRF-Token` header on all mutating requests (double-submit pattern)
4. **MFA** — If MFA enabled, login returns `mfaRequired: true` + `pendingToken`. Complete with `POST /admin/auth/mfa/verify`
5. **Refresh** — `POST /admin/auth/refresh` (cookie-based, no body needed)
6. **Logout** — `POST /admin/auth/logout` clears cookies

### Code Sample — Admin Login (curl)

```bash
# Login
curl -s -X POST https://api.zyntapos.com/admin/auth/login \
  -H "Content-Type: application/json" \
  -H "X-CSRF-Token: $(curl -s -c - https://api.zyntapos.com/admin/auth/status | grep csrf)" \
  -d '{"email": "admin@zyntapos.com", "password": "admin-password"}' \
  -c cookies.txt

# Use cookies for subsequent requests
curl -s https://api.zyntapos.com/admin/stores -b cookies.txt

# If MFA is required
curl -s -X POST https://api.zyntapos.com/admin/auth/mfa/verify \
  -H "Content-Type: application/json" \
  -d '{"code": "123456", "pendingToken": "mfa-pending-token"}' \
  -c cookies.txt
```

### Admin Roles

| Role | Access Level |
|------|-------------|
| ADMIN | Full access — all endpoints |
| OPERATOR | Stores, sync, diagnostics, tickets |
| FINANCE | Metrics, reports, config (read-only) |
| AUDITOR | Audit logs (read-only) |
| HELPDESK | Support tickets only |

## IP Allowlisting

Admin panel endpoints are protected by an IP allowlist. Configure via the
`ADMIN_IP_ALLOWLIST` environment variable (comma-separated CIDRs). When empty,
all IPs are allowed.

## Brute-Force Protection

Both authentication systems implement brute-force protection:

- **POS:** Account locked after 5 failed attempts (5-minute cooldown)
- **Admin:** Account locked after 5 failed attempts with progressive lockout
- **Timing** — Constant-time password comparison prevents timing attacks
- **Audit logging** — All login attempts are recorded in the audit log
- **Login alerts** — Admin accounts receive email alerts on login from new IPs

## Password Policies

- **POS users:** SHA-256 + 16-byte salt (PinManager format)
- **Admin users:** BCrypt cost 12 with forced rotation (`ADMIN_PASSWORD_MAX_AGE_DAYS`)

## Security Best Practices

- Store refresh tokens securely (EncryptedSharedPreferences on Android, KeyStore on JVM)
- Never log or expose JWT tokens in error messages
- Implement token rotation on every refresh (POS refresh tokens are single-use)
- Handle 401 responses by triggering the refresh flow
- Verify the RS256 public key via `GET /.well-known/public-key` (TOFU model)
