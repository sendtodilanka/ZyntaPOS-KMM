# ADR-007: Database-Per-Service for Backend Microservices

**Status:** ACCEPTED
**Date:** 2026-03-06
**Deciders:** Infrastructure team

## Context

The ZyntaPOS backend consists of three Ktor-based services:
- **API server** (`zyntapos-api`) — REST API for auth, products, sync
- **License server** (`zyntapos-license`) — License validation and device registration
- **Sync server** (`zyntapos-sync`) — WebSocket real-time sync (Redis-only, no DB)

Initially, both API and License services shared a single PostgreSQL database (`zyntapos`). This caused **Flyway migration conflicts**: both services had `V1` migrations with different content, leading to checksum mismatches when the second service tried to run its migration.

```
Migration checksum mismatch for migration version 1
Applied to database: -1262888335 (License V1)
Resolved locally:    329499277  (API V1)
```

## Decision

Each database-backed service gets its own PostgreSQL database:

| Service | Database | Flyway migrations |
|---------|----------|-------------------|
| API     | `zyntapos_api` | `backend/api/src/main/resources/db/migration/` |
| License | `zyntapos_license` | `backend/license/src/main/resources/db/migration/` |
| Sync    | _(none — Redis only)_ | N/A |

All databases are hosted on the same PostgreSQL 16 instance, owned by the same `zyntapos` user.

### Implementation

1. **`backend/postgres/init-databases.sh`** — Docker entrypoint init script that creates per-service databases on first PostgreSQL volume initialization.

2. **`docker-compose.yml`** changes:
   - API: `DB_URL=jdbc:postgresql://postgres:5432/zyntapos_api`
   - License: `DB_URL=jdbc:postgresql://postgres:5432/zyntapos_license`
   - PostgreSQL: mounts `init-databases.sh` to `/docker-entrypoint-initdb.d/`

3. **Migration cleanup:**
   - API `V1__initial_schema.sql`: removed `REFERENCES licenses(key)` FK (licenses live in a different DB; validated at app layer)
   - API `V2__licenses_table.sql`: deleted (licenses table is owned by license server)
   - License `V1__license_schema.sql`: comment updated to reflect DB isolation

## Consequences

### Positive
- **No migration conflicts** — each service owns its Flyway schema history
- **Independent scaling** — databases can be moved to separate hosts if needed
- **Independent backup/restore** — per-service recovery without affecting other services
- **Clean ownership** — each service is sole owner of its tables

### Negative
- **No cross-database FK constraints** — `stores.license_key` cannot reference `licenses.key` at DB level; must be validated in application code
- **Slightly more complex init** — requires init script to create multiple databases

### Neutral
- Single PostgreSQL instance still used (resource efficient for MVP)
- Same `zyntapos` user owns all databases (simplifies credential management)

## Notes

- When resetting the database, the `pgdata` Docker volume must be removed so `init-databases.sh` runs again: `docker volume rm zyntapos_pgdata`
- The "VPS Full Fix" workflow has an optional `reset_db=yes` input for this purpose
