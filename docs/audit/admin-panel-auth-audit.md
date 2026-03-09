# Admin Panel — Login / Logout Comprehensive Audit

**Date:** 2026-03-09
**Scope:** `admin-panel/src/` — all auth, session, routing logic
**Status:** Plan Mode — read-only analysis, no code changes made

---

## Files Audited

| File | Role |
|------|------|
| `src/routes/__root.tsx` | Auth guard, bootstrap detection, route protection |
| `src/routes/login.tsx` | Login form, MFA step, OAuth error handling |
| `src/routes/setup/index.tsx` | First-run bootstrap |
| `src/routes/settings/profile.tsx` | Password change, session list/revoke |
| `src/routes/settings/mfa.tsx` | MFA enable/disable |
| `src/api/auth.ts` | All auth mutations and queries |
| `src/api/users.ts` | User management, session revocation |
| `src/stores/auth-store.ts` | Zustand auth state |
| `src/hooks/use-auth.ts` | Permission hook |
| `src/lib/api-client.ts` | ky HTTP client, CSRF, 401 handler |
| `src/lib/constants.ts` | API URLs, route names |
| `src/components/layout/UserMenu.tsx` | Sign-out button |

---

## System Architecture Summary

```
Browser Cookie (httpOnly)
  admin_access_token  ← 15 min TTL
  admin_refresh_token ← 7 day TTL
  XSRF-TOKEN          ← readable, CSRF double-submit

Page Load
  GET /admin/auth/status  → needsBootstrap?
  GET /admin/auth/me      → AdminUser | 401

Auth State: Zustand (auth-store)
  user: AdminUser | null
  isLoading: boolean (starts true)

Server State: TanStack Query
  queryKey: ['admin', 'me']  staleTime: 5 min, gcTime: 10 min
  queryKey: ['admin', 'status']  staleTime: Infinity

Auth Guard: __root.tsx
  loading = statusLoading || queryLoading || storeLoading
  → spinner while any loading
  → redirect to /setup, /login, or / based on state
```

---

## CRITICAL Issues

---

### BUG-01 — No Token Refresh Mechanism (Session-Breaking)

**Severity:** CRITICAL
**File:** `src/lib/api-client.ts`
**Lines:** 34–44

The `admin_access_token` cookie expires in 15 minutes. There is **no automatic token refresh anywhere in the client**:

- No `beforeRequest` hook that checks token expiry and calls a refresh endpoint
- No background timer or `setInterval` to proactively refresh before expiry
- No use of the `admin_refresh_token` cookie that the backend sets (7-day TTL)

**What actually happens after 15 minutes:**
1. Any authenticated API call returns `401`
2. `afterResponse` hook fires: `window.location.href = '/login'`
3. Full page reload — all unsaved form data is **permanently lost**
4. User sees blank page then login form with no explanation

**Current 401 handler:**
```typescript
// src/lib/api-client.ts  lines 34–43
afterResponse: [
  async (_request, _options, response) => {
    if (response.status === 401) {
      if (!window.location.pathname.startsWith('/login')) {
        window.location.href = '/login';  // hard nav, no refresh attempt
      }
    }
    return response;
  },
],
```

**Expected behaviour:** On 401, attempt `POST /admin/auth/refresh` (using the httpOnly refresh cookie). Only redirect to login if the refresh also fails.

**Impact:** Any user who stays on a protected page for >15 minutes (e.g., filling out a long form, reviewing reports) is abruptly ejected with no warning and all state lost.

---

### BUG-02 — Side Effects Inside `queryFn` (Anti-Pattern / StrictMode Bug)

**Severity:** CRITICAL
**File:** `src/api/auth.ts`
**Lines:** 58–77

`setUser()` and `clearUser()` are called **inside** the `queryFn` of `useCurrentUser()`:

