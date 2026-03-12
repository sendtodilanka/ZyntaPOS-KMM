# License Activation Guide

ZyntaPOS requires license activation before a POS terminal can operate. The license service manages device activation, heartbeats, and edition management.

## Activation Flow

### 1. Request Activation

```bash
POST https://license.zyntapos.com/v1/license/activate
Content-Type: application/json

{
  "licenseKey": "ZYNTA-XXXX-XXXX-XXXX",
  "deviceId": "unique-device-identifier",
  "deviceName": "Front Counter iPad",
  "platform": "ANDROID",
  "appVersion": "1.0.0"
}
```

### 2. Activation Response

```json
{
  "activated": true,
  "license": {
    "id": "lic_...",
    "edition": "PROFESSIONAL",
    "maxDevices": 5,
    "activeDevices": 2,
    "expiresAt": "2027-03-12T00:00:00Z"
  },
  "device": {
    "id": "dev_...",
    "deviceId": "unique-device-identifier",
    "activatedAt": "2026-03-12T10:00:00Z"
  }
}
```

### 3. Periodic Heartbeat

After activation, the POS app sends periodic heartbeats to confirm the device is still active:

```bash
POST https://license.zyntapos.com/v1/license/heartbeat
Content-Type: application/json
Authorization: Bearer <token>

{
  "deviceId": "unique-device-identifier",
  "appVersion": "1.0.0",
  "uptime": 3600
}
```

Heartbeats should be sent every 15 minutes. The server uses heartbeats to track active devices and can revoke access if a device stops reporting.

## Editions

| Edition | Max Devices | Features |
|---------|-------------|----------|
| STARTER | 1 | Single terminal, basic POS |
| PROFESSIONAL | 5 | Multi-terminal, inventory, reports |
| ENTERPRISE | Unlimited | Multi-store, staff management, API access |

## Offline Grace Period

If a device cannot reach the license server:

- **7-day grace period** — The app continues to operate normally
- **After 7 days** — The app enters read-only mode (no new sales)
- **On reconnection** — Full functionality is restored after a successful heartbeat

## Error Codes

| Code | Description |
|------|-------------|
| `LICENSE_INVALID` | License key not found or revoked |
| `LICENSE_EXPIRED` | License has expired |
| `MAX_DEVICES_REACHED` | Cannot activate — device limit reached |
| `DEVICE_ALREADY_ACTIVATED` | This device is already activated |
| `HEARTBEAT_REJECTED` | Device has been deactivated by admin |
