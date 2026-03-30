# ZyntaPOS Admin Panel — Route Files Audit (Categories B, C, D, E, F, H, I, J, K, L)

> Auditing: forms, buttons, error handling, empty states, pagination, search, mutation feedback, routing, layout, console issues

**Audit Date:** 2026-03-30
**Auditor:** Claude Code
**Scope:** 30 route files in `admin-panel/src/routes/`

## Findings Index

*(populated as audit progresses)*

---

## File 1 — `admin-panel/src/routes/login.tsx`

**Summary:** Login form with email/password + MFA second-step. Uses react-hook-form + Zod. No findings — this file is well-implemented.

- Form has Zod validation (email + password, MFA code 6–8 chars)
- Submit disabled during `isSubmitting || login.isPending` — correct
- Server errors shown inline via `serverError` state
- Field-level errors shown via `formState.errors`
- MFA step submit also disabled during `mfaSubmitting || mfaVerify.isPending`
- `useEffect` navigation on `isAuthenticated` — correct pattern (not render-body side-effect)
- No `form.reset()` call needed (login success navigates away)

**No findings for this file.**

---

## File 24 — `admin-panel/src/routes/security/index.tsx`

**Summary:** Security dashboard with KPI cards, auth event log, active sessions, vulnerability scan. Read-only monitoring page.

- `overflow-x-auto` on events table — correct
- `key={event.id}` and `key={session.id}` on maps — correct
- Empty states present for all sections
- `isError` not destructured from any of the 4 queries
- Skeleton loading with `Array.from({ length: N })` — `key={i}` on static skeletons is acceptable

### FINDING-038
**SEVERITY**: HIGH
**CATEGORY**: D
**FILE**: admin-panel/src/routes/security/index.tsx:92-95
**FINDING**: `isError` not destructured from any of the 4 security queries — all API failures silently show empty states.
**EVIDENCE**: `const { data: metrics, isLoading: metricsLoading } = useSecurityMetrics();`
**IMPACT**: Security monitoring page appears to have no activity when the API is down — a security analyst may miss critical events due to a silent data fetch failure.
**FIX**: Destructure `isError` from each hook, render error banners.

---

## File 22 — `admin-panel/src/routes/audit/index.tsx`

**Summary:** Audit log with debounced search, filter panel, pagination, CSV export. Well-implemented.

- Debounced search: correct
- Pagination: in `AuditLogTable` component
- Export button with try/catch and toast feedback: correct
- `isError` not destructured from `useAuditLogs`

### FINDING-035
**SEVERITY**: HIGH
**CATEGORY**: D
**FILE**: admin-panel/src/routes/audit/index.tsx:22
**FINDING**: `isError` from `useAuditLogs` not checked — API errors silently show empty audit table.
**EVIDENCE**: `const { data, isLoading } = useAuditLogs(effectiveFilters);`
**IMPACT**: Audit compliance team sees empty log with no indication of an API failure.
**FIX**: Destructure `isError`, render error banner.

---

## File 23 — `admin-panel/src/routes/sync/index.tsx`

**Summary:** Sync monitoring with overview, conflicts, and dead letters tabs. Dead letter discard uses `ConfirmDialog`. Well-structured.

- "Discard" uses `ConfirmDialog` with `variant="destructive"` — correct
- Retry button disabled during `retryOp.isPending` — correct
- `retryOp.mutate` has no `onError` handler — retry failures silently swallowed
- `discardOp.mutate` has `onSettled` but no `onError` — discard failures also silently swallowed
- `isError` not destructured from `useSyncStatus`, `useConflictLog`, or `useDeadLetters`

### FINDING-036
**SEVERITY**: HIGH
**CATEGORY**: I
**FILE**: admin-panel/src/routes/sync/index.tsx:122
**FINDING**: `retryOp.mutate(row.id)` has no `onError` handler — retry failure gives no user feedback.
**EVIDENCE**: `onClick={() => retryOp.mutate(row.id)}`
**IMPACT**: Retry silently fails; user may click retry multiple times thinking it's not working.
**FIX**: Add `onSuccess/onError` handlers with toast feedback.

