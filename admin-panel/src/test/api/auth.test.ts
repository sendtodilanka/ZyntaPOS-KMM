import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '../mocks/server';
import { createWrapper } from '../utils/wrapper';
import { useAuthStore } from '@/stores/auth-store';
import {
  useCurrentUser,
  useAdminLogin,
  useAdminStatus,
  useAdminBootstrap,
  useAdminLogout,
  useAdminMfaSetup,
  useAdminMfaEnable,
  useAdminMfaDisable,
  useAdminMfaVerify,
  useChangePassword,
  useListSessions,
} from '@/api/auth';
import type { AdminUser } from '@/types/user';

// ── Router mock ──────────────────────────────────────────────────────────────
// useAdminLogout calls useNavigate() which requires a RouterProvider.
// We mock the module so the hook can be tested in isolation without a full
// router context. Each test that cares about navigation asserts on mockNavigate.

const mockNavigate = vi.fn();

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => mockNavigate,
}));

const API_BASE = 'https://api.zyntapos.com';

const mockUser: AdminUser = {
  id: 'user-1',
  email: 'admin@zyntapos.com',
  name: 'System Admin',
  role: 'ADMIN',
  mfaEnabled: false,
  isActive: true,
  lastLoginAt: null,
  createdAt: new Date('2024-01-01T00:00:00Z').getTime(),
};

// Reset auth store state and router mock between tests
beforeEach(() => {
  act(() => {
    useAuthStore.getState().clearUser();
  });
  mockNavigate.mockClear();
});

// ── window.location.href redirect helpers ────────────────────────────────────
// Still needed for the api-client 401 interceptor tests (non-auth endpoints).

const originalLocation = window.location;

beforeEach(() => {
  Object.defineProperty(window, 'location', {
    value: { href: '', pathname: '/' },
    writable: true,
    configurable: true,
  });
});

afterEach(() => {
  Object.defineProperty(window, 'location', {
    value: originalLocation,
    writable: true,
    configurable: true,
  });
});

// ── useCurrentUser ───────────────────────────────────────────────────────────

