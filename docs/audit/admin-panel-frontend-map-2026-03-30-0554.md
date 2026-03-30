# ZyntaPOS Admin Panel — Frontend API Map & Audit Findings

## LIST B — Frontend API Calls

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
| 15 | `useDeactivateUser` | PATCH | `admin/users/{userId}` | `useMutation` |
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
| **licenses.ts** (via `licenseClient`) | | | | |
| 31 | `useLicenses` | GET | `admin/licenses?{filters}` | `useQuery(['licenses',filters])` |
| 32 | `useLicense` | GET | `admin/licenses/{key}` | `useQuery(['licenses',key])` |
| 33 | `useLicenseStats` | GET | `admin/licenses/stats` | `useQuery(['licenses','stats'])` |
| 34 | `useLicenseDevices` | GET | `admin/licenses/{key}/devices` | `useQuery(['licenses',key,'devices'])` |
| 35 | `useCreateLicense` | POST | `admin/licenses` | `useMutation` |
| 36 | `useUpdateLicense` | PUT | `admin/licenses/{key}` | `useMutation` |
| 37 | `useRevokeLicense` | DELETE | `admin/licenses/{key}` | `useMutation` |
| 38 | `useDeregisterDevice` | DELETE | `admin/licenses/{key}/devices/{deviceId}` | `useMutation` |
| 39 | `useForceSyncLicense` | PUT | `admin/licenses/{key}` | `useMutation` |
| **stores.ts** | | | | |
| 40 | `useStores` | GET | `admin/stores?{filters}` | `useQuery(['stores',filters])` |
| 41 | `useStore` | GET | `admin/stores/{storeId}` | `useQuery(['stores',storeId])` |
| 42 | `useStoreHealth` *(stores.ts)* | GET | `admin/stores/{storeId}/health` | `useQuery(['stores',storeId,'health'])` |
| 43 | `useAllStoreHealth` *(stores.ts)* | GET | `admin/health/stores` | `useQuery(['stores','health'])` |
| 44 | `useUpdateStoreConfig` | PUT | `admin/stores/{storeId}/config` | `useMutation` |
| **inventory.ts** | | | | |
| 45 | `useGlobalInventory` | GET | `admin/inventory/global?{filters}` | `useQuery(['inventory-global',filters])` |
| **health.ts** | | | | |
| 46 | `useSystemHealth` | GET | `admin/health/system` | `useQuery(['health','system'])` |
| 47 | `useAllStoreHealth` *(health.ts)* | GET | `admin/health/stores` | `useQuery(['health','stores'])` |
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
| 62 | `exportAuditLogs` *(plain async fn)* | GET | `admin/audit/export?{filters}` | direct fetch (no query key) |
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
| 83 | `useMasterProductStores` | GET | `admin/master-products/{masterProductId}/stores` | `useQuery(['master-products',masterProductId,'stores'])` |
| 84 | `useAssignToStore` | POST | `admin/master-products/{masterProductId}/stores/{storeId}` | `useMutation` |
| 85 | `useRemoveFromStore` | DELETE | `admin/master-products/{masterProductId}/stores/{storeId}` | `useMutation` |
| 86 | `useUpdateStoreOverride` | PUT | `admin/master-products/{masterProductId}/stores/{storeId}` | `useMutation` |
| 87 | `useBulkAssign` | POST | `admin/master-products/{masterProductId}/bulk-assign` | `useMutation` |
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

### Notes

- Functions #31–39 (`licenses.ts`) use `licenseClient` (targets the License service on a separate base URL) — all other functions use `apiClient` (API service).
- `useAllStoreHealth` is declared in **both** `stores.ts` (queryKey `['stores','health']`) and `health.ts` (queryKey `['health','stores']`). Both hit the same endpoint `admin/health/stores` — this is a duplicate that could cause cache divergence.
- `useDeactivateUser` (#15) and `useUpdateUser` (#14) both call `PATCH admin/users/{userId}` — deactivate is a specialised wrapper that hard-codes `{ isActive: false }`.
- `useForceSyncLicense` (#39) and `useUpdateLicense` (#36) both call `PUT admin/licenses/{key}` — force-sync is a thin wrapper that hard-codes `{ forceSync: true }`.
- `exportAuditLogs` (#62) is a plain `async` function, not a TanStack hook. It fires a direct `GET admin/audit/export` and pipes the response to a CSV download.

=== LIST B COMPLETE ===

## PERMISSIONS MAP (from use-auth.ts)

| Role | Permissions |
|------|-------------|
| **ADMIN** | `dashboard:ops`, `dashboard:financial`, `dashboard:support`, `license:read`, `license:write`, `license:revoke`, `license:export`, `store:read`, `store:sync:manage`, `store:config:read`, `diagnostics:access`, `diagnostics:read`, `config:push`, `reports:financial`, `reports:operational`, `reports:support`, `reports:read`, `reports:export`, `alerts:read`, `alerts:acknowledge`, `alerts:configure`, `audit:read`, `audit:export`, `tickets:read`, `tickets:create`, `tickets:update`, `tickets:assign`, `tickets:resolve`, `tickets:close`, `tickets:comment`, `users:read`, `users:write`, `users:deactivate`, `users:sessions:revoke`, `system:settings`, `system:health`, `system:backup`, `email:settings`, `email:logs`, `inventory:read`, `inventory:write`, `transfers:read`, `customers:read` |
| **OPERATOR** | `dashboard:ops`, `dashboard:support`, `license:read`, `store:read`, `store:sync:manage`, `store:config:read`, `diagnostics:access`, `diagnostics:read`, `reports:operational`, `reports:support`, `reports:read`, `alerts:read`, `alerts:acknowledge`, `tickets:read`, `tickets:create`, `tickets:update`, `tickets:assign`, `tickets:resolve`, `tickets:close`, `tickets:comment`, `system:health`, `email:logs`, `inventory:read`, `transfers:read`, `customers:read` |
| **FINANCE** | `dashboard:financial`, `license:read`, `license:export`, `reports:financial`, `reports:read`, `reports:export`, `inventory:read` |
| **AUDITOR** | `license:read`, `reports:read`, `audit:read`, `audit:export` |
| **HELPDESK** | `dashboard:support`, `license:read`, `store:read`, `diagnostics:read`, `reports:support`, `reports:read`, `tickets:read`, `tickets:create`, `tickets:update`, `tickets:assign`, `tickets:close`, `tickets:comment`, `customers:read` |

### Permission Summary

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

*Total atomic permissions: 43 (39 defined in use-auth.ts comment; actual count in PERMISSIONS map is 43)*

=== PERMISSIONS MAP COMPLETE ===