### FINDING-037
**SEVERITY**: HIGH
**CATEGORY**: D
**FILE**: admin-panel/src/routes/sync/index.tsx:168
**FINDING**: `isError` not destructured from `useSyncStatus` — sync API failure silently shows empty overview.
**EVIDENCE**: `const { data: stores = [], isLoading } = useSyncStatus();`
**IMPACT**: Sync failures invisible to operator — may not realize monitoring is down.
**FIX**: Destructure `isError`, render error banner.

---

## File 21 — `admin-panel/src/routes/alerts/index.tsx`

**Summary:** Alert management with acknowledge/resolve actions, alert rules toggle. Pagination present. Well-structured.

- `acknowledge` and `resolve` buttons disabled during `isPending` — correct
- `useToggleAlertRule` has optimistic update with rollback on `onError` — good pattern
- `acknowledge` and `resolve` mutations have no `onError` handlers — failures silently swallowed
- `isError` not destructured from `useAlerts` or `useAlertRules`
- Filter state is local (not URL params)
- Pagination: present

### FINDING-033
**SEVERITY**: HIGH
**CATEGORY**: I
**FILE**: admin-panel/src/routes/alerts/index.tsx:45-46
**FINDING**: `acknowledge` and `resolve` mutations in `AlertRow` have no `onError` handlers — failures are silently swallowed.
**EVIDENCE**: `const { mutate: acknowledge, isPending: acking } = useAcknowledgeAlert();` then `onClick={() => acknowledge(alert.id)}`
**IMPACT**: If acknowledging or resolving an alert fails, the UI shows no feedback. Alert status appears unchanged but user doesn't know if action was applied.
**FIX**: Add `onError: (err) => toast.error(...)` to each mutation call.

### FINDING-034
**SEVERITY**: HIGH
**CATEGORY**: D
**FILE**: admin-panel/src/routes/alerts/index.tsx:174
**FINDING**: `isError` not destructured from `useAlerts` — API failures silently show "No active alerts" empty state.
**EVIDENCE**: `const { data: alertsPage, isLoading } = useAlerts(effectiveFilter);`
**IMPACT**: Critical alerts missing from view due to API failure with no indication to operator.
**FIX**: Destructure `isError`, render error banner.

---

## File 19 — `admin-panel/src/routes/health/index.tsx`

**Summary:** System health dashboard with backend service cards and store health rows. Refresh button, skeleton loading. Well-implemented.

- `isError` not destructured from `useSystemHealth` or `useAllStoreHealth` — silent failure
- Refresh button disabled during `sysFetching` — correct
- `key={s.name}` and `key={s.storeId}` on maps — correct
- Empty state for stores: "No stores registered" — present
- Skeleton loading placeholders — good UX

### FINDING-031
**SEVERITY**: HIGH
**CATEGORY**: D
**FILE**: admin-panel/src/routes/health/index.tsx:91-92
**FINDING**: `isError` not destructured from `useSystemHealth` or `useAllStoreHealth` — API failures silently show empty health cards.
**EVIDENCE**: `const { data: system, isLoading: sysLoading, refetch: refetchSystem, isFetching: sysFetching } = useSystemHealth();`
**IMPACT**: Backend health check failure shows blank service grid with no indication of an error — operator may assume services are healthy when the monitoring API is down.
**FIX**: Destructure `isError` from both hooks, show error state.

---

## File 20 — `admin-panel/src/routes/health/$storeId.tsx`

**Summary:** Store health detail with latency chart and error log. `error` from `useStoreHealthDetail` is checked.

- `error` is checked at line 41: `if (error || !data)` — correct
- Error log uses `key={i}` (index) — minor issue since log entries are static display
- `key={stat.label}` on stats grid map — correct
- No pagination for `errorLog` — but capped with `max-h-64 overflow-y-auto` which is a reasonable UX choice

### FINDING-032
**SEVERITY**: LOW
**CATEGORY**: L
**FILE**: admin-panel/src/routes/health/$storeId.tsx:117-129
**FINDING**: Error log entries use array index as `key` prop.
**EVIDENCE**: `{data.errorLog.map((entry, i) => (<div key={i} ...>...))}`
**IMPACT**: Negligible — the log is read-only and never reordered. Acceptable for a display-only list.
**FIX**: Use a composite key like `key={entry.timestamp + '-' + i}` for best practice.

