# Sync Protocol Guide

ZyntaPOS uses a WebSocket-based real-time sync protocol for multi-device coordination.

## Architecture

```
POS App ←→ Sync Service (WSS) ←→ Redis Pub/Sub ←→ API Service
```

- **POS App** pushes operations to the API service via REST (`POST /v1/sync/push`)
- **API Service** publishes a notification to Redis
- **Sync Service** relays the notification to all connected store devices via WebSocket
- **POS App** pulls new operations from the API (`POST /v1/sync/pull`)

## WebSocket Connection

```
WSS wss://sync.zyntapos.com/v1/sync/ws
Authorization: Bearer <jwt-token>
```

### Connection Lifecycle

1. **Connect** — Establish WebSocket with JWT Bearer token
2. **Acknowledge** — Server sends `ack` message with store/device info
3. **Listen** — Receive `delta`, `notify`, or `force_sync` messages
4. **Keep-alive** — Send `ping` messages; server responds with `pong`
5. **Disconnect** — Graceful close or timeout after missed pings

### Message Types

#### Server -> Client

| Type | Description | Action |
|------|-------------|--------|
| `ack` | Connection acknowledged | Store connection metadata |
| `delta` | Small change (<=10 ops) | Apply operations directly |
| `notify` | Large change (>10 ops) | Trigger a pull request |
| `force_sync` | Admin-triggered sync | Pull immediately |
| `diag_command` | Diagnostic command from technician | Execute and respond |
| `diag_session_event` | Diagnostic session started/ended | Update UI indicator |

#### Client -> Server

| Type | Description |
|------|-------------|
| `ping` | Keep-alive heartbeat |
| `diag_response` | Response to a diagnostic command |

## Push/Pull Protocol

### Push Operations

```bash
POST /v1/sync/push
Content-Type: application/json
Authorization: Bearer <token>

{
  "deviceId": "device-123",
  "operations": [
    {
      "id": "op-uuid",
      "tableName": "products",
      "rowId": "prod-456",
      "operationType": "UPDATE",
      "columnValues": {"price": "29.99"},
      "timestamp": 1710000000000,
      "seq": 42
    }
  ]
}
```

### Pull Operations

```bash
POST /v1/sync/pull
Content-Type: application/json
Authorization: Bearer <token>

{
  "deviceId": "device-123",
  "lastSeq": 41
}
```

## Conflict Resolution

The server uses **Last-Write-Wins (LWW)** conflict resolution based on operation timestamps. When two devices modify the same row:

1. The operation with the latest timestamp wins
2. Losing operations are logged in `conflict_log` for audit purposes
3. The winning state is returned to all devices on the next pull

## Reconnection Strategy

POS apps should implement exponential backoff reconnection:

1. Immediate retry
2. Wait 1 second
3. Wait 2 seconds
4. Wait 4 seconds
5. Cap at 30 seconds between retries

On reconnection, always perform a pull to catch up on missed changes.
