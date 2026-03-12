# Getting Started with ZyntaPOS API

## Overview

ZyntaPOS provides a REST API for POS terminal operations, admin panel management, and license activation. All endpoints use JSON for request/response bodies.

## Base URLs

| Service | URL | Purpose |
|---------|-----|---------|
| POS API | `https://api.zyntapos.com` | POS terminal + Admin panel |
| License | `https://license.zyntapos.com` | License activation & heartbeat |
| Sync | `wss://sync.zyntapos.com` | Real-time WebSocket sync |

## Quick Start

### 1. Authenticate

```bash
curl -X POST https://api.zyntapos.com/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "admin@store.com", "password": "your-password"}'
```

Response:
```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIs...",
  "refreshToken": "opaque-refresh-token",
  "expiresIn": 900,
  "user": {
    "id": "usr_...",
    "name": "Admin",
    "role": "ADMIN"
  }
}
```

### 2. Use the Access Token

Include the JWT in the `Authorization` header for all subsequent requests:

```bash
curl https://api.zyntapos.com/v1/products \
  -H "Authorization: Bearer eyJhbGciOiJSUzI1NiIs..."
```

### 3. Refresh Tokens

Access tokens expire after 15 minutes. Use the refresh token to obtain a new pair:

```bash
curl -X POST https://api.zyntapos.com/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken": "opaque-refresh-token"}'
```

## Rate Limits

| Tier | Limit | Endpoints |
|------|-------|-----------|
| Auth | 10 req/min | `/v1/auth/*` |
| Sync | 60 req/min | `/v1/sync/*` |
| API | 300 req/min | All other `/v1/*` |

Exceeding the limit returns `429 Too Many Requests` with a `Retry-After` header.

## Error Format

All errors follow a consistent JSON structure:

```json
{
  "error": "VALIDATION_ERROR",
  "message": "Human-readable description",
  "details": [
    {"field": "email", "message": "Must be a valid email address"}
  ]
}
```

## Next Steps

- [Authentication Guide](./authentication.md) — JWT flow, refresh tokens, admin auth
- [Sync Protocol Guide](./sync-protocol.md) — WebSocket real-time sync
- [License Activation Guide](./license-activation.md) — Device activation flow
- [Error Handling Guide](./error-handling.md) — Error codes and retry strategies