---

## File 18 — `admin-panel/src/routes/inventory/index.tsx`

**Summary:** Global inventory view. Read-only. Debounced filters, empty state, overflow-x-auto present.

- Debounced filters: correct
- `overflow-x-auto` present: correct
- Empty state: present
- `isError` not destructured from `useGlobalInventory` — same gap
- No pagination — all items returned at once (potential performance issue at scale)
- "Low stock only" filter is client-side (not server-side), applied to already-fetched data

### FINDING-029
**SEVERITY**: HIGH
**CATEGORY**: D
**FILE**: admin-panel/src/routes/inventory/index.tsx:19-22
**FINDING**: `isError` from `useGlobalInventory` not checked — API errors silently show "No stock rows found".
**EVIDENCE**: `const { data, isLoading } = useGlobalInventory({...})`
**IMPACT**: API errors look identical to empty inventory.
**FIX**: Destructure `isError`, render error banner.

### FINDING-030
**SEVERITY**: MEDIUM
**CATEGORY**: F
**FILE**: admin-panel/src/routes/inventory/index.tsx:19-26
**FINDING**: No pagination — all inventory rows loaded in a single request with no page/limit parameters.
**EVIDENCE**: `useGlobalInventory({ productId: ..., storeId: ... })` — no `page` or `size` parameter
**IMPACT**: With many stores and products, this page could request thousands of rows, causing slow load times and browser memory pressure.
**FIX**: Add `page`/`size` pagination parameters and controls.

---

## File 17 — `admin-panel/src/routes/diagnostic/index.tsx`

**Summary:** Remote diagnostic session management. Well-implemented — uses `ConfirmDialog` for revoke, submit disabled during mutation, token shown only once.

- "Revoke" uses `ConfirmDialog` with `variant="destructive"` — correct
- Submit button disabled during `createMutation.isPending` — correct
- `key={store.id}` on map — correct
- `overflow-x-auto` present on table wrapper — correct
- `revokeSession.mutate` has `onSuccess` but no `onError`
- `createMutation.mutate` has `onSuccess` but no `onError`
- `storesQuery.isError` not checked — if stores fail to load, shows "No stores found" silently

### FINDING-027
**SEVERITY**: HIGH
**CATEGORY**: I
**FILE**: admin-panel/src/routes/diagnostic/index.tsx:89-93
**FINDING**: `createMutation.mutate` has no `onError` handler — session creation failures are silently swallowed.
**EVIDENCE**: `createMutation.mutate({ storeId, technicianId: user?.id ?? '', dataScope }, { onSuccess: (session) => { onCreated(session); onClose(); } })`
**IMPACT**: If session creation fails (e.g., store unreachable, permission denied), the modal stays open with no error message.
**FIX**: Add `onError: (err) => toast.error(...)`.

### FINDING-028
**SEVERITY**: HIGH
**CATEGORY**: I
**FILE**: admin-panel/src/routes/diagnostic/index.tsx:307
**FINDING**: `revokeSession.mutate` has no `onError` handler.
**EVIDENCE**: `revokeSession.mutate(revokeTarget, { onSuccess: () => setRevokeTarget(null) })`
**IMPACT**: Revocation failure gives no user feedback — the dialog closes on success but stays open silently on error.
**FIX**: Add `onError: (err) => toast.error(...)`.

---

## File 16 — `admin-panel/src/routes/settings/exchange-rates.tsx`

**Summary:** Exchange rate CRUD. Well-implemented with inline form validation and error handling.

- `error` from `useExchangeRates` is checked and shown — correct
- Empty state present with CTA button
- Form submit disabled when `!isValid || isSaving` — correct
- `isValid` correctly checks `source !== target && rate !== '' && parseFloat(rate) > 0`
- `onSave` try/catch with `toast.error` — correct
- `key={rate.id}` on map — correct
- No confirmation dialog on edit — but this is a non-destructive update, acceptable

**No findings for this file.**

---

## File 15 — `admin-panel/src/routes/settings/email.tsx`