```typescript
// src/api/auth.ts  lines 60–69
queryFn: async () => {
  try {
    const user = await apiClient.get('admin/auth/me').json<AdminUser>();
    setUser(user);    // ← SIDE EFFECT INSIDE queryFn
    return user;
  } catch (error) {
    clearUser();      // ← SIDE EFFECT INSIDE queryFn
    throw error;
  }
},
```

**Problems:**
1. **React StrictMode (development):** React 18 intentionally invokes render functions and `useEffect` twice in dev. While `queryFn` is async, concurrent mode can cause the queryFn to run more than once before settling, resulting in `setUser()` being called multiple times with potentially different values.
2. **TanStack Query contract violation:** `queryFn` is supposed to be a pure data-fetching function. Embedding Zustand store mutations inside it makes the query unpredictable during retries, background refetches, and cache invalidations. Every background refetch silently calls `setUser()`.
3. **Correct pattern for TanStack Query v5:** Side effects belong in `useEffect` observing the query's `data`/`isError` state. Since `onSuccess`/`onError` were removed in v5, the pattern is:
   ```typescript
   const query = useQuery({ queryKey: ['admin', 'me'], queryFn: ... });
   useEffect(() => { if (query.data) setUser(query.data); }, [query.data]);
   useEffect(() => { if (query.isError) clearUser(); }, [query.isError]);
   ```

---

## HIGH Issues

---

### BUG-03 — Triple Navigation After Successful Login

**Severity:** HIGH
**Files:** `src/routes/login.tsx` (lines 61–63, 71–73), `src/routes/__root.tsx` (lines 64–66)

After a successful login (no MFA), **three separate code paths all try to navigate to `/`** simultaneously:

**Path 1 — Direct call in `onSubmit`** (`login.tsx` line 71–73):
```typescript
// src/routes/login.tsx  line 71
} else {
  navigate({ to: '/' });
}
```

**Path 2 — `useEffect` watching `isAuthenticated`** (`login.tsx` lines 61–63):
```typescript
// src/routes/login.tsx  lines 61–63
useEffect(() => {
  if (isAuthenticated) navigate({ to: '/' });
}, [isAuthenticated, navigate]);
```

**Path 3 — Root guard effect** (`__root.tsx` lines 64–66):
```typescript
// src/routes/__root.tsx  lines 64–66
if (isAuthenticated && isPublicPage) {
  navigate({ to: '/' });
}
```

**Sequence of events on login:**
1. `login.mutateAsync()` resolves → `onSuccess` fires → `setUser(user)` (Zustand updates)
2. Component code resumes → Path 1 fires `navigate({ to: '/' })`
3. Zustand update triggers re-render → login.tsx `useEffect` (Path 2) fires `navigate({ to: '/' })`
4. `__root.tsx` effect re-runs (Path 3) → fires `navigate({ to: '/' })` again

TanStack Router handles duplicate same-route navigations as no-ops, but this is an inconsistent design. The same triple-navigation occurs after MFA verification (`onMfaSubmit` line 95 + the same two effects).

**Fix:** Remove Path 1 (the direct `navigate` calls in `onSubmit`/`onMfaSubmit`). The `useEffect` in `login.tsx` and the `__root.tsx` guard already handle the redirect reactively. Remove one of those too — the root guard alone is sufficient.

---

### BUG-04 — Logout Error Toast Is Never Shown

**Severity:** HIGH
**File:** `src/api/auth.ts`
**Lines:** 132–137

```typescript
// src/api/auth.ts  lines 132–137
onError: () => {
  clearUser();
  toast.error('Logout failed', 'Session cleared locally.');  // ← dead code
  window.location.href = '/login';  // ← destroys the toast immediately
},
```

`toast.error()` adds a toast to the Zustand `ui-store`. But `window.location.href = '/login'` fires on the very next line, causing a **full page reload**. The entire React app, including the UI store with the queued toast, is destroyed before the user can see it.

**User experience:** Clicks "Sign out" → page reloads to `/login` with no error feedback, even if the server-side logout failed.

