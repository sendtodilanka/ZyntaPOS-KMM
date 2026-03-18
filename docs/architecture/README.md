# Architecture Documentation

> Diagrams, ADRs (Architecture Decision Records), and module dependency maps.

## Contents

- `module-dependency-graph.md` — Visual module dependency tree (29 modules, 17 feature modules)
- `sync-strategy.md` — Offline-first outbox sync, delta application, CRDT backlog
- `security-model.md` — Encryption at rest, RBAC, JWT flow, PIN management, key storage
- `backend-database-schemas.md` — PostgreSQL schemas for `zyntapos_api` (26 migrations) and `zyntapos_license` (4 migrations)
- `deployment.md` — VPS deployment topology, Cloudflare, Docker Compose service map
- `adr/` — Architecture Decision Records (ADR-001 through ADR-008, all ACCEPTED)