**Summary:** Multi-tab email management page (delivery logs, templates, preferences, unsubscribes, config). Thorough implementation — all sub-tabs handle `isError` and empty states. One concern: `dangerouslySetInnerHTML` for template preview.

- Delivery logs: `isError` handled — correct
- Templates: `isError` handled — correct
- Preferences: `isError` handled — correct
- Unsubscribes: `isError` handled — correct
- Config: `isError` handled — correct
- "Save" in template editor disabled during `updateMutation.isPending` — correct
- Filter state is local (not URL params) — minor issue for email logs filters
- Template editor initializes state directly in render body (lines 376-380) — anti-pattern
- `dangerouslySetInnerHTML` used for email template preview (line 450)

### FINDING-025
**SEVERITY**: HIGH
**CATEGORY**: B
**FILE**: admin-panel/src/routes/settings/email.tsx:376-380
**FINDING**: Template form state is initialized via a side effect inside the render body (not `useEffect`) — this is a React anti-pattern that causes state updates during render.
**EVIDENCE**: `if (template && !initialized) { setSubject(template.subject); setHtmlBody(template.htmlBody); setInitialized(true); }`
**IMPACT**: React may warn about state updates during render; in StrictMode this can cause double-initialization bugs.
**FIX**: Move initialization into `useEffect(() => { if (template) { setSubject(...); ... }}, [template])`.

### FINDING-026
**SEVERITY**: MEDIUM
**CATEGORY**: B
**FILE**: admin-panel/src/routes/settings/email.tsx:449-451
**FINDING**: Email template preview uses `dangerouslySetInnerHTML` to render user-edited HTML — potential XSS if the template contains `<script>` tags or event handler attributes.
**EVIDENCE**: `<div className="bg-white p-4 rounded border text-sm" dangerouslySetInnerHTML={{ __html: htmlBody }} />`
**IMPACT**: An admin could accidentally save a template with XSS payload, which would execute in all browsers viewing the preview. Lower risk because only admin users edit templates, but still a concern.
**FIX**: Render preview in a sandboxed `<iframe>` or sanitize `htmlBody` with DOMPurify before rendering.

---

## File 14 — `admin-panel/src/routes/settings/mfa.tsx`

**Summary:** MFA setup and disable flow. Well-implemented with TOTP verification required to disable.

- "Enable MFA" button disabled until code is 6 digits — correct
- "Disable MFA" button disabled until code is 6 digits — correct
- `handleStartSetup`, `handleEnable`, `handleDisable` all have `try/catch` — errors handled
- Backup codes list uses `key={i}` (index) — minor issue, but backup codes are static so index keys are acceptable here
- No issues found

### FINDING-024
**SEVERITY**: LOW
**CATEGORY**: L
**FILE**: admin-panel/src/routes/settings/mfa.tsx:173
**FINDING**: Backup codes list uses array index as `key` prop.
**EVIDENCE**: `{setupData.backupCodes.map((code, i) => (<code key={i} ...>{code}</code>))}`
**IMPACT**: Negligible — backup codes are displayed once and never reordered. No functional issue.
**FIX**: Use `key={code}` (the code value itself is unique) for best practice.

---

## File 12 — `admin-panel/src/routes/settings/index.tsx`

**Summary:** Timezone preference page. Local-only settings stored via Zustand. Well-implemented.

- "Save Preferences" button disabled when `!isDirty` — correct
- `toast.success` called on save — mutation feedback present
- No API calls — no isError concern
- Empty state shown when timezone search has no matches

**No findings for this file.**

---

## File 13 — `admin-panel/src/routes/settings/profile.tsx`

**Summary:** Password change + session revocation. Well-implemented overall.

- Submit button disabled during `changePassword.isPending` — correct
- Form fields cleared on success
- `passwordError` shown inline
- "Revoke All" is a destructive action — but it correctly asks by being disabled when no sessions exist; however there is NO confirmation dialog before firing `revokeSessions.mutate`
- `revokeSessions.mutate` has `onSuccess` handler but no `onError` handler
- `key={session.id}` on sessions map — correct

