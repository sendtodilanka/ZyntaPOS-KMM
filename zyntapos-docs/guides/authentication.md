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

## Admin Panel Authentication (HS256 JWT)

The admin panel uses HttpOnly cookies with CSRF protection.

### Flow

1. **Login** — `POST /admin/auth/login` with email + password
2. **Cookie set** — Server sets `admin_token` HttpOnly cookie + `csrf_token` header
3. **CSRF token** — Include `X-CSRF-Token` header on all mutating requests
4. **MFA** — If enabled, `POST /admin/auth/verify-mfa` with TOTP code after login

### Admin Roles

| Role | Access Level |
|------|-------------|
| ADMIN | Full access — all endpoints |
| OPERATOR | Store management, user management |
| FINANCE | Financial reports, invoices |
| AUDITOR | Read-only access to all data |
| HELPDESK | Ticket management, customer support |

## Brute-Force Protection

Both authentication systems implement brute-force protection:

- **5 failed attempts** — Account locked for 5 minutes
- **Timing** — Constant-time password comparison prevents timing attacks
- **Audit logging** — All login attempts are recorded in the audit log

## Security Best Practices

- Store refresh tokens securely (EncryptedSharedPreferences on Android, KeyStore on JVM)
- Never log or expose JWT tokens in error messages
- Implement token rotation on every refresh
- Handle 401 responses by triggering the refresh flow