**Fix:** The `toast.error` call should either be removed (since the user is redirected anyway) or replaced with a more appropriate mechanism. Alternatively, use `navigate({ to: '/login' })` (soft nav) so the toast can render, but this leaves the TanStack Query cache intact which could cause re-authentication issues.

---

### BUG-05 — Unhandled Promise Rejection in `handleChangePassword`

**Severity:** HIGH
**File:** `src/routes/settings/profile.tsx`
**Lines:** 24–45

```typescript
// src/routes/settings/profile.tsx  lines 41–44
const handleChangePassword = async (e: React.FormEvent) => {
  ...
  await changePassword.mutateAsync({ currentPassword, newPassword });
  // ← if mutateAsync throws (wrong password, network error), execution stops here
  setCurrentPassword('');   // ← these lines never run on error
  setNewPassword('');
  setConfirmPassword('');
};
```

`mutateAsync` throws when the server returns an error (e.g., 401 wrong current password, 422 validation). There is **no `try/catch`** block.

**Consequences:**
1. **Unhandled Promise Rejection** in the browser console / error monitoring
2. The `useChangePassword`'s `onError` toast fires (via TanStack Query internals), so the user DOES see an error toast — but only because the hook's `onError` ran, not because the component handled it
3. The form fields are NOT cleared on error (fine)
4. The form fields ARE cleared on success (correct)
5. React's `handleSubmit` event handler propagation might swallow the error silently, but this is undefined behavior

---

### BUG-06 — "Revoke All Sessions" Revokes Own Current Session Without Logout

**Severity:** HIGH
**File:** `src/routes/settings/profile.tsx`
**Lines:** 47–51

```typescript
// src/routes/settings/profile.tsx  lines 47–51
const handleRevokeAll = () => {
  if (user) {
    revokeSessions.mutate(user.id, { onSuccess: () => refetchSessions() });
  }
};
```

`DELETE /admin/users/{userId}/sessions` revokes ALL sessions for the given user, including the currently active session. After this call:

1. The user's `admin_access_token` and `admin_refresh_token` cookies are invalidated server-side
2. The client has NO knowledge of this — `clearUser()` is never called
3. The next API call made (e.g., navigating to another page, auto-refresh) returns `401`
4. The `afterResponse` 401 hook fires → `window.location.href = '/login'` (abrupt forced logout)

**User experience:** User clicks "Revoke All" on their own profile → page appears to work (success toast) → shortly after, they're unexpectedly ejected from the panel with no warning when they next interact.

**Fix:** After revoking own sessions, call `clearUser()` and explicitly navigate to `/login` with a message. Or: the backend should exclude the current session from bulk revocation.

---

### BUG-07 — Two Sources of Truth for Authenticated User Data

**Severity:** HIGH
**Files:** `src/routes/settings/profile.tsx` (line 13), `src/routes/settings/mfa.tsx` (line 13)

The **profile** and **mfa** settings pages read the user object from the TanStack Query cache:

```typescript
// src/routes/settings/profile.tsx  line 13
const { data: user } = useCurrentUser();

// src/routes/settings/mfa.tsx  line 13
const { data: user, refetch } = useCurrentUser();
```

But the rest of the app (header, sidebar, user menu) reads from the **Zustand auth store** via `useAuth()`:

```typescript
// src/hooks/use-auth.ts  line 52
const { user, isLoading } = useAuthStore();
```

**Problem:** If the TanStack Query cache and the Zustand store fall out of sync (e.g., cache expires but store still has old data, or cache is invalidated by another component), the profile/mfa pages display different user data than the header. This also means:

- `mfa.tsx` calls `refetch()` after enabling/disabling MFA to update `user.mfaEnabled` in the query cache — but this does NOT update the Zustand store. The UserMenu still shows stale MFA status.
- On profile page, `user?.id` could be `undefined` if the query cache is empty (e.g., after a background cache eviction), breaking `useListSessions(user?.id)`.

---

## MEDIUM Issues

---

### BUG-08 — Redundant Triple Loading State Creates Infinite Spinner Risk