### FINDING-022
**SEVERITY**: HIGH
**CATEGORY**: C
**FILE**: admin-panel/src/routes/settings/profile.tsx:157-162
**FINDING**: "Revoke All" sessions button fires immediately without a confirmation dialog — revoking all sessions immediately logs out the current user.
**EVIDENCE**: `<button onClick={handleRevokeAll} disabled={revokeSessions.isPending || !sessions?.length}>Revoke All</button>`
**IMPACT**: Accidental click immediately logs the user out and invalidates all their sessions — no undo.
**FIX**: Add a `ConfirmDialog` before calling `revokeSessions.mutate`.

### FINDING-023
**SEVERITY**: HIGH
**CATEGORY**: I
**FILE**: admin-panel/src/routes/settings/profile.tsx:59-65
**FINDING**: `revokeSessions.mutate` has no `onError` handler — session revocation failures are silently swallowed.
**EVIDENCE**: `revokeSessions.mutate(user.id, { onSuccess: () => { clearUser(); navigate(...) } })`
**IMPACT**: If revocation fails, the UI shows no error and the user may believe they've been logged out when they haven't.
**FIX**: Add `onError: (err) => toast.error(...)`.

---

## File 11 — `admin-panel/src/routes/master-products/$masterProductId.tsx`

**Summary:** Master product detail with store assignments. "Remove" store assignment button has no confirmation dialog. Same pattern as index.tsx.

- `assignMutation.mutate` has `onSuccess` but no `onError`
- `removeMutation.mutate` at line 103 has neither `onSuccess` nor `onError`
- "Remove" button not disabled during `removeMutation.isPending` — allows double-click
- Store assignments table missing `overflow-x-auto` wrapper
- `isError` not destructured from `useMasterProduct`
- `AssignStoreDialog` submit button correctly disabled with `!selectedStore || isLoading`

### FINDING-018
**SEVERITY**: CRITICAL
**CATEGORY**: C
**FILE**: admin-panel/src/routes/master-products/$masterProductId.tsx:102-107
**FINDING**: "Remove" button calls `removeMutation.mutate(...)` directly with no confirmation dialog — removes store assignment without user confirmation.
**EVIDENCE**: `<button onClick={() => removeMutation.mutate({ masterProductId, storeId: a.store_id })}>`
**IMPACT**: Accidental click removes a product assignment from a live store with no undo.
**FIX**: Wrap in `ConfirmDialog` before firing mutation.

### FINDING-019
**SEVERITY**: HIGH
**CATEGORY**: C
**FILE**: admin-panel/src/routes/master-products/$masterProductId.tsx:102-107
**FINDING**: "Remove" button not disabled during `removeMutation.isPending`.
**EVIDENCE**: `<button onClick={() => removeMutation.mutate(...)}` — no `disabled` prop
**IMPACT**: Double-click can send duplicate removal requests.
**FIX**: Add `disabled={removeMutation.isPending}`.

### FINDING-020
**SEVERITY**: HIGH
**CATEGORY**: I
**FILE**: admin-panel/src/routes/master-products/$masterProductId.tsx:20-21
**FINDING**: Neither `assignMutation` nor `removeMutation` has `onError` handlers — failures are silently swallowed.
**EVIDENCE**: `assignMutation.mutate({ masterProductId, storeId, data }, { onSuccess: () => setShowAssign(false) })` — no `onError`
**IMPACT**: Store assignment or removal failures give no feedback to the user.
**FIX**: Add `onError: (err) => toast.error(...)` to both.

### FINDING-021
**SEVERITY**: MEDIUM
**CATEGORY**: K
**FILE**: admin-panel/src/routes/master-products/$masterProductId.tsx:78-112
**FINDING**: Store assignments table at line 78 has no `overflow-x-auto` wrapper — truncates on narrow screens.
**EVIDENCE**: `<table className="w-full text-sm">` inside a div with no overflow control
**IMPACT**: Table content clips on mobile or narrow viewports.
**FIX**: Wrap in `<div className="overflow-x-auto">`.

---

## File 10 — `admin-panel/src/routes/master-products/index.tsx`

**Summary:** Master product catalog list with inline create dialog. Several issues found.

