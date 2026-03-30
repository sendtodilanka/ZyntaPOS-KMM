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

