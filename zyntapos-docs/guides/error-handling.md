# Error Handling Guide

All ZyntaPOS API errors follow a consistent JSON format with HTTP status codes.

## Error Response Format

```json
{
  "error": "ERROR_CODE",
  "message": "Human-readable description of the error",
  "details": []
}
```

The `details` array is optional and contains field-level validation errors when applicable.

## HTTP Status Codes

| Status | Meaning | Action |
|--------|---------|--------|
| 400 | Bad Request | Fix the request body or parameters |
| 401 | Unauthorized | Refresh the access token or re-login |
| 403 | Forbidden | User lacks the required role/permission |
| 404 | Not Found | Resource does not exist |
| 409 | Conflict | Resource already exists or version conflict |
| 422 | Unprocessable Entity | Validation failed — check `details` array |
| 429 | Too Many Requests | Wait for `Retry-After` header duration |
| 500 | Internal Server Error | Report to support; retry with backoff |

## Common Error Codes

### Authentication

| Code | Status | Description |
|------|--------|-------------|
| `INVALID_CREDENTIALS` | 401 | Wrong email or password |
| `ACCOUNT_LOCKED` | 401 | Too many failed login attempts |
| `TOKEN_EXPIRED` | 401 | JWT access token has expired |
| `REFRESH_TOKEN_INVALID` | 401 | Refresh token revoked or expired |
| `INSUFFICIENT_PERMISSIONS` | 403 | Role does not have the required permission |

### Validation

| Code | Status | Description |
|------|--------|-------------|
| `VALIDATION_ERROR` | 422 | One or more fields failed validation |
| `MISSING_FIELD` | 422 | Required field is missing |
| `INVALID_FORMAT` | 422 | Field value does not match expected format |

### Business Logic

| Code | Status | Description |
|------|--------|-------------|
| `INSUFFICIENT_STOCK` | 409 | Not enough stock for the requested quantity |
| `REGISTER_NOT_OPEN` | 409 | No active register session |
| `ORDER_ALREADY_COMPLETED` | 409 | Cannot modify a completed order |
| `COUPON_EXPIRED` | 422 | Coupon code has expired |

## Retry Strategy

For transient errors (5xx, network failures), implement exponential backoff:

```
Attempt 1: Immediate
Attempt 2: Wait 1 second
Attempt 3: Wait 2 seconds
Attempt 4: Wait 4 seconds (max)
```

The Ktor HTTP client in ZyntaPOS is pre-configured with this retry strategy.

## Validation Error Details

When the server returns a 422, the `details` array contains per-field errors:

```json
{
  "error": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "details": [
    {"field": "email", "message": "Must be a valid email address"},
    {"field": "password", "message": "Must be at least 8 characters"}
  ]
}
```

Map these to inline form errors in the UI for a good user experience.