- Debounced search: correct
- Pagination: present
- Empty state: present (colspan row in table)
- `overflow-x-auto` present on table container — correct
- `key={p.id}` present on map — correct
- Delete button at line 101 has NO confirmation dialog — calls `deleteMutation.mutate(p.id)` directly
- `deleteMutation` has no `onError` handler — errors silently swallowed
- `createMutation` has no `onError` handler
- Form validation is native HTML `required` only — no Zod/react-hook-form; no error message display
- `parseFloat(basePrice) || 0` — if user enters "abc", silently submits 0 as base price
- `isError` from `useMasterProducts` not checked

### FINDING-013
**SEVERITY**: CRITICAL
**CATEGORY**: C
**FILE**: admin-panel/src/routes/master-products/index.tsx:100-105
**FINDING**: Delete button calls `deleteMutation.mutate(p.id)` directly with no confirmation dialog.
**EVIDENCE**: `<button onClick={() => deleteMutation.mutate(p.id)} className="text-red-400 hover:text-red-300 text-xs">Delete</button>`
**IMPACT**: One accidental click permanently deletes a master product shared across all stores. No undo. This is a data-loss risk.
**FIX**: Wrap in a `ConfirmDialog` (already used in tickets/$ticketId.tsx) before invoking the mutation.

### FINDING-014
**SEVERITY**: HIGH
**CATEGORY**: C
**FILE**: admin-panel/src/routes/master-products/index.tsx:100-105
**FINDING**: Delete button is not disabled while `deleteMutation.isPending` — allows double-click to trigger duplicate delete requests.
**EVIDENCE**: `<button onClick={() => deleteMutation.mutate(p.id)}` — no `disabled` prop
**IMPACT**: Double-clicking delete before server responds can fire two delete requests.
**FIX**: Add `disabled={deleteMutation.isPending}`.

### FINDING-015
**SEVERITY**: HIGH
**CATEGORY**: I
**FILE**: admin-panel/src/routes/master-products/index.tsx:23-28
**FINDING**: Neither `createMutation` nor `deleteMutation` has an `onError` callback — mutation failures are silently swallowed.
**EVIDENCE**: `createMutation.mutate(req, { onSuccess: () => setShowCreate(false) })` — no `onError`
**IMPACT**: If creation or deletion fails (e.g., duplicate SKU, server error), the user sees no feedback and may retry or assume success.
**FIX**: Add `onError: (err) => toast.error(...)` to both mutation calls.

### FINDING-016
**SEVERITY**: MEDIUM
**CATEGORY**: B
**FILE**: admin-panel/src/routes/master-products/index.tsx:177
**FINDING**: `parseFloat(basePrice) || 0` silently coerces invalid input (e.g., letters) to 0 — no validation error shown.
**EVIDENCE**: `base_price: parseFloat(basePrice) || 0,`
**IMPACT**: Admin can accidentally submit a master product with a base price of 0 if they mistype, with no warning.
**FIX**: Add explicit validation (Zod schema or manual check) and display error message before submitting.

### FINDING-017
**SEVERITY**: HIGH
**CATEGORY**: D
**FILE**: admin-panel/src/routes/master-products/index.tsx:17-21
**FINDING**: `isError` from `useMasterProducts` not checked — API failures silently show empty table.
**EVIDENCE**: `const { data, isLoading } = useMasterProducts({...})`
**IMPACT**: Admin sees "No master products found" on API error.
**FIX**: Destructure `isError`, render error banner.

---

## File 8 — `admin-panel/src/routes/stores/index.tsx`

**Summary:** Store list with search, status filter, pagination. Read-only, delegates to `StoreTable`.

- `isError` not destructured — same gap as other list pages
- Filter state local (not URL params)

### FINDING-011
**SEVERITY**: HIGH
**CATEGORY**: D
**FILE**: admin-panel/src/routes/stores/index.tsx:19-23
**FINDING**: `isError` from `useStores` not checked — API failures silently show empty list.
**EVIDENCE**: `const { data, isLoading } = useStores({...})`
**IMPACT**: Store directory appears empty on API failure.
**FIX**: Destructure `isError`, render error banner.

---

## File 9 — `admin-panel/src/routes/stores/$storeId.tsx`

**Summary:** Store detail page. Minimal, delegates to `StoreDetailPanel`. Same `isError` gap.