**Severity:** MEDIUM
**File:** `src/routes/__root.tsx`
**Lines:** 38–44

```typescript
// src/routes/__root.tsx  lines 38–44
const { data: statusData, isLoading: statusLoading } = useAdminStatus();
const { isLoading: queryLoading } = useCurrentUser();
const { isAuthenticated, isLoading: storeLoading } = useAuth();

const loading = statusLoading || queryLoading || storeLoading;
```

Three separate loading booleans are combined with `||`. If any one of them fails to settle, the spinner is shown forever:

- `statusLoading`: Settles when `GET /admin/auth/status` completes. With `retry: false`, a network error settles it quickly. **OK.**
- `queryLoading`: Settles when `GET /admin/auth/me` completes. With `retry: false`, also settles quickly. **OK.**
- `storeLoading`: Starts `true`. Set to `false` ONLY when `setUser()` or `clearUser()` is called inside `queryFn`.

**Race condition:** `queryLoading` becomes `false` when TanStack Query marks the query as settled. `storeLoading` becomes `false` when the side effect inside `queryFn` runs. These happen in different microtasks/render cycles.

**Edge case:** If `queryFn` throws before `clearUser()` is called (e.g., a JS error before line 67 in `auth.ts`), `storeLoading` stays `true` forever. The spinner never goes away.

**Additionally:** `storeLoading` and `queryLoading` are conceptually the same thing — both track whether `/admin/auth/me` has resolved. Having both is redundant and the relationship is fragile.

---

### BUG-09 — Login Page Shows Briefly Before Bootstrap Redirect

**Severity:** MEDIUM
**File:** `src/routes/__root.tsx`
**Lines:** 70–81

```typescript
// src/routes/__root.tsx  lines 70–81
if (loading && !isPublicPage) {
  return <spinner />;
}
if (isPublicPage) {
  return <Outlet />;   // ← renders login/setup IMMEDIATELY, no loading check
}
```

On first-run (bootstrap needed), the sequence is:
1. User hits `/` → redirected to `/login` by the router
2. `__root.tsx` renders: `isPublicPage=true` → immediately renders `<Outlet />` (login form)
3. `useAdminStatus()` completes: `needsBootstrap=true`
4. `useEffect` fires → `navigate({ to: '/setup' })`
5. User sees: **login form briefly flashes, then redirects to setup**

The spinner is only shown for non-public pages (`!isPublicPage`). Public pages render immediately regardless of loading state, causing a visual flash before bootstrap redirect.

---

### BUG-10 — `useAdminMfaDisable` Missing `.json()` — Inconsistent Response Parsing

**Severity:** MEDIUM
**File:** `src/api/auth.ts`
**Lines:** 159–165

```typescript
// src/api/auth.ts  lines 159–165
export function useAdminMfaDisable() {
  return useMutation({
    mutationFn: (code: string) =>
      apiClient.post('admin/auth/mfa/disable', { json: { code } }),
      // ← No .json() — returns raw ky Response object
```

Compare with `useAdminMfaEnable` (line 151–155):
```typescript
mutationFn: (data: MfaEnableRequest) =>
  apiClient.post('admin/auth/mfa/enable', { json: data }).json(),
  // ← Has .json()
```

`useAdminMfaDisable` returns the raw `ky` `Response` object, not parsed JSON. If the backend returns a response body (e.g., `{ success: true }` or error details), they are ignored. The mutation resolves with the raw Response — this is inconsistent and could cause type errors if the response body is ever used.

---

### BUG-11 — `useRevokeSessions` Missing `.json()` — Inconsistent Response Parsing

**Severity:** MEDIUM
**File:** `src/api/users.ts`
**Lines:** 63–64

```typescript
// src/api/users.ts  lines 63–64
mutationFn: (userId: string) =>
  apiClient.delete(`admin/users/${userId}/sessions`),
  // ← No .json() — returns raw ky Response object
```

