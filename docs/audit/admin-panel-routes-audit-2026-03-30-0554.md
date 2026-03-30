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