### FINDING-012
**SEVERITY**: HIGH
**CATEGORY**: D
**FILE**: admin-panel/src/routes/stores/$storeId.tsx:15
**FINDING**: `isError` not destructured from `useStore` — API error shows "Store not found" instead of error state.
**EVIDENCE**: `const { data: store, isLoading } = useStore(storeId);`
**IMPACT**: API errors indistinguishable from non-existent store.
**FIX**: Destructure `isError`, render error banner.

---

## File 7 — `admin-panel/src/routes/customers/index.tsx`

**Summary:** Global customer directory. Debounced search + store ID filter, pagination, inline table. Read-only view.

- Debounced search and storeId: correct
- Pagination: present (Previous/Next buttons with disabled guards)
- Empty state: present (icon + "No customers found")
- `isError` not checked — query errors silently show "No customers found" message
- `overflow-x-auto` present on the table container — correct
- `key={customer.id}` present on map — correct

### FINDING-010
**SEVERITY**: HIGH
**CATEGORY**: D
**FILE**: admin-panel/src/routes/customers/index.tsx:21-26
**FINDING**: `isError` not destructured from `useGlobalCustomers` — API errors silently display "No customers found" empty state.
**EVIDENCE**: `const { data, isLoading } = useGlobalCustomers({...})`
**IMPACT**: API failure looks identical to an empty directory.
**FIX**: Destructure `isError`, render error banner.

---

## File 5 — `admin-panel/src/routes/licenses/index.tsx`

**Summary:** License list with stats banner, debounced search, status/edition filters, CSV export, pagination.

- Debounced search: correct
- Pagination: present
- `isError` from `useLicenses` not checked — same pattern as users/tickets
- Filter state local (not URL params)
- "New License" button has no `disabled` guard — but it opens a modal, not an async action directly, so this is acceptable

### FINDING-007
**SEVERITY**: HIGH
**CATEGORY**: D
**FILE**: admin-panel/src/routes/licenses/index.tsx:27-32
**FINDING**: `isError` from `useLicenses` not destructured — API failure silently shows empty list.
**EVIDENCE**: `const { data, isLoading } = useLicenses({...})`
**IMPACT**: License management page appears empty on API error; admin may create duplicate licenses thinking none exist.
**FIX**: Destructure `isError`, render error banner.

### FINDING-008
**SEVERITY**: MEDIUM
**CATEGORY**: H
**FILE**: admin-panel/src/routes/licenses/index.tsx:21-22
**FINDING**: Status and edition filter state is local — lost on navigation.
**EVIDENCE**: `const [statusFilter, setStatusFilter] = useState<LicenseStatus | ''>('');`
**IMPACT**: Filtering to "EXPIRING_SOON" and navigating to a license detail page resets filter on return.
**FIX**: Persist in URL search params.

---

## File 6 — `admin-panel/src/routes/licenses/$licenseKey.tsx`

**Summary:** License detail page. Minimal, delegates to `LicenseDetailCard`. Well-structured.

- `isError` not destructured — same gap

### FINDING-009
**SEVERITY**: HIGH
**CATEGORY**: D
**FILE**: admin-panel/src/routes/licenses/$licenseKey.tsx:17
**FINDING**: `isError` not destructured from `useLicense` — API error shows "License not found" instead of an error state.
**EVIDENCE**: `const { data, isLoading } = useLicense(licenseKey);`
**IMPACT**: API errors indistinguishable from non-existent license.
**FIX**: Destructure `isError`, render error banner.

---

## File 4 — `admin-panel/src/routes/tickets/$ticketId.tsx`

**Summary:** Ticket detail page. Modals for assign/resolve. `ConfirmDialog` used for destructive "Close Ticket" action. Well-implemented.

- "Close Ticket" uses `ConfirmDialog` with `variant="destructive"` — correct pattern
- `closeTicket.isPending` passed to `ConfirmDialog` `isLoading` — button disabled during mutation
- `isError` not destructured from `useTicket` — query errors silently show "Ticket not found"
- `setNow(Date.now)` at line 61 — passes function reference, not call result. Should be `Date.now()`.

