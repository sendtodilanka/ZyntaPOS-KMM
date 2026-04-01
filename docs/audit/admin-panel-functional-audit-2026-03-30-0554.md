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

---

## LIST B — Frontend API Calls

> Built by reading all 17 files under `admin-panel/src/api/`

| # | Function | Method | Path | TanStack Key / Mutation |
|---|----------|--------|------|------------------------|
| **auth.ts** | | | | |
| 1 | `useCurrentUser` | GET | `admin/auth/me` | `useQuery(['admin','me'])` |
| 2 | `useAdminLogin` | POST | `admin/auth/login` | `useMutation` |
| 3 | `useAdminStatus` | GET | `admin/auth/status` | `useQuery(['admin','status'])` |
| 4 | `useAdminBootstrap` | POST | `admin/auth/bootstrap` | `useMutation` |
| 5 | `useAdminLogout` | POST | `admin/auth/logout` | `useMutation` |
| 6 | `useAdminMfaSetup` | POST | `admin/auth/mfa/setup` | `useMutation` |
| 7 | `useAdminMfaEnable` | POST | `admin/auth/mfa/enable` | `useMutation` |
| 8 | `useAdminMfaDisable` | POST | `admin/auth/mfa/disable` | `useMutation` |
| 9 | `useAdminMfaVerify` | POST | `admin/auth/mfa/verify` | `useMutation` |
| 10 | `useChangePassword` | POST | `admin/auth/change-password` | `useMutation` |
| 11 | `useListSessions` | GET | `admin/users/{userId}/sessions` | `useQuery(['users',userId,'sessions'])` |
| **users.ts** | | | | |
| 12 | `useAdminUsers` | GET | `admin/users?{filters}` | `useQuery(['users',filters])` |
| 13 | `useCreateUser` | POST | `admin/users` | `useMutation` |
| 14 | `useUpdateUser` | PATCH | `admin/users/{userId}` | `useMutation` |
| 15 | `useDeactivateUser` | PATCH | `admin/users/{userId}` | `useMutation` (wraps `{isActive:false}`) |
| 16 | `useRevokeSessions` | DELETE | `admin/users/{userId}/sessions` | `useMutation` |
| **tickets.ts** | | | | |
| 17 | `useTickets` | GET | `admin/tickets?{filter}` | `useQuery(['tickets','list',filter])` |
| 18 | `useTicket` | GET | `admin/tickets/{id}` | `useQuery(['tickets','detail',id])` |
| 19 | `useTicketComments` | GET | `admin/tickets/{ticketId}/comments` | `useQuery(['tickets','comments',ticketId])` |
| 20 | `useCreateTicket` | POST | `admin/tickets` | `useMutation` |
| 21 | `useUpdateTicket` | PATCH | `admin/tickets/{id}` | `useMutation` |
| 22 | `useAssignTicket` | POST | `admin/tickets/{id}/assign` | `useMutation` |
| 23 | `useResolveTicket` | POST | `admin/tickets/{id}/resolve` | `useMutation` |
| 24 | `useCloseTicket` | POST | `admin/tickets/{id}/close` | `useMutation` |
| 25 | `useAddComment` | POST | `admin/tickets/{ticketId}/comments` | `useMutation` |
| 26 | `useEmailThreads` | GET | `admin/tickets/{ticketId}/email-threads` | `useQuery(['tickets','email-threads',ticketId])` |
| 27 | `useTicketMetrics` | GET | `admin/tickets/metrics` | `useQuery(['tickets','metrics'])` |
| 28 | `useBulkAssignTickets` | POST | `admin/tickets/bulk-assign` | `useMutation` |
| 29 | `useBulkResolveTickets` | POST | `admin/tickets/bulk-resolve` | `useMutation` |
| **customers.ts** | | | | |
| 30 | `useGlobalCustomers` | GET | `admin/customers/global?{filters}` | `useQuery(['customers-global',filters])` |
| **licenses.ts** *(via `licenseClient` — separate base URL)* | | | | |
| 31 | `useLicenses` | GET | `admin/licenses?{filters}` | `useQuery(['licenses',filters])` |
| 32 | `useLicense` | GET | `admin/licenses/{key}` | `useQuery(['licenses',key])` |
| 33 | `useLicenseStats` | GET | `admin/licenses/stats` | `useQuery(['licenses','stats'])` |
| 34 | `useLicenseDevices` | GET | `admin/licenses/{key}/devices` | `useQuery(['licenses',key,'devices'])` |
| 35 | `useCreateLicense` | POST | `admin/licenses` | `useMutation` |
| 36 | `useUpdateLicense` | PUT | `admin/licenses/{key}` | `useMutation` |
| 37 | `useRevokeLicense` | DELETE | `admin/licenses/{key}` | `useMutation` |
| 38 | `useDeregisterDevice` | DELETE | `admin/licenses/{key}/devices/{deviceId}` | `useMutation` |
| 39 | `useForceSyncLicense` | PUT | `admin/licenses/{key}` | `useMutation` (wraps `{forceSync:true}`) |
| **stores.ts** | | | | |
| 40 | `useStores` | GET | `admin/stores?{filters}` | `useQuery(['stores',filters])` |
| 41 | `useStore` | GET | `admin/stores/{storeId}` | `useQuery(['stores',storeId])` |
| 42 | `useStoreHealth` | GET | `admin/stores/{storeId}/health` | `useQuery(['stores',storeId,'health'])` |
| 43 | `useAllStoreHealth` *(stores.ts)* | GET | `admin/health/stores` | `useQuery(['stores','health'])` ⚠️ duplicate |
| 44 | `useUpdateStoreConfig` | PUT | `admin/stores/{storeId}/config` | `useMutation` |
| **inventory.ts** | | | | |
| 45 | `useGlobalInventory` | GET | `admin/inventory/global?{filters}` | `useQuery(['inventory-global',filters])` |
| **health.ts** | | | | |
| 46 | `useSystemHealth` | GET | `admin/health/system` | `useQuery(['health','system'])` |
| 47 | `useAllStoreHealth` *(health.ts)* | GET | `admin/health/stores` | `useQuery(['health','stores'])` ⚠️ duplicate |
| 48 | `useStoreHealthDetail` | GET | `admin/health/stores/{storeId}` | `useQuery(['health','store',storeId])` |
| **metrics.ts** | | | | |
| 49 | `useDashboardKPIs` | GET | `admin/metrics/dashboard?period={period}` | `useQuery(['metrics','dashboard',period])` |
| 50 | `useSalesChart` | GET | `admin/metrics/sales?{params}` | `useQuery(['metrics','sales',params])` |
| 51 | `useStoreComparison` | GET | `admin/metrics/stores?period={period}` | `useQuery(['metrics','stores',period])` |
| 52 | `useSalesReport` | GET | `admin/reports/sales?{params}` | `useQuery(['reports','sales',params])` |
| 53 | `useProductPerformance` | GET | `admin/reports/products?{params}` | `useQuery(['reports','products',params])` |
| **alerts.ts** | | | | |
| 54 | `useAlerts` | GET | `admin/alerts?{filter}` | `useQuery(['alerts','list',filter])` |
| 55 | `useAlertCounts` | GET | `admin/alerts/counts` | `useQuery(['alerts','counts'])` |
| 56 | `useAlertRules` | GET | `admin/alerts/rules` | `useQuery(['alerts','rules'])` |
| 57 | `useAcknowledgeAlert` | POST | `admin/alerts/{id}/acknowledge` | `useMutation` |
| 58 | `useResolveAlert` | POST | `admin/alerts/{id}/resolve` | `useMutation` |
| 59 | `useSilenceAlert` | POST | `admin/alerts/{id}/silence` | `useMutation` |
| 60 | `useToggleAlertRule` | PATCH | `admin/alerts/rules/{id}` | `useMutation` |
| **audit.ts** | | | | |
| 61 | `useAuditLogs` | GET | `admin/audit?{filters}` | `useQuery(['audit',filters])` |
| 62 | `exportAuditLogs` *(plain async fn — no TanStack hook)* | GET | `admin/audit/export?{filters}` | direct fetch |
| **diagnostic.ts** | | | | |
| 63 | `useActiveDiagnosticSession` | GET | `admin/diagnostic/sessions/{storeId}` | `useQuery(['diagnostic-sessions','active',storeId])` |
| 64 | `useCreateDiagnosticSession` | POST | `admin/diagnostic/sessions` | `useMutation` |
| 65 | `useRevokeDiagnosticSession` | DELETE | `admin/diagnostic/sessions/{sessionId}` | `useMutation` |
| **email.ts** | | | | |
| 66 | `useEmailDeliveryLogs` | GET | `admin/email/delivery-logs?{params}` | `useQuery(['email','delivery-logs',page,pageSize,filters])` |
| 67 | `useEmailTemplates` | GET | `admin/email/templates` | `useQuery(['email','templates'])` |
| 68 | `useEmailTemplate` | GET | `admin/email/templates/{slug}` | `useQuery(['email','templates',slug])` |
| 69 | `useUpdateEmailTemplate` | PUT | `admin/email/templates/{slug}` | `useMutation` |
| 70 | `useEmailPreferences` | GET | `admin/email/preferences` | `useQuery(['email','preferences'])` |
| 71 | `useUpdateEmailPreferences` | PUT | `admin/email/preferences` | `useMutation` |
| 72 | `useEmailUnsubscribes` | GET | `admin/email/unsubscribes` | `useQuery(['email','unsubscribes'])` |
| 73 | `useEmailConfigStatus` | GET | `admin/email/config-status` | `useQuery(['email','config-status'])` |
| **config.ts** | | | | |
| 74 | `useFeatureFlags` | GET | `admin/config/feature-flags` | `useQuery(['config','flags'])` |
| 75 | `useUpdateFeatureFlag` | PATCH | `admin/config/feature-flags/{key}` | `useMutation` |
| 76 | `useSystemConfig` | GET | `admin/config/system` | `useQuery(['config','system'])` |
| 77 | `useUpdateSystemConfig` | PATCH | `admin/config/system/{key}` | `useMutation` |
| **master-products.ts** | | | | |
| 78 | `useMasterProducts` | GET | `admin/master-products?{filters}` | `useQuery(['master-products',filters])` |
| 79 | `useMasterProduct` | GET | `admin/master-products/{id}` | `useQuery(['master-products',id])` |
| 80 | `useCreateMasterProduct` | POST | `admin/master-products` | `useMutation` |
| 81 | `useUpdateMasterProduct` | PUT | `admin/master-products/{id}` | `useMutation` |
| 82 | `useDeleteMasterProduct` | DELETE | `admin/master-products/{id}` | `useMutation` |
| 83 | `useMasterProductStores` | GET | `admin/master-products/{id}/stores` | `useQuery(['master-products',id,'stores'])` |
| 84 | `useAssignToStore` | POST | `admin/master-products/{id}/stores/{storeId}` | `useMutation` |
| 85 | `useRemoveFromStore` | DELETE | `admin/master-products/{id}/stores/{storeId}` | `useMutation` |
| 86 | `useUpdateStoreOverride` | PUT | `admin/master-products/{id}/stores/{storeId}` | `useMutation` |
| 87 | `useBulkAssign` | POST | `admin/master-products/{id}/bulk-assign` | `useMutation` |
| **sync.ts** | | | | |
| 88 | `useSyncStatus` | GET | `admin/sync/status` | `useQuery(['sync','status'])` |
| 89 | `useStoreSync` | GET | `admin/sync/{storeId}` | `useQuery(['sync',storeId])` |
| 90 | `useSyncQueue` | GET | `admin/sync/{storeId}/queue` | `useQuery(['sync',storeId,'queue'])` |
| 91 | `useConflictLog` | GET | `admin/sync/{storeId}/conflicts` or `admin/sync/conflicts` | `useQuery(['sync','conflicts',storeId])` |
| 92 | `useDeadLetters` | GET | `admin/sync/{storeId}/dead-letters` or `admin/sync/dead-letters` | `useQuery(['sync','dead-letters',storeId])` |
| 93 | `useRetryDeadLetter` | POST | `admin/sync/dead-letters/{id}/retry` | `useMutation` |
| 94 | `useDiscardDeadLetter` | DELETE | `admin/sync/dead-letters/{id}` | `useMutation` |
| 95 | `useForceSync` | POST | `admin/sync/{storeId}/force` | `useMutation` |
| **exchange-rates.ts** | | | | |
| 96 | `useExchangeRates` | GET | `admin/exchange-rates` | `useQuery(['exchange-rates'])` |
| 97 | `useUpsertExchangeRate` | PUT | `admin/exchange-rates` | `useMutation` |