describe('useCurrentUser', () => {
  it('fetches current user and calls setUser on success', async () => {
    server.use(
      http.get(`${API_BASE}/admin/auth/me`, () => HttpResponse.json(mockUser)),
    );

    const { result } = renderHook(() => useCurrentUser(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual(mockUser);
    expect(useAuthStore.getState().user).toEqual(mockUser);
  });

  it('calls clearUser and enters error state on 401', async () => {
    // Seed a user into the store first so we can verify it gets cleared
    act(() => {
      useAuthStore.getState().setUser(mockUser);
    });

    server.use(
      http.get(`${API_BASE}/admin/auth/me`, () =>
        HttpResponse.json({ message: 'Unauthorized' }, { status: 401 }),
      ),
    );

    const { result } = renderHook(() => useCurrentUser(), { wrapper: createWrapper() });

    // throwOnError: false means the hook settles to error state without throwing to React
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(useAuthStore.getState().user).toBeNull();
  });

  it('does not retry on error', async () => {
    let callCount = 0;
    server.use(
      http.get(`${API_BASE}/admin/auth/me`, () => {
        callCount++;
        return HttpResponse.json({ message: 'Unauthorized' }, { status: 401 });
      }),
    );

    const { result } = renderHook(() => useCurrentUser(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isError).toBe(true));
    // retry: false — should be called exactly once
    expect(callCount).toBe(1);
  });
});

// ── useAdminLogin ────────────────────────────────────────────────────────────

describe('useAdminLogin', () => {
  it('calls setUser when login succeeds with a full user response', async () => {
    server.use(
      http.post(`${API_BASE}/admin/auth/login`, () =>
        HttpResponse.json({ user: mockUser, expiresIn: 3600 }),
      ),
    );

    const { result } = renderHook(() => useAdminLogin(), { wrapper: createWrapper() });

    act(() => {
      result.current.mutate({ email: 'admin@zyntapos.com', password: 'secret' });
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(useAuthStore.getState().user).toEqual(mockUser);
  });

  it('does NOT call setUser when response contains pendingToken (MFA required)', async () => {
    server.use(
      http.post(`${API_BASE}/admin/auth/login`, () =>
        HttpResponse.json({ mfaRequired: true, pendingToken: 'tok_pending' }),
      ),
    );

    const { result } = renderHook(() => useAdminLogin(), { wrapper: createWrapper() });

    act(() => {
      result.current.mutate({ email: 'admin@zyntapos.com', password: 'secret' });
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    // Store should remain empty — setUser must not have been called
    expect(useAuthStore.getState().user).toBeNull();
  });

  it('does NOT call setUser when login fails', async () => {
    server.use(
      http.post(`${API_BASE}/admin/auth/login`, () =>
        HttpResponse.json({ message: 'Invalid credentials' }, { status: 401 }),
      ),
    );

    const { result } = renderHook(() => useAdminLogin(), { wrapper: createWrapper() });

    act(() => {
      result.current.mutate({ email: 'admin@zyntapos.com', password: 'wrong' });
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(useAuthStore.getState().user).toBeNull();
  });
});

// ── useAdminStatus ───────────────────────────────────────────────────────────

describe('useAdminStatus', () => {
  it('returns needsBootstrap: false for an established system', async () => {
    server.use(
      http.get(`${API_BASE}/admin/auth/status`, () =>
        HttpResponse.json({ needsBootstrap: false }),
      ),
    );

    const { result } = renderHook(() => useAdminStatus(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual({ needsBootstrap: false });
  });

  it('returns needsBootstrap: true for a fresh installation', async () => {
    server.use(
      http.get(`${API_BASE}/admin/auth/status`, () =>
        HttpResponse.json({ needsBootstrap: true }),
      ),
    );

    const { result } = renderHook(() => useAdminStatus(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual({ needsBootstrap: true });
  });

  it('surfaces server errors', async () => {
    server.use(
      http.get(`${API_BASE}/admin/auth/status`, () =>
        HttpResponse.json({ message: 'Service Unavailable' }, { status: 503 }),
      ),
    );

    const { result } = renderHook(() => useAdminStatus(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useAdminBootstrap ────────────────────────────────────────────────────────

describe('useAdminBootstrap', () => {
  it('returns the created admin user on success', async () => {
    server.use(
      http.post(`${API_BASE}/admin/auth/bootstrap`, () =>
        HttpResponse.json(mockUser, { status: 201 }),
      ),
    );

    const { result } = renderHook(() => useAdminBootstrap(), { wrapper: createWrapper() });

    act(() => {
      result.current.mutate({
        email: 'admin@zyntapos.com',
        name: 'System Admin',
        password: 'StrongP@ss1!',
      });
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(mockUser);
  });

  it('surfaces 400 validation errors', async () => {
    server.use(
      http.post(`${API_BASE}/admin/auth/bootstrap`, () =>
        HttpResponse.json({ message: 'Validation failed' }, { status: 400 }),
      ),
    );

    const { result } = renderHook(() => useAdminBootstrap(), { wrapper: createWrapper() });

    act(() => {
      result.current.mutate({ email: '', name: '', password: '' });
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useAdminLogout ───────────────────────────────────────────────────────────

describe('useAdminLogout', () => {
  it('calls clearUser and navigates to /login on success', async () => {
    act(() => {
      useAuthStore.getState().setUser(mockUser);
    });

    server.use(
      http.post(`${API_BASE}/admin/auth/logout`, () =>
        new HttpResponse(null, { status: 200 }),
      ),
    );

    const { result } = renderHook(() => useAdminLogout(), { wrapper: createWrapper() });

    act(() => {
      result.current.mutate();
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(useAuthStore.getState().user).toBeNull();
    expect(mockNavigate).toHaveBeenCalledWith({ to: '/login' });
  });

  it('still calls clearUser and navigates to /login when the server returns an error', async () => {
    act(() => {
      useAuthStore.getState().setUser(mockUser);
    });

    server.use(
      http.post(`${API_BASE}/admin/auth/logout`, () =>
        HttpResponse.json({ message: 'Internal Server Error' }, { status: 500 }),
      ),
    );

    const { result } = renderHook(() => useAdminLogout(), { wrapper: createWrapper() });

    act(() => {
      result.current.mutate();
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(useAuthStore.getState().user).toBeNull();
    expect(mockNavigate).toHaveBeenCalledWith({ to: '/login' });
  });
});

// ── useAdminMfaSetup ─────────────────────────────────────────────────────────

describe('useAdminMfaSetup', () => {
  it('returns secret, qrCodeUrl and backupCodes on success', async () => {
    const mfaSetupPayload = {
      secret: 'JBSWY3DPEHPK3PXP',
      qrCodeUrl: 'data:image/png;base64,abc',
      backupCodes: ['AAAA-BBBB', 'CCCC-DDDD'],
    };

    server.use(
      http.post(`${API_BASE}/admin/auth/mfa/setup`, () => HttpResponse.json(mfaSetupPayload)),
    );

    const { result } = renderHook(() => useAdminMfaSetup(), { wrapper: createWrapper() });

    act(() => {
      result.current.mutate();
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(mfaSetupPayload);
  });

  it('surfaces server errors', async () => {
    server.use(
      http.post(`${API_BASE}/admin/auth/mfa/setup`, () =>
        HttpResponse.json({ message: 'MFA already enabled' }, { status: 409 }),
      ),
    );

    const { result } = renderHook(() => useAdminMfaSetup(), { wrapper: createWrapper() });

    act(() => {
      result.current.mutate();
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useAdminMfaEnable ────────────────────────────────────────────────────────

describe('useAdminMfaEnable', () => {
  it('resolves successfully with a valid TOTP code', async () => {
    server.use(
      http.post(`${API_BASE}/admin/auth/mfa/enable`, () =>
        new HttpResponse(null, { status: 200 }),
      ),
    );

    const { result } = renderHook(() => useAdminMfaEnable(), { wrapper: createWrapper() });

    act(() => {
      result.current.mutate({ secret: 'JBSWY3DPEHPK3PXP', code: '123456' });
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('enters error state on an invalid code', async () => {
    server.use(
      http.post(`${API_BASE}/admin/auth/mfa/enable`, () =>
        HttpResponse.json({ message: 'Invalid TOTP code' }, { status: 400 }),
      ),
    );

    const { result } = renderHook(() => useAdminMfaEnable(), { wrapper: createWrapper() });

    act(() => {
      result.current.mutate({ secret: 'JBSWY3DPEHPK3PXP', code: '000000' });
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useAdminMfaDisable ───────────────────────────────────────────────────────

describe('useAdminMfaDisable', () => {
  it('resolves successfully with a valid code', async () => {
    server.use(
      http.post(`${API_BASE}/admin/auth/mfa/disable`, () =>
        new HttpResponse(null, { status: 200 }),
      ),
    );

    const { result } = renderHook(() => useAdminMfaDisable(), { wrapper: createWrapper() });

    act(() => {
      result.current.mutate('654321');
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('enters error state on an invalid code', async () => {
    server.use(
      http.post(`${API_BASE}/admin/auth/mfa/disable`, () =>
        HttpResponse.json({ message: 'Invalid code' }, { status: 400 }),
      ),
    );

    const { result } = renderHook(() => useAdminMfaDisable(), { wrapper: createWrapper() });

    act(() => {
      result.current.mutate('000000');
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useAdminMfaVerify ────────────────────────────────────────────────────────

describe('useAdminMfaVerify', () => {
  it('calls setUser with the returned user on success', async () => {
    const mfaUser: AdminUser = { ...mockUser, mfaEnabled: true };

    server.use(
      http.post(`${API_BASE}/admin/auth/mfa/verify`, () =>
        HttpResponse.json({ user: mfaUser, expiresIn: 3600 }),
      ),
    );

    const { result } = renderHook(() => useAdminMfaVerify(), { wrapper: createWrapper() });

    act(() => {
      result.current.mutate({ code: '123456', pendingToken: 'tok_pending' });
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(useAuthStore.getState().user).toEqual(mfaUser);
  });

  it('enters error state on an invalid code', async () => {
    server.use(
      http.post(`${API_BASE}/admin/auth/mfa/verify`, () =>
        HttpResponse.json({ message: 'Invalid TOTP code' }, { status: 401 }),
      ),
    );

    const { result } = renderHook(() => useAdminMfaVerify(), { wrapper: createWrapper() });

    act(() => {
      result.current.mutate({ code: '000000', pendingToken: 'tok_pending' });
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(useAuthStore.getState().user).toBeNull();
  });
});

// ── useChangePassword ────────────────────────────────────────────────────────

describe('useChangePassword', () => {
  it('resolves successfully when passwords are valid', async () => {
    server.use(
      http.post(`${API_BASE}/admin/auth/change-password`, () =>
        new HttpResponse(null, { status: 200 }),
      ),
    );

    const { result } = renderHook(() => useChangePassword(), { wrapper: createWrapper() });

    act(() => {
      result.current.mutate({
        currentPassword: 'OldP@ss1!',
        newPassword: 'NewP@ss2!',
      });
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('enters error state when the current password is wrong', async () => {
    server.use(
      http.post(`${API_BASE}/admin/auth/change-password`, () =>
        HttpResponse.json({ message: 'Incorrect current password' }, { status: 400 }),
      ),
    );

    const { result } = renderHook(() => useChangePassword(), { wrapper: createWrapper() });

    act(() => {
      result.current.mutate({ currentPassword: 'wrong', newPassword: 'NewP@ss2!' });
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

// ── useListSessions ──────────────────────────────────────────────────────────

describe('useListSessions', () => {
  it('fetches sessions for a given userId', async () => {
    const mockSessions = [
      {
        id: 'session-1',
        userAgent: 'Mozilla/5.0',
        ipAddress: '192.168.1.1',
        createdAt: Date.now() - 3600_000,
        expiresAt: Date.now() + 3600_000,
      },
    ];

    server.use(
      http.get(`${API_BASE}/admin/users/:userId/sessions`, () =>
        HttpResponse.json(mockSessions),
      ),
    );

    const { result } = renderHook(() => useListSessions('user-1'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(1);
    expect(result.current.data![0].id).toBe('session-1');
  });

  it('does NOT fetch when userId is undefined', () => {
    const { result } = renderHook(() => useListSessions(undefined), {
      wrapper: createWrapper(),
    });

    // enabled: !!userId — query should remain idle when userId is falsy
    expect(result.current.fetchStatus).toBe('idle');
    expect(result.current.isLoading).toBe(false);
  });

  it('does NOT fetch when userId is an empty string', () => {
    const { result } = renderHook(() => useListSessions(''), {
      wrapper: createWrapper(),
    });

    expect(result.current.fetchStatus).toBe('idle');
  });

  it('surfaces server errors', async () => {
    server.use(
      http.get(`${API_BASE}/admin/users/:userId/sessions`, () =>
        HttpResponse.json({ message: 'Forbidden' }, { status: 403 }),
      ),
    );

    const { result } = renderHook(() => useListSessions('user-1'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