### FINDING-005
**SEVERITY**: HIGH
**CATEGORY**: D
**FILE**: admin-panel/src/routes/tickets/$ticketId.tsx:59
**FINDING**: `isError` not destructured from `useTicket` — API errors silently display "Ticket not found" instead of an error message.
**EVIDENCE**: `const { data: ticket, isLoading } = useTicket(ticketId);`
**IMPACT**: A network error or 500 looks identical to a genuinely missing ticket — agent can't distinguish between a real error and a non-existent record.
**FIX**: Destructure `isError`, show an error banner when true.

### FINDING-006
**SEVERITY**: LOW
**CATEGORY**: L
**FILE**: admin-panel/src/routes/tickets/$ticketId.tsx:61
**FINDING**: `useState(Date.now)` passes the function reference as initializer (lazy init), which is valid React, but the intent is likely `Date.now()` to capture the current timestamp.
**EVIDENCE**: `const [now, setNow] = useState(Date.now);`
**IMPACT**: Lazy init behavior with `Date.now` is actually valid here (React calls it once) — low impact. But `setNow(Date.now())` in the interval is correct at line 63. Pattern inconsistency only.
**FIX**: Change to `useState(() => Date.now())` or `useState(Date.now())` for clarity.

---

## File 3 — `admin-panel/src/routes/tickets/index.tsx`

**Summary:** Tickets list with metrics cards, filters, date range, search. Similar pattern to users.

- `useDebounce(search, 300)` — correct
- Pagination present
- `isError` from `useTickets` not destructured — same pattern as users
- Filter state local (not URL params) — lost on navigation

### FINDING-003
**SEVERITY**: HIGH
**CATEGORY**: D
**FILE**: admin-panel/src/routes/tickets/index.tsx:32-42
**FINDING**: `isError` from `useTickets` is not checked — API failures silently show empty list.
**EVIDENCE**: `const { data, isLoading } = useTickets({...})`
**IMPACT**: Agent sees an empty ticket list with no indication of an API error, potentially missing urgent support tickets.
**FIX**: Destructure `isError`, render an error banner when true.

### FINDING-004
**SEVERITY**: MEDIUM
**CATEGORY**: H
**FILE**: admin-panel/src/routes/tickets/index.tsx:19-27
**FINDING**: All filter state (status, priority, category, date range, searchBody) is local — lost on navigation.
**EVIDENCE**: `const [statusFilter, setStatusFilter] = useState<TicketStatus | ''>('');`
**IMPACT**: Operator sets complex ticket filters, navigates to a ticket detail, returns — all filters reset.
**FIX**: Persist filter values in URL search params.

---

## File 2 — `admin-panel/src/routes/users/index.tsx`

**Summary:** User management list page. Uses pagination, debounced search, role/status filters. Well-implemented.

- `useDebounce(search, 300)` — search is debounced correctly
- Pagination present: `page` state, `totalPages`, `onPageChange`
- Filter state is local (not URL params) — filter values lost on navigation
- `isError` from `useAdminUsers` is not destructured or shown — query errors are silently swallowed
- Empty state handled by `UserTable` component (not visible here)

### FINDING-001
**SEVERITY**: MEDIUM
**CATEGORY**: H
**FILE**: admin-panel/src/routes/users/index.tsx:26
**FINDING**: Role and status filter values are stored in local component state, so navigating away and back resets the filters to defaults.
**EVIDENCE**: `const [roleFilter, setRoleFilter] = useState<AdminRole | ''>('');`
**IMPACT**: User sets filters, navigates to another route, returns — all filters reset, requiring re-entry.
**FIX**: Persist filter values in URL search params using TanStack Router's `search` option.

### FINDING-002
**SEVERITY**: HIGH
**CATEGORY**: D
**FILE**: admin-panel/src/routes/users/index.tsx:26-31
**FINDING**: `isError` from `useAdminUsers` is not destructured — query errors are silently ignored with no UI feedback.
**EVIDENCE**: `const { data, isLoading } = useAdminUsers({...})`
**IMPACT**: If the users API call fails, the page shows an empty list with no error message, appearing as if there are simply no users.
**FIX**: Destructure `isError` and `error`, render an error banner when `isError` is true.

---