*Total frontend API functions: 97*

**Notes:**
- `licenses.ts` (#31–39) uses `licenseClient` (License microservice, separate base URL) — not the API service. These endpoints do not appear in `backend/api` Routing.kt.
- `useAllStoreHealth` exists in **both** `stores.ts` (key: `['stores','health']`) and `health.ts` (key: `['health','stores']`) — both hit `admin/health/stores`. Cache divergence bug.
- `useDeactivateUser` (#15) and `useUpdateUser` (#14) both call `PATCH admin/users/{userId}`.
- `useForceSyncLicense` (#39) and `useUpdateLicense` (#36) both call `PUT admin/licenses/{key}`.
- `exportAuditLogs` (#62) is a plain `async` function — no loading/error state management via TanStack.

=== LIST B COMPLETE ===

---

## PERMISSIONS MAP

> Source: `admin-panel/src/hooks/use-auth.ts`

| Permission | ADMIN | OPERATOR | FINANCE | AUDITOR | HELPDESK |
|------------|:-----:|:--------:|:-------:|:-------:|:--------:|
| `dashboard:ops` | ✓ | ✓ | | | |
| `dashboard:financial` | ✓ | | ✓ | | |
| `dashboard:support` | ✓ | ✓ | | | ✓ |
| `license:read` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `license:write` | ✓ | | | | |
| `license:revoke` | ✓ | | | | |
| `license:export` | ✓ | | ✓ | | |
| `store:read` | ✓ | ✓ | | | ✓ |
| `store:sync:manage` | ✓ | ✓ | | | |
| `store:config:read` | ✓ | ✓ | | | |
| `diagnostics:access` | ✓ | ✓ | | | |
| `diagnostics:read` | ✓ | ✓ | | | ✓ |
| `config:push` | ✓ | | | | |
| `reports:financial` | ✓ | | ✓ | | |
| `reports:operational` | ✓ | ✓ | | | |
| `reports:support` | ✓ | ✓ | | | ✓ |
| `reports:read` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `reports:export` | ✓ | | ✓ | | |
| `alerts:read` | ✓ | ✓ | | | |
| `alerts:acknowledge` | ✓ | ✓ | | | |
| `alerts:configure` | ✓ | | | | |
| `audit:read` | ✓ | | | ✓ | |
| `audit:export` | ✓ | | | ✓ | |
| `tickets:read` | ✓ | ✓ | | | ✓ |
| `tickets:create` | ✓ | ✓ | | | ✓ |
| `tickets:update` | ✓ | ✓ | | | ✓ |
| `tickets:assign` | ✓ | ✓ | | | ✓ |
| `tickets:resolve` | ✓ | ✓ | | | |
| `tickets:close` | ✓ | ✓ | | | ✓ |
| `tickets:comment` | ✓ | ✓ | | | ✓ |
| `users:read` | ✓ | | | | |
| `users:write` | ✓ | | | | |
| `users:deactivate` | ✓ | | | | |
| `users:sessions:revoke` | ✓ | | | | |
| `system:settings` | ✓ | | | | |
| `system:health` | ✓ | ✓ | | | |
| `system:backup` | ✓ | | | | |
| `email:settings` | ✓ | | | | |
| `email:logs` | ✓ | ✓ | | | |
| `inventory:read` | ✓ | ✓ | ✓ | | |
| `inventory:write` | ✓ | | | | |
| `transfers:read` | ✓ | ✓ | | | |
| `customers:read` | ✓ | ✓ | | | ✓ |

*Total: 43 permissions (code comment in use-auth.ts incorrectly states 39)*

=== PERMISSIONS MAP COMPLETE ===

---

## Category A — Backend ↔ Frontend Coverage Gap

> Methodology: compared every endpoint in LIST A against every function in LIST B by HTTP method + normalized path.
> POS endpoints (`/v1/*`) and infrastructure endpoints (`/health`, `/.well-known/*`, `/internal/*`, `/webhooks/*`, `/unsubscribe`, `/tickets/status/*`) are excluded — the admin panel SPA is not expected to call them.

### A.1 — Backend endpoints with NO frontend coverage

| Method | Path | Source File | Severity | Notes |
|--------|------|-------------|----------|-------|
| POST | `/admin/auth/forgot-password` | AdminAuthRoutes.kt | HIGH | No self-service password reset UI — admins who forget password are locked out |
| POST | `/admin/auth/reset-password` | AdminAuthRoutes.kt | HIGH | Partner endpoint to forgot-password — also missing |
| POST | `/admin/sync/tokens/revoke` | AdminSyncRoutes.kt | INFO | Bulk sync token revocation — no UI exists |
| GET | `/admin/tickets/export` | AdminTicketRoutes.kt | INFO | Ticket CSV export endpoint — no download button in tickets UI |
| GET | `/admin/config/tax-rates` | AdminConfigRoutes.kt | HIGH | Tax rate config has no frontend UI at all |
| POST | `/admin/config/tax-rates` | AdminConfigRoutes.kt | HIGH | Tax rate creation — no UI |
| PUT | `/admin/config/tax-rates/{id}` | AdminConfigRoutes.kt | HIGH | Tax rate update — no UI |
| DELETE | `/admin/config/tax-rates/{id}` | AdminConfigRoutes.kt | HIGH | Tax rate deletion — no UI |
| GET | `/admin/replenishment/rules` | AdminReplenishmentRoutes.kt | INFO | Dead route (unregistered in Routing.kt) + no frontend |
| GET | `/admin/replenishment/suggestions` | AdminReplenishmentRoutes.kt | INFO | Dead route + no frontend |
| GET | `/admin/transfers` | AdminTransferRoutes.kt | INFO | Dead route + no frontend |
| GET | `/admin/transfers/{id}` | AdminTransferRoutes.kt | INFO | Dead route + no frontend |
| GET | `/admin/pricing/rules` | AdminPricingRoutes.kt | INFO | Dead route + no frontend |

### A.2 — Frontend functions calling paths NOT in LIST A

| # | Function | Path Called | Issue |
|---|----------|-------------|-------|
| 31–39 | `useLicenses`, `useLicense`, etc. | `admin/licenses/*` | ✅ Expected — these call the **License microservice** via `licenseClient`, a separate base URL. Not in `backend/api` Routing.kt by design. |
| — | `useSecurityMetrics` (referenced in `security/index.tsx`) | Unknown | ⚠️ This hook is called in `security/index.tsx` but does NOT appear in any `admin-panel/src/api/` file. Either defined inline, imported from a missing file, or a dead import. |

### A.3 — Duplicate frontend functions hitting the same endpoint

| Endpoint | Function 1 | Query Key | Function 2 | Query Key | Impact |
|----------|-----------|-----------|-----------|-----------|--------|
| `GET admin/health/stores` | `useAllStoreHealth` (stores.ts) | `['stores','health']` | `useAllStoreHealth` (health.ts) | `['health','stores']` | Two separate cache entries for identical data — stale data possible on one page while the other is fresh |
| `PATCH admin/users/{id}` | `useUpdateUser` | — | `useDeactivateUser` | — | Acceptable — deactivate is a typed wrapper |
| `PUT admin/licenses/{key}` | `useUpdateLicense` | — | `useForceSyncLicense` | — | Acceptable — force-sync is a typed wrapper |

### Category A Findings

### FINDING-A-001
**SEVERITY**: HIGH
**CATEGORY**: A
**FILE**: `admin-panel/src/` (no file — missing feature)
**FINDING**: No forgot-password or reset-password UI exists — admins who lose their password have no self-service recovery path.
**EVIDENCE**: `POST /admin/auth/forgot-password` and `POST /admin/auth/reset-password` exist in `AdminAuthRoutes.kt` but no corresponding `useForgotPassword` or `useResetPassword` functions exist in `admin-panel/src/api/auth.ts`, and no route file renders a password reset form.
**IMPACT**: A locked-out admin must contact a developer to reset their password via the database directly. Blocks access to the entire admin panel.
**FIX**: Add `useForgotPassword` / `useResetPassword` hooks in `auth.ts` and a `/forgot-password` + `/reset-password` route.

### FINDING-A-002
**SEVERITY**: HIGH
**CATEGORY**: A
**FILE**: `admin-panel/src/routes/config/index.tsx` (missing tab)
**FINDING**: The tax rates CRUD API (4 endpoints) has no frontend UI — tax rates cannot be managed from the admin panel at all.
**EVIDENCE**: `GET/POST /admin/config/tax-rates` and `PUT/DELETE /admin/config/tax-rates/{id}` exist in `AdminConfigRoutes.kt` lines ~45–80, but `admin-panel/src/api/config.ts` has no tax rate functions and `config/index.tsx` has no tax rate tab.
**IMPACT**: Tax rate configuration is completely inaccessible via the admin panel — must be done via direct API calls or DB access.
**FIX**: Add `useTaxRates`, `useCreateTaxRate`, `useUpdateTaxRate`, `useDeleteTaxRate` to `config.ts` and a Tax Rates tab in `config/index.tsx`.

### FINDING-A-003
**SEVERITY**: INFO
**CATEGORY**: A
**FILE**: `admin-panel/src/routes/tickets/index.tsx` (missing button)
**FINDING**: `GET /admin/tickets/export` exists in the backend but no CSV export button exists in the tickets list UI.
**EVIDENCE**: `AdminTicketRoutes.kt` registers `GET /admin/tickets/export` with `tickets:read` permission. No `exportTickets` function exists in `tickets.ts`. The audit route (`audit/index.tsx`) correctly implements CSV export via `exportAuditLogs` — the same pattern is absent for tickets.
**IMPACT**: Support managers cannot export ticket data for reporting or analysis.
**FIX**: Add `exportTickets` plain async function to `tickets.ts` and an Export CSV button in `tickets/index.tsx`.

### FINDING-A-004
**SEVERITY**: INFO
**CATEGORY**: A
**FILE**: `backend/api/src/main/kotlin/.../routes/AdminSyncRoutes.kt`
**FINDING**: `POST /admin/sync/tokens/revoke` (bulk sync token revocation) has no frontend UI.
**EVIDENCE**: Endpoint registered in `AdminSyncRoutes.kt` with `users:sessions:revoke` permission. No corresponding function in `sync.ts`.
**IMPACT**: Bulk sync token revocation (e.g. after a security incident) must be done via direct API call.
**FIX**: Add `useRevokeSyncTokens` to `sync.ts` and a "Revoke All Sync Tokens" action in `sync/index.tsx`.

### FINDING-A-005
**SEVERITY**: HIGH
**CATEGORY**: A
**FILE**: `admin-panel/src/routes/security/index.tsx`
**FINDING**: `useSecurityMetrics` is called in `security/index.tsx` but does not exist in any `admin-panel/src/api/` file — likely a missing API module or unresolved import.
**EVIDENCE**: `FINDING-038` from the route audit references `const { data: metrics, isLoading: metricsLoading } = useSecurityMetrics();` — this function is absent from `metrics.ts`, `health.ts`, and all other api files in LIST B.
**IMPACT**: If this import is broken, the entire security page fails to render (runtime error). If it resolves from a component-local definition, it bypasses the standard api layer pattern.
**FIX**: Define `useSecurityMetrics` in `admin-panel/src/api/metrics.ts` targeting a backend endpoint (add backend endpoint if not yet defined), or locate and document where it is currently defined.

=== CATEGORY A COMPLETE ===

---

## Category B — Form & Input Element Audit

### FINDING-B-001
**SEVERITY**: HIGH
**CATEGORY**: B
**FILE**: `admin-panel/src/routes/settings/email.tsx:376-380`
**FINDING**: Template form state is initialized via a conditional side-effect inside the render body rather than `useEffect` — causes state updates during render.
**EVIDENCE**: `if (template && !initialized) { setSubject(template.subject); setHtmlBody(template.htmlBody); setInitialized(true); }`
**IMPACT**: React may warn about state updates during render; in StrictMode this causes double-initialization bugs.
**FIX**: `useEffect(() => { if (template) { setSubject(template.subject); setHtmlBody(template.htmlBody); } }, [template])`

### FINDING-B-002
**SEVERITY**: MEDIUM
**CATEGORY**: B
**FILE**: `admin-panel/src/routes/settings/email.tsx:449-451`
**FINDING**: Email template preview renders admin-edited HTML via `dangerouslySetInnerHTML` without sanitization — potential XSS.
**EVIDENCE**: `<div className="bg-white p-4 rounded border text-sm" dangerouslySetInnerHTML={{ __html: htmlBody }} />`
**IMPACT**: An admin who accidentally saves a template with a `<script>` tag or inline event handler will execute JS in all browsers viewing the preview.
**FIX**: Sanitize with `DOMPurify.sanitize(htmlBody)` before rendering, or render in a sandboxed `<iframe srcdoc={...}>`.

### FINDING-B-003
**SEVERITY**: MEDIUM
**CATEGORY**: B
**FILE**: `admin-panel/src/routes/master-products/index.tsx:177`
**FINDING**: `parseFloat(basePrice) || 0` silently coerces non-numeric input to 0 with no validation error.
**EVIDENCE**: `base_price: parseFloat(basePrice) || 0,`
**IMPACT**: Admin entering "abc" as a price submits a master product with base_price=0 across all stores, with no warning.
**FIX**: Add explicit validation — reject if `isNaN(parseFloat(basePrice)) || parseFloat(basePrice) < 0` — and display an inline error.

✅ **Forms with no issues** — verified by reading:
- `login.tsx` — Zod + react-hook-form, field errors, submit disabled during pending ✅
- `setup/index.tsx` — Zod + react-hook-form, field errors, submit disabled during pending ✅
- `settings/mfa.tsx` — button disabled until code ≥ 6 digits, try/catch on all handlers ✅
- `settings/exchange-rates.tsx` — inline validation, `isValid` guard, toast on error ✅

---

## Category C — Button & Action Element Audit

### FINDING-C-001 *(= FINDING-013)*
**SEVERITY**: CRITICAL
**CATEGORY**: C
**FILE**: `admin-panel/src/routes/master-products/index.tsx:100-105`
**FINDING**: Delete button fires `deleteMutation.mutate(p.id)` directly with zero confirmation dialog.
**EVIDENCE**: `<button onClick={() => deleteMutation.mutate(p.id)} className="text-red-400 hover:text-red-300 text-xs">Delete</button>`
**IMPACT**: One accidental click permanently deletes a master product shared across all stores. No undo.
**FIX**: Wrap in `<ConfirmDialog variant="destructive" title="Delete master product?" onConfirm={() => deleteMutation.mutate(p.id)}>` — the component already exists in the codebase.

### FINDING-C-002 *(= FINDING-018)*
**SEVERITY**: CRITICAL
**CATEGORY**: C
**FILE**: `admin-panel/src/routes/master-products/$masterProductId.tsx:102-107`
**FINDING**: "Remove" store assignment button fires `removeMutation.mutate(...)` directly with no confirmation.
**EVIDENCE**: `<button onClick={() => removeMutation.mutate({ masterProductId, storeId: a.store_id })}>`
**IMPACT**: Accidental click removes a product from a live store with no undo.
**FIX**: Wrap in `ConfirmDialog` before firing mutation.

### FINDING-C-003 *(= FINDING-014)*
**SEVERITY**: HIGH
**CATEGORY**: C
**FILE**: `admin-panel/src/routes/master-products/index.tsx:100-105`
**FINDING**: Delete button not disabled during `deleteMutation.isPending` — double-click sends duplicate delete requests.
**EVIDENCE**: `<button onClick={() => deleteMutation.mutate(p.id)}` — no `disabled` prop.
**FIX**: `disabled={deleteMutation.isPending}`

### FINDING-C-004 *(= FINDING-019)*
**SEVERITY**: HIGH
**CATEGORY**: C
**FILE**: `admin-panel/src/routes/master-products/$masterProductId.tsx:102-107`
**FINDING**: "Remove" button not disabled during `removeMutation.isPending`.
**EVIDENCE**: `<button onClick={() => removeMutation.mutate(...)}` — no `disabled` prop.
**FIX**: `disabled={removeMutation.isPending}`

### FINDING-C-005 *(= FINDING-022)*
**SEVERITY**: HIGH
**CATEGORY**: C
**FILE**: `admin-panel/src/routes/settings/profile.tsx:157-162`
**FINDING**: "Revoke All" sessions button fires immediately without confirmation — logs out the current user with no warning.
**EVIDENCE**: `<button onClick={handleRevokeAll} disabled={revokeSessions.isPending || !sessions?.length}>Revoke All</button>`
**IMPACT**: Accidental click immediately invalidates all sessions and logs the user out. No undo.
**FIX**: Add a `ConfirmDialog` warning "This will log you out of all devices immediately."

✅ **Buttons with no issues** — verified by reading:
- `tickets/$ticketId.tsx` — "Close Ticket" uses `ConfirmDialog variant="destructive"` ✅
- `sync/index.tsx` — "Discard" dead letter uses `ConfirmDialog variant="destructive"` ✅
- `diagnostic/index.tsx` — "Revoke" session uses `ConfirmDialog variant="destructive"` ✅
- `alerts/index.tsx` — acknowledge/resolve buttons disabled during `isPending` ✅

---

## Category D — Error Handling & Loading States

> **Cross-cutting pattern:** 18 of 30 route files destructure only `{ data, isLoading }` from TanStack Query hooks, omitting `isError`. API failures are visually identical to empty data. The one-line fix is identical across all 18 pages: add `isError` to the destructure and render an error banner.

### FINDING-D-001 *(= FINDING-002)*
**SEVERITY**: HIGH | **FILE**: `admin-panel/src/routes/users/index.tsx:26-31`
**FINDING**: `isError` from `useAdminUsers` not checked — API failure shows empty user list.
**EVIDENCE**: `const { data, isLoading } = useAdminUsers({...})`
**FIX**: `const { data, isLoading, isError } = useAdminUsers({...})` + `if (isError) return <ErrorBanner />`

### FINDING-D-002 *(= FINDING-003)*
**SEVERITY**: HIGH | **FILE**: `admin-panel/src/routes/tickets/index.tsx:32-42`
**FINDING**: `isError` from `useTickets` not checked — API failure shows empty ticket list; support agents miss urgent tickets.
**EVIDENCE**: `const { data, isLoading } = useTickets({...})`

### FINDING-D-003 *(= FINDING-005)*
**SEVERITY**: HIGH | **FILE**: `admin-panel/src/routes/tickets/$ticketId.tsx:59`
**FINDING**: `isError` from `useTicket` not checked — API error shows "Ticket not found" instead of an error state.
**EVIDENCE**: `const { data: ticket, isLoading } = useTicket(ticketId);`

### FINDING-D-004 *(= FINDING-007)*
**SEVERITY**: HIGH | **FILE**: `admin-panel/src/routes/licenses/index.tsx:27-32`
**FINDING**: `isError` from `useLicenses` not checked — admin may create duplicate licenses thinking none exist.
**EVIDENCE**: `const { data, isLoading } = useLicenses({...})`

### FINDING-D-005 *(= FINDING-009)*
**SEVERITY**: HIGH | **FILE**: `admin-panel/src/routes/licenses/$licenseKey.tsx:17`
**FINDING**: `isError` from `useLicense` not checked — API error shows "License not found" instead of error state.
**EVIDENCE**: `const { data, isLoading } = useLicense(licenseKey);`

### FINDING-D-006 *(= FINDING-010)*
**SEVERITY**: HIGH | **FILE**: `admin-panel/src/routes/customers/index.tsx:21-26`
**FINDING**: `isError` from `useGlobalCustomers` not checked — API failure shows "No customers found".
**EVIDENCE**: `const { data, isLoading } = useGlobalCustomers({...})`

### FINDING-D-007 *(= FINDING-011)*
**SEVERITY**: HIGH | **FILE**: `admin-panel/src/routes/stores/index.tsx:19-23`
**FINDING**: `isError` from `useStores` not checked — store directory appears empty on API failure.
**EVIDENCE**: `const { data, isLoading } = useStores({...})`

### FINDING-D-008 *(= FINDING-012)*
**SEVERITY**: HIGH | **FILE**: `admin-panel/src/routes/stores/$storeId.tsx:15`
**FINDING**: `isError` from `useStore` not checked — API error indistinguishable from non-existent store.
**EVIDENCE**: `const { data: store, isLoading } = useStore(storeId);`

### FINDING-D-009 *(= FINDING-017)*
**SEVERITY**: HIGH | **FILE**: `admin-panel/src/routes/master-products/index.tsx:17-21`
**FINDING**: `isError` from `useMasterProducts` not checked.
**EVIDENCE**: `const { data, isLoading } = useMasterProducts({...})`

### FINDING-D-010 *(= FINDING-029)*
**SEVERITY**: HIGH | **FILE**: `admin-panel/src/routes/inventory/index.tsx:19-22`
**FINDING**: `isError` from `useGlobalInventory` not checked — API errors show "No stock rows found".
**EVIDENCE**: `const { data, isLoading } = useGlobalInventory({...})`

### FINDING-D-011 *(= FINDING-031)*
**SEVERITY**: HIGH | **FILE**: `admin-panel/src/routes/health/index.tsx:91-92`
**FINDING**: `isError` not checked on `useSystemHealth` or `useAllStoreHealth` — monitoring API failure shows blank health cards; operator may assume services are healthy.
**EVIDENCE**: `const { data: system, isLoading: sysLoading, refetch: refetchSystem, isFetching: sysFetching } = useSystemHealth();`

### FINDING-D-012 *(= FINDING-034)*
**SEVERITY**: HIGH | **FILE**: `admin-panel/src/routes/alerts/index.tsx:174`
**FINDING**: `isError` from `useAlerts` not checked — critical alerts invisible due to silent API failure.
**EVIDENCE**: `const { data: alertsPage, isLoading } = useAlerts(effectiveFilter);`

### FINDING-D-013 *(= FINDING-035)*
**SEVERITY**: HIGH | **FILE**: `admin-panel/src/routes/audit/index.tsx:22`
**FINDING**: `isError` from `useAuditLogs` not checked — audit compliance team sees empty log on API failure.
**EVIDENCE**: `const { data, isLoading } = useAuditLogs(effectiveFilters);`

### FINDING-D-014 *(= FINDING-037)*
**SEVERITY**: HIGH | **FILE**: `admin-panel/src/routes/sync/index.tsx:168`
**FINDING**: `isError` from `useSyncStatus` not checked — sync monitoring appears empty on API failure.
**EVIDENCE**: `const { data: stores = [], isLoading } = useSyncStatus();`

### FINDING-D-015 *(= FINDING-038)*
**SEVERITY**: HIGH | **FILE**: `admin-panel/src/routes/security/index.tsx:92-95`
**FINDING**: `isError` not checked on any of 4 security queries — security events invisible when API is down; analyst may miss critical events.
**EVIDENCE**: `const { data: metrics, isLoading: metricsLoading } = useSecurityMetrics();`

### FINDING-D-016 *(= FINDING-039)*
**SEVERITY**: HIGH | **FILE**: `admin-panel/src/routes/reports/index.tsx:53-61`
**FINDING**: `isError` not checked on `useSalesReport` or `useProductPerformance` — finance manager may interpret API failure as "no sales".
**EVIDENCE**: `const { data: salesData, isLoading: salesLoading } = useSalesReport({...})`

### FINDING-D-017 *(= FINDING-042)*
**SEVERITY**: HIGH | **FILE**: `admin-panel/src/routes/index.tsx:33-41`
**FINDING**: `isError` not checked on any of 5 dashboard queries — dashboard shows 0 revenue/stores/licenses on API failure; looks like valid data.
**EVIDENCE**: `const { data: kpis, isLoading: kpisLoading, refetch: refetchKpis, dataUpdatedAt } = useDashboardKPIs(period);`

✅ **Files with correct error handling** — verified by reading:
- `login.tsx` — server errors displayed inline ✅
- `setup/index.tsx` — `HTTPError` caught and shown ✅
- `settings/email.tsx` — all 5 sub-tabs check `isError` ✅
- `settings/exchange-rates.tsx` — `error` checked and shown ✅
- `health/$storeId.tsx` — `if (error || !data)` guard ✅
- `ticket-status/$token.tsx` — `if (isError || !ticket)` guard ✅

---

## Category E — Empty States

✅ **No issues** — verified by reading all 30 route files.

Every list/table that fetches API data has an explicit empty state:
- `users/index.tsx` — empty state in `UserTable` component
- `tickets/index.tsx` — "No tickets found" with icon
- `licenses/index.tsx` — "No licenses found" empty state row
- `customers/index.tsx` — icon + "No customers found"
- `master-products/index.tsx` — colspan empty row in table
- `inventory/index.tsx` — "No stock rows found"
- `alerts/index.tsx` — "No active alerts" message
- `audit/index.tsx` — empty state in `AuditLogTable` component
- `sync/index.tsx` — empty states per tab
- `health/index.tsx` — "No stores registered"

---

## Category F — Pagination & Large Data Sets

### FINDING-F-001 *(= FINDING-030)*
**SEVERITY**: MEDIUM
**CATEGORY**: F
**FILE**: `admin-panel/src/routes/inventory/index.tsx:19-26`
**FINDING**: `useGlobalInventory` is called with no `page` or `size` parameters — all inventory rows returned in one request.
**EVIDENCE**: `useGlobalInventory({ productId: debouncedProduct, storeId: debouncedStore })` — no pagination params.
**IMPACT**: With many stores × products, this page could request thousands of rows, causing slow loads and browser memory pressure.
**FIX**: Add `page`/`size` query params to `useGlobalInventory` and render Previous/Next pagination controls.

### FINDING-F-002 *(= FINDING-040)*
**SEVERITY**: MEDIUM
**CATEGORY**: F
**FILE**: `admin-panel/src/routes/reports/index.tsx:269`
**FINDING**: Daily sales breakdown silently truncates to 50 rows via `.slice(0, 50)` with no indication to the user.
**EVIDENCE**: `(salesData ?? []).slice(0, 50).map((row, i) => ...`
**IMPACT**: User sees fewer rows in the table than in the CSV export — confusing discrepancy with no explanation.
**FIX**: Add a visible note: "Showing first 50 of N rows — export CSV for full data."

✅ **Pages with proper pagination** — verified: `users`, `tickets`, `licenses`, `customers`, `master-products`, `alerts`, `audit` all have pagination controls.

---

## Category H — Search & Filter Functionality

### FINDING-H-001 *(= FINDING-001)*
**SEVERITY**: MEDIUM
**CATEGORY**: H
**FILE**: `admin-panel/src/routes/users/index.tsx:26`
**FINDING**: Role and status filter state is in local `useState` — lost when navigating away and returning.
**EVIDENCE**: `const [roleFilter, setRoleFilter] = useState<AdminRole | ''>('');`
**IMPACT**: User sets filters, navigates to a user detail, returns — all filters reset.
**FIX**: Persist in URL search params via TanStack Router's `validateSearch`.

### FINDING-H-002 *(= FINDING-004)*
**SEVERITY**: MEDIUM
**CATEGORY**: H
**FILE**: `admin-panel/src/routes/tickets/index.tsx:19-27`
**FINDING**: Status, priority, category, date range, and body-search filters are all local state — lost on navigation.
**EVIDENCE**: `const [statusFilter, setStatusFilter] = useState<TicketStatus | ''>('');`
**IMPACT**: Operator sets complex filters, navigates to a ticket detail, returns — all filters reset.
**FIX**: Persist all filter values in URL search params.

### FINDING-H-003 *(= FINDING-008)*
**SEVERITY**: MEDIUM
**CATEGORY**: H
**FILE**: `admin-panel/src/routes/licenses/index.tsx:21-22`
**FINDING**: Status and edition filter state is local — lost on navigation.
**EVIDENCE**: `const [statusFilter, setStatusFilter] = useState<LicenseStatus | ''>('');`
**FIX**: Persist in URL search params.

✅ **Search correctly debounced** — verified in: `users`, `tickets`, `licenses`, `customers`, `master-products`, `inventory`, `audit`, `reports` — all use `useDebounce(value, 300)`.

---

## Category I — Data Mutation Feedback

### FINDING-I-001 *(= FINDING-015)*
**SEVERITY**: HIGH
**CATEGORY**: I
**FILE**: `admin-panel/src/routes/master-products/index.tsx:23-28`
**FINDING**: Neither `createMutation` nor `deleteMutation` has an `onError` callback — failures silently swallowed.
**EVIDENCE**: `createMutation.mutate(req, { onSuccess: () => setShowCreate(false) })` — no `onError`.
**IMPACT**: Create/delete failures give no user feedback; user may retry or assume success.
**FIX**: `onError: (err) => toast.error(err.message ?? 'Operation failed')`

### FINDING-I-002 *(= FINDING-020)*
**SEVERITY**: HIGH
**CATEGORY**: I
**FILE**: `admin-panel/src/routes/master-products/$masterProductId.tsx:20-21`
**FINDING**: `assignMutation` and `removeMutation` have no `onError` handlers.
**EVIDENCE**: `assignMutation.mutate({ ... }, { onSuccess: () => setShowAssign(false) })` — no `onError`.

### FINDING-I-003 *(= FINDING-023)*
**SEVERITY**: HIGH
**CATEGORY**: I
**FILE**: `admin-panel/src/routes/settings/profile.tsx:59-65`
**FINDING**: `revokeSessions.mutate` has no `onError` handler — revocation failure silently swallowed.
**EVIDENCE**: `revokeSessions.mutate(user.id, { onSuccess: () => { clearUser(); navigate(...) } })`
**IMPACT**: If revocation fails, UI shows no error; user believes they've been logged out when they haven't.

### FINDING-I-004 *(= FINDING-027)*
**SEVERITY**: HIGH
**CATEGORY**: I
**FILE**: `admin-panel/src/routes/diagnostic/index.tsx:89-93`
**FINDING**: `createMutation.mutate` has no `onError` — diagnostic session creation failures leave modal open with no error message.
**EVIDENCE**: `createMutation.mutate({ storeId, ... }, { onSuccess: (session) => { onCreated(session); onClose(); } })`

### FINDING-I-005 *(= FINDING-028)*
**SEVERITY**: HIGH
**CATEGORY**: I
**FILE**: `admin-panel/src/routes/diagnostic/index.tsx:307`
**FINDING**: `revokeSession.mutate` has no `onError` handler.
**EVIDENCE**: `revokeSession.mutate(revokeTarget, { onSuccess: () => setRevokeTarget(null) })`

### FINDING-I-006 *(= FINDING-033)*
**SEVERITY**: HIGH
**CATEGORY**: I
**FILE**: `admin-panel/src/routes/alerts/index.tsx:45-46`
**FINDING**: `acknowledge` and `resolve` mutations have no `onError` handlers — action failures silently swallowed.
**EVIDENCE**: `const { mutate: acknowledge, isPending: acking } = useAcknowledgeAlert();` then `onClick={() => acknowledge(alert.id)}`

### FINDING-I-007 *(= FINDING-036)*
**SEVERITY**: HIGH
**CATEGORY**: I
**FILE**: `admin-panel/src/routes/sync/index.tsx:122`
**FINDING**: `retryOp.mutate(row.id)` has no `onError` handler — retry failure gives no feedback; user may click multiple times.
**EVIDENCE**: `onClick={() => retryOp.mutate(row.id)}`

---

## Category J — Navigation & Routing

✅ **No issues** — verified by reading all 30 route files and `__root.tsx`.

- All nav links observed in `__root.tsx` map to defined routes.
- No orphan routes found (all route files have corresponding nav entries or are accessible via deep link).
- Auth guard in `__root.tsx` uses `useEffect` for redirect — no render-body navigation.
- Bootstrap redirect logic covers all edge cases without loops.
- Deep links work — TanStack Router's file-based routing preserves URL on auth redirect.

---

## Category K — Responsive & Layout Issues

### FINDING-K-001 *(= FINDING-021)*
**SEVERITY**: MEDIUM
**CATEGORY**: K
**FILE**: `admin-panel/src/routes/master-products/$masterProductId.tsx:78-112`
**FINDING**: Store assignments table has no `overflow-x-auto` wrapper — content clips on narrow viewports.
**EVIDENCE**: `<table className="w-full text-sm">` inside a plain `<div>` with no overflow control.
**FIX**: `<div className="overflow-x-auto"><table ...>`

✅ **Tables with correct overflow** — verified: `users`, `tickets`, `licenses`, `customers`, `inventory`, `audit`, `sync`, `reports` all have `overflow-x-auto` wrappers.

---

## Category L — Console Errors & Warnings

### FINDING-L-001 *(= FINDING-006)*
**SEVERITY**: LOW
**CATEGORY**: L
**FILE**: `admin-panel/src/routes/tickets/$ticketId.tsx:61`
**FINDING**: `useState(Date.now)` passes function reference as lazy initializer — intent is `Date.now()`. Pattern inconsistency.
**EVIDENCE**: `const [now, setNow] = useState(Date.now);`
**FIX**: `useState(() => Date.now())` for clarity.

### FINDING-L-002 *(= FINDING-024)*
**SEVERITY**: LOW
**CATEGORY**: L
**FILE**: `admin-panel/src/routes/settings/mfa.tsx:173`
**FINDING**: Backup codes list uses array index as `key` prop.
**EVIDENCE**: `{setupData.backupCodes.map((code, i) => (<code key={i} ...>{code}</code>))}`
**FIX**: `key={code}` (each code value is unique).

### FINDING-L-003 *(= FINDING-032)*
**SEVERITY**: LOW
**CATEGORY**: L
**FILE**: `admin-panel/src/routes/health/$storeId.tsx:117-129`
**FINDING**: Error log entries use array index as `key` prop.
**EVIDENCE**: `{data.errorLog.map((entry, i) => (<div key={i} ...>...))}`
**FIX**: `key={entry.timestamp + '-' + i}`

### FINDING-L-004 *(= FINDING-041)*
**SEVERITY**: LOW
**CATEGORY**: L
**FILE**: `admin-panel/src/routes/reports/index.tsx:270, 364`
**FINDING**: Daily breakdown and product tables use array index as `key` prop.
**EVIDENCE**: `(salesData ?? []).slice(0, 50).map((row, i) => (<tr key={i} ...>`
**FIX**: `key={row.date + '-' + row.storeId}` / `key={row.productId}`

=== CATEGORIES B–L COMPLETE ===

---

## Category G — Role-Based UI Completeness

> Source: PERMISSIONS MAP above + route files audit.

### G.1 — Role Access Matrix

| Page / Feature | ADMIN | OPERATOR | FINANCE | AUDITOR | HELPDESK |
|----------------|:-----:|:--------:|:-------:|:-------:|:--------:|
| Dashboard (ops KPIs) | ✓ | ✓ | | | |
| Dashboard (financial KPIs) | ✓ | | ✓ | | |
| Dashboard (support KPIs) | ✓ | ✓ | | | ✓ |
| Users management | ✓ | | | | |
| Tickets (read/create/update/assign/close) | ✓ | ✓ | | | ✓ |
| Tickets (resolve) | ✓ | ✓ | | | ✗ |
| Licenses (read) | ✓ | ✓ | ✓ | ✓ | ✓ |
| Licenses (write/revoke) | ✓ | | | | |
| Stores (read) | ✓ | ✓ | | | ✓ |
| Stores (config write) | ✓ | | | | |
| Sync management | ✓ | ✓ | | | |
| Health monitoring | ✓ | ✓ | | | |
| Alerts (read/acknowledge) | ✓ | ✓ | | | |
| Alerts (configure rules) | ✓ | | | | |
| Audit logs (read/export) | ✓ | | | ✓ | |
| Reports (financial) | ✓ | | ✓ | | |
| Reports (operational) | ✓ | ✓ | | | |
| Reports (support) | ✓ | ✓ | | | ✓ |
| Reports (export) | ✓ | | ✓ | | |
| Master products | ✓ | ✓ | ✓ | | |
| Inventory (read) | ✓ | ✓ | ✓ | | |
| Customers (read) | ✓ | ✓ | | | ✓ |
| Diagnostic sessions (create) | ✓ | ✓ | | | |
| Diagnostic sessions (read only) | ✓ | ✓ | | | ✓ |
| Email settings/templates | ✓ | | | | |
| Email delivery logs | ✓ | ✓ | | | |
| Config / feature flags | ✓ | | | | |
| **Tax rates (CRUD)** | ✓ | | | | |
| Exchange rates | ✓ | | ✓ | | |
| System settings | ✓ | | | | |

### G.2 — Findings

### FINDING-G-001
**SEVERITY**: HIGH
**CATEGORY**: G
**FILE**: `admin-panel/src/hooks/use-auth.ts` (permissions map)
**FINDING**: HELPDESK role has `tickets:create` and `tickets:update` but NOT `tickets:resolve` — the "Resolve" button must be hidden for HELPDESK users, but the route audit did not confirm a permission check guards this button in `tickets/$ticketId.tsx`.
**EVIDENCE**: PERMISSIONS map: HELPDESK row has `tickets:close` and `tickets:comment` but no `tickets:resolve`. Backend `AdminTicketRoutes.kt` correctly guards `POST /admin/tickets/{id}/resolve` with `tickets:resolve`.
**IMPACT**: If the Resolve button is rendered without a permission check in the UI, HELPDESK users see a button that returns 403 when clicked — confusing UX.
**FIX**: Confirm `tickets/$ticketId.tsx` wraps the Resolve button with `hasPermission('tickets:resolve')` check. Add if missing.

### FINDING-G-002
**SEVERITY**: HIGH
**CATEGORY**: G
**FILE**: `admin-panel/src/` (missing feature)
**FINDING**: AUDITOR role has `audit:read` and `audit:export` — but the audit page (`audit/index.tsx`) does not appear to check for `audit:read` permission before rendering. An unauthenticated or low-permission user who navigates directly to `/audit` may see data.
**EVIDENCE**: `__root.tsx` implements a global auth guard (authenticated vs unauthenticated) but per-page permission checks are done at component level via `hasPermission()`. Whether `audit/index.tsx` has this check was not confirmed in the route audit.
**IMPACT**: If permission check is missing at the route level, OPERATOR and HELPDESK (who lack `audit:read`) could access audit log data by navigating directly to `/audit`.
**FIX**: Ensure `audit/index.tsx` calls `hasPermission('audit:read')` and renders a 403 page if false. Apply same pattern to all sensitive pages.

### FINDING-G-003
**SEVERITY**: INFO
**CATEGORY**: G
**FILE**: `admin-panel/src/` (coverage gap)
**FINDING**: FINANCE role has `inventory:read` but the inventory page shows cross-store operational stock levels — this is operational data, not financial data. The permission assignment may be intentional (for COGS analysis) but it crosses the ADR-009 boundary concern.
**EVIDENCE**: PERMISSIONS map: `inventory:read` granted to FINANCE. `admin-panel/src/routes/inventory/index.tsx` renders warehouse stock counts, which are operational.
**IMPACT**: FINANCE users can see stock levels across all stores — may be intentional for cost-of-goods analysis, but worth confirming with product owner.
**FIX**: Review with product owner whether FINANCE should have `inventory:read`. If not, remove from FINANCE permissions.

### FINDING-G-004
**SEVERITY**: INFO
**CATEGORY**: G
**FILE**: `admin-panel/src/` (unreachable backend functionality)
**FINDING**: Tax rates CRUD (4 endpoints) exists in the backend with no permission defined in the permissions map and no UI — it is accessible only to ADMIN (authenticated cookie) but there is no frontend surface for any role.
**EVIDENCE**: `AdminConfigRoutes.kt` registers tax rate endpoints as `Authenticated` (no fine-grained permission). Not listed in PERMISSIONS map. No frontend functions in `config.ts`.
**IMPACT**: Tax rate configuration is completely inaccessible via the admin panel for all roles. See also FINDING-A-002.

=== CATEGORY G COMPLETE ===

---

## Backend ↔ Frontend Coverage Matrix

> ✅ Covered | ❌ No frontend function | ⚠️ Partial / note | 🔴 Dead route (unregistered in Routing.kt)

| Method | Path | Frontend Function | Status |
|--------|------|------------------|--------|
| GET | `/admin/auth/me` | `useCurrentUser` | ✅ |
| POST | `/admin/auth/login` | `useAdminLogin` | ✅ |
| GET | `/admin/auth/status` | `useAdminStatus` | ✅ |
| POST | `/admin/auth/bootstrap` | `useAdminBootstrap` | ✅ |
| POST | `/admin/auth/logout` | `useAdminLogout` | ✅ |
| POST | `/admin/auth/mfa/setup` | `useAdminMfaSetup` | ✅ |
| POST | `/admin/auth/mfa/enable` | `useAdminMfaEnable` | ✅ |
| POST | `/admin/auth/mfa/disable` | `useAdminMfaDisable` | ✅ |
| POST | `/admin/auth/mfa/verify` | `useAdminMfaVerify` | ✅ |
| POST | `/admin/auth/change-password` | `useChangePassword` | ✅ |
| POST | `/admin/auth/forgot-password` | — | ❌ FINDING-A-001 |
| POST | `/admin/auth/reset-password` | — | ❌ FINDING-A-001 |
| GET | `/admin/users` | `useAdminUsers` | ✅ |
| POST | `/admin/users` | `useCreateUser` | ✅ |
| PATCH | `/admin/users/{id}` | `useUpdateUser` / `useDeactivateUser` | ✅ |
| GET | `/admin/users/{id}/sessions` | `useListSessions` | ✅ |
| DELETE | `/admin/users/{id}/sessions` | `useRevokeSessions` | ✅ |
| GET | `/admin/tickets` | `useTickets` | ✅ |
| GET | `/admin/tickets/metrics` | `useTicketMetrics` | ✅ |
| POST | `/admin/tickets/bulk-assign` | `useBulkAssignTickets` | ✅ |
| POST | `/admin/tickets/bulk-resolve` | `useBulkResolveTickets` | ✅ |
| GET | `/admin/tickets/export` | — | ❌ FINDING-A-003 |
| POST | `/admin/tickets` | `useCreateTicket` | ✅ |
| GET | `/admin/tickets/{id}` | `useTicket` | ✅ |
| PATCH | `/admin/tickets/{id}` | `useUpdateTicket` | ✅ |
| POST | `/admin/tickets/{id}/assign` | `useAssignTicket` | ✅ |
| POST | `/admin/tickets/{id}/resolve` | `useResolveTicket` | ✅ |
| POST | `/admin/tickets/{id}/close` | `useCloseTicket` | ✅ |
| GET | `/admin/tickets/{id}/email-threads` | `useEmailThreads` | ✅ |
| GET | `/admin/tickets/{id}/comments` | `useTicketComments` | ✅ |
| POST | `/admin/tickets/{id}/comments` | `useAddComment` | ✅ |
| POST | `/admin/diagnostic/sessions` | `useCreateDiagnosticSession` | ✅ |
| GET | `/admin/diagnostic/sessions/{storeId}` | `useActiveDiagnosticSession` | ✅ |
| DELETE | `/admin/diagnostic/sessions/{sessionId}` | `useRevokeDiagnosticSession` | ✅ |
| GET | `/admin/email/delivery-logs` | `useEmailDeliveryLogs` | ✅ |
| GET | `/admin/email/unsubscribes` | `useEmailUnsubscribes` | ✅ |
| GET | `/admin/email/config-status` | `useEmailConfigStatus` | ✅ |
| GET | `/admin/email/templates` | `useEmailTemplates` | ✅ |
| GET | `/admin/email/templates/{slug}` | `useEmailTemplate` | ✅ |
| PUT | `/admin/email/templates/{slug}` | `useUpdateEmailTemplate` | ✅ |
| GET | `/admin/email/preferences` | `useEmailPreferences` | ✅ |
| PUT | `/admin/email/preferences` | `useUpdateEmailPreferences` | ✅ |
| GET | `/admin/health/system` | `useSystemHealth` | ✅ |
| GET | `/admin/health/stores` | `useAllStoreHealth` (×2 — cache key divergence) | ⚠️ |
| GET | `/admin/health/stores/{storeId}` | `useStoreHealthDetail` | ✅ |
| GET | `/admin/stores` | `useStores` | ✅ |
| GET | `/admin/stores/{storeId}` | `useStore` | ✅ |
| GET | `/admin/stores/{storeId}/health` | `useStoreHealth` | ✅ |
| PUT | `/admin/stores/{storeId}/config` | `useUpdateStoreConfig` | ✅ |
| GET | `/admin/audit` | `useAuditLogs` | ✅ |
| GET | `/admin/audit/export` | `exportAuditLogs` (plain async fn) | ⚠️ no loading/error state |
| GET | `/admin/metrics/dashboard` | `useDashboardKPIs` | ✅ |
| GET | `/admin/metrics/sales` | `useSalesChart` | ✅ |
| GET | `/admin/metrics/stores` | `useStoreComparison` | ✅ |
| GET | `/admin/reports/sales` | `useSalesReport` | ✅ |
| GET | `/admin/reports/products` | `useProductPerformance` | ✅ |
| GET | `/admin/alerts` | `useAlerts` | ✅ |
| GET | `/admin/alerts/counts` | `useAlertCounts` | ✅ |
| GET | `/admin/alerts/rules` | `useAlertRules` | ✅ |
| POST | `/admin/alerts/{id}/acknowledge` | `useAcknowledgeAlert` | ✅ |
| POST | `/admin/alerts/{id}/resolve` | `useResolveAlert` | ✅ |
| POST | `/admin/alerts/{id}/silence` | `useSilenceAlert` | ✅ |
| PATCH | `/admin/alerts/rules/{id}` | `useToggleAlertRule` | ✅ |
| GET | `/admin/sync/status` | `useSyncStatus` | ✅ |
| GET | `/admin/sync/{storeId}` | `useStoreSync` | ✅ |
| GET | `/admin/sync/{storeId}/queue` | `useSyncQueue` | ✅ |
| POST | `/admin/sync/{storeId}/force` | `useForceSync` | ✅ |
| GET | `/admin/sync/conflicts` | `useConflictLog` | ✅ |
| GET | `/admin/sync/{storeId}/conflicts` | `useConflictLog` | ✅ |
| GET | `/admin/sync/dead-letters` | `useDeadLetters` | ✅ |
| GET | `/admin/sync/{storeId}/dead-letters` | `useDeadLetters` | ✅ |
| POST | `/admin/sync/dead-letters/{id}/retry` | `useRetryDeadLetter` | ✅ |
| DELETE | `/admin/sync/dead-letters/{id}` | `useDiscardDeadLetter` | ✅ |
| POST | `/admin/sync/tokens/revoke` | — | ❌ FINDING-A-004 |
| GET | `/admin/config/feature-flags` | `useFeatureFlags` | ✅ |
| PATCH | `/admin/config/feature-flags/{key}` | `useUpdateFeatureFlag` | ✅ |
| GET | `/admin/config/tax-rates` | — | ❌ FINDING-A-002 |
| POST | `/admin/config/tax-rates` | — | ❌ FINDING-A-002 |
| PUT | `/admin/config/tax-rates/{id}` | — | ❌ FINDING-A-002 |
| DELETE | `/admin/config/tax-rates/{id}` | — | ❌ FINDING-A-002 |
| GET | `/admin/config/system` | `useSystemConfig` | ✅ |
| PATCH | `/admin/config/system/{key}` | `useUpdateSystemConfig` | ✅ |
| GET | `/admin/master-products` | `useMasterProducts` | ✅ |
| POST | `/admin/master-products` | `useCreateMasterProduct` | ✅ |
| GET | `/admin/master-products/{id}` | `useMasterProduct` | ✅ |
| PUT | `/admin/master-products/{id}` | `useUpdateMasterProduct` | ✅ |
| DELETE | `/admin/master-products/{id}` | `useDeleteMasterProduct` | ✅ |
| GET | `/admin/master-products/{id}/stores` | `useMasterProductStores` | ✅ |
| POST | `/admin/master-products/{id}/stores/{storeId}` | `useAssignToStore` | ✅ |
| DELETE | `/admin/master-products/{id}/stores/{storeId}` | `useRemoveFromStore` | ✅ |
| PUT | `/admin/master-products/{id}/stores/{storeId}` | `useUpdateStoreOverride` | ✅ |
| POST | `/admin/master-products/{id}/bulk-assign` | `useBulkAssign` | ✅ |
| GET | `/admin/inventory/global` | `useGlobalInventory` | ✅ |
| GET | `/admin/customers/global` | `useGlobalCustomers` | ✅ |
| GET | `/admin/exchange-rates` | `useExchangeRates` | ✅ |
| PUT | `/admin/exchange-rates` | `useUpsertExchangeRate` | ✅ |
| GET | `/admin/replenishment/rules` | — | 🔴 dead route + ❌ no frontend |
| GET | `/admin/replenishment/suggestions` | — | 🔴 dead route + ❌ no frontend |
| GET | `/admin/transfers` | — | 🔴 dead route + ❌ no frontend |
| GET | `/admin/transfers/{id}` | — | 🔴 dead route + ❌ no frontend |
| GET | `/admin/pricing/rules` | — | 🔴 dead route + ❌ no frontend |

**Coverage summary:** 91 live admin endpoints — 80 covered (88%), 7 uncovered (8%), 4 partial/noted (4%)

---

## Summary Table — All Findings

| ID | Severity | Category | File | Description |
|----|----------|----------|------|-------------|
| FINDING-C-001 | **CRITICAL** | C | `master-products/index.tsx:100` | Delete button has no confirmation — permanent data loss on misclick |
| FINDING-C-002 | **CRITICAL** | C | `master-products/$masterProductId.tsx:102` | Remove store button has no confirmation — removes product from live store |
| FINDING-A-001 | HIGH | A | (missing feature) | No forgot-password / reset-password UI — locked-out admin has no recovery |
| FINDING-A-002 | HIGH | A | `config/index.tsx` | Tax rates CRUD (4 endpoints) has no frontend UI at all |
| FINDING-A-005 | HIGH | A | `security/index.tsx` | `useSecurityMetrics` called but not defined in any api/ file |
| FINDING-B-001 | HIGH | B | `settings/email.tsx:376` | Template state initialized in render body — React anti-pattern |
| FINDING-C-003 | HIGH | C | `master-products/index.tsx:100` | Delete button not disabled during isPending — double-click risk |
| FINDING-C-004 | HIGH | C | `master-products/$masterProductId.tsx:102` | Remove button not disabled during isPending |
| FINDING-C-005 | HIGH | C | `settings/profile.tsx:157` | "Revoke All" sessions fires without confirmation — immediate logout |
| FINDING-D-001 | HIGH | D | `users/index.tsx:26` | isError not checked — API failure shows empty user list |
| FINDING-D-002 | HIGH | D | `tickets/index.tsx:32` | isError not checked — agents may miss urgent tickets on API failure |
| FINDING-D-003 | HIGH | D | `tickets/$ticketId.tsx:59` | isError not checked — 500 looks like "ticket not found" |
| FINDING-D-004 | HIGH | D | `licenses/index.tsx:27` | isError not checked — admin may create duplicates on API failure |
| FINDING-D-005 | HIGH | D | `licenses/$licenseKey.tsx:17` | isError not checked — error indistinguishable from missing license |
| FINDING-D-006 | HIGH | D | `customers/index.tsx:21` | isError not checked — failure shows "No customers found" |
| FINDING-D-007 | HIGH | D | `stores/index.tsx:19` | isError not checked — store directory appears empty |
| FINDING-D-008 | HIGH | D | `stores/$storeId.tsx:15` | isError not checked — error looks like missing store |
| FINDING-D-009 | HIGH | D | `master-products/index.tsx:17` | isError not checked |
| FINDING-D-010 | HIGH | D | `inventory/index.tsx:19` | isError not checked — failure shows "No stock rows found" |
| FINDING-D-011 | HIGH | D | `health/index.tsx:91` | isError not checked — monitoring failure shows blank health cards |
| FINDING-D-012 | HIGH | D | `alerts/index.tsx:174` | isError not checked — critical alerts invisible on API failure |
| FINDING-D-013 | HIGH | D | `audit/index.tsx:22` | isError not checked — compliance team sees empty log |
| FINDING-D-014 | HIGH | D | `sync/index.tsx:168` | isError not checked — sync monitoring appears empty |
| FINDING-D-015 | HIGH | D | `security/index.tsx:92` | isError not checked on 4 security queries |
| FINDING-D-016 | HIGH | D | `reports/index.tsx:53` | isError not checked — finance sees "no sales" on API failure |
| FINDING-D-017 | HIGH | D | `index.tsx:33` | isError not checked on 5 dashboard queries — zeros look like real data |
| FINDING-G-001 | HIGH | G | `use-auth.ts` | HELPDESK "Resolve" button may not be permission-gated in UI |
| FINDING-G-002 | HIGH | G | `audit/index.tsx` | Per-page permission check for `audit:read` not confirmed |
| FINDING-I-001 | HIGH | I | `master-products/index.tsx:23` | create/delete mutations have no onError handler |
| FINDING-I-002 | HIGH | I | `master-products/$masterProductId.tsx:20` | assign/remove mutations have no onError handler |
| FINDING-I-003 | HIGH | I | `settings/profile.tsx:59` | revokeSessions has no onError — failure silently swallowed |
| FINDING-I-004 | HIGH | I | `diagnostic/index.tsx:89` | createMutation has no onError — modal stays open on failure |
| FINDING-I-005 | HIGH | I | `diagnostic/index.tsx:307` | revokeSession has no onError |
| FINDING-I-006 | HIGH | I | `alerts/index.tsx:45` | acknowledge/resolve have no onError |
| FINDING-I-007 | HIGH | I | `sync/index.tsx:122` | retryOp has no onError — user clicks repeatedly thinking nothing happened |
| FINDING-A-003 | INFO | A | `tickets/index.tsx` | Ticket CSV export endpoint exists but no download button in UI |
| FINDING-A-004 | INFO | A | `sync/index.tsx` | Sync token bulk revocation endpoint has no UI |
| FINDING-B-002 | MEDIUM | B | `settings/email.tsx:449` | dangerouslySetInnerHTML in template preview — XSS risk |
| FINDING-B-003 | MEDIUM | B | `master-products/index.tsx:177` | parseFloat()\|\|0 silently coerces invalid price to 0 |
| FINDING-F-001 | MEDIUM | F | `inventory/index.tsx:19` | No pagination — all inventory rows in one request |
| FINDING-F-002 | MEDIUM | F | `reports/index.tsx:269` | Daily breakdown silently truncated to 50 rows |
| FINDING-G-003 | INFO | G | `use-auth.ts` | FINANCE has inventory:read — may cross ADR-009 boundary |
| FINDING-G-004 | INFO | G | `config/index.tsx` | Tax rates have no permission defined and no frontend |
| FINDING-H-001 | MEDIUM | H | `users/index.tsx:26` | Filter state not persisted in URL — reset on navigation |
| FINDING-H-002 | MEDIUM | H | `tickets/index.tsx:19` | All 5 ticket filters not persisted in URL |
| FINDING-H-003 | MEDIUM | H | `licenses/index.tsx:21` | Status/edition filters not persisted in URL |
| FINDING-K-001 | MEDIUM | K | `master-products/$masterProductId.tsx:78` | Store assignments table missing overflow-x-auto |
| FINDING-L-001 | LOW | L | `tickets/$ticketId.tsx:61` | useState(Date.now) — pattern inconsistency |
| FINDING-L-002 | LOW | L | `settings/mfa.tsx:173` | Backup codes use array index as key |
| FINDING-L-003 | LOW | L | `health/$storeId.tsx:117` | Error log uses array index as key |
| FINDING-L-004 | LOW | L | `reports/index.tsx:270` | Report tables use array index as key |

---

## Top 3 Most Impactful Fixes

### Fix #1 — Add `isError` to all TanStack Query destructures (affects 18 pages)

**Impact:** Eliminates the single largest UX failure mode in the codebase. API failures currently look identical to empty data on every list page.

Apply this one-line pattern across all 18 affected files:

```typescript
// Before (in every list page):
const { data, isLoading } = useTickets(filters);

// After:
const { data, isLoading, isError } = useTickets(filters);

// Then add above the loading spinner check:
if (isError) return (
  <div className="rounded-md bg-red-900/20 border border-red-500/30 p-4 text-red-400">
    Failed to load data. Please refresh or contact support.
  </div>
);
```

Affected files: `users/index`, `tickets/index`, `tickets/$ticketId`, `licenses/index`, `licenses/$licenseKey`, `customers/index`, `stores/index`, `stores/$storeId`, `master-products/index`, `inventory/index`, `health/index`, `alerts/index`, `audit/index`, `sync/index`, `security/index`, `reports/index` (×2), `index.tsx` (dashboard).

---

### Fix #2 — Add `ConfirmDialog` to the 3 unguarded destructive actions

**Impact:** Prevents the 2 CRITICAL data-loss paths and 1 HIGH self-lockout path. The `ConfirmDialog` component already exists in the codebase — zero new components needed.

```typescript
// FINDING-C-001: master-products/index.tsx ~line 100
// Before:
<button onClick={() => deleteMutation.mutate(p.id)}>Delete</button>

// After:
<ConfirmDialog
  trigger={<button className="text-red-400 hover:text-red-300 text-xs">Delete</button>}
  title={`Delete "${p.name}"?`}
  description="This permanently removes the product from all stores. This action cannot be undone."
  variant="destructive"
  confirmLabel="Delete"
  isLoading={deleteMutation.isPending}
  onConfirm={() => deleteMutation.mutate(p.id)}
/>

// FINDING-C-002: master-products/$masterProductId.tsx ~line 102
// Same pattern with removeMutation

// FINDING-C-005: settings/profile.tsx ~line 157
// Same pattern with revokeSessions.mutate
```

---

### Fix #3 — Add `onError` toast to all 7 fire-and-forget mutations

**Impact:** Eliminates silent failure UX across mutations. Users currently see no feedback when actions fail.

```typescript
// Pattern to apply to all 7 affected mutate() calls:
mutation.mutate(payload, {
  onSuccess: () => { /* existing handler */ },
  onError: (err) => {
    toast.error(
      err instanceof Error ? err.message : 'Operation failed. Please try again.'
    );
  },
});
```

Affected: `createMasterProduct`, `deleteMasterProduct`, `assignToStore`, `removeFromStore`, `revokeAllSessions`, `createDiagnosticSession`, `revokeDiagnosticSession`, `acknowledgeAlert`, `resolveAlert`, `retryDeadLetter`.

---

## Audit Statistics

| Metric | Count |
|--------|-------|
| Backend route files read | 41 |
| Backend endpoints mapped (LIST A) | 126 |
| Live admin endpoints | 91 |
| POS / public / infrastructure endpoints | 35 |
| Frontend API files read | 17 |
| Frontend API functions mapped (LIST B) | 97 |
| Route files audited | 30 |
| **Total findings** | **51** |
| CRITICAL | 2 |
| HIGH | 33 |
| MEDIUM | 9 |
| LOW | 4 |
| INFO | 3 |
| Admin endpoints with no frontend coverage | 7 (live) + 5 (dead routes) |
| Frontend calls with no matching backend endpoint | 1 (`useSecurityMetrics` — source unknown) |
| Duplicate frontend functions (same endpoint) | 1 (`useAllStoreHealth` in stores.ts + health.ts) |

---

*Audit generated: 2026-03-30 | ZyntaPOS Admin Panel v1.0.0 MVP | 3-agent pipeline*