Same issue as BUG-10. DELETE returning 204 No Content would work fine, but if the backend changes to return a body (e.g., count of revoked sessions), it's silently dropped.

---

### BUG-12 — `staleTime: 5 min` on `/admin/auth/me` Delays Security Propagation

**Severity:** MEDIUM
**File:** `src/api/auth.ts`
**Lines:** 72, 74

```typescript
staleTime: 5 * 60 * 1000,   // 5 min — backend cookie is 15 min
refetchOnWindowFocus: false,
```

The `/admin/auth/me` query has a 5-minute stale time with window focus refetch disabled. This means:

1. If an admin deactivates another admin's account (`isActive: false`), the target user's client continues showing them as authenticated for up to 5 minutes.
2. If an admin changes another user's role, the target user won't get the updated role (and therefore updated permissions) until the cache expires.
3. Server-side session revocation by another admin takes up to 5 minutes to manifest client-side.

The comment "5 min — backend cookie is 15 min" suggests this was intentional to reduce request volume, but it creates a security gap for account management scenarios.

---

## LOW Issues

---

### BUG-13 — `setUser(null)` Duplicates `clearUser()` — Ambiguous API

**Severity:** LOW
**File:** `src/stores/auth-store.ts`
**Lines:** 16–18

```typescript
setUser: (user) => set({ user, isLoading: false }),    // accepts null
clearUser: () => set({ user: null, isLoading: false }), // same as setUser(null)
```

`setUser(null)` and `clearUser()` are identical in behavior. The `setUser` function signature is `(user: AdminUser | null) => void`, making the `null` case overlap with `clearUser()`. No code currently calls `setUser(null)`, but the API is confusing. If someone adds a code path that calls `setUser(null)` thinking it "unsets" the user, they get the same behavior as `clearUser()` with no distinction.

**Fix:** Remove `null` from `setUser`'s parameter type: `setUser: (user: AdminUser) => void`. Force all "unset" paths to go through `clearUser()`.

---

### BUG-14 — `useAdminStatus` `staleTime: Infinity` Persists Stale Bootstrap State

**Severity:** LOW
**File:** `src/api/auth.ts`
**Lines:** 106–109

```typescript
staleTime: Infinity,
gcTime: Infinity,
refetchOnWindowFocus: false,
```

The comment "Bootstrap state never changes mid-session" is correct for production. However, in development/debugging scenarios (DB reset, backend restart), the cached `needsBootstrap: false` persists for the entire browser session. After a DB wipe, the developer would need to manually clear the TanStack Query cache or reload with devtools to trigger the bootstrap flow again.

Minor in production; annoying in development.

---

### BUG-15 — Google OAuth URL Falls Back to Runtime Expression Instead of Constant

**Severity:** LOW
**File:** `src/routes/login.tsx`
**Line:** 278

```typescript
onClick={() => { window.location.href = `${import.meta.env.VITE_GOOGLE_AUTH_URL ?? `${API_BASE_URL}/admin/auth/google`}`; }}
```

The Google OAuth URL is constructed inline in the template literal with a double-nesting fallback. `VITE_GOOGLE_AUTH_URL` is not defined in `constants.ts` or the known `.env` structure. If not set, it falls back to the `API_BASE_URL` constant correctly — but this URL is not listed in the documented environment variables in `CLAUDE.md` or `local.properties.template`.

---

### BUG-16 — Dual Redirect on Authenticated User at `/login`

**Severity:** LOW
**Files:** `src/routes/login.tsx` (lines 61–63), `src/routes/__root.tsx` (lines 64–66)

Two independent `useEffect`s fire when an authenticated user is on `/login`:

**In `login.tsx`:**
```typescript
useEffect(() => {
  if (isAuthenticated) navigate({ to: '/' });
}, [isAuthenticated, navigate]);
```

**In `__root.tsx`:**
```typescript
if (isAuthenticated && isPublicPage) {
  navigate({ to: '/' });
}
```

