# ZyntaPOS Admin Panel — Functional Adversarial Audit
**Date:** 2026-03-30
**Auditor:** Claude (automated QA agent)
**Scope:** Admin Panel SPA (`admin-panel/src/`) ↔ Backend API (`backend/api/`)

---

## LIST A — Backend Endpoints

> Built by reading Routing.kt + every *Routes.kt file under backend/api/

| Method | Path | Auth | Role | Source File |
|--------|------|------|------|-------------|
| GET | /health | No | Public | HealthRoutes.kt |
| GET | /health/deep | No | Public | HealthRoutes.kt |
| GET | /health/sync | No | Public | HealthRoutes.kt |
| GET | /ping | No | Public | HealthRoutes.kt |
| GET | /.well-known/public-key | No | Public | WellKnownRoutes.kt |
| GET | /.well-known/tls-pins.json | No | Public | WellKnownRoutes.kt |
| POST | /v1/auth/login | No (rate-limited) | Public | AuthRoutes.kt |
| POST | /v1/auth/refresh | No (rate-limited) | Public | AuthRoutes.kt |
| GET | /admin/auth/status | No (CSRF+IP) | Public | AdminAuthRoutes.kt |
| POST | /admin/auth/bootstrap | No (CSRF+IP) | Public (first-run only) | AdminAuthRoutes.kt |
| POST | /admin/auth/login | No (CSRF+IP, rate-limited) | Public | AdminAuthRoutes.kt |
| POST | /admin/auth/refresh | Cookie (CSRF+IP) | Authenticated | AdminAuthRoutes.kt |
| POST | /admin/auth/logout | Cookie (CSRF+IP) | Authenticated | AdminAuthRoutes.kt |
| GET | /admin/auth/me | Cookie (CSRF+IP) | Authenticated | AdminAuthRoutes.kt |
| POST | /admin/auth/mfa/setup | Cookie (CSRF+IP) | Authenticated | AdminAuthRoutes.kt |
| POST | /admin/auth/mfa/enable | Cookie (CSRF+IP) | Authenticated | AdminAuthRoutes.kt |
| POST | /admin/auth/mfa/disable | Cookie (CSRF+IP) | Authenticated | AdminAuthRoutes.kt |
| POST | /admin/auth/mfa/verify | Cookie (CSRF+IP) | Authenticated (pending token) | AdminAuthRoutes.kt |
| POST | /admin/auth/forgot-password | No (CSRF+IP) | Public | AdminAuthRoutes.kt |
| POST | /admin/auth/reset-password | No (CSRF+IP) | Public | AdminAuthRoutes.kt |
| POST | /admin/auth/change-password | Cookie (CSRF+IP) | Authenticated | AdminAuthRoutes.kt |
| GET | /admin/users | Cookie (CSRF+IP) | users:read | AdminAuthRoutes.kt |
| POST | /admin/users | Cookie (CSRF+IP) | users:write | AdminAuthRoutes.kt |
| PATCH | /admin/users/{id} | Cookie (CSRF+IP) | users:write | AdminAuthRoutes.kt |
| GET | /admin/users/{id}/sessions | Cookie (CSRF+IP) | users:read | AdminAuthRoutes.kt |
| DELETE | /admin/users/{id}/sessions | Cookie (CSRF+IP) | users:sessions:revoke | AdminAuthRoutes.kt |
| GET | /v1/products | JWT RS256 | Any POS role | ProductRoutes.kt |
| GET | /v1/orders | JWT RS256 | Any POS role | OrderRoutes.kt |
| GET | /v1/orders/{orderId} | JWT RS256 | Any POS role | OrderRoutes.kt |
| POST | /v1/sync/push | JWT RS256 (sync rate) | Any POS role | SyncRoutes.kt |
| GET | /v1/sync/pull | JWT RS256 (sync rate) | Any POS role | SyncRoutes.kt |
| GET | /v1/export/customer/{customerId} | JWT RS256 | ADMIN or MANAGER | ExportRoutes.kt |
| GET | /admin/health/system | Cookie (CSRF+IP) | Authenticated | AdminHealthRoutes.kt |
| GET | /admin/health/stores | Cookie (CSRF+IP) | Authenticated | AdminHealthRoutes.kt |
| GET | /admin/health/stores/{storeId} | Cookie (CSRF+IP) | Authenticated | AdminHealthRoutes.kt |
| GET | /admin/stores | Cookie (CSRF+IP) | Authenticated | AdminStoresRoutes.kt |
| GET | /admin/stores/{storeId} | Cookie (CSRF+IP) | Authenticated | AdminStoresRoutes.kt |
| GET | /admin/stores/{storeId}/health | Cookie (CSRF+IP) | Authenticated | AdminStoresRoutes.kt |
| PUT | /admin/stores/{storeId}/config | Cookie (CSRF+IP) | Authenticated | AdminStoresRoutes.kt |
| GET | /admin/audit | Cookie (CSRF+IP) | Authenticated | AdminAuditRoutes.kt |
| GET | /admin/audit/export | Cookie (CSRF+IP) | Authenticated | AdminAuditRoutes.kt |
| GET | /admin/metrics/dashboard | Cookie (CSRF+IP) | Authenticated | AdminMetricsRoutes.kt |
| GET | /admin/metrics/sales | Cookie (CSRF+IP) | Authenticated | AdminMetricsRoutes.kt |
| GET | /admin/metrics/stores | Cookie (CSRF+IP) | Authenticated | AdminMetricsRoutes.kt |
| GET | /admin/reports/sales | Cookie (CSRF+IP) | Authenticated | AdminMetricsRoutes.kt |
| GET | /admin/reports/products | Cookie (CSRF+IP) | Authenticated | AdminMetricsRoutes.kt |
| GET | /admin/alerts | Cookie (CSRF+IP) | Authenticated | AdminAlertsRoutes.kt |
| GET | /admin/alerts/counts | Cookie (CSRF+IP) | Authenticated | AdminAlertsRoutes.kt |
| GET | /admin/alerts/rules | Cookie (CSRF+IP) | Authenticated | AdminAlertsRoutes.kt |
| POST | /admin/alerts/{id}/acknowledge | Cookie (CSRF+IP) | Authenticated | AdminAlertsRoutes.kt |
| POST | /admin/alerts/{id}/resolve | Cookie (CSRF+IP) | Authenticated | AdminAlertsRoutes.kt |
| POST | /admin/alerts/{id}/silence | Cookie (CSRF+IP) | Authenticated | AdminAlertsRoutes.kt |
| PATCH | /admin/alerts/rules/{id} | Cookie (CSRF+IP) | Authenticated | AdminAlertsRoutes.kt |
| GET | /admin/sync/status | Cookie (CSRF+IP) | Authenticated | AdminSyncRoutes.kt |
| GET | /admin/sync/{storeId} | Cookie (CSRF+IP) | Authenticated | AdminSyncRoutes.kt |
| GET | /admin/sync/{storeId}/queue | Cookie (CSRF+IP) | Authenticated | AdminSyncRoutes.kt |
| POST | /admin/sync/{storeId}/force | Cookie (CSRF+IP) | Authenticated | AdminSyncRoutes.kt |
| GET | /admin/sync/conflicts | Cookie (CSRF+IP) | Authenticated | AdminSyncRoutes.kt |
| GET | /admin/sync/{storeId}/conflicts | Cookie (CSRF+IP) | Authenticated | AdminSyncRoutes.kt |
| GET | /admin/sync/dead-letters | Cookie (CSRF+IP) | Authenticated | AdminSyncRoutes.kt |
| GET | /admin/sync/{storeId}/dead-letters | Cookie (CSRF+IP) | Authenticated | AdminSyncRoutes.kt |
| POST | /admin/sync/dead-letters/{id}/retry | Cookie (CSRF+IP) | Authenticated | AdminSyncRoutes.kt |
| DELETE | /admin/sync/dead-letters/{id} | Cookie (CSRF+IP) | Authenticated | AdminSyncRoutes.kt |
| POST | /admin/sync/tokens/revoke | Cookie (CSRF+IP) | users:sessions:revoke | AdminSyncRoutes.kt |
| GET | /admin/config/feature-flags | Cookie (CSRF+IP) | Authenticated | AdminConfigRoutes.kt |
| PATCH | /admin/config/feature-flags/{key} | Cookie (CSRF+IP) | Authenticated | AdminConfigRoutes.kt |
| GET | /admin/config/tax-rates | Cookie (CSRF+IP) | Authenticated | AdminConfigRoutes.kt |
| POST | /admin/config/tax-rates | Cookie (CSRF+IP) | Authenticated | AdminConfigRoutes.kt |
| PUT | /admin/config/tax-rates/{id} | Cookie (CSRF+IP) | Authenticated | AdminConfigRoutes.kt |
| DELETE | /admin/config/tax-rates/{id} | Cookie (CSRF+IP) | Authenticated | AdminConfigRoutes.kt |
| GET | /admin/config/system | Cookie (CSRF+IP) | Authenticated | AdminConfigRoutes.kt |
| PATCH | /admin/config/system/{key} | Cookie (CSRF+IP) | Authenticated | AdminConfigRoutes.kt |
| GET | /admin/tickets | Cookie (CSRF+IP) | tickets:read | AdminTicketRoutes.kt |
| GET | /admin/tickets/metrics | Cookie (CSRF+IP) | tickets:read | AdminTicketRoutes.kt |
| POST | /admin/tickets/bulk-assign | Cookie (CSRF+IP) | tickets:assign | AdminTicketRoutes.kt |
| POST | /admin/tickets/bulk-resolve | Cookie (CSRF+IP) | tickets:resolve | AdminTicketRoutes.kt |
| GET | /admin/tickets/export | Cookie (CSRF+IP) | tickets:read | AdminTicketRoutes.kt |
| POST | /admin/tickets | Cookie (CSRF+IP) | tickets:create | AdminTicketRoutes.kt |
| GET | /admin/tickets/{id} | Cookie (CSRF+IP) | tickets:read | AdminTicketRoutes.kt |
| PATCH | /admin/tickets/{id} | Cookie (CSRF+IP) | tickets:update | AdminTicketRoutes.kt |
| POST | /admin/tickets/{id}/assign | Cookie (CSRF+IP) | tickets:assign | AdminTicketRoutes.kt |
| POST | /admin/tickets/{id}/resolve | Cookie (CSRF+IP) | tickets:resolve | AdminTicketRoutes.kt |
| POST | /admin/tickets/{id}/close | Cookie (CSRF+IP) | tickets:close | AdminTicketRoutes.kt |
| GET | /admin/tickets/{id}/email-threads | Cookie (CSRF+IP) | tickets:read | AdminTicketRoutes.kt |
| GET | /admin/tickets/{id}/comments | Cookie (CSRF+IP) | tickets:read | AdminTicketRoutes.kt |
| POST | /admin/tickets/{id}/comments | Cookie (CSRF+IP) | tickets:comment | AdminTicketRoutes.kt |
| POST | /admin/diagnostic/sessions | Cookie (CSRF+IP) | diagnostics:access | AdminDiagnosticRoutes.kt |
| GET | /admin/diagnostic/sessions/{storeId} | Cookie (CSRF+IP) | diagnostics:read | AdminDiagnosticRoutes.kt |
| DELETE | /admin/diagnostic/sessions/{sessionId} | Cookie (CSRF+IP) | diagnostics:access | AdminDiagnosticRoutes.kt |
| GET | /admin/email/delivery-logs | Cookie (CSRF+IP) | email:logs | AdminEmailRoutes.kt |
| GET | /admin/email/unsubscribes | Cookie (CSRF+IP) | email:logs | AdminEmailRoutes.kt |
| GET | /admin/email/config-status | Cookie (CSRF+IP) | email:settings | AdminEmailRoutes.kt |
| GET | /admin/email/templates | Cookie (CSRF+IP) | email:settings | AdminEmailTemplateRoutes.kt |
| GET | /admin/email/templates/{slug} | Cookie (CSRF+IP) | email:settings | AdminEmailTemplateRoutes.kt |
| PUT | /admin/email/templates/{slug} | Cookie (CSRF+IP) | email:settings | AdminEmailTemplateRoutes.kt |
| GET | /admin/email/preferences | Cookie (CSRF+IP) | Authenticated | AdminEmailPreferencesRoutes.kt |
| PUT | /admin/email/preferences | Cookie (CSRF+IP) | Authenticated | AdminEmailPreferencesRoutes.kt |
| GET | /admin/master-products | Cookie (CSRF+IP) | Authenticated | AdminMasterProductRoutes.kt |
| POST | /admin/master-products | Cookie (CSRF+IP) | Authenticated | AdminMasterProductRoutes.kt |
| GET | /admin/master-products/{id} | Cookie (CSRF+IP) | Authenticated | AdminMasterProductRoutes.kt |
| PUT | /admin/master-products/{id} | Cookie (CSRF+IP) | Authenticated | AdminMasterProductRoutes.kt |
| DELETE | /admin/master-products/{id} | Cookie (CSRF+IP) | Authenticated | AdminMasterProductRoutes.kt |
| GET | /admin/master-products/{id}/stores | Cookie (CSRF+IP) | Authenticated | AdminMasterProductRoutes.kt |
| POST | /admin/master-products/{id}/stores/{storeId} | Cookie (CSRF+IP) | Authenticated | AdminMasterProductRoutes.kt |
| DELETE | /admin/master-products/{id}/stores/{storeId} | Cookie (CSRF+IP) | Authenticated | AdminMasterProductRoutes.kt |
| PUT | /admin/master-products/{id}/stores/{storeId} | Cookie (CSRF+IP) | Authenticated | AdminMasterProductRoutes.kt |
| POST | /admin/master-products/{id}/bulk-assign | Cookie (CSRF+IP) | Authenticated | AdminMasterProductRoutes.kt |
| GET | /admin/inventory/global | Cookie (CSRF+IP) | inventory:read | AdminInventoryRoutes.kt |
| GET | /admin/customers/global | Cookie (CSRF+IP) | customers:read | AdminCustomersRoutes.kt |
| GET | /admin/exchange-rates | Cookie (CSRF+IP) | inventory:read | AdminExchangeRateRoutes.kt |
| PUT | /admin/exchange-rates | Cookie (CSRF+IP) | inventory:write | AdminExchangeRateRoutes.kt |
| GET | /v1/promotions | JWT RS256 | Any POS role | PromotionsRoutes.kt |
| POST | /v1/promotions | JWT RS256 | ADMIN or MANAGER | PromotionsRoutes.kt |
| DELETE | /v1/promotions/{id} | JWT RS256 | ADMIN or MANAGER | PromotionsRoutes.kt |
| POST | /v1/diagnostic/consent/grant | JWT RS256 | POS ADMIN only | DiagnosticConsentRoutes.kt |
| POST | /v1/diagnostic/consent/revoke | JWT RS256 | POS ADMIN only | DiagnosticConsentRoutes.kt |
| POST | /v1/integrity/verify | JWT RS256 | Any POS role | IntegrityRoutes.kt |
| GET | /v1/transfers | JWT RS256 | Any POS role | TransferRoutes.kt |
| GET | /v1/transfers/{id} | JWT RS256 | Any POS role | TransferRoutes.kt |
| POST | /v1/transfers | JWT RS256 | ADMIN or MANAGER | TransferRoutes.kt |
| PUT | /v1/transfers/{id}/approve | JWT RS256 | ADMIN or MANAGER | TransferRoutes.kt |
| PUT | /v1/transfers/{id}/dispatch | JWT RS256 | ADMIN or MANAGER | TransferRoutes.kt |
| PUT | /v1/transfers/{id}/receive | JWT RS256 | ADMIN or MANAGER | TransferRoutes.kt |
| PUT | /v1/transfers/{id}/cancel | JWT RS256 | ADMIN or MANAGER | TransferRoutes.kt |
| GET | /v1/replenishment/rules | JWT RS256 | Any POS role | ReplenishmentRoutes.kt |
| POST | /v1/replenishment/rules | JWT RS256 | ADMIN or MANAGER | ReplenishmentRoutes.kt |
| DELETE | /v1/replenishment/rules/{id} | JWT RS256 | ADMIN or MANAGER | ReplenishmentRoutes.kt |
| GET | /v1/replenishment/suggestions | JWT RS256 | Any POS role | ReplenishmentRoutes.kt |
| GET | /v1/pricing/rules | JWT RS256 | Any POS role | PricingRoutes.kt |
| POST | /v1/pricing/rules | JWT RS256 | ADMIN or MANAGER | PricingRoutes.kt |
| DELETE | /v1/pricing/rules/{id} | JWT RS256 | ADMIN or MANAGER | PricingRoutes.kt |
| GET | /v1/store-access/my-stores | JWT RS256 | Any POS role | StoreAccessRoutes.kt |
| GET | /v1/store-access/users | JWT RS256 | ADMIN or STORE_MANAGER | StoreAccessRoutes.kt |
| POST | /v1/store-access/grant | JWT RS256 | POS ADMIN only | StoreAccessRoutes.kt |
| POST | /v1/store-access/revoke | JWT RS256 | POS ADMIN only | StoreAccessRoutes.kt |
| GET | /v1/store-access/check | JWT RS256 | Any POS role | StoreAccessRoutes.kt |
| GET | /v1/loyalty/summary | JWT RS256 | Any POS role | LoyaltyRoutes.kt |
| GET | /v1/taxes/overrides | JWT RS256 | Any POS role | TaxOverrideRoutes.kt |
| POST | /v1/taxes/overrides | JWT RS256 | Any POS role | TaxOverrideRoutes.kt |
| GET | /v1/fulfillment | JWT RS256 | Any POS role | FulfillmentRoutes.kt |
| PATCH | /v1/fulfillment/{orderId}/status | JWT RS256 | Any POS role | FulfillmentRoutes.kt |
| GET | /tickets/status/{token} | No (token-based) | Public | CustomerTicketRoutes.kt |
| GET | /unsubscribe | No | Public | UnsubscribeRoutes.kt |
| POST | /internal/email/inbound | HMAC-SHA256 | Internal (CF Worker) | InboundEmailRoutes.kt |
| POST | /webhooks/resend | No (Resend webhook) | External service | WebhookRoutes.kt |
| GET | /admin/replenishment/rules | Cookie (CSRF+IP) | inventory:read | AdminReplenishmentRoutes.kt |
| GET | /admin/replenishment/suggestions | Cookie (CSRF+IP) | inventory:read | AdminReplenishmentRoutes.kt |
| GET | /admin/transfers | Cookie (CSRF+IP) | Authenticated | AdminTransferRoutes.kt ⚠️ NOT REGISTERED in Routing.kt |
| GET | /admin/transfers/{id} | Cookie (CSRF+IP) | Authenticated | AdminTransferRoutes.kt ⚠️ NOT REGISTERED in Routing.kt |
| GET | /admin/pricing/rules | Cookie (CSRF+IP) | inventory:read | AdminPricingRoutes.kt ⚠️ NOT REGISTERED in Routing.kt |

*Total endpoints found: 126*

> **Note — Unregistered routes (dead code):** `AdminTransferRoutes.kt` (2 endpoints), `AdminPricingRoutes.kt` (1 endpoint), and `AdminReplenishmentRoutes.kt` (2 endpoints) exist as compiled files but are **not imported or called** in `Routing.kt`. These 5 endpoints are unreachable at runtime. Confirmed by grep: no matches for `adminTransferRoutes`, `adminPricingRoutes`, or `adminReplenishmentRoutes` in Routing.kt.

=== LIST A COMPLETE ===