Both fire in the same render cycle when `isAuthenticated` becomes true. TanStack Router deduplicates same-route navigation, so this is not visibly broken — but it's redundant code that creates confusion about which guard is authoritative.

---

## Summary Table

| ID | Severity | File | Description |
|----|----------|------|-------------|
| BUG-01 | CRITICAL | `api-client.ts` | No token refresh — hard logout after 15 min |
| BUG-02 | CRITICAL | `api/auth.ts` | Side effects in `queryFn` — StrictMode double-execution |
| BUG-03 | HIGH | `login.tsx`, `__root.tsx` | Triple `navigate({ to: '/' })` on login success |
| BUG-04 | HIGH | `api/auth.ts` | Logout error toast destroyed by immediate page reload |
| BUG-05 | HIGH | `settings/profile.tsx` | `mutateAsync` without try/catch → unhandled rejection |
| BUG-06 | HIGH | `settings/profile.tsx` | Revoke All Sessions silently expires own session |
| BUG-07 | HIGH | `profile.tsx`, `mfa.tsx` | Two sources of truth: query cache vs Zustand store |
| BUG-08 | MEDIUM | `__root.tsx` | Triple loading check — infinite spinner if store doesn't settle |
| BUG-09 | MEDIUM | `__root.tsx` | Login page flashes before bootstrap redirect fires |
| BUG-10 | MEDIUM | `api/auth.ts` | `useAdminMfaDisable` missing `.json()` |
| BUG-11 | MEDIUM | `api/users.ts` | `useRevokeSessions` missing `.json()` |
| BUG-12 | MEDIUM | `api/auth.ts` | 5-min stale time delays account deactivation propagation |
| BUG-13 | LOW | `auth-store.ts` | `setUser(null)` duplicates `clearUser()` |
| BUG-14 | LOW | `api/auth.ts` | `staleTime: Infinity` on status query persists through DB resets |
| BUG-15 | LOW | `login.tsx` | Undocumented `VITE_GOOGLE_AUTH_URL` env var |
| BUG-16 | LOW | `login.tsx`, `__root.tsx` | Dual redirect for authenticated users at `/login` |

---

## Recommended Fix Priority

### Phase 1 — Session Stability (CRITICAL)
1. **BUG-01:** Implement token refresh in `api-client.ts` — intercept 401, call refresh endpoint, retry, redirect to login only on refresh failure
2. **BUG-02:** Move `setUser`/`clearUser` out of `queryFn` into `useEffect` observing query state

### Phase 2 — Behavioral Correctness (HIGH)
3. **BUG-03:** Remove duplicate `navigate({ to: '/' })` calls from `onSubmit`/`onMfaSubmit` — rely solely on the reactive guard in `__root.tsx`
4. **BUG-04:** Remove the dead `toast.error` call in logout `onError` (or delay the hard nav)
5. **BUG-05:** Wrap `changePassword.mutateAsync()` in try/catch in `handleChangePassword`
6. **BUG-06:** After `revokeSessions` succeeds for own user, call `clearUser()` + navigate to `/login`
7. **BUG-07:** Replace `useCurrentUser()` in `profile.tsx` and `mfa.tsx` with `useAuth()` as the single source of truth

### Phase 3 — Reliability (MEDIUM)
8. **BUG-08:** Remove `storeLoading` from the `loading` check — it is redundant with `queryLoading`
9. **BUG-09:** Apply the spinner to public pages too when `statusLoading` is true
10. **BUG-10/11:** Add `.json()` to `useAdminMfaDisable` and `useRevokeSessions`
11. **BUG-12:** Reduce or eliminate stale time, or enable `refetchOnWindowFocus` to propagate permission/account changes faster

### Phase 4 — Cleanup (LOW)
12. **BUG-13:** Narrow `setUser` to non-null: `setUser: (user: AdminUser) => void`
13. **BUG-16:** Remove `useEffect` redirect from `login.tsx` — the root guard handles it
14. **BUG-15:** Document `VITE_GOOGLE_AUTH_URL` in `local.properties.template`
